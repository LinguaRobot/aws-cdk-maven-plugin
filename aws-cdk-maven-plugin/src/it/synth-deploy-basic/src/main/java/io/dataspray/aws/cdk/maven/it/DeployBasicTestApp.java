package io.dataspray.aws.cdk.maven.it;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;


public class DeployBasicTestApp {

    public static void main(String[] args) {
        String stage = args.length > 0 ? args[0] : "dev";
        App app = new App();
        new DeployBasicTestStack(app, "synth-deploy-basic-test-stack", stage);
        app.synth();
    }

}
