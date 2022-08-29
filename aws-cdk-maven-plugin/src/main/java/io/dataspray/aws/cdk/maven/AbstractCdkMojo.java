package io.dataspray.aws.cdk.maven;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import io.dataspray.aws.cdk.maven.api.AccountCredentialsProvider;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An abstract Mojo that defines some parameters common for synthesis and deployment.
 */
public abstract class AbstractCdkMojo extends AbstractMojo {

    private static final Logger logger = LoggerFactory.getLogger(AbstractCdkMojo.class);

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JSR353Module());

    /**
     * A profile that will be used while looking for credentials and region.
     */
    @Parameter(property = "aws.cdk.profile")
    private String profile;

    /**
     * A cloud assembly directory.
     */
    @Parameter(property = "aws.cdk.cloud.assembly.directory", defaultValue = "${project.build.directory}/cdk.out")
    private File cloudAssemblyDirectory;

    /**
     * Enables/disables an execution.
     */
    @Parameter(defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (!skip) {
            try {
                execute(cloudAssemblyDirectory.toPath(), createEnvironmentResolver());
            } catch (CdkPluginException e) {
                throw new MojoExecutionException(e.getMessage(), e.getCause());
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        } else {
            logger.debug("The execution is configured to be skipped");
        }
    }

    public abstract void execute(Path cloudAssemblyDirectory, EnvironmentResolver environmentResolver);

    /**
     * Creates an {@code EnvironmentResolved} based on the default region and credentials.
     */
    private EnvironmentResolver createEnvironmentResolver() {
        Region defaultRegion = getDefaultRegion().orElse(Region.US_EAST_1); // us-east-1 is used by default in CDK
        AwsCredentials defaultCredentials = getDefaultCredentials().orElse(null);
        String defaultAccount = defaultCredentials != null ? getAccount(defaultRegion, defaultCredentials) : null;
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
     * Returns an account number for the given credentials.
     */
    private String getAccount(Region region, AwsCredentials credentials) {
        StsClient stsClient = StsClient.builder()
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        return stsClient.getCallerIdentity().account();
    }

    private Optional<Region> getDefaultRegion() {
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

    private Optional<AwsCredentials> getDefaultCredentials() {
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
