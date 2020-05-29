package io.linguarobot.aws.cdk.maven.context;

import io.linguarobot.aws.cdk.maven.CdkPluginException;
import software.amazon.awscdk.cxapi.Environment;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.Optional;


public class SsmContextProvider implements ContextProvider {

    public static final String KEY = "ssm";

    private final AwsClientProvider awsClientProvider;

    public SsmContextProvider(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    @Override
    public JsonValue getContextValue(JsonObject properties) {
        Environment environment = ContextProviders.buildEnvironment(properties);
        String parameterName = ContextProviders.getRequiredProperty(properties, "parameterName");

        try (SsmClient ssmClient = awsClientProvider.getClient(SsmClient.class, environment)) {
            String value;
            try {
                GetParameterResponse response = ssmClient.getParameter(parameterRequest(parameterName));
                value = Optional.of(response)
                        .map(GetParameterResponse::parameter)
                        .map(Parameter::value)
                        .orElse(null);
            } catch (ParameterNotFoundException e) {
                value = null;
            }

            if (value == null) {
                throw new CdkPluginException("The SSM parameter '" + parameterName + "' is not available for the following " +
                        "environment " + environment.getName());
            }

            return Json.createValue(value);
        }
    }

    private GetParameterRequest parameterRequest(String parameterName) {
        return GetParameterRequest.builder()
                .name(parameterName)
                .build();
    }


}
