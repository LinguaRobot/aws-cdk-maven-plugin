package io.dataspray.aws.cdk.node;

import io.dataspray.aws.cdk.process.ProcessRunner;

import java.nio.file.Path;

/**
 * A client for interacting with Node.js.
 */
public interface NodeClient extends ProcessRunner {

    ProcessRunner npm();

    ProcessRunner npx();

    Path getPath();

}
