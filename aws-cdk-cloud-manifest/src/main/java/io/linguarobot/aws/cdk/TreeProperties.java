package io.linguarobot.aws.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Artifact properties for the Construct Tree Artifact.
 */
class TreeProperties {

    private final String file;

    public TreeProperties(@JsonProperty("file") String file) {
        this.file = file;
    }

    /**
     * Filename of the tree artifact.
     */
    public String getFile() {
        return file;
    }
}
