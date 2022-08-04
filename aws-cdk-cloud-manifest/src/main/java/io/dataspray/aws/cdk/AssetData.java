package io.dataspray.aws.cdk;

public abstract class AssetData {

    private final String packaging;
    private final String id;
    private final String sourceHash;
    private final String path;

    AssetData(String packaging, String id, String sourceHash, String path) {
        this.id = id;
        this.packaging = packaging;
        this.sourceHash = sourceHash;
        this.path = path;
    }

    /**
     * Requested packaging style.
     */
    public String getPackaging() {
        return packaging;
    }

    /**
     * Logical identifier for the asset.
     */
    public String getId() {
        return id;
    }

    /**
     * The hash of the asset source.
     */
    public String getSourceHash() {
        return sourceHash;
    }

    /**
     * Path on disk to the asset.
     */
    public String getPath() {
        return path;
    }
}
