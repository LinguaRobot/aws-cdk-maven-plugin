import io.linguarobot.aws.cdk.maven.Stacks
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackStatus

CloudFormationClient cfnClient = CloudFormationClient.create();

try {
    def stack = Stacks.findStack(cfnClient, "synth-deploy-after-rollback-test-stack").orElse(null)
    assert stack?.stackStatus() == StackStatus.CREATE_COMPLETE
} finally {
    def stack = Stacks.findStack(cfnClient, "synth-deploy-after-rollback-test-stack").orElse(null)
    if (stack != null) {
        Stacks.awaitCompletion(cfnClient, Stacks.deleteStack(cfnClient, stack.stackName()))
    }
}
