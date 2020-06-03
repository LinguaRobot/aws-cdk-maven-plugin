package io.linguarobot.aws.cdk;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Type of artifact metadata.
 */
public enum MetadataType {

    /**
     * Asset in metadata.
     */
    ASSET("aws:cdk:asset"),
    /**
     * Metadata key used to print INFO-level messages by the toolkit when an app is synthesized.
     */
    INFO("aws:cdk:info"),
    /**
     * Metadata key used to print WARNING-level messages by the toolkit when an app is synthesized.
     */
    WARN("aws:cdk:warning"),
    /**
     * Metadata key used to print ERROR-level messages by the toolkit when an app is synthesized.
     */
    ERROR("aws:cdk:error"),
    /**
     * Represents the CloudFormation logical ID of a resource at a certain path.
     */
    LOGICAL_ID("aws:cdk:logicalId"),
    /**
     * Represents tags of a stack.
     */
    STACK_TAGS("aws:cdk:stack-tags");

    private static final Map<String, MetadataType> MAPPING = Stream.of(values())
            .collect(Collectors.toMap(MetadataType::getValue, Function.identity()));

    private final String value;

    MetadataType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getValue();
    }

    @JsonCreator
    public static MetadataType fromType(String type) {
        MetadataType metadataType = MAPPING.get(type);
        if (metadataType == null) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
        return metadataType;
    }
}
