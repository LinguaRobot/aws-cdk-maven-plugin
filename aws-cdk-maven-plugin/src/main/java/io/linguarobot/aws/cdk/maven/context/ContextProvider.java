package io.linguarobot.aws.cdk.maven.context;

import javax.json.JsonObject;
import javax.json.JsonValue;

/**
 * Contextual information provider.
 */
public interface ContextProvider {

    /**
     * Provides context value based on the given set of properties.
     *
     * The implementation is expected to throw an exception if an error occurs while providing the value or if some of
     * the properties are missing or have invalid values.
     *
     * @param properties the properties based on which the contextual information will be provided
     * @return the context value represented as a JSON
     */
    JsonValue getContextValue(JsonObject properties);

}
