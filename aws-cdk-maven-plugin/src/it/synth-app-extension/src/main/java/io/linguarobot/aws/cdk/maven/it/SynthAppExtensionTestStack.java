package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;


public class SynthAppExtensionTestStack extends Stack {

    public SynthAppExtensionTestStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public SynthAppExtensionTestStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
    }
}
