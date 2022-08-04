package io.dataspray.aws.cdk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A manifest which describes the cloud assembly.
 */
@JsonPropertyOrder({"version", "artifacts", "missing", "runtime"})
public class CloudManifest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String version;
    private final Map<String, Artifact> artifacts;
    private final List<MissingContext> missingContexts;
    private final RuntimeInfo runtime;

    @JsonCreator
    private CloudManifest(
            @JsonProperty("version") String version,
            @JsonProperty("artifacts") Map<String, Artifact> artifacts,
            @JsonProperty("missing") List<MissingContext> missingContexts,
            @JsonProperty("runtime") RuntimeInfo runtime) {
        this.version = version;
        this.artifacts = artifacts != null ? artifacts : Collections.emptyMap();
        this.missingContexts = missingContexts != null ? missingContexts : Collections.emptyList();
        this.runtime = runtime;
    }

    /**
     * Protocol version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * The set of artifacts in this assembly.
     */
    public Map<String, Artifact> getArtifacts() {
        return artifacts;
    }

    /**
     * Missing context information. If this field has values, it means that the cloud assembly is not complete and
     * should not be deployed.
     */
    public List<MissingContext> getMissingContexts() {
        return missingContexts;
    }

    /**
     * Information about the application's runtime components.
     */
    public RuntimeInfo getRuntime() {
        return runtime;
    }

    @Override
    public String toString() {
        return "CloudManifest{" +
                "version='" + version + '\'' +
                ", artifacts=" + artifacts +
                ", missingContexts=" + missingContexts +
                ", runtime=" + runtime +
                '}';
    }

    public static CloudManifest create(Path cloudAssemblyDirectory) throws IOException {
        Path manifest = cloudAssemblyDirectory.resolve("manifest.json");
        if (!Files.exists(cloudAssemblyDirectory)) {
            throw new IllegalArgumentException("The manifest file '" + cloudAssemblyDirectory + "' is missing");
        }

        return OBJECT_MAPPER.readValue(manifest.toFile(), CloudManifest.class);
    }
}
