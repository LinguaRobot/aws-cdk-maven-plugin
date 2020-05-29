package io.linguarobot.aws.cdk.maven;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.cloudassembly.schema.ContainerImageAssetMetadataEntry;
import software.amazon.awscdk.cloudassembly.schema.FileAssetMetadataEntry;
import software.amazon.awscdk.cxapi.CloudFormationStackArtifact;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.jsii.JsiiClient;
import software.amazon.jsii.JsiiEngine;
import software.amazon.jsii.JsiiObjectRef;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StackDeployer {

    private static final Logger logger = LoggerFactory.getLogger(StackDeployer.class);

    private static final String BOOTSTRAP_VERSION_OUTPUT = "BootstrapVersion";
    private static final String BUCKET_NAME_OUTPUT = "BucketName";
    private static final String BUCKET_DOMAIN_NAME_OUTPUT = "BucketDomainName";
    private static final int MAX_TOOLKIT_STACK_VERSION = 1;
    private static final String ASSET_PREFIX_SEPARATOR = "||";
    private static final String ZIP_PACKAGING = "zip";
    private static final String FILE_PACKAGING = "file";
    private static final int MAX_TEMPLATE_SIZE = 50 * 1024;
    private static final int DEFAULT_BOOTSTRAP_STACK_VERSION = getDefaultBootstrapStackVersion();

    private final CloudFormationClient client;
    private final Path cloudAssemblyDirectory;
    private final ResolvedEnvironment environment;
    private final ToolkitConfiguration toolkitConfiguration;
    private final FileAssetPublisher fileAssetPublisher;
    private final DockerImageAssetPublisher dockerImagePublisher;

    private Toolkit toolkit;

    public StackDeployer(Path cloudAssemblyDirectory,
                         ResolvedEnvironment environment,
                         ToolkitConfiguration toolkitConfiguration,
                         FileAssetPublisher fileAssetPublisher,
                         DockerImageAssetPublisher dockerImagePublisher) {
        this.cloudAssemblyDirectory = cloudAssemblyDirectory;
        this.environment = environment;
        this.toolkitConfiguration = toolkitConfiguration;
        this.fileAssetPublisher = fileAssetPublisher;
        this.dockerImagePublisher = dockerImagePublisher;
        this.client = CloudFormationClient.builder()
                .region(environment.getRegion())
                .credentialsProvider(StaticCredentialsProvider.create(environment.getCredentials()))
                .build();
    }

    public Stack deploy(CloudFormationStackArtifact stackArtifact) {
        String stackName = stackArtifact.getStackName();
        logger.info("Deploying '{}' stack", stackName);

        Map<String, ParameterValue> parameters = new HashMap<>();
        Stack deployedStack = Stacks.findStack(client, stackName).orElse(null);
        if (deployedStack != null) {
            if (deployedStack.stackStatus() == StackStatus.ROLLBACK_IN_PROGRESS) {
                logger.info("Waiting until rollback operation on the stack '{}' is completed after unsuccessful " +
                        "creation", deployedStack.stackName());
                deployedStack = awaitCompletion(deployedStack);
            }
            if (deployedStack.stackStatus() == StackStatus.ROLLBACK_COMPLETE || deployedStack.stackStatus() == StackStatus.ROLLBACK_FAILED) {
                logger.warn("The stack '{}' is in {} state after unsuccessful creation. The stack will be deleted " +
                        "and re-created.", stackName, deployedStack.stackStatus());
                deployedStack = Stacks.deleteStack(client, deployedStack.stackName());
            }
            if (Stacks.isInProgress(deployedStack)) {
                logger.info("Waiting until stack '{}' reaches stable state", deployedStack.stackName());
                deployedStack = awaitCompletion(deployedStack);
            }
            if (Stacks.isFailed(deployedStack)) {
                throw StackDeploymentException.builder(stackName, environment)
                        .withCause("The stack '" + stackName + "' is in the failed state " + deployedStack.stackStatus())
                        .build();
            }

            if (deployedStack.stackStatus() != StackStatus.DELETE_COMPLETE) {
                deployedStack.parameters().forEach(p -> parameters.put(p.parameterKey(), ParameterValue.unchanged()));
            }
        }

        List<Runnable> publishmentTasks = new ArrayList<>();
        Map<String, ContainerImageAssetMetadataEntry> imageAssets = null;
        for (Object asset : stackArtifact.getAssets()) {
            JsiiEngine jsiiEngine = JsiiEngine.getInstance();
            JsiiObjectRef objectRef = jsiiEngine.nativeToObjRef(asset);
            JsiiClient client = jsiiEngine.getClient();
            String id = client.getPropertyValue(objectRef, "id").asText();
            String packaging = client.getPropertyValue(objectRef, "packaging").asText();
            switch (packaging) {
                case FILE_PACKAGING:
                case ZIP_PACKAGING:
                    if (this.toolkit == null) {
                        int requiredToolkitStackVersion = Optional.ofNullable(stackArtifact.getRequiresBootstrapStackVersion())
                                .map(Number::intValue)
                                .orElse(DEFAULT_BOOTSTRAP_STACK_VERSION);
                        this.toolkit = bootstrap(requiredToolkitStackVersion);
                    }
                    FileAssetMetadataEntry fileAsset = JsiiObjects.cast(objectRef, FileAssetMetadataEntry.class);
                    String prefix = generatePrefix(fileAsset);
                    String filename = generateFilename(fileAsset);
                    parameters.put(fileAsset.getS3BucketParameter(), ParameterValue.value(toolkit.getBucketName()));
                    parameters.put(fileAsset.getS3KeyParameter(), ParameterValue.value(String.join(ASSET_PREFIX_SEPARATOR, prefix, filename)));
                    parameters.put(fileAsset.getArtifactHashParameter(), ParameterValue.value(fileAsset.getSourceHash()));

                    publishmentTasks.add(() -> {
                        Path file = cloudAssemblyDirectory.resolve(fileAsset.getPath());
                        try {
                            fileAssetPublisher.publish(file, prefix + filename, toolkit.getBucketName());
                        } catch (IOException e) {
                            throw StackDeploymentException.builder(stackName, environment)
                                    .withCause("An error occurred while publishing the file asset " + file)
                                    .withCause(e)
                                    .build();
                        }
                    });
                    break;
                case "container-image":
                    ContainerImageAssetMetadataEntry imageAsset;
                    if (JsiiObjects.isInstanceOf(objectRef, ContainerImageAssetMetadataEntry.class)) {
                        imageAsset = JsiiObjects.cast(objectRef, ContainerImageAssetMetadataEntry.class);
                    } else {
                        if (imageAssets == null) {
                            imageAssets = loadImageAssets(stackName);
                        }
                        imageAsset = imageAssets.get(id);
                        if (imageAsset == null) {
                            throw StackDeploymentException.builder(stackName, environment)
                                    .withCause("The metadata for the image asset " + id + " is not available")
                                    .build();
                        }
                    }
                    publishmentTasks.add(createImagePublishmentTask(stackName, imageAsset));
                    break;
                default:
                    throw StackDeploymentException.builder(stackName, environment)
                            .withCause("The asset packaging " + packaging + " is not supported. You might need to " +
                                    "update the plugin in order to support the assets of this type")
                            .build();
            }
        }

        Map<String, ParameterDefinition> stackParameters = getParameterDefinitions(stackArtifact);

        Map<String, ParameterValue> effectiveParameters = parameters.entrySet().stream()
                .filter(parameter -> stackParameters.containsKey(parameter.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        TemplateRef templateRef = getTemplateRef(stackArtifact, publishmentTasks);

        List<String> missingParameters = stackParameters.values().stream()
                .filter(parameterDefinition -> !parameterDefinition.getDefaultValue().isPresent())
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
        if (deployedStack != null) {
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

            logger.info("The stack '{}' has been successfully deployed", stackName);
        }

        return stack;
    }

    private Map<String, ContainerImageAssetMetadataEntry> loadImageAssets(String stackName) {
        final File manifestFile = cloudAssemblyDirectory.resolve("manifest.json").toFile();
        try (JsonReader reader = Json.createReader(new BufferedReader(new FileReader(manifestFile)))) {
            return Optional.of(reader.readObject())
                    .map(manifest -> manifest.getJsonObject("artifacts"))
                    .map(artifacts -> artifacts.getJsonObject(stackName))
                    .map(stackArtifact -> stackArtifact.getJsonObject("metadata"))
                    .map(metadata -> metadata.getJsonArray("/" + stackName))
                    .map(stackMetadata -> stackMetadata.stream()
                            .map(JsonValue::asJsonObject)
                            .filter(metadata -> metadata.getString("type").equals("aws:cdk:asset"))
                            .map(assetMetadata -> toContainerImageAssetMetadataEntry(assetMetadata.getJsonObject("data")))
                            .collect(Collectors.toMap(ContainerImageAssetMetadataEntry::getId, Function.identity())))
                    .orElse(Collections.emptyMap());
        } catch (FileNotFoundException e) {
            throw new CdkPluginException("Unable to load the manifest: " + manifestFile);
        }
    }

    private ContainerImageAssetMetadataEntry toContainerImageAssetMetadataEntry(JsonObject data) {
        DefaultContainerImageAssetMetadataEntry containerImageAsset = new DefaultContainerImageAssetMetadataEntry();
        containerImageAsset.setId(data.getString("id"));
        containerImageAsset.setPackaging(data.getString("packaging"));
        containerImageAsset.setPath(data.getString("path"));
        containerImageAsset.setSourceHash(data.getString("sourceHash"));
        if (data.containsKey("buildArgs")) {
            Map<String, String> buildArgs = data.getJsonObject("buildArgs").entrySet().stream()
                    .filter(arg -> arg.getValue().getValueType() == JsonValue.ValueType.STRING)
                    .map(arg -> Maps.immutableEntry(arg.getKey(),((JsonString) arg.getValue()).getString()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            containerImageAsset.setBuildArgs(buildArgs);
        }
        containerImageAsset.setFile(data.getString("file", null));
        containerImageAsset.setImageNameParameter(data.getString("imageNameParameter", null));
        containerImageAsset.setImageTag(data.getString("imageTag", null));
        containerImageAsset.setRepositoryName(data.getString("repositoryName", null));
        containerImageAsset.setTarget(data.getString("target", null));
        return containerImageAsset;
    }

    private Runnable createImagePublishmentTask(String stackName, ContainerImageAssetMetadataEntry imageAsset) {
        Path contextDirectory = cloudAssemblyDirectory.resolve(imageAsset.getPath());
        if (!Files.exists(contextDirectory)) {
            throw StackDeploymentException.builder(stackName, environment)
                    .withCause("The Docker context directory doesn't exist: " + contextDirectory)
                    .build();
        }

        Path dockerfilePath;
        if (imageAsset.getFile() != null) {
            dockerfilePath = contextDirectory.resolve(imageAsset.getFile());
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
            Map<String, String> arguments = imageAsset.getBuildArgs() != null ? imageAsset.getBuildArgs() : ImmutableMap.of();
            ImageBuild imageBuild = new ImageBuild(contextDirectory, dockerfilePath, localTag, arguments, imageAsset.getTarget());
            dockerImagePublisher.publish(imageAsset.getRepositoryName(), imageAsset.getImageTag(), imageBuild);
        };
    }

    private Optional<Path> findDockerfile(Path contextDirectory) {
        Path dockerfile = contextDirectory.resolve("Dockerfile");
        if (!Files.exists(dockerfile)) {
            dockerfile = contextDirectory.resolve("dockerfile");
        }

        return Optional.of(dockerfile).filter(Files::exists);
    }

    private TemplateRef getTemplateRef(CloudFormationStackArtifact stackArtifact, List<Runnable> deploymentTasks) {
        String stackName = stackArtifact.getStackName();
        Path templateFile = cloudAssemblyDirectory.resolve(stackArtifact.getTemplateFile());
        TemplateRef templateRef;
        try {
            templateRef = readTemplateBody(templateFile, MAX_TEMPLATE_SIZE)
                    .map(TemplateRef::fromString)
                    .orElse(null);
        } catch (IOException e) {
            throw StackDeploymentException.builder(stackName, environment)
                    .withCause("Unable to read the template file: " + templateFile)
                    .withCause(e)
                    .build();
        }

        if (templateRef == null) {
            if (toolkit == null) {
                toolkit = bootstrap(DEFAULT_BOOTSTRAP_STACK_VERSION);
            }
            String contentHash;
            try {
                contentHash = hash(templateFile.toFile());
            } catch (IOException e) {
                throw StackDeploymentException.builder(stackName, environment)
                        .withCause("Unable to read the template file: " + templateFile)
                        .withCause(e)
                        .build();
            }

            String objectName = "cdk/" + stackName + "/" + contentHash + ".json";
            deploymentTasks.add(() -> {
                try {
                    fileAssetPublisher.publish(templateFile, objectName, toolkit.getBucketName());
                } catch (IOException e) {
                    throw StackDeploymentException.builder(stackName, environment)
                            .withCause("An error occurred while uploading the template file to the deployment bucket")
                            .withCause(e)
                            .build();
                }
            });

            templateRef = TemplateRef.fromUrl("https://" + toolkit.getBucketDomainName() + "/" + objectName);
        }

        return templateRef;
    }

    public Optional<Stack> destroy(CloudFormationStackArtifact stackArtifact) {
        String stackName = stackArtifact.getStackName();
        Stack stack = Stacks.findStack(client, stackName).orElse(null);
        if (stack != null && stack.stackStatus() != StackStatus.DELETE_COMPLETE) {
            logger.info("Destroying '{}' stack", stackName);
            stack = Stacks.deleteStack(client, stack.stackId());
        } else {
            logger.warn("The generated template for the stack '{}' doesn't have any resources defined. The deployment " +
                    "will be skipped", stackName);
        }

        return Optional.ofNullable(stack);
    }

    private String hash(File file) throws IOException {
        return com.google.common.io.Files.asByteSource(file).hash(Hashing.sha256()).toString();
    }

    private String generateFilename(FileAssetMetadataEntry fileAsset) {
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

    private String generatePrefix(FileAssetMetadataEntry fileAsset) {
        StringBuilder prefix = new StringBuilder();
        prefix.append("assets").append('/');
        if (!fileAsset.getId().equals(fileAsset.getSourceHash())) {
            prefix.append(fileAsset.getId()).append('/');
        }

        return prefix.toString();
    }

    private Map<String, ParameterDefinition> getParameterDefinitions(CloudFormationStackArtifact stackArtifact) {
        Map<String, Map<String, Object>> parameters = Optional.of(stackArtifact)
                .map(artifact -> (Map<String, Object>) artifact.getTemplate())
                .map(template -> (Map<String, Map<String, Object>>) template.get("Parameters"))
                .orElse(Collections.emptyMap());

        return parameters.entrySet().stream()
                .map(parameter -> {
                    String name = parameter.getKey();
                    String defaultValue = (String) parameter.getValue().get("Default");
                    return new ParameterDefinition(name, defaultValue);
                })
                .collect(Collectors.toMap(ParameterDefinition::getName, Function.identity()));
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

    private Toolkit bootstrap(int version) {
        if (version > MAX_TOOLKIT_STACK_VERSION) {
            throw BootstrapException.deploymentError(toolkitConfiguration.getStackName(), environment)
                    .withCause("One of the stacks requires toolkit stack version " + version + " which is not " +
                            "supported by the plugin. Please try to update the plugin version in order to fix the problem")
                    .build();
        }

        String toolkitStackName = toolkitConfiguration.getStackName();
        Stack toolkitStack = Stacks.findStack(client, toolkitStackName).orElse(null);

        if (toolkitStack != null) {
            if (Stacks.isInProgress(toolkitStack)) {
                logger.info("Waiting until toolkit stack reaches stable state, environment={}, stackName={}",
                        environment, toolkitStackName);
                toolkitStack = awaitCompletion(toolkitStack);
            }

            if (toolkitStack.stackStatus() == StackStatus.ROLLBACK_COMPLETE || toolkitStack.stackStatus() == StackStatus.ROLLBACK_FAILED) {
                logger.warn("The toolkit stack is in {} state. The stack will be deleted and a new one will be" +
                        " created, environment={}, stackName={}", StackStatus.ROLLBACK_COMPLETE, environment, toolkitStackName);

                toolkitStack = Stacks.deleteStack(client, toolkitStack.stackId());
                toolkitStack = awaitCompletion(toolkitStack);

            }

            if (Stacks.isFailed(toolkitStack)) {
                throw BootstrapException.deploymentError(toolkitStackName, environment)
                        .withCause("The toolkit stack is in failed state: " + toolkitStack.stackStatus())
                        .build();
            }
        }

        int toolkitStackVersion = Stream.of(toolkitStack)
                .filter(stack -> stack != null && stack.stackStatus() != StackStatus.DELETE_COMPLETE)
                .filter(Stack::hasOutputs)
                .flatMap(stack -> stack.outputs().stream())
                .filter(output -> output.outputKey().equals(BOOTSTRAP_VERSION_OUTPUT))
                .map(Output::outputValue)
                .map(Integer::parseInt)
                .findAny()
                .orElse(version);

        if (toolkitStackVersion > MAX_TOOLKIT_STACK_VERSION) {
            throw BootstrapException.invalidStateError(toolkitStackName, environment)
                    .withCause("The deployment toolkit stack version is newer than the latest supported by the plugin." +
                            " Please try to update the plugin version in order to fix the problem")
                    .build();
        }

        if (toolkitStack == null || toolkitStack.stackStatus() == StackStatus.DELETE_COMPLETE || toolkitStackVersion < version) {
            TemplateRef toolkitTemplate;
            try {
                toolkitTemplate = getToolkitTemplateRef(version)
                        .orElseThrow(() -> BootstrapException.deploymentError(toolkitStackName, environment)
                                .withCause("The required bootstrap stack version " + version + " is not supported by " +
                                        "the plugin. Please try to update the plugin version in order to fix the problem")
                                .build());
            } catch (IOException e) {
                throw BootstrapException.deploymentError(toolkitStackName, environment)
                        .withCause("Unable to load a template for the toolkit stack")
                        .withCause(e)
                        .build();
            }

            if (toolkitStack != null && toolkitStack.stackStatus() != StackStatus.DELETE_COMPLETE) {
                logger.info("Deploying a newer version of the toolkit stack (updating from {} to {}), environment={}, " +
                                "stackName={}", toolkitStackVersion, version, environment, toolkitStackName);
                // TODO: consider the case when some of the parameters may be removed in the newer version
                Map<String, ParameterValue> parameters = Stream.of(toolkitStack)
                        .filter(Stack::hasParameters)
                        .flatMap(s -> s.parameters().stream())
                        .collect(Collectors.toMap(Parameter::parameterKey, p -> ParameterValue.unchanged()));
                toolkitStack = Stacks.updateStack(client, toolkitStackName, toolkitTemplate, parameters);
            } else {
                logger.info("The toolkit stack doesn't exist. Deploying a new one, environment={}, stackName={}",
                        environment, toolkitStackName);
                toolkitStack = Stacks.createStack(client, toolkitStackName, toolkitTemplate);
            }

            logger.info("Waiting until the toolkit stack reaches stable state, environment={}, stackName={}",
                    environment, toolkitStackName);
            toolkitStack = awaitCompletion(toolkitStack);

            if (toolkitStack.stackStatus() != StackStatus.CREATE_COMPLETE && toolkitStack.stackStatus() != StackStatus.UPDATE_COMPLETE) {
                throw BootstrapException.deploymentError(toolkitStackName, environment)
                        .withCause("The stack didn't reach stable state: " + toolkitStack.stackStatus())
                        .build();
            }
        }

        Map<String, Output> outputs = toolkitStack.outputs().stream()
                .collect(Collectors.toMap(Output::outputKey, Function.identity()));
        String bucketName = Optional.ofNullable(outputs.get(BUCKET_NAME_OUTPUT))
                .map(Output::outputValue)
                .orElseThrow(() -> BootstrapException.invalidStateError(toolkitStackName, environment)
                        .withCause("The toolkit stack doesn't have a required output '" + BUCKET_NAME_OUTPUT + "'")
                        .build());
        String bucketDomainName = Optional.ofNullable(outputs.get(BUCKET_DOMAIN_NAME_OUTPUT))
                .map(Output::outputValue)
                .orElseThrow(() -> BootstrapException.invalidStateError(toolkitStackName, environment)
                        .withCause("The toolkit stack doesn't have a required output '" + BUCKET_DOMAIN_NAME_OUTPUT + "'")
                        .build());

        return new Toolkit(bucketName, bucketDomainName);
    }

    private Optional<TemplateRef> getToolkitTemplateRef(Number version) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String templateFileName = String.format("bootstrap-template-v%d.yaml", version);
        InputStream inputStream = classLoader.getResourceAsStream(templateFileName);
        if (inputStream == null) {
            return Optional.empty();
        }

        try (
                InputStream is = inputStream;
                Reader reader = new BufferedReader(new InputStreamReader(is))
        ) {
            return Optional.of(TemplateRef.fromString(CharStreams.toString(reader)));
        }
    }

    private Stack awaitCompletion(Stack stack) {
        Stack completedStack;
        if (logger.isInfoEnabled()) {
            completedStack = Stacks.awaitCompletion(client, stack, new LoggingStackEventListener());
        } else {
            completedStack = Stacks.awaitCompletion(client, stack);
        }
        return completedStack;
    }

    private static Integer getDefaultBootstrapStackVersion() {
        String newBootstrapEnabled = System.getenv("CDK_NEW_BOOTSTRAP");
        return newBootstrapEnabled != null && !newBootstrapEnabled.isEmpty() ? 1 : 0;
    }

}
