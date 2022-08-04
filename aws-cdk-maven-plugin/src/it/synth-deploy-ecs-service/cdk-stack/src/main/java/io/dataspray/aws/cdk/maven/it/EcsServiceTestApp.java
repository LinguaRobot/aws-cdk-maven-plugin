package io.dataspray.aws.cdk.maven.it;

import software.amazon.awscdk.core.App;

/**
 * CDK application that defines a ECS Fargate HTTP service running behind network load balancer. The service
 * has a single {@code GET /} endpoint returning {@code SUCCESS} upon calling.
 */
public class EcsServiceTestApp extends App {

    public EcsServiceTestApp() {
        new EcsServiceTestStack(this, "synth-deploy-ecs-service-test-stack");
    }

}
