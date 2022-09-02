package io.dataspray.aws.cdk;

import com.google.common.collect.Streams;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class StackDeployer {

    private static final Logger logger = LoggerFactory.getLogger(StackDeployer.class);

    public static final String ZIP_PACKAGING = "zip";
    public static final String FILE_PACKAGING = "file";
    public static final String IMAGE_PACKAGING = "container-image";

    private static final String BOOTSTRAP_VERSION_OUTPUT = "BootstrapVersion";
    private static final String BUCKET_NAME_OUTPUT = "BucketName";
    private static final String BUCKET_DOMAIN_NAME_OUTPUT = "BucketDomainName";
    private static final String ASSET_PREFIX_SEPARATOR = "||";
    private static final int MAX_TEMPLATE_SIZE = 50 * 1024;

    private final CloudFormationClient client;
    private final Path cloudAssemblyDirectory;
    private final ResolvedEnvironment environment;
    private final ToolkitConfiguration toolkitConfiguration;
    private final FileAssetPublisher fileAssetPublisher;
    private final DockerImageAssetPublisher dockerImagePublisher;
    private final boolean isInteractive;

    public StackDeployer(Path cloudAssemblyDirectory,
                         ResolvedEnvironment environment,
                         ToolkitConfiguration toolkitConfiguration,
                         FileAssetPublisher fileAssetPublisher,
                         DockerImageAssetPublisher dockerImagePublisher,
                         boolean isInteractive) {
        this.cloudAssemblyDirectory = cloudAssemblyDirectory;
        this.environment = environment;
        this.toolkitConfiguration = toolkitConfiguration;
        this.fileAssetPublisher = fileAssetPublisher;
        this.dockerImagePublisher = dockerImagePublisher;
        this.isInteractive = isInteractive;
        this.client = CloudFormationClient.builder()
                .region(environment.getRegion())
                .credentialsProvider(StaticCredentialsProvider.create(environment.getCredentials()))
                .build();
    }

    public Stack deploy(StackDefinition stackDefinition, Map<String, String> parameters, Map<String, String> tags) {
        String stackName = stackDefinition.getStackName();
        logger.info("Deploying '{}' stack", stackName);

        Map<String, ParameterValue> stackParameters = new HashMap<>();
        Stack deployedStack = Stacks.findStack(client, stackName).orElse(null);
        if (deployedStack != null) {
            if (Stacks.isInProgress(deployedStack)) {
                logger.info("Waiting until stack '{}' reaches stable state", deployedStack.stackName());
                deployedStack = awaitCompletion(deployedStack);
            }
            if (deployedStack.stackStatus() == StackStatus.ROLLBACK_COMPLETE || deployedStack.stackStatus() == StackStatus.ROLLBACK_FAILED) {
                logger.warn("The stack '{}' is in {} state after unsuccessful creation. The stack will be deleted " +
                        "and re-created.", stackName, deployedStack.stackStatus());
                deployedStack = Stacks.awaitCompletion(client, Stacks.deleteStack(client, deployedStack.stackName()));
            }
            if (Stacks.isFailed(deployedStack)) {
                throw StackDeploymentException.builder(stackName, environment)
                        .withCause("The stack '" + stackName + "' is in the failed state " + deployedStack.stackStatus())
                        .build();
            }
            if (deployedStack.stackStatus() != StackStatus.DELETE_COMPLETE) {
                deployedStack.parameters().forEach(p -> stackParameters.put(p.parameterKey(), ParameterValue.unchanged()));
            }
        }

        Streams.concat(stackDefinition.getParameterValues().entrySet().stream(), parameters.entrySet().stream())
                .filter(parameter -> parameter.getKey() != null && parameter.getValue() != null)
                .forEach(parameter -> stackParameters.put(parameter.getKey(), ParameterValue.value(parameter.getValue())));


        Map<String, ParameterValue> effectiveParameters = stackParameters.entrySet().stream()
                .filter(parameter -> stackDefinition.getParameters().containsKey(parameter.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        TemplateRef templateRef = getTemplateRef(stackDefinition);

        List<String> missingParameters = stackDefinition.getParameters().values().stream()
                .filter(parameterDefinition -> parameterDefinition.getDefaultValue() == null)
                .filter(parameterDefinition -> !effectiveParameters.containsKey(parameterDefinition.getName()))
                .map(ParameterDefinition::getName)
                .collect(Collectors.toList());

        if (!missingParameters.isEmpty()) {
            throw StackDeploymentException.builder(stackName, environment)
                    .withCause("The values for the following template parameters are missing: " + String.join(", ", missingParameters))
                    .build();
        }

        boolean updated = true;
        Stack stack;
        if (deployedStack != null && deployedStack.stackStatus() != StackStatus.DELETE_COMPLETE) {
            try {
                stack = Stacks.updateStack(client, stackName, templateRef, effectiveParameters, tags);
            } catch (CloudFormationException e) {
                AwsErrorDetails errorDetails = e.awsErrorDetails();
                if (!errorDetails.errorCode().equals("ValidationError") ||
                        !errorDetails.errorMessage().startsWith("No updates are to be performed")) {
                    throw e;
                }
                logger.info("No changes of the '{}' stack are detected. The deployment will be skipped", stackName);
                stack = deployedStack;
                updated = false;
            }
        } else {
            stack = Stacks.createStack(client, stackName, templateRef, effectiveParameters, tags);
        }

        if (updated) {
            if (!Stacks.isCompleted(stack)) {
                logger.info("Waiting until '{}' reaches stable state", stackName);
                stack = awaitCompletion(stack);
            }
            if (Stacks.isFailed(stack)) {
                throw StackDeploymentException.builder(stackName, environment)
                        .withCause("The deployment has failed: " + stack.stackStatus())
                        .build();
            }
            if (Stacks.isRolledBack(stack)) {
                throw StackDeploymentException.builder(stackName, environment)
                        .withCause("The deployment has been unsuccessful, the stack has been rolled back to its previous state")
                        .build();
            }
            logger.info("The stack '{}' has been successfully deployed", stackName);
        }

        return stack;
    }

    private TemplateRef getTemplateRef(StackDefinition stackDefinition) {
        Path templateFile = cloudAssemblyDirectory.resolve(stackDefinition.getTemplateFile());
        TemplateRef templateRef;
        try {
            templateRef = readTemplateBody(templateFile, MAX_TEMPLATE_SIZE)
                    .map(TemplateRef::fromString)
                    .orElse(null);
        } catch (IOException e) {
            throw StackDeploymentException.builder(stackDefinition.getStackName(), environment)
                    .withCause("Unable to read the template file: " + templateFile)
                    .withCause(e)
                    .build();
        }

        if (templateRef == null) {
            Toolkit toolkit = getToolkit(stackDefinition);
            String contentHash;
            try {
                contentHash = hash(templateFile.toFile());
            } catch (IOException e) {
                throw StackDeploymentException.builder(stackDefinition.getStackName(), environment)
                        .withCause("Unable to read the template file: " + templateFile)
                        .withCause(e)
                        .build();
            }

            String objectName = "cdk/" + stackDefinition.getStackName() + "/" + contentHash + ".json";

            try {
                fileAssetPublisher.publish(templateFile, objectName, toolkit.getBucketName(), environment);
            } catch (IOException e) {
                throw StackDeploymentException.builder(stackDefinition.getStackName(), environment)
                        .withCause("An error occurred while uploading the template file to the deployment bucket")
                        .withCause(e)
                        .build();
            } catch (CdkException e) {
                throw StackDeploymentException.builder(stackDefinition.getStackName(), environment)
                        .withCause(e.getMessage())
                        .withCause(e.getCause())
                        .build();
            } catch (Exception e) {
                throw StackDeploymentException.builder(stackDefinition.getStackName(), environment)
                        .withCause(e)
                        .build();
            }

            templateRef = TemplateRef.fromUrl("https://" + toolkit.getBucketDomainName() + "/" + objectName);
        }

        return templateRef;
    }

    public Optional<Stack> destroy(StackDefinition stackDefinition) {
        Stack stack = Stacks.findStack(client, stackDefinition.getStackName()).orElse(null);
        if (stack != null && stack.stackStatus() != StackStatus.DELETE_COMPLETE) {
            logger.info("The stack '${} is being deleted, awaiting until the operation is completed", stackDefinition.getStackName());
            stack = awaitCompletion(Stacks.deleteStack(client, stack.stackId()));
            if (stack.stackStatus() != StackStatus.DELETE_COMPLETE) {
                throw new CdkException("The deletion of '" + stackDefinition.getStackName() + "' stack has failed: " + stack.stackStatus());
            }
            logger.info("The stack '{}' has been successfully deleted", stack.stackName());
        } else {
            logger.warn("The generated template for the stack '{}' doesn't have any resources defined. The deployment " +
                    "will be skipped", stackDefinition.getStackName());
        }

        return Optional.ofNullable(stack);
    }

    private String hash(File file) throws IOException {
        return com.google.common.io.Files.asByteSource(file).hash(Hashing.sha256()).toString();
    }

    private Optional<String> readTemplateBody(Path template, long limit) throws IOException {
        byte[] buffer = new byte[1024 * 8];
        ByteArrayOutputStream templateBody = new ByteArrayOutputStream();
        int bytesRead;
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(template.toFile()))) {
            while (templateBody.size() <= limit && (bytesRead = inputStream.read(buffer)) != -1) {
                templateBody.write(buffer, 0, bytesRead);
            }
        }

        if (templateBody.size() > limit) {
            return Optional.empty();
        }

        return Optional.of(templateBody.toString(StandardCharsets.UTF_8.name()));
    }

    private Toolkit getToolkit(StackDefinition stack) {
        Stack toolkitStack = Stacks.findStack(client, toolkitConfiguration.getStackName()).orElse(null);
        if (toolkitStack != null && Stacks.isInProgress(toolkitStack)) {
            logger.info("Waiting until toolkit stack reaches stable state, environment={}, stackName={}",
                    environment, toolkitConfiguration.getStackName());
            toolkitStack = awaitCompletion(toolkitStack);
        }

        if (toolkitStack == null || toolkitStack.stackStatus() == StackStatus.DELETE_COMPLETE ||
                toolkitStack.stackStatus() == StackStatus.ROLLBACK_COMPLETE) {
            throw StackDeploymentException.builder(stack.getStackName(), environment)
                    .withCause("The stack " + stack.getStackName() + " requires a bootstrap. Did you forged to " +
                            "add 'bootstrap' goal to the execution")
                    .build();
        }

        if (Stacks.isFailed(toolkitStack)) {
            throw StackDeploymentException.builder(stack.getStackName(), environment)
                    .withCause("The toolkit stack is in failed state. Please make sure that the toolkit stack is " +
                            "stable before the deployment")
                    .build();
        }

        Map<String, String> outputs = toolkitStack.outputs().stream()
                .collect(Collectors.toMap(Output::outputKey, Output::outputValue));

        if (stack.getRequiredToolkitStackVersion() != null) {
            Integer toolkitStackVersion = Optional.ofNullable(outputs.get(BOOTSTRAP_VERSION_OUTPUT))
                    .map(Integer::parseInt)
                    .orElse(0);
            if (toolkitStackVersion < stack.getRequiredToolkitStackVersion()) {
                throw StackDeploymentException.builder(stack.getStackName(), environment)
                        .withCause("The toolkit stack version is lower than the minimum version required by the " +
                                "stack. Please update the toolkit stack or add 'bootstrap' goal to the plugin " +
                                "execution if you want the plugin to automatically create or update toolkit stack")
                        .build();
            }
        }

        String bucketName = outputs.get(BUCKET_NAME_OUTPUT);
        if (bucketName == null) {
            throw StackDeploymentException.builder(stack.getStackName(), environment)
                    .withCause("The toolkit stack " + toolkitConfiguration.getStackName() + " doesn't have a " +
                            "required output '" + BUCKET_NAME_OUTPUT + "'")
                    .build();
        }

        String bucketDomainName = outputs.get(BUCKET_DOMAIN_NAME_OUTPUT);
        if (bucketDomainName == null) {
            throw StackDeploymentException.builder(stack.getStackName(), environment)
                    .withCause("The toolkit stack " + toolkitConfiguration.getStackName() + " doesn't have a " +
                            "required output '" + BUCKET_DOMAIN_NAME_OUTPUT + "'")
                    .build();
        }

        return new Toolkit(bucketName, bucketDomainName);
    }

    private Stack awaitCompletion(Stack stack) {
        Stack completedStack;
        if (logger.isInfoEnabled() && isInteractive) {
            completedStack = Stacks.awaitCompletion(client, stack, new LoggingStackEventListener());
        } else {
            completedStack = Stacks.awaitCompletion(client, stack);
        }
        return completedStack;
    }

}
