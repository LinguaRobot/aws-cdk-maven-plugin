import io.dataspray.aws.cdk.Stacks
import io.dataspray.aws.cdk.TemplateRef
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackStatus

File template = new File(basedir, "template.json")
CloudFormationClient cfnClient = CloudFormationClient.create()
def stack = Stacks.createStack(cfnClient, "destroy-test-stack", TemplateRef.fromString(template.text))
stack = Stacks.awaitCompletion(cfnClient, stack)
assert stack?.stackStatus() == StackStatus.CREATE_COMPLETE
