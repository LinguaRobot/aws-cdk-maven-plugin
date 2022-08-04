package io.dataspray.aws.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.Map;

@JsonPropertyOrder({"packaging", "imageNameParameter", "repositoryName", "imageTag", "buildArgs", "target", "file",
        "id", "sourceHash", "path"})
public class ContainerAssetData extends AssetData {

    private final String imageNameParameter;
    private final String repositoryName;
    private final String imageTag;
    private final Map<String, String> buildArguments;
    private final String target;
    private final String file;

    ContainerAssetData(@JsonProperty("packaging") String packaging,
                       @JsonProperty("imageNameParameter") String imageNameParameter,
                       @JsonProperty("repositoryName") String repositoryName,
                       @JsonProperty("imageTag") String imageTag,
                       @JsonProperty("buildArgs") Map<String, String> buildArguments,
                       @JsonProperty("target") String target,
                       @JsonProperty("file") String file,
                       @JsonProperty("id") String id,
                       @JsonProperty("sourceHash") String sourceHash,
                       @JsonProperty("path") String path) {
        super(packaging, id, sourceHash, path);
        this.imageNameParameter = imageNameParameter;
        this.repositoryName = repositoryName;
        this.imageTag = imageTag;
        this.buildArguments = buildArguments != null ? Collections.unmodifiableMap(buildArguments) : Collections.emptyMap();
        this.target = target;
        this.file = file;
    }

    /**
     * ECR Repository name and repo digest (separated by "@sha256:") where this image is stored.
     */
    public String getImageNameParameter() {
        return imageNameParameter;
    }

    /**
     * ECR repository name, if omitted a default name based on the asset's ID is used instead.
     */
    public String getRepositoryName() {
        return repositoryName;
    }


    public String getImageTag() {
        return imageTag;
    }

    /**
     * "Build args to pass to the `docker build` command.
     */
    public Map<String, String> getBuildArguments() {
        return buildArguments;
    }

    /**
     * Docker target to build to.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Path to the Dockerfile.
     */
    public String getFile() {
        return file;
    }

    @Override
    public String toString() {
        return "ContainerAssetData{" +
                "packaging='" + getPackaging() + '\'' +
                ", imageNameParameter='" + imageNameParameter + '\'' +
                ", repositoryName='" + repositoryName + '\'' +
                ", imageTag='" + imageTag + '\'' +
                ", buildArguments=" + buildArguments +
                ", target='" + target + '\'' +
                ", file='" + file + '\'' +
                ", id='" + getId() + '\'' +
                ", sourceHash='" + getSourceHash() + '\'' +
                ", path='" + getPath() + '\'' +
                '}';
    }
}
