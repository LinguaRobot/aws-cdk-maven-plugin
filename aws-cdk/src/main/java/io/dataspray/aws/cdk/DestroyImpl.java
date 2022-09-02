package io.dataspray.aws.cdk;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.cxapi.CloudAssembly;
import software.amazon.awscdk.cxapi.CloudFormationStackArtifact;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

public class DestroyImpl implements Destroy {

    private static final Logger logger = LoggerFactory.getLogger(DestroyImpl.class);

    @Override
    public void execute(Path cloudAssemblyDirectory, Set<String> stacks, Optional<String> profileOpt, boolean isInteractive) {
        execute(CloudDefinition.create(cloudAssemblyDirectory), stacks, profileOpt, isInteractive);
    }

    @Override
    public void execute(CloudAssembly cloudAssembly) {
        execute(cloudAssembly,
                ImmutableSet.copyOf(Lists.transform(cloudAssembly.getStacks(), CloudFormationStackArtifact::getStackName)),
                Optional.empty(), true);
    }

    @Override
    public void execute(CloudAssembly cloudAssembly, String... stacks) {
        execute(cloudAssembly, ImmutableSet.copyOf(stacks), Optional.empty(), true);
    }

    @Override
    public void execute(CloudAssembly cloudAssembly, Set<String> stacks, String profile) {
        execute(cloudAssembly, stacks, Optional.of(profile), true);
    }

    @Override
    public void execute(CloudAssembly cloudAssembly, Set<String> stacks, Optional<String> profileOpt, boolean isInteractive) {
        execute(CloudDefinition.create(cloudAssembly), stacks, profileOpt, isInteractive);
    }

    private void execute(CloudDefinition cloudDefinition, Set<String> stacks, Optional<String> profileOpt, boolean isInteractive) {
        EnvironmentResolver environmentResolver = EnvironmentResolver.create(profileOpt.orElse(null));
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
                .filter(stack -> stacks == null || stacks.isEmpty() || stacks.contains(stack.getStackName()))
                .forEach(stack -> {
                    CloudFormationClient client = clients.computeIfAbsent(stack.getEnvironment(), environment -> {
                        ResolvedEnvironment resolvedEnvironment = environmentResolver.resolve(environment);
                        return CloudFormationClient.builder()
                                .region(resolvedEnvironment.getRegion())
                                .credentialsProvider(StaticCredentialsProvider.create(resolvedEnvironment.getCredentials()))
                                .build();
                    });

                    destroy(client, stack, isInteractive);
                });

    }

    private void destroy(CloudFormationClient client, StackDefinition stackDefinition, boolean isInteractive) {
        Stack stack = Stacks.findStack(client, stackDefinition.getStackName())
                .filter(s -> s.stackStatus() != StackStatus.DELETE_COMPLETE)
                .orElse(null);
        if (stack != null) {
            stack = Stacks.deleteStack(client, stack.stackName());
            logger.info("The stack '{}' is being deleted, waiting until the operation is completed", stack.stackName());
            if (logger.isInfoEnabled() && isInteractive) {
                stack = Stacks.awaitCompletion(client, stack, new LoggingStackEventListener());
            } else {
                stack = Stacks.awaitCompletion(client, stack);
            }
            if (stack.stackStatus() != StackStatus.DELETE_COMPLETE) {
                throw new CdkException("The deletion of '" + stack.stackName() + "' has failed.");
            }
            logger.info("The stack '{}' has been successfully deleted", stack.stackName());
        }
    }
}
