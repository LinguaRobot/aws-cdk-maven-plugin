package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;


public class DeployAfterRollbackTestApp {

    public static void main(String[] args) {
        App app = new App();
        new DeployAfterRollbackTestStack(app, "synth-deploy-after-rollback-test-stack");
        app.synth();
    }

}
