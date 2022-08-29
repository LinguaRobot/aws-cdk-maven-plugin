package io.dataspray.aws.cdk.maven;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * Represents a resolved execution environment.
 */
public class ResolvedEnvironment {

    private final String name;
    private final Region region;
    private final String account;
    private final AwsCredentialsProvider credentialsProvider;

    public ResolvedEnvironment(Region region, String account, AwsCredentials credentials) {
        this.name = "aws://" + account + "/" + region;
        this.region = region;
        this.account = account;
        this.credentialsProvider = StaticCredentialsProvider.create(credentials);
    }

    public String getName() {
        return name;
    }

    public Region getRegion() {
        return region;
    }

    public String getAccount() {
        return account;
    }

    public AwsCredentials getCredentials() {
        return credentialsProvider.resolveCredentials();
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public String resolveVariables(String input) {
        return input.replaceAll("\\$\\{AWS::Region}", region);
    }

    @Override
    public String toString() {
        return getName();
    }

}
