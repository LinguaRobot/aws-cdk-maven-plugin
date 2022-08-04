package io.dataspray.aws.cdk.maven;

import io.dataspray.aws.cdk.maven.api.AccountCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

import java.util.List;
import java.util.Optional;

/**
 * Composite {@link AccountCredentialsProvider} that sequentially delegates to a chain of providers looking in order
 * to find {@link AwsCredentials} for the account.
 */
public class AccountCredentialsProviderChain implements AccountCredentialsProvider {

    private final List<AccountCredentialsProvider> credentialsProviders;

    public AccountCredentialsProviderChain(List<AccountCredentialsProvider> credentialsProviders) {
        this.credentialsProviders = credentialsProviders;
    }

    @Override
    public Optional<AwsCredentials> get(String accountId) {
        return credentialsProviders.stream()
                .map(credentialsProvider -> credentialsProvider.get(accountId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findAny();
    }
}
