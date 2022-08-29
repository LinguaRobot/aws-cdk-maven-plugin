package io.dataspray.aws.cdk.maven;

import com.google.common.io.CharStreams;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Deploys toolkit stacks required by the CDK application.
 */
@Mojo(name = "bootstrap", defaultPhase = LifecyclePhase.DEPLOY)
public class BootstrapMojo extends AbstractCloudActionMojo {

    private static final Logger logger = LoggerFactory.getLogger(BootstrapMojo.class);

    /**
     * Corresponds to the version defined in bootstrap-template.yaml
     *
     * To upgrade:
     * - Pull latest bootstrap-template.yaml from https://raw.githubusercontent.com/aws/aws-cdk/main/packages/aws-cdk/lib/api/bootstrap/bootstrap-template.yaml
     * - Update this version to match the newly updated template
     */
    private static final int TOOLKIT_STACK_VERSION = 14;
    private static final int DEFAULT_BOOTSTRAP_STACK_VERSION = getDefaultBootstrapStackVersion();
    private static final String BOOTSTRAP_VERSION_OUTPUT = "BootstrapVersion";

    @Parameter(defaultValue = "${settings}", required = true, readonly = true)
    private Settings settings;

    /**
     * The name of the CDK toolkit stack.
     */
    @Parameter(property = "aws.cdk.toolkit.stack.name", defaultValue = "CDKToolkit")
    private String toolkitStackName;

    /**
     * Stacks, for which bootstrapping will be performed if it's required.
     */
    @Parameter(property = "aws.cdk.stacks")
    private Set<String> stacks;

    @Override
    public void execute(CloudDefinition cloudDefinition, EnvironmentResolver environmentResolver) {
        Map<String, Integer> environments = cloudDefinition.getStacks().stream()
                .filter(stack -> this.stacks == null || this.stacks.isEmpty() || this.stacks.contains(stack.getStackName()))
                .collect(Collectors.groupingBy(
                        StackDefinition::getEnvironment,
                        Collectors.reducing(
                                DEFAULT_BOOTSTRAP_STACK_VERSION,
                                stack -> ObjectUtils.firstNonNull(stack.getRequiredToolkitStackVersion(), DEFAULT_BOOTSTRAP_STACK_VERSION),
                                Math::max
                        )
                ));

        environments.forEach((environment, version) -> {
            ResolvedEnvironment resolvedEnvironment = environmentResolver.resolve(environment);
            if (version > TOOLKIT_STACK_VERSION) {
                throw BootstrapException.deploymentError(toolkitStackName, resolvedEnvironment)
                        .withCause("One of the stacks requires toolkit stack version " + version + " which is not " +
                                "supported by the plugin. Please try to update the plugin version in order to fix the problem")
                        .build();
            }
            bootstrap(resolvedEnvironment, version);
        });
    }

    private void bootstrap(ResolvedEnvironment environment, int version) {
        CloudFormationClient client = CloudFormationClient.builder()
                .region(environment.getRegion())
                .credentialsProvider(environment.getCredentialsProvider())
                .build();

        Stack toolkitStack = Stacks.findStack(client, toolkitStackName).orElse(null);
        if (toolkitStack != null) {
            if (Stacks.isInProgress(toolkitStack)) {
                logger.info("Waiting until toolkit stack reaches stable state, environment={}, stackName={}",
                        environment, toolkitStackName);
                toolkitStack = awaitCompletion(client, toolkitStack);
            }
            if (toolkitStack.stackStatus() == StackStatus.ROLLBACK_COMPLETE || toolkitStack.stackStatus() == StackStatus.ROLLBACK_FAILED) {
                logger.warn("The toolkit stack is in {} state. The stack will be deleted and a new one will be" +
                        " created, environment={}, stackName={}", StackStatus.ROLLBACK_COMPLETE, environment, toolkitStackName);
                toolkitStack = awaitCompletion(client, Stacks.deleteStack(client, toolkitStack.stackId()));

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

        if (toolkitStackVersion > TOOLKIT_STACK_VERSION) {
            throw BootstrapException.invalidStateError(toolkitStackName, environment)
                    .withCause("The deployed toolkit stack version is newer than the latest supported by the plugin." +
                            " Please try to update the plugin version in order to fix the problem")
                    .build();
        }

        if (toolkitStack == null || toolkitStack.stackStatus() == StackStatus.DELETE_COMPLETE || toolkitStackVersion < version) {
            TemplateRef toolkitTemplate;
            try {
                toolkitTemplate = getToolkitTemplateRef()
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
                        .collect(Collectors.toMap(software.amazon.awssdk.services.cloudformation.model.Parameter::parameterKey, p -> ParameterValue.unchanged()));
                toolkitStack = Stacks.updateStack(client, toolkitStackName, toolkitTemplate, parameters);
            } else {
                logger.info("The toolkit stack doesn't exist. Deploying a new one, environment={}, stackName={}",
                        environment, toolkitStackName);
                toolkitStack = Stacks.createStack(client, toolkitStackName, toolkitTemplate);
            }
            if (!Stacks.isCompleted(toolkitStack)) {
                logger.info("Waiting until the toolkit stack reaches stable state, environment={}, stackName={}",
                        environment, toolkitStackName);
                toolkitStack = awaitCompletion(client, toolkitStack);
            }
            if (Stacks.isFailed(toolkitStack)) {
                throw BootstrapException.deploymentError(toolkitStackName, environment)
                        .withCause("The deployment has failed: " + toolkitStack.stackStatus())
                        .build();
            }
            if (Stacks.isRolledBack(toolkitStack)) {
                throw BootstrapException.deploymentError(toolkitStackName, environment)
                        .withCause("The deployment has been unsuccessful, the stack has been rolled back to its previous state")
                        .build();
            }
            logger.info("The toolkit stack has been successfully deployed, stackName={}", toolkitStackName);
        }
    }

    private Optional<TemplateRef> getToolkitTemplateRef() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("bootstrap-template.yaml");
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

    private Stack awaitCompletion(CloudFormationClient client, Stack stack) {
        Stack completedStack;
        if (logger.isInfoEnabled() && settings.isInteractiveMode()) {
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
