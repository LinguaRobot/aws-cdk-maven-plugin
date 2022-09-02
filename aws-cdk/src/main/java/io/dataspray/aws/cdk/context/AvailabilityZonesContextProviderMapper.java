package io.dataspray.aws.cdk.context;

import software.amazon.awscdk.cloudassembly.schema.AvailabilityZonesContextQuery;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import software.amazon.awssdk.services.ec2.model.AvailabilityZoneState;
import software.amazon.awssdk.services.ec2.model.DescribeAvailabilityZonesResponse;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonValue;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class AvailabilityZonesContextProviderMapper implements ContextProviderMapper<AvailabilityZonesContextQuery> {

    private final AwsClientProvider awsClientProvider;

    public AvailabilityZonesContextProviderMapper(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    @Override
    public JsonValue getContextValue(AvailabilityZonesContextQuery properties) {
        String environment = ContextProviders.buildEnvironment(properties.getAccount(), properties.getRegion());
        try (Ec2Client ec2Client = awsClientProvider.getClient(Ec2Client.class, environment)) {
            return Stream.of(ec2Client.describeAvailabilityZones())
                    .filter(DescribeAvailabilityZonesResponse::hasAvailabilityZones)
                    .flatMap(availabilityZone -> availabilityZone.availabilityZones().stream())
                    .filter(availabilityZone -> availabilityZone.state() == AvailabilityZoneState.AVAILABLE)
                    .map(AvailabilityZone::zoneName)
                    .map(Json::createValue)
                    .collect(toJsonArray());
        }
    }

    @Override
    public Class<AvailabilityZonesContextQuery> getContextType() {
        return AvailabilityZonesContextQuery.class;
    }

    private static Collector<JsonValue, JsonArrayBuilder, JsonArray> toJsonArray() {
        return Collector.of(
                Json::createArrayBuilder,
                JsonArrayBuilder::add,
                JsonArrayBuilder::add,
                JsonArrayBuilder::build
        );
    }

}
