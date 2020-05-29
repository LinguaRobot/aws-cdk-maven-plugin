package io.linguarobot.aws.cdk.maven.context;

import software.amazon.awscdk.cxapi.Environment;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import software.amazon.awssdk.services.ec2.model.AvailabilityZoneState;
import software.amazon.awssdk.services.ec2.model.DescribeAvailabilityZonesResponse;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class AvailabilityZonesContextProvider implements ContextProvider {

    public static final String KEY = "availability-zones";

    private final AwsClientProvider awsClientProvider;

    public AvailabilityZonesContextProvider(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    @Override
    public JsonValue getContextValue(JsonObject properties) {
        Environment environment = ContextProviders.buildEnvironment(properties);
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

    private static Collector<JsonValue, JsonArrayBuilder, JsonArray> toJsonArray() {
        return Collector.of(
                Json::createArrayBuilder,
                JsonArrayBuilder::add,
                JsonArrayBuilder::add,
                JsonArrayBuilder::build
        );
    }

}
