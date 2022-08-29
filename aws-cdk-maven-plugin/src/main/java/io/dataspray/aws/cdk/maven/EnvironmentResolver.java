package io.dataspray.aws.cdk.maven;

import io.dataspray.aws.cdk.maven.api.AccountCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.regions.Region;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves the execution environment i.e. populates account/region-agnostic environments with the default values and
 * lookups for the credentials to be used with the environment.
 *
 * The default region is determined using the default region provider chain. The default account is determined based
 * on the credentials provided by the default credentials provider chain.
 *
 * @see software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain
 * @see software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
 */
public class EnvironmentResolver {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentResolver.class);

    private static final String SCHEMA_PREFIX = "aws://";
    private static final String UNKNOWN_ACCOUNT = "unknown-account";
    private static final String UNKNOWN_REGION = "unknown-region";
    private static final String CURRENT_REGION = "current_region";

    private final Region defaultRegion;
    private final String defaultAccount;
    private final AccountCredentialsProvider accountCredentialsProvider;

    public EnvironmentResolver(Region defaultRegion, @Nullable String defaultAccount, AccountCredentialsProvider accountCredentialsProvider) {
        this.defaultRegion = defaultRegion;
        this.defaultAccount = defaultAccount;
        this.accountCredentialsProvider = accountCredentialsProvider;
    }

    /**
     * Resolves an environment from the given environment URI.
     *
     * @param environment an environment URI in the following format: {@code aws://account/region}
     * @return resolved environment
     * @throws IllegalArgumentException if the given environment URI is invalid
     * @throws CdkPluginException in case the given environment is account-agnostic and a default account cannot be
     * determined or if credentials cannot be resolved for the account
     */
    public ResolvedEnvironment resolve(String environment) {
        logger.debug("Resolving env from {}", environment);
        if (environment.startsWith(SCHEMA_PREFIX)) {
            String[] parts = environment.substring(SCHEMA_PREFIX.length()).split("/");
            if (parts.length == 2) {
                String account = !parts[0].equals(UNKNOWN_ACCOUNT) ? parts[0] : defaultAccount;
                Region region = !parts[1].equals(UNKNOWN_REGION) ? Region.of(parts[1]) : defaultRegion;
                if (account == null) {
                    throw new CdkPluginException("Unable to dynamically determine which AWS account to use for deployment");
                }

                AwsCredentials credentials = accountCredentialsProvider.get(account)
                        .orElseThrow(() -> new CdkPluginException("Credentials for the account '" + account +
                                "' are not available."));

                return new ResolvedEnvironment(region, account, credentials);
            }
        }

        throw new IllegalArgumentException("Invalid environment format '" + environment + "'. Expected format: " +
                "aws://account/region");
    }

    public ResolvedEnvironment resolveFromDestination(String destinationKey) {
        logger.debug("Resolving env from destination {}", destinationKey);
        String[] parts = destinationKey.split("-", 2);
        if (parts.length == 2) {
            String account = !parts[0].equals(UNKNOWN_ACCOUNT) ? parts[0] : defaultAccount;
            Region region = (!parts[1].equals(UNKNOWN_REGION) && !parts[1].equals(CURRENT_REGION)) ? Region.of(parts[1]) : defaultRegion;
            if (account == null) {
                throw new CdkPluginException("Unable to dynamically determine which AWS account to use for deployment");
            }

            AwsCredentials credentials = accountCredentialsProvider.get(account)
                    .orElseThrow(() -> new CdkPluginException("Credentials for the account '" + account +
                            "' are not available."));

            return new ResolvedEnvironment(region, account, credentials);
        }

        throw new IllegalArgumentException("Invalid environment format '" + destinationKey + "'. Expected format: " +
                "account-region");
    }

    @Nonnull
    public Region getDefaultRegion() {
        return this.defaultRegion;
    }

    @Nullable
    public String getDefaultAccount() {
        return this.defaultAccount;
    }
}
