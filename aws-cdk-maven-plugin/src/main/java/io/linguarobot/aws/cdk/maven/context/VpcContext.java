package io.linguarobot.aws.cdk.maven.context;

import lombok.Builder;
import lombok.Value;
import software.amazon.awscdk.cxapi.VpcContextResponse;
import software.amazon.awscdk.cxapi.VpcSubnetGroup;

import java.util.List;

@Value
@Builder(builderClassName = "Builder")
class VpcContext implements VpcContextResponse {

    String vpcId;
    String vpcCidrBlock;
    String vpnGatewayId;
    List<String> availabilityZones;
    List<String> isolatedSubnetIds;
    List<String> isolatedSubnetNames;
    List<String> isolatedSubnetRouteTableIds;
    List<String> privateSubnetIds;
    List<String> privateSubnetNames;
    List<String> privateSubnetRouteTableIds;
    List<String> publicSubnetIds;
    List<String> publicSubnetNames;
    List<String> publicSubnetRouteTableIds;
    List<VpcSubnetGroup> subnetGroups;

}
