package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;


public class SynthContextTestApp {

    public static void main(String[] args) {
        App app = new App();
        Environment environment = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();
        StackProps stackProps = StackProps.builder()
                .env(environment)
                .build();
        new SynthContextTestStack(app, "synth-context-test-stack", stackProps);
        app.synth();
    }

}
