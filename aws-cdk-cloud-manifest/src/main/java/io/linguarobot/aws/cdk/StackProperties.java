package io.linguarobot.aws.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.Map;


@JsonPropertyOrder({"templateFile", "parameters", "stackName", "terminationProtection", "assumeRoleArn",
        "cloudFormationExecutionRoleArn", "stackTemplateAssetObjectUrl", "requiresBootstrapStackVersion"})
public class StackProperties {

    private final String templateFile;
    private final Map<String, String> parameters;
    private final String stackName;
    private final boolean terminationProtectionEnabled;
    private final String assumeRoleArn;
    private final String cloudFormationExecutionRoleArn;
    private final String stackTemplateAssetObjectUrl;
    private final Number requiredToolkitStackVersion;

    public StackProperties(
            @JsonProperty("templateFile") String templateFile,
            @JsonProperty("parameters") Map<String, String> parameters,
            @JsonProperty("stackName") String stackName,
            @JsonProperty("terminationProtection") boolean terminationProtectionEnabled,
            @JsonProperty("assumeRoleArn") String assumeRoleArn,
            @JsonProperty("cloudFormationExecutionRoleArn") String cloudFormationExecutionRoleArn,
            @JsonProperty("stackTemplateAssetObjectUrl") String stackTemplateAssetObjectUrl,
            @JsonProperty("requiresBootstrapStackVersion") Number requiredToolkitStackVersion) {
        this.templateFile = templateFile;
        this.parameters = parameters != null ? Collections.unmodifiableMap(parameters) : Collections.emptyMap();
        this.stackName = stackName;
        this.terminationProtectionEnabled = terminationProtectionEnabled;
        this.assumeRoleArn = assumeRoleArn;
        this.cloudFormationExecutionRoleArn = cloudFormationExecutionRoleArn;
        this.stackTemplateAssetObjectUrl = stackTemplateAssetObjectUrl;
        this.requiredToolkitStackVersion = requiredToolkitStackVersion;
    }

    /**
     * A file relative to the assembly root which contains the CloudFormation template for this stack.
     */
    public String getTemplateFile() {
        return templateFile;
    }

    /**
     * Values for CloudFormation stack parameters that should be passed when the stack is deployed.
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * The name to use for the CloudFormation stack.
     */
    public String getStackName() {
        return stackName;
    }

    /**
     * Whether the termination protection is enabled.
     */
    public boolean isTerminationProtectionEnabled() {
        return terminationProtectionEnabled;
    }

    /**
     * The role that needs to be assumed to deploy the stack.
     */
    public String getAssumeRoleArn() {
        return assumeRoleArn;
    }

    /**
     * The role that is passed to CloudFormation to execute the change set.
     */
    public String getCloudFormationExecutionRoleArn() {
        return cloudFormationExecutionRoleArn;
    }

    /**
     * If the stack template has already been included in the asset manifest, its asset URL.
     */
    public String getStackTemplateAssetObjectUrl() {
        return stackTemplateAssetObjectUrl;
    }

    /**
     * Version of bootstrap stack required to deploy this stack.
     */
    public Number getRequiredToolkitStackVersion() {
        return requiredToolkitStackVersion;
    }

    @Override
    public String toString() {
        return "StackProperties{" +
                "templateFile='" + templateFile + '\'' +
                ", parameters=" + parameters +
                ", stackName='" + stackName + '\'' +
                ", terminationProtectionEnabled=" + terminationProtectionEnabled +
                ", assumeRoleArn='" + assumeRoleArn + '\'' +
                ", cloudFormationExecutionRoleArn='" + cloudFormationExecutionRoleArn + '\'' +
                ", stackTemplateAssetObjectUrl='" + stackTemplateAssetObjectUrl + '\'' +
                ", requiredToolkitStackVersion=" + requiredToolkitStackVersion +
                '}';
    }
}
