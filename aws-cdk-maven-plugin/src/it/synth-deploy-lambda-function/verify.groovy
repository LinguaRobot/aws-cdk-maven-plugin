import io.linguarobot.aws.cdk.maven.Stacks
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.Stack
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*

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
            .map(output -> new URL(output.outputValue()))
            .orElse(null)
    assert endpointUrl != null

    def connection = endpointUrl.openConnection()
    connection.connect()
    assert connection.getResponseCode() == 200
    assert connection.getInputStream().getText() == "SUCCESS"
} finally {
    def stack = Stacks.findStack(cfnClient, STACK_NAME)
            .map(s -> Stacks.deleteStack(cfnClient, s.stackName()))
            .orElse(null)
    def toolkitStack = Stacks.findStack(cfnClient, TOOLKIT_STACK_NAME)
            .map(s -> deleteToolkitStack(cfnClient, s))
            .orElse(null)

    Stream.of(stack, toolkitStack)
        .filter(Objects::nonNull)
        .forEach(s -> Stacks.awaitCompletion(cfnClient, s))
}

static Stack deleteToolkitStack(CloudFormationClient cfnClient, Stack toolkitStack) {
    toolkitStack.outputs().stream()
            .filter { output -> output.outputKey() == "BucketName" }
            .map { output -> output.outputValue() }
            .findAny()
            .ifPresent { fileAssetBucketName -> emptyBucket(S3Client.create(), fileAssetBucketName) }

    return Stacks.deleteStack(cfnClient, toolkitStack.stackName());
}

static def emptyBucket(S3Client client, String bucketName) {
    ListObjectVersionsRequest listObjectVersionsRequest = ListObjectVersionsRequest.builder()
            .bucket(bucketName)
            .build()
    def objectVersionsIterator = client.listObjectVersionsPaginator(listObjectVersionsRequest)
            .versions()
            .iterator()
    while (objectVersionsIterator.hasNext()) {
        List<ObjectIdentifier> objectIdentifiers = new ArrayList<>();
        while (objectVersionsIterator.hasNext() && objectIdentifiers.size() < 1000) {
            ObjectVersion objectVersion = objectVersionsIterator.next();
            ObjectIdentifier objectIdentifier = ObjectIdentifier.builder()
                    .key(objectVersion.key())
                    .versionId(objectVersion.versionId())
                    .build();
            objectIdentifiers.add(objectIdentifier);
        }
        DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(objectIdentifiers).build())
                .build()
        client.deleteObjects(deleteRequest);
    }
}
