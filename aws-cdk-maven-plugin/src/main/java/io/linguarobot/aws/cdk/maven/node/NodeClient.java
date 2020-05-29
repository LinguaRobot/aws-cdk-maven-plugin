package io.linguarobot.aws.cdk.maven.node;

import io.linguarobot.aws.cdk.maven.process.ProcessRunner;

import java.nio.file.Path;

/**
 * A client for interacting with Node.js.
 */
public interface NodeClient extends ProcessRunner {

    ProcessRunner npm();

    ProcessRunner npx();

    Path getPath();

}
