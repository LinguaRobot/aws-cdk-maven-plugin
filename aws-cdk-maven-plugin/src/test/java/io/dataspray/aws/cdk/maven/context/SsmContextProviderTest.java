package io.dataspray.aws.cdk.maven.context;

import io.dataspray.aws.cdk.maven.CdkPluginException;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class SsmContextProviderTest {

    @Test
    public void test() {
        SsmClient ssmClient = mock(SsmClient.class);
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(GetParameterResponse.builder().parameter(parameter("name", "value")).build());

        AwsClientProvider awsClientProvider = Mockito.mock(AwsClientProvider.class);
        when(awsClientProvider.getClient(any(), any()))
                .thenReturn(ssmClient);

        SsmContextProvider ssmContextProvider = new SsmContextProvider(awsClientProvider);

        JsonObject properties = Json.createObjectBuilder()
                .add("region", "someRegion")
                .add("account", "someAccount")
                .add("parameterName", "name")
                .build();
        JsonValue contextValue = ssmContextProvider.getContextValue(properties);
        Assert.assertEquals(contextValue, Json.createValue("value"));
    }

    @Test(expectedExceptions = CdkPluginException.class)
    public void testParameterNotFound() {
        SsmClient ssmClient = mock(SsmClient.class);

        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().build());

        AwsClientProvider awsClientProvider = Mockito.mock(AwsClientProvider.class);
        when(awsClientProvider.getClient(any(), any()))
                .thenReturn(ssmClient);

        JsonObject properties = Json.createObjectBuilder()
                .add("region", "someRegion")
                .add("account", "someAccount")
                .add("parameterName", "name")
                .build();
        new SsmContextProvider(awsClientProvider).getContextValue(properties);
    }

    @Test(expectedExceptions = CdkPluginException.class)
    public void testNoParameterNameParameter() {
        JsonObject properties = Json.createObjectBuilder()
                .add("region", "someRegion")
                .add("account", "someAccount")
                .build();
        new SsmContextProvider(mock(AwsClientProvider.class)).getContextValue(properties);
    }

    private Parameter parameter(String name, String value) {
        return Parameter.builder()
                .name(name)
                .value(value)
                .build();
    }

}
