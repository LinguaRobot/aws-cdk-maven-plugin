package io.linguarobot.aws.cdk.maven.context;

import io.linguarobot.aws.cdk.maven.CdkPluginException;
import software.amazon.awscdk.cxapi.Environment;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.GetHostedZoneRequest;
import software.amazon.awssdk.services.route53.model.GetHostedZoneResponse;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameResponse;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class HostedZoneContextProvider implements ContextProvider {

    public static final String KEY = "hosted-zone";

    private final AwsClientProvider awsClientProvider;

    public HostedZoneContextProvider(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    @Override
    public JsonValue getContextValue(JsonObject properties) {
        Environment environment = ContextProviders.buildEnvironment(properties);
        String domainName = getDomainName(properties);
        boolean isPrivate = properties.getBoolean("privateZone", false);
        String vpcId = properties.getString("vpcId", null);

        try (Route53Client route53Client = awsClientProvider.getClient(Route53Client.class, environment)) {
            ListHostedZonesByNameRequest zoneListRequest = ListHostedZonesByNameRequest.builder()
                    .dnsName(domainName)
                    .build();

            List<HostedZone> matchedHostedZones = Stream.of(route53Client.listHostedZonesByName(zoneListRequest))
                    .filter(ListHostedZonesByNameResponse::hasHostedZones)
                    .flatMap(response -> response.hostedZones().stream())
                    .filter(zone -> zone.name().equals(domainName))
                    .filter(zone -> isPrivate == isPrivate(zone))
                    .filter(zone -> {
                        if (vpcId == null) {
                            return true;
                        }

                        GetHostedZoneRequest zoneRequest = GetHostedZoneRequest.builder()
                                .id(zone.id())
                                .build();

                        return Stream.of(route53Client.getHostedZone(zoneRequest))
                                .filter(GetHostedZoneResponse::hasVpCs)
                                .flatMap(response -> response.vpCs().stream())
                                .anyMatch(vpc -> vpc.vpcId().equals(vpcId));
                    })
                    .collect(Collectors.toList());

            if (matchedHostedZones.size() != 1) {
                throw new CdkPluginException("Found " + matchedHostedZones.size() + " hosted zones matching the " +
                        "criteria, however exactly 1 is required");
            }

            HostedZone hostedZone = matchedHostedZones.get(0);
            return Json.createObjectBuilder()
                    .add("Id", hostedZone.id())
                    .add("Name", hostedZone.name())
                    .build();
        }

    }

    private boolean isPrivate(HostedZone hostedZone) {
        return hostedZone.config() != null &&
                hostedZone.config().privateZone() != null &&
                hostedZone.config().privateZone();
    }

    private String getDomainName(JsonObject properties) {
        String domainName = ContextProviders.getRequiredProperty(properties, "domainName");
        return domainName.endsWith(".") ? domainName : domainName + ".";
    }

}
