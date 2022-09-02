package io.dataspray.aws.cdk.context;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.aws.cdk.CdkException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import software.amazon.awscdk.cloudassembly.schema.AmiContextQuery;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Image;

import javax.json.Json;
import javax.json.JsonValue;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class AmiContextProviderMapperTest {

    @DataProvider
    public Object[][] requestTestDataProvider() {
        return new Object[][]{
                {
                        AmiContextQuery.builder()
                                .region(Region.US_EAST_1.id())
                                .account("27")
                                .filters(ImmutableMap.of())
                                .build(),
                        ImmutableList.of(),
                        ImmutableList.of()
                },
                {
                        AmiContextQuery.builder()
                                .region(Region.US_EAST_1.id())
                                .account("27")
                                .owners(ImmutableList.of("someOwner"))
                                .filters(ImmutableMap.of("filterName", ImmutableList.of("filterValue")))
                                .build(),
                        ImmutableList.of("someOwner"),
                        ImmutableList.of(
                                Filter.builder().name("filterName").values("filterValue").build()
                        )
                },
        };
    }

    @Test(dataProvider = "requestTestDataProvider")
    public void testRequest(AmiContextQuery properties, List<String> expectedOwners, List<Filter> expectedFilters) {
        Ec2Client ec2Client = Mockito.mock(Ec2Client.class);

        DescribeImagesResponse response = DescribeImagesResponse.builder()
                .images(Image.builder()
                        .imageId("imageId")
                        .build())
                .build();
        ArgumentCaptor<DescribeImagesRequest> requestCaptor = ArgumentCaptor.forClass(DescribeImagesRequest.class);
        when(ec2Client.describeImages(requestCaptor.capture()))
                .thenReturn(response);

        new AmiContextProviderMapper(mockAwsClientProvider(ec2Client)).getContextValue(properties);

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

        AmiContextProviderMapper amiContextProvider = new AmiContextProviderMapper(clientProvider);
        AmiContextQuery properties = AmiContextQuery.builder()
                .region(Region.US_EAST_1.id())
                .account("27")
                .filters(ImmutableMap.of())
                .build();

        JsonValue contextValue = amiContextProvider.getContextValue(properties);
        Assert.assertEquals(contextValue, expectedValue);
    }

    @Test(expectedExceptions = CdkException.class)
    public void testNoMatchingAmis() {
        Ec2Client ec2Client = Mockito.mock(Ec2Client.class);
        when(ec2Client.describeImages(any(DescribeImagesRequest.class)))
                .thenReturn(DescribeImagesResponse.builder().build());
        AwsClientProvider clientProvider = mockAwsClientProvider(ec2Client);
        AmiContextProviderMapper amiContextProvider = new AmiContextProviderMapper(clientProvider);
        AmiContextQuery properties = AmiContextQuery.builder()
                .region(Region.US_EAST_1.id())
                .account("27")
                .filters(ImmutableMap.of())
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
