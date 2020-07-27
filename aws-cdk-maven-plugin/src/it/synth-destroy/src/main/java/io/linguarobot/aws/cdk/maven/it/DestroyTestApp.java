package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Stack;


public class DestroyTestApp {

    public static void main(String[] args) {
        App app = new App();
        new DestroyTestStack(app, "destroy-test-stack");
        app.synth();
    }

}
