package io.dataspray.aws.cdk.maven.node;

/**
 * A Node.js installer which downloads and installs, if needed, Node.js to the local maven repository.
 */
public interface NodeInstaller {

    String BASE_DOWNLOAD_URL = "https://nodejs.org";

    /**
     * Installs, if needed, the given version of Node.js for the current platform to the local maven repository.
     *
     * @param version Node.js version to install
     * @throws NodeInstallationException in case the installation fails
     * @return a {@code NodeClient} for the installed Node.js
     */
    NodeClient install(NodeVersion version);

}
