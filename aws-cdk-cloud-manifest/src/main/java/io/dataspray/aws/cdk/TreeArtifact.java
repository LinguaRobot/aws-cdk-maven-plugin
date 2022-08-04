package io.dataspray.aws.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

@JsonPropertyOrder({"type", "environment", "metadata", "dependencies", "properties"})
public class TreeArtifact extends Artifact {

    public TreeArtifact(
            @JsonProperty("type") ArtifactType type,
            @JsonProperty("environment") String environment,
            @JsonProperty("metadata") Map<String, List<ArtifactMetadata>> metadata,
            @JsonProperty("dependencies") List<String> dependencies,
            @JsonProperty("properties") TreeProperties properties) {
        super(type, environment, metadata, dependencies, properties);
    }

    @Override
    public TreeProperties getProperties() {
        return (TreeProperties) super.getProperties();
    }

}
