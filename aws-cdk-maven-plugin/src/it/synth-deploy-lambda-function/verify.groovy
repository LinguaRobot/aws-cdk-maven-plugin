import io.linguarobot.aws.cdk.maven.Stacks
import io.linguarobot.aws.cdk.maven.ToolkitStacks
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackStatus

import java.util.stream.Stream

final STACK_NAME = "deploy-lambda-function-test-stack"
final TOOLKIT_STACK_NAME = "lambda-function-it-cdk-toolkit";

CloudFormationClient cfnClient = CloudFormationClient.create();

try {
    def stack = Stacks.findStack(cfnClient, STACK_NAME).orElse(null);
    assert stack?.stackStatus() == StackStatus.CREATE_COMPLETE

    def toolkitStack = Stacks.findStack(cfnClient, TOOLKIT_STACK_NAME).orElse(null);
    assert toolkitStack?.stackStatus() == StackStatus.CREATE_COMPLETE

    def endpointUrl = Stacks.findOutput(stack, "Endpoint")
            .map{output -> new URL(output.outputValue())}
            .orElse(null)
    assert endpointUrl != null

    def connection = endpointUrl.openConnection()
    connection.connect()
    assert connection.getResponseCode() == 200
    assert connection.getInputStream().getText() == "SUCCESS"
} finally {
    def stack = Stacks.findStack(cfnClient, STACK_NAME)
            .map{s -> Stacks.deleteStack(cfnClient, s.stackName())}
            .orElse(null)
    def toolkitStack = Stacks.findStack(cfnClient, TOOLKIT_STACK_NAME)
            .map{s -> ToolkitStacks.deleteToolkitStack(cfnClient, s)}
            .orElse(null)

    Stream.of(stack, toolkitStack)
        .filter(Objects::nonNull)
        .forEach{s -> Stacks.awaitCompletion(cfnClient, s)}
}
