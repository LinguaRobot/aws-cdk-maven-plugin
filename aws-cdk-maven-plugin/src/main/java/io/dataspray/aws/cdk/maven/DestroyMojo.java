package io.dataspray.aws.cdk.maven;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Mojo(name = "destroy")
public class DestroyMojo extends AbstractCloudActionMojo {

    private static final Logger logger = LoggerFactory.getLogger(DestroyMojo.class);

    @Parameter(defaultValue = "${settings}", required = true, readonly = true)
    private Settings settings;

    /**
     * Stacks to be destroyed. By default, all the stacks defined in the cloud application will be deleted.
     */
    @Parameter(property = "aws.cdk.stacks")
    private Set<String> stacks;

    @Override
    public void execute(CloudDefinition cloudDefinition, EnvironmentResolver environmentResolver) {
        if (stacks != null && !stacks.isEmpty() && logger.isWarnEnabled()) {
            Set<String> undefinedStacks = new HashSet<>(stacks);
            cloudDefinition.getStacks().forEach(stack -> undefinedStacks.remove(stack.getStackName()));
            if (!undefinedStacks.isEmpty()) {
                logger.warn("The following stacks are not defined in the cloud application and can not be deleted: {}",
                        String.join(", ", undefinedStacks));
            }
        }

        Map<String, CloudFormationClient> clients = new HashMap<>();
        IntStream.range(0, cloudDefinition.getStacks().size())
                .map(i -> cloudDefinition.getStacks().size() - 1 - i)
                .mapToObj(cloudDefinition.getStacks()::get)
                .filter(stack -> this.stacks == null || this.stacks.isEmpty() || this.stacks.contains(stack.getStackName()))
                .forEach(stack -> {
                    CloudFormationClient client = clients.computeIfAbsent(stack.getEnvironment(), environment -> {
                        ResolvedEnvironment resolvedEnvironment = environmentResolver.resolve(environment);
                        return CloudFormationClient.builder()
                                .region(resolvedEnvironment.getRegion())
                                .credentialsProvider(StaticCredentialsProvider.create(resolvedEnvironment.getCredentials()))
                                .build();
                    });

                    destroy(client, stack);
                });

    }

    private void destroy(CloudFormationClient client, StackDefinition stackDefinition) {
        Stack stack = Stacks.findStack(client, stackDefinition.getStackName())
                .filter(s -> s.stackStatus() != StackStatus.DELETE_COMPLETE)
                .orElse(null);
        if (stack != null) {
            stack = Stacks.deleteStack(client, stack.stackName());
            logger.info("The stack '{}' is being deleted, waiting until the operation is completed", stack.stackName());
            if (logger.isInfoEnabled() && settings.isInteractiveMode()) {
                stack = Stacks.awaitCompletion(client, stack, new LoggingStackEventListener());
            } else {
                stack = Stacks.awaitCompletion(client, stack);
            }
            if (stack.stackStatus() != StackStatus.DELETE_COMPLETE) {
                throw new CdkPluginException("The deletion of '" + stack.stackName() + "' has failed.");
            }
            logger.info("The stack '{}' has been successfully deleted", stack.stackName());
        }
    }

}
