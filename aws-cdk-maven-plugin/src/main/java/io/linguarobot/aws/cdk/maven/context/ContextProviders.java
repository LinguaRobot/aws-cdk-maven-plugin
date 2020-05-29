package io.linguarobot.aws.cdk.maven.context;

import io.linguarobot.aws.cdk.maven.CdkPluginException;
import software.amazon.awscdk.cxapi.Environment;

import javax.json.JsonObject;


public final class ContextProviders {

    private ContextProviders() {
    }

    public static Environment buildEnvironment(JsonObject properties) {
        String region = getRequiredProperty(properties, "region");
        String account = getRequiredProperty(properties, "account");
        String name = "aws://" + account + "/" + region;

        return Environment.builder()
                .region(region)
                .account(account)
                .name(name)
                .build();
    }

    public static String getRequiredProperty(JsonObject properties, String propertyName) {
        if (!properties.containsKey(propertyName) || properties.isNull(propertyName)) {
            throw new CdkPluginException("The value for the property '" + propertyName + "' required by a context " +
                    "provider is missing");
        }

        return properties.getString(propertyName);
    }

}
