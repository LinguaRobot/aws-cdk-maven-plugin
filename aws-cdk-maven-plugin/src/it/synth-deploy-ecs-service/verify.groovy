import io.linguarobot.aws.cdk.maven.Stacks
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.Stack
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.ecr.model.DeleteRepositoryRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest

import java.util.stream.Collectors
import java.util.stream.Stream

final STACK_NAME = "synth-deploy-ecs-service-test-stack"
final TOOLKIT_STACK_NAME = "ecs-service-it-cdk-toolkit"

CloudFormationClient cfnClient = CloudFormationClient.create();

try {
    File cloudAssemblyDirectory = new File(basedir, "cdk-stack/target/cdk.out");
    assert cloudAssemblyDirectory.exists() && cloudAssemblyDirectory.directory

    def stack = Stacks.findStack(cfnClient, STACK_NAME).orElse(null);
    assert stack?.stackStatus() == StackStatus.CREATE_COMPLETE

    def toolkitStack = Stacks.findStack(cfnClient, TOOLKIT_STACK_NAME).orElse(null);
    assert toolkitStack == null
// Unfortunately, it takes to much time to wait until the service is healthy
/*  ElasticLoadBalancingV2Client elbClient = ElasticLoadBalancingV2Client.create();
    def targetGroupArn = getRequiredOutput(stack, "TargetGroupArn")
    TargetHealth targetHealth = getTargetHealth(elbClient, targetGroupArn);
    while (targetHealth.state() != TargetHealthStateEnum.HEALTHY) {
        Thread.sleep(5000);
        targetHealth = getTargetHealth(elbClient, targetGroupArn)
    }

    assert targetHealth.state() == TargetHealthStateEnum.HEALTHY

    def endpointUrl = new URL(getRequiredOutput(stack, "Endpoint"))

    def connection = endpointUrl.openConnection()
    connection.connect()
    assert connection.getResponseCode() == 200
    assert connection.getInputStream().getText() == "SUCCESS"
    */
} finally {
    def stacks = Stream.of(STACK_NAME, TOOLKIT_STACK_NAME)
            .map(stackName -> Stacks.findStack(cfnClient, stackName).orElse(null))
            .filter(Objects::nonNull)
            .map(stack -> Stacks.deleteStack(cfnClient, stack.stackName()))
            .collect(Collectors.toList())

    stacks.stream()
            .map(stack -> Stacks.awaitCompletion(cfnClient, stack))
            .forEach(stack -> {
                stack?.stackStatus() == StackStatus.DELETE_COMPLETE
            })

    def ecrClient = EcrClient.create()
    deleteRepository(ecrClient, "aws-cdk/assets")
}

def deleteRepository(EcrClient client, String repositoryName) {
    def deleteRequest = DeleteRepositoryRequest.builder()
            .repositoryName(repositoryName)
            .force(true)
            .build()
    client.deleteRepository(deleteRequest)
}

def getTargetHealth(ElasticLoadBalancingV2Client elbClient, String targetGroupArn) {
    DescribeTargetHealthRequest request = DescribeTargetHealthRequest.builder()
            .targetGroupArn(targetGroupArn)
            .build()
    return elbClient.describeTargetHealth(request).targetHealthDescriptions().get(0).targetHealth()
}

def getRequiredOutput(Stack stack, String name) {
    return stack.outputs().stream()
            .filter { output -> output.outputKey() == name }
            .map { output -> output.outputValue() }
            .findAny()
            .get();
}
