package io.linguarobot.aws.cdk;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A manifest for a single artifact within the cloud assembly.
 */
@JsonPropertyOrder({"type", "environment", "metadata", "dependencies", "properties"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StackArtifact.class, name = "aws:cloudformation:stack"),
        @JsonSubTypes.Type(value = TreeArtifact.class, name = "cdk:tree"),
        @JsonSubTypes.Type(value = AssetArtifact.class,name = "cdk:asset-manifest"),
        @JsonSubTypes.Type(value = ArtifactMetadata.class, name = "none")
})
public class Artifact {

    private final ArtifactType type;
    private final String environment;
    private final Map<String, List<ArtifactMetadata>> metadata;
    private final List<String> dependencies;
    private final Object properties;

    public Artifact(
            @JsonProperty("type") ArtifactType type,
            @JsonProperty("environment") String environment,
            @JsonProperty("metadata") Map<String, List<ArtifactMetadata>> metadata,
            @JsonProperty("dependencies") List<String> dependencies,
            @JsonProperty("properties") Object properties) {
        this.type = type;
        this.environment = environment;
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
        this.dependencies = dependencies != null ? Collections.unmodifiableList(dependencies) : Collections.emptyList();
        this.properties = properties;
    }

    /**
     * Type of the cloud artifact.
     */
    public ArtifactType getType() {
        return type;
    }

    /**
     * The environment into which this artifact is deployed.
     */
    public String getEnvironment() {
        return environment;
    }

    /**
     * Associated metadata.
     */
    public Map<String, List<ArtifactMetadata>> getMetadata() {
        return metadata;
    }

    /**
     * IDs of artifacts that must be deployed before this artifact. (Default - no dependencies.)
     */
    public List<String> getDependencies() {
        return dependencies;
    }

    /**
     * The set of properties for the artifact.
     */
    public Object getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "ArtifactManifest{" +
                "type=" + type +
                ", environment='" + environment + '\'' +
                ", metadata=" + metadata +
                ", dependencies=" + dependencies +
                ", properties=" + properties +
                '}';
    }
}
