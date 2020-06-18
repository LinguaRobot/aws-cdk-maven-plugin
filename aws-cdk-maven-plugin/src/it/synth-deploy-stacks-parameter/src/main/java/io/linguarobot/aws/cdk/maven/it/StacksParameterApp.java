package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.App;


public class StacksParameterApp {

    public static void main(String[] args) {
        App app = new App();
        new StacksParametersTestStack(app, "stacks-parameter-test-stack-dev");
        new StacksParametersTestStack(app, "stacks-parameter-test-stack-prod");
        new StacksParametersTestStack(app, "stacks-parameter-test-stack-test");
        app.synth();
    }

}
