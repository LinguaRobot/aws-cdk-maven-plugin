package io.linguarobot.aws.cdk.maven.process;

import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;

public class ProcessContext {

    public static final ProcessContext DEFAULT = ProcessContext.builder().build();

    private final File workingDirectory;
    private final Map<String, String> environment;
    private final OutputStream output;

    private ProcessContext(@Nullable File workingDirectory,
                           @Nullable Map<String, String> environment,
                           @Nullable OutputStream output) {
        this.workingDirectory = workingDirectory;
        this.environment = environment;
        this.output = output;
    }

    public Optional<File> getWorkingDirectory() {
        return Optional.ofNullable(workingDirectory);
    }

    public Optional<Map<String, String>> getEnvironment() {
        return Optional.ofNullable(environment);
    }

    public Optional<OutputStream> getOutput() {
        return Optional.ofNullable(output);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private File workingDirectory;
        private Map<String, String> environment;
        private OutputStream output;

        private Builder() {
            this.output = System.out;
        }

        public Builder withEnvironment(@Nonnull Map<String, String> environment) {
            this.environment = ImmutableMap.copyOf(environment);
            return this;
        }

        public Builder withWorkingDirectory(@Nonnull File workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder withOutput(@Nonnull OutputStream output) {
            this.output = output;
            return this;
        }

        public ProcessContext build() {
            return new ProcessContext(workingDirectory, environment, output);
        }

    }
}
