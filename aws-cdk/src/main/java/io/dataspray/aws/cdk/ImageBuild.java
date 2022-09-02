package io.dataspray.aws.cdk;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Represents Docker image build parameters.
 */
public class ImageBuild {

    @Nonnull
    private final Path contextDirectory;

    @Nonnull
    private final Path dockerfile;

    @Nonnull
    private final String imageTag;

    @Nullable
    private final String target;

    @Nonnull
    Map<String, String> arguments;

    private ImageBuild(@NotNull Path contextDirectory,
                       @NotNull Path dockerfile,
                       @NotNull String imageTag,
                       @Nullable String target,
                       @Nullable Map<String, String> arguments) {
        this.contextDirectory = Objects.requireNonNull(contextDirectory, "Docker context directory path can't be null");
        this.dockerfile = Objects.requireNonNull(dockerfile, "Docker Dockerfile path can't be null");
        this.imageTag = Objects.requireNonNull(imageTag, "Image tag can't be null");
        this.target = target;
        this.arguments = arguments != null ? ImmutableMap.copyOf(arguments) : ImmutableMap.of();
    }

    @Nonnull
    public Path getContextDirectory() {
        return contextDirectory;
    }

    @Nonnull
    public Path getDockerfile() {
        return dockerfile;
    }

    @Nonnull
    public String getImageTag() {
        return imageTag;
    }

    @Nullable
    public String getTarget() {
        return target;
    }

    @Nonnull
    public Map<String, String> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "ImageBuild{" +
                "contextDirectory=" + contextDirectory +
                ", dockerfile=" + dockerfile +
                ", imageTag='" + imageTag + '\'' +
                ", target='" + target + '\'' +
                ", arguments=" + arguments +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        Map<String, String> arguments;
        private Path contextDirectory;
        private Path dockerfile;
        private String imageTag;
        private String target;

        private Builder() {
        }

        public Builder withContextDirectory(@Nonnull Path contextDirectory) {
            this.contextDirectory = contextDirectory;
            return this;
        }

        public Builder withDockerfile(@Nonnull Path dockerfile) {
            this.dockerfile = dockerfile;
            return this;
        }

        public Builder withImageTag(@Nonnull String imageTag) {
            this.imageTag = imageTag;
            return this;
        }

        public Builder withTarget(@Nullable String target) {
            this.target = target;
            return this;
        }

        public Builder withArguments(@Nullable Map<String, String> arguments) {
            this.arguments = arguments;
            return this;
        }

        public ImageBuild build() {
            return new ImageBuild(contextDirectory, dockerfile, imageTag, target, arguments);
        }
    }
}
