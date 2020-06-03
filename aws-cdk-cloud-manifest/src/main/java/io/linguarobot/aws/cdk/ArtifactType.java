package io.linguarobot.aws.cdk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ArtifactType {

    STACK("aws:cloudformation:stack"),
    ASSET("cdk:asset-manifest"),
    TREE("cdk:tree");

    private final static Map<String, ArtifactType> MAPPING = Arrays.stream(values())
            .collect(Collectors.toMap(ArtifactType::value, Function.identity()));

    private final String value;

    static {
        for (ArtifactType artifactType : values()) {
            MAPPING.put(artifactType.value, artifactType);
        }
    }

    ArtifactType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    @JsonCreator
    public static ArtifactType fromType(String type) {
        ArtifactType artifactType = MAPPING.get(type);
        if (artifactType == null) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }

        return artifactType;
    }

}
