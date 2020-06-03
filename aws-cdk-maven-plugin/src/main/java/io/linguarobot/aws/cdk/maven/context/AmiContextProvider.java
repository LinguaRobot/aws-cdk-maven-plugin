package io.linguarobot.aws.cdk.maven.context;

import com.google.common.collect.ImmutableList;
import io.linguarobot.aws.cdk.maven.CdkPluginException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Image;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AmiContextProvider implements ContextProvider {

    public static final String KEY = "ami";

    private final AwsClientProvider awsClientProvider;

    public AmiContextProvider(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    @Override
    public JsonValue getContextValue(JsonObject properties) {
        String environment = ContextProviders.buildEnvironment(properties);
        try (Ec2Client ec2Client = awsClientProvider.getClient(Ec2Client.class, environment)) {
            DescribeImagesRequest describeImagesRequest = DescribeImagesRequest.builder()
                    .owners(getOwners(properties))
                    .filters(getFilters(properties))
                    .build();

            return Stream.of(ec2Client.describeImages(describeImagesRequest))
                    .filter(DescribeImagesResponse::hasImages)
                    .flatMap(response -> response.images().stream())
                    .filter(image -> image.imageId() != null)
                    .max(Comparator.comparing(image -> getCreationDate(image).orElse(null), Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(Image::imageId)
                    .map(Json::createValue)
                    .orElseThrow(() -> new CdkPluginException("Found 0 AMIs matching the criteria, however at lest 1 is required"));
        }
    }

    private Optional<ZonedDateTime> getCreationDate(Image image) {
        return Optional.ofNullable(image.creationDate())
                .map(ZonedDateTime::parse);
    }

    private List<String> getOwners(JsonObject properties) {
        JsonArray owners = properties.getJsonArray("owners");
        return owners != null ? owners.getValuesAs(value -> ((JsonString) value).getString()) : Collections.emptyList();
    }

    private List<Filter> getFilters(JsonObject properties) {
        if (!properties.containsKey("filters") || properties.isNull("filters")) {
            return ImmutableList.of();
        }

        return properties.getJsonObject("filters").entrySet().stream()
                .map(filter -> Filter.builder()
                        .name(filter.getKey())
                        .values(((JsonString) filter.getValue()).getString())
                        .build())
                .collect(Collectors.toList());
    }

}
