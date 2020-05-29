package io.linguarobot.aws.cdk.maven.node;

import io.linguarobot.aws.cdk.maven.CdkPluginException;

public class NodeInstallationException extends CdkPluginException {

    public NodeInstallationException(Throwable e) {
        super("Unable to install Node.js", e);
    }

    public NodeInstallationException(String message) {
        super("Unable to install Node.js. " + message);
    }

}
