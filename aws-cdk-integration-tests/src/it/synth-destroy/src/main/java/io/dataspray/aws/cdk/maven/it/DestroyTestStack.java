package io.dataspray.aws.cdk.maven.it;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;


public class DestroyTestStack extends Stack {

    public DestroyTestStack(final Construct scope, final String id) {
        super(scope, id);

        Table.Builder.create(this, "CarTable")
                .removalPolicy(RemovalPolicy.DESTROY)
                .tableName("car")
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .partitionKey(Attribute.builder()
                        .name("id")
                        .type(AttributeType.STRING)
                        .build())
                .build();
    }
}
