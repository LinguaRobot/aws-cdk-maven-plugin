package io.dataspray.aws.cdk.context;

import software.amazon.awssdk.core.SdkClient;

public interface AwsClientProvider {

    <T extends SdkClient> T getClient(Class<T> clientType, String environment);

}
