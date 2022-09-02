package io.dataspray.aws.cdk.context;

import lombok.Builder;
import lombok.Value;
import software.amazon.awscdk.cxapi.VpcSubnet;
import software.amazon.awscdk.cxapi.VpcSubnetGroup;
import software.amazon.awscdk.cxapi.VpcSubnetGroupType;

import java.util.List;

@Value
@Builder(builderClassName = "Builder")
class VpcContextSubnetGroup implements VpcSubnetGroup {

    String name;
    VpcSubnetGroupType type;
    List<VpcSubnet> subnets;

}
