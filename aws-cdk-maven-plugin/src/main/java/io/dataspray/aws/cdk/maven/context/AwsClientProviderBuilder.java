package io.dataspray.aws.cdk.maven.context;

import com.google.common.collect.ImmutableMap;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;


public class AwsClientProviderBuilder {

    private final Map<Class<? extends SdkClient>, Function<String, ? extends SdkClient>> factories;

    public AwsClientProviderBuilder() {
        this.factories = new HashMap<>();
    }

    public <B extends AwsClientBuilder<B, C>, C extends SdkClient> AwsClientProviderBuilder withClientFactory(Class<C> clientType,
                                                                                                              Function<String, C> factory) {
        factories.put(clientType, factory);
        return this;
    }

    public AwsClientProvider build() {
        Map<Class<? extends SdkClient>, Function<String, ? extends SdkClient>> clientFactories = ImmutableMap.copyOf(factories);

        return new AwsClientProvider() {

            @Override
            public <T extends SdkClient> T getClient(Class<T> clientType, String environment) {
                Function<String, ? extends SdkClient> clientFactory = clientFactories.get(clientType);
                if (clientFactory == null) {
                    throw new IllegalArgumentException("There's no factory registered for " + clientType.getSimpleName() + " client");
                }

                return clientType.cast(clientFactory.apply(environment));
            }

        };
    }
}
