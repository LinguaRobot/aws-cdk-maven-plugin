package io.dataspray.aws.cdk.context;

import io.dataspray.aws.cdk.CdkException;
import software.amazon.awscdk.cloudassembly.schema.SSMParameterContextQuery;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import javax.json.Json;
import javax.json.JsonValue;
import java.util.Optional;


public class SsmContextProviderMapper implements ContextProviderMapper<SSMParameterContextQuery> {

    private final AwsClientProvider awsClientProvider;

    public SsmContextProviderMapper(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    @Override
    public JsonValue getContextValue(SSMParameterContextQuery properties) {
        String environment = ContextProviders.buildEnvironment(properties.getAccount(), properties.getRegion());
        String parameterName = properties.getParameterName();

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
                throw new CdkException("The SSM parameter '" + parameterName + "' is not available for the " +
                        "following environment: " + environment);
            }

            return Json.createValue(value);
        }
    }

    @Override
    public Class<SSMParameterContextQuery> getContextType() {
        return SSMParameterContextQuery.class;
    }

    private GetParameterRequest parameterRequest(String parameterName) {
        return GetParameterRequest.builder()
                .name(parameterName)
                .build();
    }


}
