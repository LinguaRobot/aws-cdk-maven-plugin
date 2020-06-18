package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.CfnParameter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.s3.assets.Asset;


public class StacksParametersTestStack extends Stack {

    public StacksParametersTestStack(final Construct scope, final String id) {
        super(scope, id);

        CfnParameter userTableName = CfnParameter.Builder.create(this, "UserTableName")
                .description("The name of the user table")
                .build();

        // A file asset to trigger the deployment of the toolkit stack
        Asset.Builder.create(this, "FileAsset")
                .path("file-asset.txt")
                .build();

        Table.Builder.create(this, "UserTable")
                .removalPolicy(RemovalPolicy.DESTROY)
                .tableName(userTableName.getValueAsString())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .partitionKey(Attribute.builder()
                        .name("id")
                        .type(AttributeType.STRING)
                        .build())
                .build();
    }
}
