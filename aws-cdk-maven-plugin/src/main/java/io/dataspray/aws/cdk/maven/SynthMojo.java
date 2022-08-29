package io.dataspray.aws.cdk.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import io.dataspray.aws.cdk.maven.context.AmiContextProviderMapper;
import io.dataspray.aws.cdk.maven.context.AvailabilityZonesContextProviderMapper;
import io.dataspray.aws.cdk.maven.context.AwsClientProvider;
import io.dataspray.aws.cdk.maven.context.AwsClientProviderBuilder;
import io.dataspray.aws.cdk.maven.context.ContextProviderMapper;
import io.dataspray.aws.cdk.maven.context.HostedZoneContextProviderMapper;
import io.dataspray.aws.cdk.maven.context.SsmContextProviderMapper;
import io.dataspray.aws.cdk.maven.context.VpcNetworkContextProviderMapper;
import io.dataspray.aws.cdk.maven.node.LinuxNodeInstaller;
import io.dataspray.aws.cdk.maven.node.NodeClient;
import io.dataspray.aws.cdk.maven.node.NodeInstallationException;
import io.dataspray.aws.cdk.maven.node.NodeInstaller;
import io.dataspray.aws.cdk.maven.node.NodeVersion;
import io.dataspray.aws.cdk.maven.node.UnixNodeInstaller;
import io.dataspray.aws.cdk.maven.node.WindowsNodeInstaller;
import io.dataspray.aws.cdk.maven.process.DefaultProcessRunner;
import io.dataspray.aws.cdk.maven.process.ProcessContext;
import io.dataspray.aws.cdk.maven.process.ProcessExecutionException;
import io.dataspray.aws.cdk.maven.process.ProcessRunner;
import io.dataspray.aws.cdk.maven.runtime.Synthesizer;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.ContextEnabled;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.cloudassembly.schema.AssemblyManifest;
import software.amazon.awscdk.cloudassembly.schema.ContextProvider;
import software.amazon.awscdk.cloudassembly.schema.Manifest;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.ssm.SsmClient;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Synthesizes CloudFormation templates for a CDK application.
 */
@Mojo(
        name = "synth",
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class SynthMojo extends AbstractCdkMojo implements ContextEnabled {

    private static final Logger logger = LoggerFactory.getLogger(DeployMojo.class);

    private static final String CDK_CONTEXT_FILE_NAME = "cdk.context.json";
    private static final NodeVersion MINIMUM_REQUIRED_NODE_VERSION = NodeVersion.of(10, 3, 0);
    private static final NodeVersion INSTALLED_NODE_VERSION = NodeVersion.of(12, 17, 0);
    private static final String OUTPUT_DIRECTORY_VARIABLE_NAME = "CDK_OUTDIR";
    private static final String DEFAULT_ACCOUNT_VARIABLE_NAME = "CDK_DEFAULT_ACCOUNT";
    private static final String DEFAULT_REGION_VARIABLE_NAME = "CDK_DEFAULT_REGION";
    private static final String CONTEXT_VARIABLE_NAME = "CDK_CONTEXT_JSON";
    private static final String PATH_VARIABLE_NAME = "PATH";

    @Component
    private ToolchainManager toolchainManager;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * Current Maven session.
     */
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    /**
     * Path to the local repository that will be used to store Node.js environment if it's not available to the plugin.
     */
    @Parameter(defaultValue = "${settings.localRepository}", readonly = true)
    private File localRepositoryDirectory;

    /**
     * The name of the application class defining your cloud infrastructure. The application class must either extend
     * {@link software.amazon.awscdk.core.App} or define a main method which would create an instance of {@code App},
     * define the constructs associated with it and call {@link software.amazon.awscdk.core.App#synth()} method in order
     * to produce a cloud assembly with CloudFormation templates.
     */
    @Parameter(property = "aws.cdk.app", required = true)
    private String app;

    /**
     * Arguments to be passed to the application.
     */
    @Parameter(property = "aws.cdk.arguments")
    private List<String> arguments;

    private ProcessRunner processRunner;
    private Map<ContextProvider, ContextProviderMapper> contextProviders;

    @Override
    public void execute(Path cloudAssemblyDirectory, EnvironmentResolver environmentResolver) {
        this.processRunner = new DefaultProcessRunner(project.getBasedir());
        this.contextProviders = initContextProviders(environmentResolver);
        synthesize(app, arguments != null ? arguments : Collections.emptyList(), cloudAssemblyDirectory, environmentResolver);
    }

    private Map<ContextProvider, ContextProviderMapper> initContextProviders(EnvironmentResolver environmentResolver) {
        AwsClientProvider awsClientProvider = new AwsClientProviderBuilder()
                .withClientFactory(Ec2Client.class, env -> buildClient(Ec2Client.builder(), environmentResolver.resolve(env)))
                .withClientFactory(SsmClient.class, env -> buildClient(SsmClient.builder(), environmentResolver.resolve(env)))
                .withClientFactory(Route53Client.class, env -> {
                    ResolvedEnvironment resolvedEnvironment = environmentResolver.resolve(env);
                    return Route53Client.builder()
                            .region(Region.AWS_GLOBAL)
                            .credentialsProvider(StaticCredentialsProvider.create(resolvedEnvironment.getCredentials()))
                            .build();
                })
                .build();

        Map<ContextProvider, ContextProviderMapper> contextProviders = new HashMap<>();
        contextProviders.put(ContextProvider.AVAILABILITY_ZONE_PROVIDER, new AvailabilityZonesContextProviderMapper(awsClientProvider));
        contextProviders.put(ContextProvider.SSM_PARAMETER_PROVIDER, new SsmContextProviderMapper(awsClientProvider));
        contextProviders.put(ContextProvider.HOSTED_ZONE_PROVIDER, new HostedZoneContextProviderMapper(awsClientProvider));
        contextProviders.put(ContextProvider.VPC_PROVIDER, new VpcNetworkContextProviderMapper(awsClientProvider));
        contextProviders.put(ContextProvider.AMI_PROVIDER, new AmiContextProviderMapper(awsClientProvider));
        return contextProviders;
    }

    private <B extends AwsClientBuilder<B, C>, C> C buildClient(B builder, ResolvedEnvironment environment) {
        return builder.region(environment.getRegion())
                .credentialsProvider(StaticCredentialsProvider.create(environment.getCredentials()))
                .build();
    }

    protected AssemblyManifest synthesize(String app, List<String> arguments, Path outputDirectory, EnvironmentResolver environmentResolver) {
        Map<String, String> environment;
        if (SystemUtils.IS_OS_WINDOWS) {
            environment = System.getenv().entrySet().stream()
                    .map(variable -> Pair.of(variable.getKey().toUpperCase(), variable.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            environment = new HashMap<>(System.getenv());
        }

        NodeVersion nodeVersion = getInstalledNodeVersion().orElse(null);
        if (nodeVersion == null || nodeVersion.compareTo(MINIMUM_REQUIRED_NODE_VERSION) < 0) {
            if (nodeVersion == null) {
                logger.info("Node.js is not installed. Using the Node.js from the local Maven repository");
            } else {
                logger.info("The minimum required version of Node.js is {}, however {} is installed. Using the Node.js " +
                        "from the local Maven repository", MINIMUM_REQUIRED_NODE_VERSION, nodeVersion);
            }

            NodeClient node = getNodeInstaller().install(INSTALLED_NODE_VERSION);
            environment.compute(PATH_VARIABLE_NAME, (name, path) -> Stream.of(node.getPath().toString(), path)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(File.pathSeparator)));
        }

        environment.computeIfAbsent(OUTPUT_DIRECTORY_VARIABLE_NAME, v -> outputDirectory.toString());
        environment.computeIfAbsent(DEFAULT_REGION_VARIABLE_NAME, v -> environmentResolver.getDefaultRegion().id());
        if (environmentResolver.getDefaultAccount() != null) {
            environment.computeIfAbsent(DEFAULT_ACCOUNT_VARIABLE_NAME, v -> environmentResolver.getDefaultAccount());
        }

        JsonObject context = readContext();

        logger.info("Synthesizing the cloud assembly for the '{}' application", app);
        AssemblyManifest cloudManifest = synthesize(app, arguments, outputDirectory, environment, context);

        while (cloudManifest.getMissing() != null && !cloudManifest.getMissing().isEmpty()) {
            JsonObjectBuilder contextBuilder = Json.createObjectBuilder(context);
            cloudManifest.getMissing().forEach(missingContext -> {
                ContextProvider provider = missingContext.getProvider();
                String key = missingContext.getKey();

                ContextProviderMapper contextProviderMapper = contextProviders.get(provider);
                if (contextProviderMapper == null) {
                    throw new CdkPluginException("Unable to find a context provider for '" + provider +
                            "'. Please consider updating the version of the plugin");
                }

                Object contextProps = JsiiUtil.getProperty(missingContext, "props", contextProviderMapper.getContextType());
                JsonValue contextValue;
                try {
                    contextValue = contextProviderMapper.getContextValue(contextProps);
                } catch (Exception e) {
                    throw new CdkPluginException("An error occurred while resolving context value for the " +
                            "key '" + key + "' using '" + provider + "' provider: " + e.getMessage());
                }
                if (contextValue == null) {
                    throw new CdkPluginException("Unable to resolve context value for the key '" + key +
                            "' using '" + provider + "' provider");
                }
                contextBuilder.add(key, contextValue);
            });
            context = contextBuilder.build();
            cloudManifest = synthesize(app, arguments, outputDirectory, environment, context);
        }

        if (!context.isEmpty()) {
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            File effectiveContextFile = outputDirectory.resolve("cdk.context.json").toFile();
            try (JsonWriter jsonWriter = writerFactory.createWriter(new BufferedWriter(new FileWriter(effectiveContextFile)))) {
                jsonWriter.write(context);
            } catch (IOException e) {
                throw new CdkPluginException("Unable to write effective context file to the " + outputDirectory);
            }
        }

        logger.info("The cloud assembly has been successfully synthesized to {}", outputDirectory);
        return cloudManifest;
    }

    private JsonObject readContext() {
        File contextFile = new File(project.getBasedir(), CDK_CONTEXT_FILE_NAME);

        JsonObject context;
        if (contextFile.exists()) {
            try {
                context = OBJECT_MAPPER.readValue(contextFile, JsonObject.class);
            } catch (IOException e) {
                throw new CdkPluginException("Unable to read the runtime context from the " + contextFile);
            }
        } else {
            context = JsonValue.EMPTY_JSON_OBJECT;
        }

        return context;
    }

    private AssemblyManifest synthesize(String app, List<String> arguments, Path outputDirectory, Map<String, String> environment, JsonObject context) {
        Map<String, String> appEnvironment;
        if (context.isEmpty()) {
            appEnvironment = environment;
        } else {
            appEnvironment = ImmutableMap.<String, String>builder()
                    .putAll(environment)
                    .put(CONTEXT_VARIABLE_NAME, toString(context))
                    .build();
        }

        int exitCode;
        List<String> appExecutionCommand = buildAppExecutionCommand(app, arguments);
        ProcessContext processContext = ProcessContext.builder()
                .withEnvironment(appEnvironment)
                .build();
        try {
            exitCode = processRunner.run(appExecutionCommand, processContext);
        } catch (ProcessExecutionException e) {
            throw new CdkPluginException("The synthesis has failed", e);
        }

        if (exitCode != 0 || !Files.exists(outputDirectory)) {
            throw new CdkPluginException("The synthesis has failed: the output directory doesn't exist");
        }

        return Manifest.loadAssemblyManifest(outputDirectory.resolve("manifest.json").toString());
    }

    private String toString(JsonObject context) {
        try {
            return OBJECT_MAPPER.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new CdkPluginException("Failed to serialize the runtime context", e);
        }
    }

    private List<String> buildAppExecutionCommand(String app, List<String> arguments) {
        String java = Optional.ofNullable(this.toolchainManager.getToolchainFromBuildContext("jdk", this.session))
                .map(toolchain -> toolchain.findTool("java"))
                .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        String classpath = Streams.concat(
                project.getArtifacts().stream().map(Artifact::getFile).map(File::toString),
                Stream.of(project.getBuild().getOutputDirectory()),
                project.getResources().stream().map(FileSet::getDirectory),
                Stream.of(Synthesizer.class.getProtectionDomain().getCodeSource().getLocation().getFile())
        ).collect(Collectors.joining(File.pathSeparator));

        return Stream.concat(Stream.of(java, "-cp", classpath, Synthesizer.class.getName(), app), arguments.stream())
                .collect(Collectors.toList());
    }

    private Optional<NodeVersion> getInstalledNodeVersion() {
        try {
            return Optional.of(processRunner.run(ImmutableList.of("node", "--version")))
                    .flatMap(NodeVersion::parse);
        } catch (ProcessExecutionException e) {
            return Optional.empty();
        }
    }

    private NodeInstaller getNodeInstaller() {
        String osName = System.getProperty("os.name");
        Path localRepositoryDirectory = this.localRepositoryDirectory.toPath();
        NodeInstaller nodeInstaller;

        if (osName.startsWith("Windows")) {
            nodeInstaller = new WindowsNodeInstaller(processRunner, localRepositoryDirectory);
        } else if (osName.startsWith("Mac")) {
            nodeInstaller = new UnixNodeInstaller(processRunner, localRepositoryDirectory, "darwin", "x64");
        } else if (osName.startsWith("SunOS")) {
            nodeInstaller = new UnixNodeInstaller(processRunner, localRepositoryDirectory, "sunos", "x64");
        } else if (osName.startsWith("Linux") || osName.startsWith("LINUX")) {
            nodeInstaller = new LinuxNodeInstaller(processRunner, localRepositoryDirectory);
        } else if (osName.startsWith("AIX")) {
            nodeInstaller = new UnixNodeInstaller(processRunner, localRepositoryDirectory, "aix", "ppc64");
        } else {
            throw new NodeInstallationException("The platform is not supported: " + osName);
        }

        return nodeInstaller;
    }

}
