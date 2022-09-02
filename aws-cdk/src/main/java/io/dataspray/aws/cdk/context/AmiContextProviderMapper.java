package io.dataspray.aws.cdk.context;

import io.dataspray.aws.cdk.CdkException;
import software.amazon.awscdk.cloudassembly.schema.AmiContextQuery;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Image;

import javax.json.Json;
import javax.json.JsonValue;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AmiContextProviderMapper implements ContextProviderMapper<AmiContextQuery> {

    private final AwsClientProvider awsClientProvider;

    public AmiContextProviderMapper(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    @Override
    public JsonValue getContextValue(AmiContextQuery properties) {
        String environment = ContextProviders.buildEnvironment(properties.getAccount(), properties.getRegion());
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
                    .orElseThrow(() -> new CdkException("Found 0 AMIs matching the criteria, however at lest 1 is required"));
        }
    }

    @Override
    public Class<AmiContextQuery> getContextType() {
        return AmiContextQuery.class;
    }

    private Optional<ZonedDateTime> getCreationDate(Image image) {
        return Optional.ofNullable(image.creationDate())
                .map(ZonedDateTime::parse);
    }

    private List<String> getOwners(AmiContextQuery amiContextQuery) {
        List<String> owners = amiContextQuery.getOwners();
        return owners != null ? owners : Collections.emptyList();
    }

    private List<Filter> getFilters(AmiContextQuery amiContextQuery) {
        return amiContextQuery.getFilters().entrySet().stream()
                .map(filter -> Filter.builder()
                        .name(filter.getKey())
                        .values(filter.getValue())
                        .build())
                .collect(Collectors.toList());
    }

}
