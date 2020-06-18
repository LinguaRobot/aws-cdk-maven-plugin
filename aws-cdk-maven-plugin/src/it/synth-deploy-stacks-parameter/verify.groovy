import io.linguarobot.aws.cdk.maven.Stacks
import io.linguarobot.aws.cdk.maven.ToolkitStacks
import software.amazon.awscdk.core.Stack
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackStatus

import java.util.stream.Collectors
import java.util.stream.Stream

CloudFormationClient cfnClient = CloudFormationClient.create();

try {
    def devStack = Stacks.findStack(cfnClient, "stacks-parameter-test-stack-dev").orElse(null);
    assert devStack?.stackStatus() == StackStatus.CREATE_COMPLETE
    def devToolkitStack = Stacks.findStack(cfnClient, "stacks-parameter-cdk-toolkit-dev").orElse(null);
    assert devToolkitStack?.stackStatus() == StackStatus.CREATE_COMPLETE

    def testStack = Stacks.findStack(cfnClient, "stacks-parameter-test-stack-test").orElse(null);
    assert testStack == null
    def testToolkitStack = Stacks.findStack(cfnClient, "stacks-parameter-cdk-toolkit-test").orElse(null);
    assert testToolkitStack == null

    def prodStack = Stacks.findStack(cfnClient, "stacks-parameter-test-stack-prod").orElse(null);
    assert prodStack?.stackStatus() == StackStatus.CREATE_COMPLETE
    def prodToolkitStack = Stacks.findStack(cfnClient, "stacks-parameter-cdk-toolkit-prod").orElse(null);
    assert prodToolkitStack?.stackStatus() == StackStatus.CREATE_COMPLETE
} finally {
    List<Stack> toolkitStacks = Stream.of("dev", "test", "prod")
            .map { stage -> "stacks-parameter-cdk-toolkit-" + stage }
            .map { stackName -> Stacks.findStack(cfnClient, stackName).orElse(null) }
            .filter { stack -> stack != null }
            .map { stack -> ToolkitStacks.deleteToolkitStack(cfnClient, stack) }
            .collect(Collectors.toList())

    List<Stack> stacks = Stream.of("dev", "test", "prod")
            .map { stage -> "stacks-parameter-test-stack-" + stage }
            .map { stackName -> Stacks.findStack(cfnClient, stackName).orElse(null) }
            .filter { stack -> stack != null }
            .map { stack -> Stacks.deleteStack(cfnClient, stack.stackName()) }
            .collect(Collectors.toList())

    Stream.concat(toolkitStacks.stream(), stacks.stream())
            .forEach { stack -> Stacks.awaitCompletion(cfnClient, stack) }
}