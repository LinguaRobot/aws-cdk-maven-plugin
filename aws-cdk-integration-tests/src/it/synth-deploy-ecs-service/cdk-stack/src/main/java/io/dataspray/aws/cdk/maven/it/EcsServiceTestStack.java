package io.dataspray.aws.cdk.maven.it;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterProps;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.NetworkLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.NetworkLoadBalancedFargateServiceProps;
import software.amazon.awscdk.services.ecs.patterns.NetworkLoadBalancedTaskImageOptions;

import java.util.Collections;

public class EcsServiceTestStack extends Stack {

    public EcsServiceTestStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EcsServiceTestStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = new Vpc(this, "vpc", VpcProps.builder().maxAzs(2).build());

        ClusterProps clusterProperties = ClusterProps.builder()
                .vpc(vpc)
                .capacity(AddCapacityOptions.builder()
                        .maxCapacity(1)
                        .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.NANO))
                        .build())
                .build();

        Cluster cluster = new Cluster(this, "cluster", clusterProperties);

        DockerImageAsset restAppImageAsset = DockerImageAsset.Builder.create(this, "rest-service-app-docker-image-asset")
                .buildArgs(Collections.singletonMap("REST_SERVICE_APP", "rest-service-app.jar"))
                .directory("target/docker-context")
                .build();

        NetworkLoadBalancedFargateServiceProps serviceProperties = NetworkLoadBalancedFargateServiceProps.builder()
                .cluster(cluster)
                .healthCheckGracePeriod(Duration.minutes(1))
                .taskImageOptions(NetworkLoadBalancedTaskImageOptions.builder()
                        .image(ContainerImage.fromDockerImageAsset(restAppImageAsset))
                        .containerPort(8080)
                        .build())
                .build();

        NetworkLoadBalancedFargateService service = new NetworkLoadBalancedFargateService(this, "service", serviceProperties);
        // Allow ingress traffic (see https://github.com/aws/aws-cdk/issues/3667 for the details)
        service.getService().getConnections().getSecurityGroups().get(0).addIngressRule(Peer.anyIpv4(), Port.allTraffic());
        CfnOutput.Builder.create(this, "Endpoint")
                .value("http://" + service.getLoadBalancer().getLoadBalancerDnsName() + "/")
                .build();
        CfnOutput.Builder.create(this, "TargetGroupArn")
                .value(service.getTargetGroup().getTargetGroupArn())
                .build();
    }
}
