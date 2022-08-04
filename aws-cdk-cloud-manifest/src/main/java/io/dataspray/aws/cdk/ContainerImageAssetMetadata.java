package io.dataspray.aws.cdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"type", "data", "trace"})
public class ContainerImageAssetMetadata extends AssetMetadata {

    public ContainerImageAssetMetadata(
            @JsonProperty("type") String type,
            @JsonProperty("data") ContainerAssetData data,
            @JsonProperty("trace") List<String> trace) {
        super(type, data, trace);
    }

    public ContainerAssetData getData() {
        return (ContainerAssetData) super.getData();
    }

}
