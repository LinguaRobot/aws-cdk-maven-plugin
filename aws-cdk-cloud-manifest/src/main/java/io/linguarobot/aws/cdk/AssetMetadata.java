package io.linguarobot.aws.cdk;

import java.util.List;

public abstract class AssetMetadata extends ArtifactMetadata {

    private final AssetData data;

    protected AssetMetadata(MetadataType type, AssetData data, List<String> trace) {
        super(type, data, trace);
        this.data = data;
    }

    public AssetData getData() {
        return (AssetData) super.getData();
    }

    public String getId() {
        return data.getId();
    }

    public String getPackaging() {
        return data.getPackaging();
    }

    public String getSourceHash() {
        return data.getSourceHash();
    }

    public String getPath() {
        return data.getPath();
    }

}
