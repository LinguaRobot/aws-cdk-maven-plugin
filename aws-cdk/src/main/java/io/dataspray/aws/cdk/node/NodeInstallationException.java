package io.dataspray.aws.cdk.node;

import io.dataspray.aws.cdk.CdkException;

public class NodeInstallationException extends CdkException {

    public NodeInstallationException(Throwable e) {
        super("Failed to install Node.js", e);
    }

    public NodeInstallationException(String message) {
        super("Failed to install Node.js. " + message);
    }

}
