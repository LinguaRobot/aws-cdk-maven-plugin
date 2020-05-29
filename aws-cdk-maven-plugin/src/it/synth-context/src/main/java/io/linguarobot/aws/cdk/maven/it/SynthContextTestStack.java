package io.linguarobot.aws.cdk.maven.it;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;


public class SynthContextTestStack extends Stack {

    public SynthContextTestStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public SynthContextTestStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        getAvailabilityZones();
    }
}
