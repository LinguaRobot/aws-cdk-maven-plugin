package io.linguarobot.aws.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

@JsonPropertyOrder({"type", "environment", "metadata", "dependencies", "properties"})
public class AssetArtifact extends Artifact {

    protected AssetArtifact(
            @JsonProperty("type") ArtifactType type,
            @JsonProperty("environment") String environment,
            @JsonProperty("metadata") Map<String, List<ArtifactMetadata>> metadata,
            @JsonProperty("dependencies") List<String> dependencies,
            @JsonProperty("properties") AssetProperties properties) {
        super(type, environment, metadata, dependencies, properties);
    }

    @Override
    public AssetProperties getProperties() {
        return (AssetProperties) super.getProperties();
    }

}
