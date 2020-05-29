package io.linguarobot.aws.cdk.maven;

import javax.annotation.Nullable;

public class BootstrapException extends CdkPluginException {

    private final String stackName;
    private final ResolvedEnvironment environment;

    private BootstrapException(String stackName, ResolvedEnvironment environment, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.stackName = stackName;
        this.environment = environment;
    }

    public String getStackName() {
        return stackName;
    }

    public ResolvedEnvironment getEnvironment() {
        return environment;
    }

    public static Builder deploymentError(String toolkitStackName, ResolvedEnvironment environment) {
        String baseMessage = "Unable to deploy toolkit stack '" + toolkitStackName + "' for the environment " +
                environment.getName();
        return new Builder(toolkitStackName, environment, baseMessage);
    }

    public static Builder invalidStateError(String toolkitStackName, ResolvedEnvironment environment) {
        String baseMessage = "The toolkit stack '" + toolkitStackName + "' for the environment " + environment +
                " is invalid";
        return new Builder(toolkitStackName, environment, baseMessage);
    }

    public static class Builder {

        private final String stackName;
        private final ResolvedEnvironment environment;
        private final String baseMessage;

        private String detailedMessage;
        private Throwable cause;

        private Builder(String stackName, ResolvedEnvironment environment, String baseMessage) {
            this.stackName = stackName;
            this.environment = environment;
            this.baseMessage = baseMessage;
        }

        public Builder withCause(String detailedMessage) {
            this.detailedMessage = detailedMessage;
            return this;
        }

        public Builder withCause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public BootstrapException build() {
            String message = baseMessage;
            if (detailedMessage != null) {
                message += ". " + detailedMessage;
            }

            return new BootstrapException(stackName, environment, message, cause);
        }
    }

}
