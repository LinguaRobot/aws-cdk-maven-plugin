package io.dataspray.aws.cdk;

/**
 * Defines a set of constants for artifact metadata types.
 */
public class MetadataTypes {

    private MetadataTypes() {
    }

    /**
     * Asset in metadata.
     */
    public static final String ASSET = "aws:cdk:asset";

    /**
     * Metadata key used to print INFO-level messages by the toolkit when an app is synthesized.
     */
    public static final String INFO = "aws:cdk:info";

    /**
     * Metadata key used to print WARNING-level messages by the toolkit when an app is synthesized.
     */
    public static final String WARN = "aws:cdk:warning";

    /**
     * Metadata key used to print ERROR-level messages by the toolkit when an app is synthesized.
     */
    public static final String ERROR = "aws:cdk:error";

    /**
     * Represents the CloudFormation logical ID of a resource at a certain path.
     */
    public static final String LOGICAL_ID = "aws:cdk:logicalId";

    /**
     * Represents tags of a stack.
     */
    public static final String STACK_TAGS = "aws:cdk:stack-tags";

}
