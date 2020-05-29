package io.linguarobot.aws.cdk.maven;

import io.linguarobot.aws.cdk.maven.api.AccountCredentialsProvider;
import software.amazon.awscdk.cxapi.Environment;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.regions.Region;

import javax.annotation.Nullable;
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

    private static final String UNKNOWN_ACCOUNT = "unknown-account";
    private static final String UNKNOWN_REGION = "unknown-region";

    private final Region defaultRegion;
    private final String defaultAccount;
    private final AccountCredentialsProvider accountCredentialsProvider;

    public EnvironmentResolver(Region defaultRegion, @Nullable String defaultAccount, AccountCredentialsProvider accountCredentialsProvider) {
        this.defaultRegion = defaultRegion;
        this.defaultAccount = defaultAccount;
        this.accountCredentialsProvider = accountCredentialsProvider;
    }

    /**
     * Resolves the given environment.
     *
     * @param environment an environment to resolve
     * @throws CdkPluginException in case the given environment is account-agnostic and a default account cannot be
     * determined or if credentials cannot be resolved for the account
     * @return resolved environment
     */
    public ResolvedEnvironment resolve(Environment environment) {
        Region region = !environment.getRegion().equals(UNKNOWN_REGION) ? Region.of(environment.getRegion()) : defaultRegion;
        String account = !environment.getAccount().equals(UNKNOWN_ACCOUNT) ? environment.getAccount() : defaultAccount;
        if (account == null) {
            throw new CdkPluginException("Unable to dynamically determine which AWS account to use for deployment");
        }

        AwsCredentials credentials = accountCredentialsProvider.get(account)
                .orElseThrow(() -> new CdkPluginException("Credentials for the account '" + environment.getAccount() + "' are not available."));

        return new ResolvedEnvironment(region, account, credentials);
    }

    public Region getDefaultRegion() {
        return this.defaultRegion;
    }

    public Optional<String> getDefaultAccount() {
        return Optional.ofNullable(defaultAccount);
    }
}
