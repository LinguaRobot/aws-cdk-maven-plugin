package io.linguarobot.aws.cdk;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;

import java.util.List;

@JsonPropertyOrder({"type", "data", "trace"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "data.packaging", defaultImpl = ArtifactMetadata.class, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileAssetMetadata.class, name = "zip"),
        @JsonSubTypes.Type(value = FileAssetMetadata.class, name = "file"),
        @JsonSubTypes.Type(value = ContainerImageAssetMetadata.class, name = "container-image"),
})
@JsonTypeResolver(NestedPropertyTypeResolver.class)
public class ArtifactMetadata {

    private final String type;
    private final Object data;
    private final List<String> trace;

    public ArtifactMetadata(@JsonProperty("type") String type,
                            @JsonProperty("data") Object data,
                            @JsonProperty("trace") List<String> trace) {
        this.type = type;
        this.data = data;
        this.trace = trace;
    }

    /**
     * The type of the metadata entry.
     */
    public String getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    /**
     * A stack trace for when the entry was created.
     */
    public List<String> getTrace() {
        return trace;
    }

    @Override
    public String toString() {
        return "ArtifactMetadata{" +
                "type='" + type + '\'' +
                ", data = " + data +
                ", trace=" + trace +
                '}';
    }
}
