package io.linguarobot.aws.cdk.maven.context;

import lombok.Builder;
import lombok.Value;
import software.amazon.awscdk.cxapi.VpcSubnet;

@Value
@Builder(builderClassName = "Builder")
class VpcContextSubnet implements VpcSubnet {

    String subnetId;
    String availabilityZone;
    String cidr;
    String routeTableId;

}
