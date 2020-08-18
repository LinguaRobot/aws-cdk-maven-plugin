package io.linguarobot.aws.cdk.maven;

import com.google.common.collect.Streams;
import com.google.common.hash.Hashing;
import io.linguarobot.aws.cdk.AssetMetadata;
import io.linguarobot.aws.cdk.ContainerAssetData;
import io.linguarobot.aws.cdk.ContainerImageAssetMetadata;
import io.linguarobot.aws.cdk.FileAssetData;
import io.linguarobot.aws.cdk.FileAssetMetadata;
import org.apache.maven.settings.Settings;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class StackDeployer {

    private static final Logger logger = LoggerFactory.getLogger(StackDeployer.class);

    private static final String BOOTSTRAP_VERSION_OUTPUT = "BootstrapVersion";
    private static final String BUCKET_NAME_OUTPUT = "BucketName";
    private static final String BUCKET_DOMAIN_NAME_OUTPUT = "BucketDomainName";
    private static final String ASSET_PREFIX_SEPARATOR = "||";
    private static final String ZIP_PACKAGING = "zip";
    private static final String FILE_PACKAGING = "file";
    private static final String IMAGE_PACKAGING = "container-image";
    private static final int MAX_TEMPLATE_SIZE = 50 * 1024;

    private final CloudFormationClient client;
    private final Path cloudAssemblyDirectory;
    private final ResolvedEnvironment environment;
    private final ToolkitConfiguration toolkitConfiguration;
    private final FileAssetPublisher fileAssetPublisher;
    private final DockerImageAssetPublisher dockerImagePublisher;
    private final Settings settings;

    public StackDeployer(Path cloudAssemblyDirectory,
                         ResolvedEnvironment environment,
                         ToolkitConfiguration toolkitConfiguration,
                         FileAssetPublisher fileAssetPublisher,
                         DockerImageAssetPublisher dockerImagePublisher,
                         Settings settings) {
        this.cloudAssemblyDirectory = cloudAssemblyDirectory;
        this.environment = environment;
        this.toolkitConfiguration = toolkitConfiguration;
        this.fileAssetPublisher = fileAssetPublisher;
        this.dockerImagePublisher = dockerImagePublisher;
        this.settings = settings;
        this.client = CloudFormationClient.builder()
                .region(environment.getRegion())
                .credentialsProvider(StaticCredentialsProvider.create(environment.getCredentials()))
                .build();
    }

    public Stack deploy(StackDefinition stackDefinition, Map<String, String> parameters) {
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

        Toolkit toolkit = null;
        List<Runnable> publishmentTasks = new ArrayList<>();
        for (AssetMetadata asset : stackDefinition.getAssets()) {
            switch (asset.getPackaging()) {
                case FILE_PACKAGING:
                case ZIP_PACKAGING:
                    if (toolkit == null) {
                        toolkit = getToolkit(stackDefinition);
                    }
                    FileAssetMetadata fileAsset = (FileAssetMetadata) asset;
                    String bucketName = toolkit.getBucketName();
                    String prefix = generatePrefix(fileAsset);
                    String filename = generateFilename(fileAsset);
                    FileAssetData fileData = fileAsset.getData();
                    stackParameters.put(fileData.getS3BucketParameter(), ParameterValue.value(toolkit.getBucketName()));
                    stackParameters.put(fileData.getS3KeyParameter(), ParameterValue.value(String.join(ASSET_PREFIX_SEPARATOR, prefix, filename)));
                    stackParameters.put(fileData.getArtifactHashParameter(), ParameterValue.value(fileAsset.getSourceHash()));

                    publishmentTasks.add(() -> {
                        Path file = cloudAssemblyDirectory.resolve(fileAsset.getPath());
                        try {
                            fileAssetPublisher.publish(file, prefix + filename, bucketName);
                        } catch (IOException e) {
                            throw StackDeploymentException.builder(stackName, environment)
                                    .withCause("An error occurred while publishing the file asset " + file)
                                    .withCause(e)
                                    .build();
                        }
                    });
                    break;
                case IMAGE_PACKAGING:
                    ContainerImageAssetMetadata imageAsset = (ContainerImageAssetMetadata) asset;
                    publishmentTasks.add(createImagePublishmentTask(stackName, imageAsset));
                    break;
                default:
                    throw StackDeploymentException.builder(stackName, environment)
                            .withCause("The asset packaging " + asset.getPackaging() + " is not supported. You might " +
                                    "need to update the plugin in order to support the assets of this type")
                            .build();
            }
        }

        Map<String, ParameterValue> effectiveParameters = stackParameters.entrySet().stream()
                .filter(parameter -> stackDefinition.getParameters().containsKey(parameter.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        TemplateRef templateRef = getTemplateRef(stackDefinition, publishmentTasks);

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
        try {
            publishmentTasks.forEach(Runnable::run);
        } catch (CdkPluginException e) {
            throw StackDeploymentException.builder(stackName, environment)
                    .withCause(e.getMessage())
                    .withCause(e.getCause())
                    .build();
        } catch (Exception e) {
            throw StackDeploymentException.builder(stackName, environment)
                    .withCause(e)
                    .build();
        }

        boolean updated = true;
        Stack stack;
        if (deployedStack != null && deployedStack.stackStatus() != StackStatus.DELETE_COMPLETE) {
            try {
                stack = Stacks.updateStack(client, stackName, templateRef, effectiveParameters);
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
            stack = Stacks.createStack(client, stackName, templateRef, effectiveParameters);
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

    private Runnable createImagePublishmentTask(String stackName, ContainerImageAssetMetadata imageAsset) {
        Path contextDirectory = cloudAssemblyDirectory.resolve(imageAsset.getPath());
        if (!Files.exists(contextDirectory)) {
            throw StackDeploymentException.builder(stackName, environment)
                    .withCause("The Docker context directory doesn't exist: " + contextDirectory)
                    .build();
        }

        Path dockerfilePath;
        ContainerAssetData imageData = imageAsset.getData();
        if (imageData.getFile() != null) {
            dockerfilePath = contextDirectory.resolve(imageData.getFile());
            if (!Files.exists(dockerfilePath)) {
                throw StackDeploymentException.builder(stackName, environment)
                        .withCause("The Dockerfile doesn't exist: " + dockerfilePath)
                        .build();
            }
        } else {
            dockerfilePath = findDockerfile(contextDirectory)
                    .orElseThrow(() -> StackDeploymentException.builder(stackName, environment)
                            .withCause("Unable to find Dockerfile in the context directory " + contextDirectory)
                            .build());
        }

        return () -> {
            String localTag = String.join("-", "cdkasset", imageAsset.getId().toLowerCase());
            ImageBuild imageBuild = ImageBuild.builder()
                    .withContextDirectory(contextDirectory)
                    .withDockerfile(dockerfilePath)
                    .withImageTag(localTag)
                    .withArguments(imageData.getBuildArguments())
                    .withTarget(imageData.getTarget())
                    .build();
            dockerImagePublisher.publish(imageData.getRepositoryName(), imageData.getImageTag(), imageBuild);
        };
    }

    private Optional<Path> findDockerfile(Path contextDirectory) {
        Path dockerfile = contextDirectory.resolve("Dockerfile");
        if (!Files.exists(dockerfile)) {
            dockerfile = contextDirectory.resolve("dockerfile");
        }

        return Optional.of(dockerfile).filter(Files::exists);
    }

    private TemplateRef getTemplateRef(StackDefinition stackDefinition, List<Runnable> deploymentTasks) {
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
            deploymentTasks.add(() -> {
                try {
                    fileAssetPublisher.publish(templateFile, objectName, toolkit.getBucketName());
                } catch (IOException e) {
                    throw StackDeploymentException.builder(stackDefinition.getStackName(), environment)
                            .withCause("An error occurred while uploading the template file to the deployment bucket")
                            .withCause(e)
                            .build();
                }
            });

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
                throw new CdkPluginException("The deletion of '" + stackDefinition.getStackName() + "' stack has failed: " + stack.stackStatus());
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

    private String generateFilename(FileAssetMetadata fileAsset) {
        StringBuilder fileName = new StringBuilder();
        fileName.append(fileAsset.getSourceHash());
        if (fileAsset.getPackaging().equals(ZIP_PACKAGING)) {
            fileName.append('.').append(ZIP_PACKAGING);
        } else {
            int extensionDelimiter = fileAsset.getPath().lastIndexOf('.');
            if (extensionDelimiter > 0) {
                fileName.append(fileAsset.getPath().substring(extensionDelimiter));
            }
        }

        return fileName.toString();
    }

    private String generatePrefix(FileAssetMetadata fileAsset) {
        StringBuilder prefix = new StringBuilder();
        prefix.append("assets").append('/');
        if (!fileAsset.getId().equals(fileAsset.getSourceHash())) {
            prefix.append(fileAsset.getId()).append('/');
        }

        return prefix.toString();
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
        if (logger.isInfoEnabled() && settings.isInteractiveMode()) {
            completedStack = Stacks.awaitCompletion(client, stack, new LoggingStackEventListener());
        } else {
            completedStack = Stacks.awaitCompletion(client, stack);
        }
        return completedStack;
    }

}
