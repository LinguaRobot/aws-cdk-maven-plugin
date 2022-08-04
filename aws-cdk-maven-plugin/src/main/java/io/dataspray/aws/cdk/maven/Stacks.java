package io.dataspray.aws.cdk.maven;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.*;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Stacks {

    private static final ScheduledExecutorService SCHEDULER = new ScheduledThreadPoolExecutor(0);

    private static final Capability[] CAPABILITIES =
            new Capability[]{Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND};

    public static Optional<Stack> findStack(CloudFormationClient client, String stackName) {
        Objects.requireNonNull(client, "CloudFormation client can't be null");
        Objects.requireNonNull(stackName, "stack name can't be null");
        try {
            return Optional.of(getStack(client, stackName));
        } catch (CloudFormationException e) {
            // Assuming that the exception is thrown only if the stack doesn't exist
            return Optional.empty();
        }
    }

    public static Stack createStack(CloudFormationClient client, String stackName, TemplateRef template) {
        return createStack(client, stackName, template, Collections.emptyMap(), Collections.emptyMap());
    }

    public static Stack createStack(CloudFormationClient client,
                                    String stackName,
                                    TemplateRef template,
                                    Map<String, ParameterValue> parameters,
                                    Map<String, String> tags) {
        Objects.requireNonNull(client, "CloudFormation client can't be null");
        Objects.requireNonNull(stackName, "stack name can't be null");
        Objects.requireNonNull(template, "template reference can't be null");
        CreateStackRequest request = CreateStackRequest.builder()
                .stackName(stackName)
                .templateBody(template.getBody())
                .templateURL(template.getUrl())
                .tags(buildTags(tags))
                .parameters(parameters != null ? buildParameters(parameters) : Collections.emptyList())
                .capabilities(CAPABILITIES)
                .build();

        CreateStackResponse response = client.createStack(request);
        return getStack(client, response.stackId());
    }

    public static Stack updateStack(CloudFormationClient client,
                                    String stackName,
                                    TemplateRef template,
                                    Map<String, ParameterValue> parameters) {
        return updateStack(client, stackName, template, parameters, Collections.emptyMap());
    }

    public static Stack updateStack(CloudFormationClient client,
                                    String stackName,
                                    TemplateRef template,
                                    Map<String, ParameterValue> parameters,
                                    Map<String, String> tags) {
        Objects.requireNonNull(client, "CloudFormation client can't be null");
        Objects.requireNonNull(stackName, "stack name can't be null");
        Objects.requireNonNull(template, "template reference can't be null");
        UpdateStackRequest request = UpdateStackRequest.builder()
                .stackName(stackName)
                .templateBody(template.getBody())
                .templateURL(template.getUrl())
                .tags(buildTags(tags))
                .parameters(parameters != null ? buildParameters(parameters) : Collections.emptyList())
                .capabilities(CAPABILITIES)
                .build();

        UpdateStackResponse response = client.updateStack(request);
        return getStack(client, response.stackId());
    }

    private static List<Tag> buildTags(Map<String, String> tags) {
        return tags.entrySet().stream()
                .map(tag -> Tag.builder()
                        .key(tag.getKey())
                        .value(tag.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<Parameter> buildParameters(Map<String, ParameterValue> parameters) {
        return parameters.entrySet().stream()
                .map(parameter -> Parameter.builder()
                        .parameterKey(parameter.getKey())
                        .parameterValue(parameter.getValue().get())
                        .usePreviousValue(!parameter.getValue().isUpdated())
                        .build())
                .collect(Collectors.toList());
    }

    public static Stack deleteStack(CloudFormationClient client, String stackName) {
        Objects.requireNonNull(client, "CloudFormation client can't be null");
        Objects.requireNonNull(stackName, "stack name can't be null");
        Stack stack = getStack(client, stackName);
        DeleteStackRequest request = DeleteStackRequest.builder()
                .stackName(stack.stackId())
                .build();
        client.deleteStack(request);
        return getStack(client, stackName);
    }

    public static Optional<Output> findOutput(Stack stack, String outputKey) {
        Objects.requireNonNull(stack, "stack can't be null");
        Objects.requireNonNull(outputKey, "output key can't be null");
        return Stream.of(stack)
                .filter(Stack::hasOutputs)
                .flatMap(s -> s.outputs().stream())
                .filter(output -> output.outputKey().equals(outputKey))
                .findAny();
    }

    public static boolean isCompleted(Stack stack) {
        return !isInProgress(stack);
    }

    public static boolean isInProgress(Stack stack) {
        return stack.stackStatus().toString().endsWith("_IN_PROGRESS");
    }

    public static boolean isFailed(Stack stack) {
        return stack.stackStatus().toString().endsWith("_FAILED");
    }

    public static boolean isRolledBack(Stack stack) {
        return stack.stackStatus() == StackStatus.ROLLBACK_COMPLETE || stack.stackStatus().toString().endsWith("_ROLLBACK_COMPLETE");
    }

    public static Stack awaitCompletion(CloudFormationClient client, Stack stack) {
        return awaitCompletion(client, stack, ForkJoinPool.commonPool(), null).join();
    }

    public static Stack awaitCompletion(CloudFormationClient client, Stack stack, @Nullable Consumer<StackEvent> eventListener) {
        StackEventListener stackEventListener = eventListener != null ? new StackEventListener(eventListener) : null;
        return awaitCompletion(client, stack, ForkJoinPool.commonPool(), stackEventListener).join();
    }

    private static CompletableFuture<Stack> awaitCompletion(CloudFormationClient client,
                                                            Stack initialStack,
                                                            Executor executor,
                                                            @Nullable StackEventListener eventListener) {
        CompletableFuture<Stack> stackFuture;
        if (eventListener != null) {
            stackFuture = CompletableFuture.runAsync(() -> consumeEvents(client, initialStack.stackId(), eventListener), executor)
                    .thenCompose(r -> CompletableFuture.completedFuture(initialStack));
        } else {
            stackFuture = CompletableFuture.completedFuture(initialStack);
        }

        return stackFuture.thenCompose(stack -> {
            if (isCompleted(stack)) {
                return CompletableFuture.completedFuture(stack);
            }

            Supplier<Stack> statusRequest = () -> {
                Stack nextStack = getStack(client, stack.stackId());
                if (eventListener != null) {
                    consumeEvents(client, nextStack.stackId(), eventListener);
                }
                return nextStack;
            };

            return awaitCompletion(statusRequest, Duration.ZERO, Duration.ofSeconds(5), executor);
        });
    }

    private static CompletableFuture<Stack> awaitCompletion(Supplier<Stack> request,
                                                            Duration initialDelay,
                                                            Duration period,
                                                            Executor executor) {
        if (initialDelay.isNegative() || period.isNegative()) {
            throw new IllegalArgumentException("The initial delay and period must be equal or greater than zero");
        }

        Executor effectiveExecutor = initialDelay.isZero() ? executor : delayedExecutor(executor, initialDelay);
        return CompletableFuture.supplyAsync(request, effectiveExecutor)
                .thenCompose(stack -> {
                    if (isCompleted(stack)) {
                        return CompletableFuture.completedFuture(stack);
                    }

                    return awaitCompletion(request, period, period, executor);
                });
    }

    private static Executor delayedExecutor(Executor executor, Duration delay) {
        return command -> SCHEDULER.schedule(() -> executor.execute(command), delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static void consumeEvents(CloudFormationClient client, String stackId, StackEventListener eventListener) {
        Deque<StackEvent> events = new ArrayDeque<>();
        String token = null;
        do {
            DescribeStackEventsRequest eventsRequest = DescribeStackEventsRequest.builder()
                    .stackName(stackId)
                    .nextToken(token)
                    .build();
            DescribeStackEventsResponse eventsResponse = client.describeStackEvents(eventsRequest);
            token = eventsResponse.nextToken();

            for (StackEvent event : eventsResponse.stackEvents()) {
                if (eventListener.isConsumed(event)) {
                    token = null;
                    break;
                }
                events.add(event);
            }
        } while (token != null);

        events.descendingIterator().forEachRemaining(eventListener::onEvent);
    }

    private static Stack getStack(CloudFormationClient client, String stackName) {
        DescribeStacksRequest request = DescribeStacksRequest.builder()
                .stackName(stackName)
                .build();
        return client.describeStacks(request).stacks().get(0);
    }

    private static class StackEventListener {

        private final Consumer<StackEvent> consumer;
        private final Set<String> consumed;

        public StackEventListener(Consumer<StackEvent> consumer) {
            this.consumer = consumer;
            this.consumed = new HashSet<>();
        }

        public boolean onEvent(StackEvent event) {
            if (isConsumed(event)) {
                return false;
            }

            consumed.add(event.eventId());
            consumer.accept(event);
            return true;
        }

        public boolean isConsumed(StackEvent event) {
            return consumed.contains(event.eventId());
        }

    }
}
