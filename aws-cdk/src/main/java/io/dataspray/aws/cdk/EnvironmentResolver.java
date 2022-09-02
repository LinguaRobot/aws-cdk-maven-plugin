package io.dataspray.aws.cdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsProfileRegionProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.sts.StsClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private EnvironmentResolver(Region defaultRegion, @Nullable String defaultAccount, AccountCredentialsProvider accountCredentialsProvider) {
        this.defaultRegion = defaultRegion;
        this.defaultAccount = defaultAccount;
        this.accountCredentialsProvider = accountCredentialsProvider;
    }

    public static EnvironmentResolver create(@Nullable String profile) {
        Region defaultRegion = fetchDefaultRegion(profile).orElse(Region.US_EAST_1); // us-east-1 is used by default in CDK
        AwsCredentials defaultCredentials = fetchDefaultCredentials(profile).orElse(null);
        String defaultAccount = defaultCredentials != null ? fetchAccount(defaultRegion, defaultCredentials) : null;
        List<AccountCredentialsProvider> credentialsProviders = new ArrayList<>();
        if (defaultCredentials != null) {
            credentialsProviders.add(accountId -> {
                if (accountId.equals(defaultAccount)) {
                    return Optional.of(defaultCredentials);
                }

                return Optional.empty();
            });
        }

        AccountCredentialsProvider credentialsProvider = new AccountCredentialsProviderChain(credentialsProviders);
        return new EnvironmentResolver(defaultRegion, defaultAccount, credentialsProvider);
    }

    /**
     * Resolves an environment from the given environment URI.
     *
     * @param environment an environment URI in the following format: {@code aws://account/region}
     * @return resolved environment
     * @throws IllegalArgumentException if the given environment URI is invalid
     * @throws CdkException in case the given environment is account-agnostic and a default account cannot be
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
                    throw new CdkException("Unable to dynamically determine which AWS account to use for deployment");
                }

                AwsCredentials credentials = accountCredentialsProvider.get(account)
                        .orElseThrow(() -> new CdkException("Credentials for the account '" + account +
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
                throw new CdkException("Unable to dynamically determine which AWS account to use for deployment");
            }

            AwsCredentials credentials = accountCredentialsProvider.get(account)
                    .orElseThrow(() -> new CdkException("Credentials for the account '" + account +
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

    /**
     * Returns an account number for the given credentials.
     */
    private static String fetchAccount(Region region, AwsCredentials credentials) {
        StsClient stsClient = StsClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        return stsClient.getCallerIdentity().account();
    }

    private static Optional<Region> fetchDefaultRegion(@Nullable String profile) {
        AwsRegionProvider regionProvider;
        if (profile != null) {
            regionProvider = new AwsRegionProviderChain(
                    new AwsProfileRegionProvider(null, profile),
                    new DefaultAwsRegionProviderChain()
            );
        } else {
            regionProvider = new DefaultAwsRegionProviderChain();
        }

        try {
            return Optional.of(regionProvider.getRegion());
        } catch (SdkClientException e) {
            return Optional.empty();
        }
    }

    private static Optional<AwsCredentials> fetchDefaultCredentials(@Nullable String profile) {
        AwsCredentialsProvider credentialsProvider;
        if (profile != null) {
            ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.builder()
                    .profileName(profile)
                    .build();
            credentialsProvider = AwsCredentialsProviderChain.builder()
                    .credentialsProviders(profileCredentialsProvider, DefaultCredentialsProvider.create())
                    .build();
        } else {
            credentialsProvider = DefaultCredentialsProvider.create();
        }

        try {
            return Optional.of(credentialsProvider.resolveCredentials());
        } catch (SdkClientException e) {
            return Optional.empty();
        }
    }
}
