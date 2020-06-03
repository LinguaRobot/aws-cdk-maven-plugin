package io.linguarobot.aws.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({"file", "requiresBootstrapStackVersion"})
public class AssetProperties {

    private final String file;
    private final Number requiredToolkitStackVersion;

    public AssetProperties(@JsonProperty("file") String file,
                           @JsonProperty("requiresBootstrapStackVersion") Number requiredToolkitStackVersion) {
        this.file = file;
        this.requiredToolkitStackVersion = requiredToolkitStackVersion;
    }

    /**
     * Filename of the asset manifest.
     */
    public String getFile() {
        return file;
    }

    /**
     * Version of bootstrap stack required to deploy this stack.
     */
    public Number getRequiredToolkitStackVersion() {
        return requiredToolkitStackVersion;
    }

    @Override
    public String toString() {
        return "AssetProperties{" +
                "file='" + file + '\'' +
                ", requiredToolkitStackVersion=" + requiredToolkitStackVersion +
                '}';
    }

}
