package io.dataspray.aws.cdk.maven.node;

import io.dataspray.aws.cdk.maven.CdkPluginException;

public class NodeInstallationException extends CdkPluginException {

    public NodeInstallationException(Throwable e) {
        super("Failed to install Node.js", e);
    }

    public NodeInstallationException(String message) {
        super("Failed to install Node.js. " + message);
    }

}
