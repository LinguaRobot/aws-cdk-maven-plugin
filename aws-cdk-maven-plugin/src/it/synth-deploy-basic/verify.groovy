import io.linguarobot.aws.cdk.maven.Stacks
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest

CloudFormationClient cfnClient = CloudFormationClient.create();

try {
    File cloudAssemblyDirectory = new File(basedir, "target/cdk.out");
    assert cloudAssemblyDirectory.exists() && cloudAssemblyDirectory.directory

    def manifestFile = new File(cloudAssemblyDirectory, "manifest.json")
    assert manifestFile.exists() && manifestFile.file

    def treeFile = new File(cloudAssemblyDirectory, "tree.json")
    assert treeFile.exists() && treeFile.file

    def templateFile = new File(cloudAssemblyDirectory, "synth-deploy-basic-test-stack.template.json")
    assert templateFile.exists() && templateFile.file

    def stack = Stacks.findStack(cfnClient, "synth-deploy-basic-test-stack").orElse(null);
    assert stack?.stackStatus() == StackStatus.CREATE_COMPLETE

    def parameterValue = Stacks.findOutput(stack, "ParameterValue")
            .map{output -> output.outputValue()}
            .orElse(null)
    assert parameterValue == "OverriddenValue"

    def toolkitStack = Stacks.findStack(cfnClient, "basic-cdk-toolkit").orElse(null);
    assert toolkitStack == null
} finally {
    def stack = Stacks.findStack(cfnClient, "synth-deploy-basic-test-stack").orElse(null)
    if (stack != null) {
        Stacks.findOutput(stack, "BucketName")
                .map { output -> output.outputValue() }
                .ifPresent { bucketName ->
                    {
                        def s3Client = S3Client.create()
                        def deleteBucketRequest = DeleteBucketRequest.builder()
                                .bucket(bucketName)
                                .build()
                        s3Client.deleteBucket(deleteBucketRequest)
                    }
                }
        Stacks.awaitCompletion(cfnClient, Stacks.deleteStack(cfnClient, stack.stackName()))
    }
}
