package io.dataspray.aws.cdk;

public class AwsCdk {
    public static final String DEFAULT_TOOLKIT_STACK_NAME = "CDKToolkit";

    public static Bootstrap bootstrap() {
        return new BootstrapImpl();
    }

    public static Deploy deploy() {
        return new DeployImpl();
    }

    public static Destroy destroy() {
        return new DestroyImpl();
    }

    private AwsCdk() {
        // Disable ctor
    }
}
