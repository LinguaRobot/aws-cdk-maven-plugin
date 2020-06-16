package io.linguarobot.aws.cdk;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"type", "data", "trace"})
public class FileAssetMetadata extends AssetMetadata {

    public FileAssetMetadata(
            @JsonProperty("type") String type,
            @JsonProperty("data") FileAssetData data,
            @JsonProperty("trace") List<String> trace) {
        super(type, data, trace);
    }

    @Override
    public FileAssetData getData() {
        return (FileAssetData) super.getData();
    }

}
