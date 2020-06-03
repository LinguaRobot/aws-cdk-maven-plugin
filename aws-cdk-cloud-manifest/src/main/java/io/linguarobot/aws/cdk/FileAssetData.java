package io.linguarobot.aws.cdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"packaging", "s3BucketParameter", "s3KeyParameter", "artifactHashParameter", "id", "sourceHash", "path"})
public class FileAssetData extends AssetData {

    private final String s3BucketParameter;
    private final String s3KeyParameter;
    private final String artifactHashParameter;

    public FileAssetData(@JsonProperty("packaging") String packaging,
                         @JsonProperty("s3BucketParameter") String s3BucketParameter,
                         @JsonProperty("s3KeyParameter") String s3KeyParameter,
                         @JsonProperty("artifactHashParameter") String artifactHashParameter,
                         @JsonProperty("id") String id,
                         @JsonProperty("sourceHash") String sourceHash,
                         @JsonProperty("path") String path) {
        super(packaging, id, sourceHash, path);
        this.s3BucketParameter = s3BucketParameter;
        this.s3KeyParameter = s3KeyParameter;
        this.artifactHashParameter = artifactHashParameter;
    }

    /**
     * Name of parameter where S3 bucket should be passed in.
     */
    public String getS3BucketParameter() {
        return s3BucketParameter;
    }

    /**
     * Name of parameter where S3 key should be passed in.
     */
    public String getS3KeyParameter() {
        return s3KeyParameter;
    }

    /**
     * The name of the parameter where the hash of the bundled asset should be passed in.
     */
    public String getArtifactHashParameter() {
        return artifactHashParameter;
    }

    @Override
    public String toString() {
        return "FileAssetData{" +
                "id='" + getId() + '\'' +
                ", packaging='" + getPackaging() + '\'' +
                ", path='" + getPath() + '\'' +
                ", sourceHash='" + getSourceHash() + '\'' +
                ", s3BucketParameter='" + s3BucketParameter + '\'' +
                ", s3KeyParameter='" + s3KeyParameter + '\'' +
                ", artifactHashParameter='" + artifactHashParameter + '\'' +
                '}';
    }
}
