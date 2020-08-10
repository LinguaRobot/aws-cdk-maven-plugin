package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;


public class DeployAfterRollbackTestStack extends Stack {

    public DeployAfterRollbackTestStack(final Construct scope, final String id) {
        super(scope, id);

        Table.Builder.create(this, "UserTable")
                .removalPolicy(RemovalPolicy.DESTROY)
                .tableName("deploy_after_rollback_it_user")
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .partitionKey(Attribute.builder()
                        .name("id")
                        .type(AttributeType.STRING)
                        .build())
                .build();
    }
}
