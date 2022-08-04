import io.dataspray.aws.cdk.maven.Stacks
import io.dataspray.aws.cdk.maven.TemplateRef
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackStatus

File invalidTemplate = new File(basedir, "invalid-template.json")
CloudFormationClient cfnClient = CloudFormationClient.create()
def stack = Stacks.createStack(cfnClient, "synth-deploy-after-rollback-test-stack", TemplateRef.fromString(invalidTemplate.text))
stack = Stacks.awaitCompletion(cfnClient, stack)
assert stack?.stackStatus() == StackStatus.ROLLBACK_COMPLETE
