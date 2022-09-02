package io.dataspray.aws.cdk;

import software.amazon.awssdk.auth.credentials.AwsCredentials;

import java.util.Optional;

/**
 * Provides an {@code Optional} with AWS credentials for the given {@code accountId} or an empty {@code Optional} in
 * case the provider can't provide the credentials.
 */
public interface AccountCredentialsProvider {

    /**
     * Returns AWS credentials for the given {@code accountId}.
     *
     * @param accountId an AWS account for which the credentials will be provided
     * @return an {@code Optional} with AWS credentials or {@code Optional.empty()} in case the provider is not able to
     * provide credentials for the account.
     */
    Optional<AwsCredentials> get(String accountId);

}
