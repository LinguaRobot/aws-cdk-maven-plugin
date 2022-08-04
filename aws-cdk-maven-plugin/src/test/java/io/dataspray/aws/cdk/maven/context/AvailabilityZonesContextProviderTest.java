package io.dataspray.aws.cdk.maven.context;

import com.google.common.collect.ImmutableList;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import software.amazon.awssdk.services.ec2.model.AvailabilityZoneState;
import software.amazon.awssdk.services.ec2.model.DescribeAvailabilityZonesResponse;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.services.ec2.model.AvailabilityZoneState.*;

public class AvailabilityZonesContextProviderTest {

    @DataProvider
    public Object[][] testDataProvider() {
        return new Object[][]{
                {
                        ImmutableList.of(),
                        JsonValue.EMPTY_JSON_ARRAY
                },
                {
                        ImmutableList.of(
                                availabilityZone("us-west-2a", UNAVAILABLE),
                                availabilityZone("us-west-2b", UNAVAILABLE)
                        ),
                        JsonValue.EMPTY_JSON_ARRAY
                },
                {
                        ImmutableList.of(
                                availabilityZone("us-west-2a", AVAILABLE),
                                availabilityZone("us-west-2b", AVAILABLE),
                                availabilityZone("us-west-2c", UNAVAILABLE),
                                availabilityZone("us-west-2d", AVAILABLE),
                                availabilityZone("us-west-2e", IMPAIRED),
                                availabilityZone("us-west-2f", INFORMATION)
                        ),
                        Json.createArrayBuilder()
                                .add("us-west-2a")
                                .add("us-west-2b")
                                .add("us-west-2d")
                                .build()
                }
        };
    }

    @Test(dataProvider = "testDataProvider")
    public void test(List<AvailabilityZone> availabilityZones, JsonValue expectedValue) {
        Ec2Client ec2Client = mock(Ec2Client.class);
        DescribeAvailabilityZonesResponse response = DescribeAvailabilityZonesResponse.builder()
                .availabilityZones(availabilityZones)
                .build();
        when(ec2Client.describeAvailabilityZones())
                .thenReturn(response);

        AwsClientProvider clientProvider = mock(AwsClientProvider.class);
        when(clientProvider.getClient(any(), any()))
                .thenReturn(ec2Client);

        AvailabilityZonesContextProvider contextProvider = new AvailabilityZonesContextProvider(clientProvider);
        JsonObject properties = Json.createObjectBuilder()
                .add("region", "someRegion")
                .add("account", "someAccountId")
                .build();

        JsonValue contextValue = contextProvider.getContextValue(properties);
        Assert.assertEquals(contextValue, expectedValue);
    }

    private AvailabilityZone availabilityZone(String zoneName, AvailabilityZoneState state) {
        return AvailabilityZone.builder()
                .zoneName(zoneName)
                .state(state)
                .build();
    }

}
