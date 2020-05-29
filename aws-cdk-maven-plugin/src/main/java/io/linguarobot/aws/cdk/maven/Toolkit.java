package io.linguarobot.aws.cdk.maven;

/**
 * Represents toolkit information for an execution environment.
 */
public class Toolkit {

    private final String bucketName;
    private final String bucketDomainName;

    public Toolkit(String bucketName, String bucketEndpoint) {
        this.bucketName = bucketName;
        this.bucketDomainName = bucketEndpoint;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getBucketDomainName() {
        return bucketDomainName;
    }

    @Override
    public String toString() {
        return "Toolkit{" +
                "bucketName='" + bucketName + '\'' +
                ", bucketDomainName='" + bucketDomainName + '\'' +
                '}';
    }
}
