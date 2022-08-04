package io.dataspray.aws.cdk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/**
 * Information about the application's runtime components.
 */
public class RuntimeInfo {

    private final Map<String, String> libraries;

    @JsonCreator
    public RuntimeInfo(@JsonProperty("libraries") Map<String, String> libraries) {
        this.libraries = libraries != null ? Collections.unmodifiableMap(libraries) : Collections.emptyMap();
    }

    /**
     * Libraries loaded in the application, associated with their versions.
     */
    public Map<String, String> getLibraries() {
        return libraries;
    }

    @Override
    public String toString() {
        return "RuntimeInfo{" +
                "libraries=" + libraries +
                '}';
    }
}
