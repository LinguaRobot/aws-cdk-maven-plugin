package io.linguarobot.aws.cdk.maven;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.cloudassembly.schema.ContainerImageAssetMetadataEntry;

import java.util.Map;

public class DefaultContainerImageAssetMetadataEntry implements ContainerImageAssetMetadataEntry {

    private String id;
    private String packaging;
    private String path;
    private String sourceHash;
    private Map<String, String> buildArgs;
    private String file;
    private String imageNameParameter;
    private String imageTag;
    private String repositoryName;
    private String target;


    @Override
    public @NotNull String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public @NotNull String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    @Override
    public @NotNull String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public @NotNull String getSourceHash() {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
    }

    @Override
    public @Nullable Map<String, String> getBuildArgs() {
        return buildArgs;
    }

    public void setBuildArgs(Map<String, String> buildArgs) {
        this.buildArgs = buildArgs;
    }

    @Override
    public @Nullable String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    @Override
    public @Nullable String getImageNameParameter() {
        return imageNameParameter;
    }

    public void setImageNameParameter(String imageNameParameter) {
        this.imageNameParameter = imageNameParameter;
    }

    @Override
    public @Nullable String getImageTag() {
        return imageTag;
    }

    public void setImageTag(String imageTag) {
        this.imageTag = imageTag;
    }

    @Override
    public @Nullable String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    @Override
    public @Nullable String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    @Override
    public String toString() {
        return "DefaultContainerImageAssetMetadataEntry{" +
                "id='" + id + '\'' +
                ", packaging='" + packaging + '\'' +
                ", path='" + path + '\'' +
                ", sourceHash='" + sourceHash + '\'' +
                ", buildArgs=" + buildArgs +
                ", file='" + file + '\'' +
                ", imageNameParameter='" + imageNameParameter + '\'' +
                ", imageTag='" + imageTag + '\'' +
                ", repositoryName='" + repositoryName + '\'' +
                ", target='" + target + '\'' +
                '}';
    }
}
