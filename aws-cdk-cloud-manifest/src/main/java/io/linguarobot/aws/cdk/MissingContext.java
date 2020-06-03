package io.linguarobot.aws.cdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/**
 * Represents a missing piece of context.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"key", "provider", "props"})
public class MissingContext {

    private final String key;
    private final String provider;
    private final Map<String, Object> properties;

    public MissingContext(
            @JsonProperty("key") String key,
            @JsonProperty("provider") String provider,
            @JsonProperty("props") Map<String, Object> properties) {
        this.key = key;
        this.provider = provider;
        this.properties = properties;
    }


    /**
     * The missing context key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Identifier for the context provider.
     */
    public String getProvider() {
        return provider;
    }

    /**
     * A set of provider-specific properties.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "MissingContext{" +
                "key='" + key + '\'' +
                ", provider='" + provider + '\'' +
                ", properties=" + properties +
                '}';
    }
}
