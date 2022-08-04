package io.dataspray.aws.cdk.maven;

/**
 * Represents Toolkit stack configuration.
 */
public class ToolkitConfiguration {

    private final String stackName;

    public ToolkitConfiguration(String stackName) {
        this.stackName = stackName;
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
