package io.dataspray.aws.cdk;

/**
 * Base exception for all the exceptions thrown during execution time.
 */
public class CdkException extends RuntimeException {

    public CdkException(String message) {
        super(message);
    }

    public CdkException(String message, Throwable cause) {
        super(message, cause);
    }

}
