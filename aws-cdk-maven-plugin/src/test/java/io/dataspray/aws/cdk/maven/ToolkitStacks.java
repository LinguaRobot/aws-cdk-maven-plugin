package io.dataspray.aws.cdk.maven;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectVersion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ToolkitStacks {

    private ToolkitStacks() {
    }

    public static Stack deleteToolkitStack(CloudFormationClient client, Stack stack) {
        Stacks.findOutput(stack, "BucketName")
                .map(Output::outputValue)
                .ifPresent(bucketName -> cleanBucket(S3Client.create(), bucketName));

        return Stacks.deleteStack(client, stack.stackName());
    }

    private static void cleanBucket(S3Client client, String bucketName) {
        ListObjectVersionsRequest listObjectVersionsRequest = ListObjectVersionsRequest.builder()
                .bucket(bucketName)
                .build();
        Iterator<ObjectVersion> objectVersionsIterator = client.listObjectVersionsPaginator(listObjectVersionsRequest)
                .versions()
                .iterator();
        while (objectVersionsIterator.hasNext()) {
            List<ObjectIdentifier> objectIdentifiers = new ArrayList<>();
            while (objectVersionsIterator.hasNext() && objectIdentifiers.size() < 1000) {
                ObjectVersion objectVersion = objectVersionsIterator.next();
                objectIdentifiers.add(toObjectIdentifier(objectVersion));
            }
            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objectIdentifiers).build())
                    .build();
            client.deleteObjects(deleteRequest);
        }
    }

    private static ObjectIdentifier toObjectIdentifier(ObjectVersion objectVersion) {
        return ObjectIdentifier.builder()
                .key(objectVersion.key())
                .versionId(objectVersion.versionId())
                .build();
    }
}
