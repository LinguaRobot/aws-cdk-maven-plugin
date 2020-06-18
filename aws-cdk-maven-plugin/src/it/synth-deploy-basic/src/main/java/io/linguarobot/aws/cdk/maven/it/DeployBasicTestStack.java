package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketProps;


public class DeployBasicTestStack extends Stack {

    public DeployBasicTestStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public DeployBasicTestStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        CfnParameter parameter = CfnParameter.Builder.create(this, "Parameter")
                .defaultValue("DefaultValue")
                .build();

        BucketProps bucketProperties = BucketProps.builder()
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .build();

        Bucket bucket = new Bucket(this, "bucket", bucketProperties);

        CfnOutput.Builder.create(this, "BucketName")
                .description("The name of the bucket")
                .value(bucket.getBucketName())
                .build();

        CfnOutput.Builder.create(this, "ParameterValue")
                .value(parameter.getValueAsString())
                .build();
    }
}
