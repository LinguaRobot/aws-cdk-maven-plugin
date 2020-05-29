package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.App;

/**
 * A simple CDK application that extends {@link App} and defines all the constructs in its constructor.
 */
public class SynthAppExtensionTestApp extends App {

    public SynthAppExtensionTestApp() {
        new SynthAppExtensionTestStack(this, "synth-app-extension-test-stack");
    }

}
