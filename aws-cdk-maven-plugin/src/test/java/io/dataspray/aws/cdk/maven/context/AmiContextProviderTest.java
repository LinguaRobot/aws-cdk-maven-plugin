package io.dataspray.aws.cdk.maven.context;

import com.google.common.collect.ImmutableList;
import io.dataspray.aws.cdk.maven.CdkPluginException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Image;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class AmiContextProviderTest {

    @DataProvider
    public Object[][] requestTestDataProvider() {
        return new Object[][]{
                {
                        Json.createObjectBuilder()
                                .add("region", Region.US_EAST_1.id())
                                .add("account", "27")
                                .build(),
                        ImmutableList.of(),
                        ImmutableList.of()
                },
                {
                        Json.createObjectBuilder()
                                .add("region", Region.US_EAST_1.id())
                                .add("account", "27")
                                .add("owners", Json.createArrayBuilder().add("someOwner").build())
                                .add("filters", Json.createObjectBuilder().add("filterName", "filterValue").build())
                                .build(),
                        ImmutableList.of("someOwner"),
                        ImmutableList.of(
                                Filter.builder().name("filterName").values("filterValue").build()
                        )
                },
        };
    }

    @Test(dataProvider = "requestTestDataProvider")
    public void testRequest(JsonObject properties, List<String> expectedOwners, List<Filter> expectedFilters) {
        Ec2Client ec2Client = Mockito.mock(Ec2Client.class);

        DescribeImagesResponse response = DescribeImagesResponse.builder()
                .images(Image.builder()
                        .imageId("imageId")
                        .build())
                .build();
        ArgumentCaptor<DescribeImagesRequest> requestCaptor = ArgumentCaptor.forClass(DescribeImagesRequest.class);
        when(ec2Client.describeImages(requestCaptor.capture()))
                .thenReturn(response);

        new AmiContextProvider(mockAwsClientProvider(ec2Client)).getContextValue(properties);

        DescribeImagesRequest request = requestCaptor.getValue();
        Assert.assertEquals(request.owners(), expectedOwners);
        Assert.assertEquals(request.filters(), expectedFilters);
    }


    @DataProvider
    public Object[][] returnedImageIdTestDataProvider() {
        return new Object[][]{
                {
                        ImmutableList.<Image>builder()
                                .add(Image.builder()
                                        .imageId("someImageId")
                                        .build())
                                .build(),
                        Json.createValue("someImageId")
                },
                {
                        ImmutableList.<Image>builder()
                                .add(Image.builder()
                                        .imageId("1994-image-id")
                                        .creationDate("1994-09-27T02:22:22.000Z")
                                        .build())
                                .add(Image.builder()
                                        .imageId("2020-image-id")
                                        .creationDate("2020-05-14T14:51:00.000Z")
                                        .build())
                                .build(),
                        Json.createValue("2020-image-id")
                },
                {
                        ImmutableList.<Image>builder()
                                .add(Image.builder()
                                        .imageId("2020-image-id")
                                        .creationDate("2020-05-14T14:51:00.000Z")
                                        .build())
                                .add(Image.builder()
                                        .imageId("unknownCreationDateImage")
                                        .build())
                                .build(),
                        Json.createValue("2020-image-id")
                }
        };
    }

    @Test(dataProvider = "returnedImageIdTestDataProvider")
    public void testReturnedImageId(List<Image> images, JsonValue expectedValue) {
        Ec2Client ec2Client = Mockito.mock(Ec2Client.class);
        when(ec2Client.describeImages(any(DescribeImagesRequest.class)))
                .thenReturn(DescribeImagesResponse.builder().images(images).build());
        AwsClientProvider clientProvider = mockAwsClientProvider(ec2Client);

        AmiContextProvider amiContextProvider = new AmiContextProvider(clientProvider);
        JsonObject properties = Json.createObjectBuilder()
                .add("region", Region.US_EAST_1.id())
                .add("account", "27")
                .build();

        JsonValue contextValue = amiContextProvider.getContextValue(properties);
        Assert.assertEquals(contextValue, expectedValue);
    }

    @Test(expectedExceptions = CdkPluginException.class)
    public void testNoMatchingAmis() {
        Ec2Client ec2Client = Mockito.mock(Ec2Client.class);
        when(ec2Client.describeImages(any(DescribeImagesRequest.class)))
                .thenReturn(DescribeImagesResponse.builder().build());
        AwsClientProvider clientProvider = mockAwsClientProvider(ec2Client);
        AmiContextProvider amiContextProvider = new AmiContextProvider(clientProvider);
        JsonObject properties = Json.createObjectBuilder()
                .add("region", Region.US_EAST_1.id())
                .add("account", "27")
                .build();
        amiContextProvider.getContextValue(properties);
    }

    private AwsClientProvider mockAwsClientProvider(Ec2Client ec2Client) {
        AwsClientProvider clientProvider = Mockito.mock(AwsClientProvider.class);
        when(clientProvider.getClient(any(), any()))
                .thenReturn(ec2Client);
        return clientProvider;
    }

}
