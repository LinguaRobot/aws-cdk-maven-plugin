package io.dataspray.aws.cdk;

import javax.annotation.Nullable;

/**
 * An exception that is thrown in case CloudFormation stack can't be deployed for some reason.
 */
public class StackDeploymentException extends CdkException {

    private final String stackName;
    private final ResolvedEnvironment environment;

    protected StackDeploymentException(String stackName,
                                       @Nullable ResolvedEnvironment environment,
                                       String message,
                                       @Nullable Throwable cause) {
        super(formatErrorMessage(stackName, environment, message), cause);
        this.stackName = stackName;
        this.environment = environment;
    }

    private static String formatErrorMessage(@Nullable String stackName, @Nullable ResolvedEnvironment environment, @Nullable String message) {
        StringBuilder errorMessage = new StringBuilder();
        if (stackName == null) {
            errorMessage.append("Cannot deploy");
        } else {
            errorMessage.append("The stack '")
                    .append(stackName)
                    .append("' cannot be deployed");
        }
        if (environment != null) {
            errorMessage.append(environment.getName())
                    .append(" in environment");
        }
        if (message != null) {
            errorMessage.append(". ").append(message);
        }
        return errorMessage.toString();
    }

    public String getStackName() {
        return stackName;
    }

    public ResolvedEnvironment getEnvironment() {
        return environment;
    }

    public static Builder builder() {
        return new Builder(null, null);
    }

    public static Builder builder(ResolvedEnvironment environment) {
        return new Builder(null, environment);
    }

    public static Builder builder(String stackName, ResolvedEnvironment environment) {
        return new Builder(stackName, environment);
    }

    public static class Builder {

        private final String stackName;
        private final ResolvedEnvironment environment;

        private String message;
        private Throwable cause;

        private Builder(String stackName, ResolvedEnvironment environment) {
            this.stackName = stackName;
            this.environment = environment;
        }

        public Builder withCause(String cause) {
            this.message = cause;
            return this;
        }

        public Builder withCause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public StackDeploymentException build() {
            return new StackDeploymentException(stackName, environment, message, cause);
        }
    }
}
