package io.dataspray.aws.cdk.maven.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.dataspray.aws.cdk.maven.CdkPluginException;
import io.dataspray.aws.cdk.maven.MoreCollectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awscdk.cloudassembly.schema.VpcContextQuery;
import software.amazon.awscdk.cxapi.VpcSubnet;
import software.amazon.awscdk.cxapi.VpcSubnetGroup;
import software.amazon.awscdk.cxapi.VpcSubnetGroupType;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeRouteTablesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeRouteTablesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpnGatewaysRequest;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.RouteTable;
import software.amazon.awssdk.services.ec2.model.RouteTableAssociation;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;
import software.amazon.awssdk.services.ec2.model.VpnGateway;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class VpcNetworkContextProviderMapper implements ContextProviderMapper<VpcContextQuery> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JSR353Module())
            .registerModule(new SimpleModule()
                    .addSerializer(VpcSubnetGroupType.class, new VpcSubnetGroupTypeJsonSerializer()));

    private static final String PUBLIC_SUBNET_TYPE = "Public";
    private static final String PRIVATE_SUBNET_TYPE = "Private";
    private static final String ISOLATED_SUBNET_TYPE = "Isolated";
    private static final Set<String> SUBNET_TYPES =
            ImmutableSet.of(PUBLIC_SUBNET_TYPE, PRIVATE_SUBNET_TYPE, ISOLATED_SUBNET_TYPE);

    private final AwsClientProvider awsClientProvider;

    public VpcNetworkContextProviderMapper(AwsClientProvider awsClientProvider) {
        this.awsClientProvider = awsClientProvider;
    }

    @Override
    public JsonValue getContextValue(VpcContextQuery properties) {
        String environment = ContextProviders.buildEnvironment(properties.getAccount(), properties.getRegion());
        try (Ec2Client ec2Client = awsClientProvider.getClient(Ec2Client.class, environment)) {
            Vpc vpc = getVpc(ec2Client, getFilters(properties));
            VpcContext vpcContext = getVpcContext(ec2Client, vpc, properties);
            return OBJECT_MAPPER.convertValue(vpcContext, JsonObject.class);
        }
    }

    @Override
    public Class<VpcContextQuery> getContextType() {
        return VpcContextQuery.class;
    }

    private Vpc getVpc(Ec2Client ec2Client, List<Filter> filters) {
        DescribeVpcsRequest describeRequest = DescribeVpcsRequest.builder()
                .filters(filters)
                .build();

        List<Vpc> vpcs = Optional.of(ec2Client.describeVpcs(describeRequest))
                .map(DescribeVpcsResponse::vpcs)
                .orElse(Collections.emptyList());

        if (vpcs.size() != 1) {
            throw new CdkPluginException("Found " + vpcs.size() + " or more VPCs matching the criteria while exactly " +
                    "1 is required");
        }

        return vpcs.get(0);
    }

    private VpcContext getVpcContext(Ec2Client ec2Client, Vpc vpc, VpcContextQuery vpcContextQuery) {
        VpcContext.Builder contextBuilder = VpcContext.builder()
                .vpcId(vpc.vpcId())
                .vpcCidrBlock(vpc.cidrBlock())
                .vpnGatewayId(getVpnGateway(ec2Client, vpc).map(VpnGateway::vpnGatewayId).orElse(null));
        String groupNameTagName = Strings.isNullOrEmpty(vpcContextQuery.getSubnetGroupNameTag())
                ? "aws-cdk:subnet-name" : vpcContextQuery.getSubnetGroupNameTag();
        Map<String, Map<String, List<Subnet>>> vpcSubnets = getSubnets(ec2Client, vpc).stream()
                .collect(Collectors.groupingBy(
                        Subnet::getType,
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                subnet -> subnet.getTags().getOrDefault(groupNameTagName, subnet.getType()),
                                LinkedHashMap::new,
                                MoreCollectors.sorting(Comparator.comparing(Subnet::getAvailabilityZone), Collectors.toList())
                        )
                ));

        boolean asymmetricSubnetsRequested = Boolean.TRUE.equals(vpcContextQuery.getReturnAsymmetricSubnets());

        if (asymmetricSubnetsRequested) {
            contextBuilder.availabilityZones(ImmutableList.of());
            List<VpcSubnetGroup> subnetGroups = vpcSubnets.entrySet().stream()
                    .flatMap(typeGroupedSubnets -> {
                        VpcSubnetGroupType type = VpcSubnetGroupType.valueOf(typeGroupedSubnets.getKey().toUpperCase());
                        return typeGroupedSubnets.getValue().entrySet().stream()
                                .map(nameGroupedSubnets -> {
                                    List<VpcSubnet> subnets = nameGroupedSubnets.getValue().stream()
                                            .map(this::toVpcSubnet)
                                            .collect(Collectors.toList());

                                    return VpcContextSubnetGroup.builder()
                                            .name(nameGroupedSubnets.getKey())
                                            .type(type)
                                            .subnets(subnets)
                                            .build();
                                });
                    })
                    .collect(Collectors.toList());
            contextBuilder.subnetGroups(subnetGroups);
        } else {
            List<List<String>> availabilityZones = vpcSubnets.values().stream()
                    .flatMap(groupedSubnets -> groupedSubnets.values().stream())
                    .map(subnets -> subnets.stream()
                            .map(Subnet::getAvailabilityZone)
                            .collect(Collectors.toList()))
                    .distinct()
                    .collect(Collectors.toList());
            if (availabilityZones.size() > 1) {
                throw new CdkPluginException("Not all subnetworks in the VPC have the same availability zones");
            }
            contextBuilder.availabilityZones(Iterables.getOnlyElement(availabilityZones, ImmutableList.of()));
            vpcSubnets.forEach((type, subnets) -> {
                List<String> subnetNames = ImmutableList.copyOf(subnets.keySet());
                List<String> subnetIds = new ArrayList<>();
                List<String> routeTableIds = new ArrayList<>();
                subnets.values().stream()
                        .flatMap(List::stream)
                        .forEach(subnet -> {
                            subnetIds.add(subnet.getId());
                            routeTableIds.add(subnet.getRouteTableId());
                        });

                switch (type) {
                    case ISOLATED_SUBNET_TYPE:
                        contextBuilder.isolatedSubnetIds(subnetIds)
                                .isolatedSubnetNames(subnetNames)
                                .isolatedSubnetRouteTableIds(routeTableIds);
                        break;
                    case PRIVATE_SUBNET_TYPE:
                        contextBuilder.privateSubnetIds(subnetIds)
                                .privateSubnetNames(subnetNames)
                                .privateSubnetRouteTableIds(routeTableIds);
                        break;
                    case PUBLIC_SUBNET_TYPE:
                        contextBuilder.publicSubnetIds(subnetIds)
                                .publicSubnetNames(subnetNames)
                                .publicSubnetRouteTableIds(routeTableIds);
                        break;
                }
            });
        }

        return contextBuilder.build();
    }

    private VpcSubnet toVpcSubnet(Subnet subnet) {
        return VpcContextSubnet.builder()
                .subnetId(subnet.getId())
                .availabilityZone(subnet.getAvailabilityZone())
                .cidr(subnet.getCidrBlock())
                .routeTableId(subnet.getRouteTableId())
                .build();
    }

    private List<RouteTable> getRouteTables(Ec2Client ec2Client, Vpc vpc) {
        List<RouteTable> routeTables = new ArrayList<>();

        String token = null;
        do {
            DescribeRouteTablesRequest describeRouteTablesRequest = DescribeRouteTablesRequest.builder()
                    .filters(filter("vpc-id", vpc.vpcId()))
                    .nextToken(token)
                    .build();
            DescribeRouteTablesResponse response = ec2Client.describeRouteTables(describeRouteTablesRequest);
            if (response.routeTables() != null) {
                routeTables.addAll(response.routeTables());
            }
            token = response.nextToken();
        } while (token != null);

        return routeTables;
    }

    private List<Subnet> getSubnets(Ec2Client ec2Client, Vpc vpc) {
        List<RouteTable> routeTables = getRouteTables(ec2Client, vpc);
        RouteTable mainRouteTable = routeTables.stream()
                .filter(routeTable -> getStream(routeTable.associations())
                        .anyMatch(association -> association.main() != null && association.main()))
                .findAny()
                .orElse(null);

        Map<String, RouteTable> subnetRouteTables = routeTables.stream()
                .flatMap(routeTable -> getStream(routeTable.associations())
                        .map(RouteTableAssociation::subnetId)
                        .filter(Objects::nonNull)
                        .map(subnetId -> Pair.of(subnetId, routeTable)))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        String token = null;

        List<Subnet> subnets = new ArrayList<>();
        do {
            DescribeSubnetsRequest request = DescribeSubnetsRequest.builder()
                    .filters(filter("vpc-id", vpc.vpcId()))
                    .nextToken(token)
                    .build();
            DescribeSubnetsResponse response = ec2Client.describeSubnets(request);
            if (response.subnets() != null) {
                response.subnets().forEach(subnet -> {
                    RouteTable routeTable = subnetRouteTables.getOrDefault(subnet.subnetId(), mainRouteTable);
                    if (routeTable == null) {
                        throw new CdkPluginException("The subnet '" + subnet.subnetId() + "' doesn't have an associated " +
                                "route table");
                    }

                    Subnet result = new Subnet();
                    result.setId(subnet.subnetId());
                    result.setAvailabilityZone(subnet.availabilityZone());
                    result.setCidrBlock(subnet.cidrBlock());

                    Map<String, String> tags = getStream(subnet.tags())
                            .collect(Collectors.toMap(Tag::key, Tag::value, (a, b) -> a));

                    String type = Optional.ofNullable(tags.get("aws-cdk:subnet-type"))
                            .orElseGet(() -> {
                                if (subnet.mapPublicIpOnLaunch() != null && subnet.mapPublicIpOnLaunch()) {
                                    return PUBLIC_SUBNET_TYPE;
                                }

                                return hasInternetGateway(routeTable) ? PUBLIC_SUBNET_TYPE : PRIVATE_SUBNET_TYPE;
                            });

                    if (!SUBNET_TYPES.contains(type)) {
                        throw new CdkPluginException("The subnet '" + subnet.subnetId() + "' has invalid type '" +
                                type + "'. The type must be one of the following values: " + String.join(", ", SUBNET_TYPES));
                    }

                    result.setType(type);
                    result.setTags(tags);
                    result.setRouteTableId(routeTable.routeTableId());
                    subnets.add(result);
                });
            }
            token = response.nextToken();
        } while (token != null);

        return subnets;
    }

    private boolean hasInternetGateway(RouteTable routeTable) {
        return getStream(routeTable.routes())
                .anyMatch(route -> route.gatewayId() != null && route.gatewayId().startsWith("igw-"));
    }

    private Optional<VpnGateway> getVpnGateway(Ec2Client ec2Client, Vpc vpc) {
        DescribeVpnGatewaysRequest request = DescribeVpnGatewaysRequest.builder()
                .filters(ImmutableList.of(
                        filter("attachment.vpc-id", vpc.vpcId()),
                        filter("attachment.state", "attached"),
                        filter("state", "available")
                ))
                .build();

        return Optional.of(ec2Client.describeVpnGateways(request))
                .filter(response -> response.vpnGateways() != null && response.vpnGateways().size() == 1)
                .map(response -> response.vpnGateways().get(0));
    }

    private List<Filter> getFilters(VpcContextQuery vpcContextQuery) {
        return vpcContextQuery.getFilter().entrySet().stream()
                .map(filter -> filter(filter.getKey(), filter.getValue()))
                .collect(Collectors.toList());
    }

    private Filter filter(String name, String... values) {
        return Filter.builder()
                .name(name)
                .values(values)
                .build();
    }

    private <T> Stream<T> getStream(List<T> values) {
        return values != null ? values.stream() : Stream.empty();
    }

    private static class Subnet {

        private String id;
        private String type;
        private String availabilityZone;
        private String cidrBlock;
        private Map<String, String> tags;
        private String routeTableId;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getAvailabilityZone() {
            return availabilityZone;
        }

        public void setAvailabilityZone(String availabilityZone) {
            this.availabilityZone = availabilityZone;
        }

        public String getCidrBlock() {
            return cidrBlock;
        }

        public void setCidrBlock(String cidrBlock) {
            this.cidrBlock = cidrBlock;
        }

        public Map<String, String> getTags() {
            return tags;
        }

        public void setTags(Map<String, String> tags) {
            this.tags = tags;
        }

        public String getRouteTableId() {
            return routeTableId;
        }

        public void setRouteTableId(String routeTableId) {
            this.routeTableId = routeTableId;
        }
    }

    private static class VpcSubnetGroupTypeJsonSerializer extends JsonSerializer<VpcSubnetGroupType> {

        @Override
        public void serialize(VpcSubnetGroupType value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
            generator.writeString(StringUtils.capitalize(value.toString().toLowerCase()));
        }

    }
}
