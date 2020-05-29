package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.App;


public class DeployLambdaFunctionTestApp {

    public static void main(String[] args) {
        App app = new App();
        new DeployLambdaFunctionTestStack(app, "deploy-lambda-function-test-stack");
        app.synth();
    }

}
