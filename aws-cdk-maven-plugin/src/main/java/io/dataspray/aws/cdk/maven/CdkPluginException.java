package io.dataspray.aws.cdk.maven;

/**
 * Base exception for all the exceptions thrown during plugin execution time.
 */
public class CdkPluginException extends RuntimeException {

    public CdkPluginException(String message) {
        super(message);
    }

    public CdkPluginException(String message, Throwable cause) {
        super(message, cause);
    }

}
