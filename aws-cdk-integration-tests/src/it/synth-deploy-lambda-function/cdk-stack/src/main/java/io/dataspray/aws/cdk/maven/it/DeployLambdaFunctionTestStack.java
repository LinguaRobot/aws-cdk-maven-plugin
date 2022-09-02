package io.dataspray.aws.cdk.maven.it;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;

public class DeployLambdaFunctionTestStack extends Stack {

    public DeployLambdaFunctionTestStack(final Construct parent, final String name) {
        super(parent, name);

        FunctionProps functionProperties = FunctionProps.builder()
                .code(Code.fromAsset("./target/synth-deploy-lambda-function-it-handler-1.0.0.jar"))
                .handler("io.dataspray.aws.cdk.maven.it.Function")
                .runtime(Runtime.JAVA_8)
                .timeout(Duration.seconds(30))
                .memorySize(512)
                .build();

        Function function = new Function(this, "function", functionProperties);

        RestApiProps restApiProperties = RestApiProps.builder()
                .restApiName("Basic Rest Service AWS CDK Maven Plugin Test")
                .build();
        RestApi restApi = new RestApi(this, "api", restApiProperties);

        restApi.getRoot().addMethod("GET", new LambdaIntegration(function));

        CfnOutput.Builder.create(this, "Endpoint")
                        .description("URL of the REST endpoint")
                        .value(restApi.getDeploymentStage().urlForPath("/"))
                        .build();
    }

}
