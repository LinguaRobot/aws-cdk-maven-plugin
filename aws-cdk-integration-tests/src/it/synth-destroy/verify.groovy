import io.dataspray.aws.cdk.Stacks
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackStatus

CloudFormationClient cfnClient = CloudFormationClient.create();

try {
    def stack = Stacks.findStack(cfnClient, "destroy-test-stack").orElse(null)
    assert stack?.stackStatus() == null || stack?.stackStatus() == StackStatus.DELETE_COMPLETE
} finally {
    Stacks.findStack(cfnClient, "destroy-test-stack")
            .ifPresent{stack -> Stacks.awaitCompletion(cfnClient, Stacks.deleteStack(cfnClient, stack.stackName()))}
}
