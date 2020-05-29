package io.linguarobot.aws.cdk.maven;

/**
 * Represents Toolkit stack configuration.
 */
public class ToolkitConfiguration {

    /**
     * The name of the toolkit stack.
     */
    private String stackName;

    public ToolkitConfiguration() {
        this.stackName = "CDKToolkit";
    }

    public String getStackName() {
        return stackName;
    }

    @Override
    public String toString() {
        return "ToolkitConfiguration{" +
                "stackName='" + stackName + '\'' +
                '}';
    }
}
