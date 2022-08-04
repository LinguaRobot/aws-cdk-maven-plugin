package io.dataspray.aws.cdk.maven.context;

import io.dataspray.aws.cdk.maven.CdkPluginException;

import javax.json.JsonObject;


public final class ContextProviders {

    private ContextProviders() {
    }

    public static String buildEnvironment(JsonObject properties) {
        String region = getRequiredProperty(properties, "region");
        String account = getRequiredProperty(properties, "account");
        return "aws://" + account + "/" + region;
    }

    public static String getRequiredProperty(JsonObject properties, String propertyName) {
        if (!properties.containsKey(propertyName) || properties.isNull(propertyName)) {
            throw new CdkPluginException("The value for the property '" + propertyName + "' required by a context " +
                    "provider is missing");
        }

        return properties.getString(propertyName);
    }

}
