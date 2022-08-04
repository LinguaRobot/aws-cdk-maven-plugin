package io.dataspray.aws.cdk.maven.context;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.aws.cdk.maven.CdkPluginException;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.GetHostedZoneRequest;
import software.amazon.awssdk.services.route53.model.GetHostedZoneResponse;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.HostedZoneConfig;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameResponse;
import software.amazon.awssdk.services.route53.model.NoSuchHostedZoneException;
import software.amazon.awssdk.services.route53.model.VPC;

import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class HostedZoneContextProviderTest {

    @Test(expectedExceptions = CdkPluginException.class)
    public void testNoDomainNameParameter() {
        JsonObject properties = Json.createObjectBuilder()
                .add("region", "someRegion")
                .add("account", "someAccountId")
                .build();
        new HostedZoneContextProvider(Mockito.mock(AwsClientProvider.class)).getContextValue(properties);
    }

    @DataProvider
    public Object[][] testDataProvider() {
        return new Object[][]{
//                // Simply return the hosted zone if no filters are specified
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom", "example.com.")),
                        Json.createObjectBuilder()
                                .add("Id", "examplecom")
                                .add("Name", "example.com.")
                                .build(),
                        null
                },
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom", "example.com.", hostedZoneConfig(false))),
                        Json.createObjectBuilder()
                                .add("Id", "examplecom")
                                .add("Name", "example.com.")
                                .build(),
                        null
                },
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom", "example.com.", hostedZoneConfig(null))),
                        Json.createObjectBuilder()
                                .add("Id", "examplecom")
                                .add("Name", "example.com.")
                                .build(),
                        null
                },
                // Throw an exception if there're no matching hosted zones
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .build(),
                        ClientMockData.get(),
                        null,
                        CdkPluginException.class
                },
                // Filter the hosted zones whose name doesn't match to the one in properties
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("exampleco", "example.co.")),
                        null,
                        CdkPluginException.class
                },
                // Throw an exception if there's more than one matching hosted zone
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom-1", "example.com."))
                                .withHostedZone(hostedZone("examplecom-2", "example.com.")),
                        null,
                        CdkPluginException.class
                },
                // Private hosted zones are filtered by default
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom", "example.com.", hostedZoneConfig(true))),
                        null,
                        CdkPluginException.class
                },
                // Test privateZone filter: privateZone is true
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .add("privateZone", true)
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom", "example.com.", hostedZoneConfig(true))),
                        Json.createObjectBuilder()
                                .add("Id", "examplecom")
                                .add("Name", "example.com.")
                                .build(),
                        null
                },
                // Test privateZone filter: privateZone is false
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .add("privateZone", false)
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom", "example.com.", hostedZoneConfig(true))),
                        null,
                        CdkPluginException.class
                },
                // Test privateZone filter: privateZone is false
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .add("privateZone", false)
                                .add("vpcId", "vpc-1")
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom", "example.com."), ImmutableList.of(vpc("vpc-1"))),
                        Json.createObjectBuilder()
                                .add("Id", "examplecom")
                                .add("Name", "example.com.")
                                .build(),
                        null
                },
                // Test privateZone filter: privateZone is false
                {
                        Json.createObjectBuilder()
                                .add("region", "someRegion")
                                .add("account", "someAccount")
                                .add("domainName", "example.com")
                                .add("privateZone", false)
                                .add("vpcId", "vpc-1")
                                .build(),
                        ClientMockData.get()
                                .withHostedZone(hostedZone("examplecom", "example.com."), ImmutableList.of(vpc("vpc-2"))),
                        null,
                        CdkPluginException.class
                },
        };
    }

    @Test(dataProvider = "testDataProvider")
    public void test(JsonObject properties, ClientMockData mockData, JsonValue expectedValue, Class<?> exceptionClass) {
        Route53Client route53Client = mock(Route53Client.class);

        when(route53Client.listHostedZonesByName(any(ListHostedZonesByNameRequest.class)))
                .thenReturn(ListHostedZonesByNameResponse.builder().hostedZones(mockData.getHostedZones()).build());

        when(route53Client.getHostedZone(any(GetHostedZoneRequest.class)))
                .thenAnswer(invocation -> {
                    GetHostedZoneRequest request = invocation.getArgumentAt(0, GetHostedZoneRequest.class);
                    String id = request.id();
                    HostedZone hostedZone = mockData.getHostedZone(id);
                    if (hostedZone == null) {
                        throw NoSuchHostedZoneException.builder().build();
                    }
                    return GetHostedZoneResponse.builder()
                            .hostedZone(hostedZone)
                            .vpCs(mockData.getHostedZoneVpcs(id))
                            .build();
                });

        AwsClientProvider awsClientProvider = mock(AwsClientProvider.class);
        when(awsClientProvider.getClient(any(), any()))
                .thenReturn(route53Client);

        HostedZoneContextProvider hostedZoneContextProvider = new HostedZoneContextProvider(awsClientProvider);
        if (exceptionClass != null) {
            try {
                hostedZoneContextProvider.getContextValue(properties);
                Assert.fail("The provider is expected to throw the " + exceptionClass.getSimpleName() + " exception");
            } catch (Exception e) {
                Assert.assertEquals(e.getClass(), exceptionClass);
            }
        } else {
            JsonValue contextValue = hostedZoneContextProvider.getContextValue(properties);
            Assert.assertEquals(expectedValue, contextValue);
        }

    }

    private HostedZone hostedZone(String id, String name) {
        return hostedZone(id, name, null);
    }

    private HostedZone hostedZone(String id, String name, @Nullable HostedZoneConfig config) {
        return HostedZone.builder()
                .id(id)
                .name(name)
                .config(config)
                .build();
    }

    private HostedZoneConfig hostedZoneConfig(Boolean isPrivate) {
        return HostedZoneConfig.builder()
                .privateZone(isPrivate)
                .build();
    }

    private VPC vpc(String id) {
        return VPC.builder()
                .vpcId(id)
                .build();
    }

    private static class ClientMockData {

        private static final ClientMockData EMPTY = new ClientMockData(ImmutableMap.of());

        private final Map<String, Pair<HostedZone, List<VPC>>> hostedZones;

        private ClientMockData(Map<String, Pair<HostedZone, List<VPC>>> hostedZones) {
            this.hostedZones = hostedZones;
        }

        public List<HostedZone> getHostedZones() {
            return hostedZones.values().stream()
                    .map(Pair::getKey)
                    .collect(Collectors.toList());
        }

        public HostedZone getHostedZone(String id) {
            Pair<HostedZone, List<VPC>> data = hostedZones.get(id);
            return data != null ? data.getKey() : null;
        }

        public List<VPC> getHostedZoneVpcs(String id) {
            Pair<HostedZone, List<VPC>> data = hostedZones.get(id);
            return data != null ? data.getValue() : ImmutableList.of();
        }

        public ClientMockData withHostedZone(HostedZone hostedZone) {
            return withHostedZone(hostedZone, ImmutableList.of());
        }

        public ClientMockData withHostedZone(HostedZone hostedZone, List<VPC> vpcs) {
            Map<String, Pair<HostedZone, List<VPC>>> hostedZones = ImmutableMap.<String, Pair<HostedZone, List<VPC>>>builder()
                    .putAll(this.hostedZones)
                    .put(hostedZone.id(), Pair.of(hostedZone, vpcs))
                    .build();
            return new ClientMockData(hostedZones);
        }

        @Override
        public String toString() {
            return "MockData{" +
                    "hostedZones=" + hostedZones +
                    '}';
        }

        public static ClientMockData get() {
            return EMPTY;
        }

    }


}
