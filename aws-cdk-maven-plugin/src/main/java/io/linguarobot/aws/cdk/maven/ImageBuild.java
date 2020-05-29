package io.linguarobot.aws.cdk.maven;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;

/**
 * Represents Docker image build parameters.
 */
public class ImageBuild {

    private final Path contextDirectory;
    private final Path dockerfile;
    private final String tag;
    private final String target;
    private final Map<String, String> arguments;

    public ImageBuild(Path contextDirectory, Path dockerfile, String tag, Map<String, String> arguments, @Nullable String target) {
        this.contextDirectory = contextDirectory;
        this.dockerfile = dockerfile;
        this.tag = tag;
        this.target = target;
        this.arguments = arguments;
    }

    public Path getContextDirectory() {
        return contextDirectory;
    }

    public Path getDockerfile() {
        return dockerfile;
    }

    public String getTag() {
        return tag;
    }

    @Nullable
    public String getTarget() {
        return target;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "ImageBuild{" +
                "context=" + contextDirectory +
                ", dockerfile=" + dockerfile +
                ", tag='" + tag + '\'' +
                ", target='" + target + '\'' +
                ", arguments=" + arguments +
                ", contextDirectory=" + contextDirectory +
                '}';
    }
}
