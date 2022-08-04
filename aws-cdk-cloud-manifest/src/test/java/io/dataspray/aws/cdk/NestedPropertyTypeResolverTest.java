package io.dataspray.aws.cdk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class NestedPropertyTypeResolverTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @DataProvider
    public Object[][] dataProvider() {
        return new Object[][]{
                {
                        "data-object-file.json",
                        new AssetMetadata(
                                "aws:cdk:asset",
                                new AssetData("file.jar", "file", "file-hash"),
                                Collections.singletonList("trace")
                        )
                },
                {
                        "data-missing.json",
                        new Metadata(
                                "aws:cdk:asset",
                                null,
                                Collections.singletonList("trace")
                        )
                },
                {
                        "data-null.json",
                        new Metadata(
                                "aws:cdk:asset",
                                null,
                                Collections.singletonList("trace")
                        )
                },
                {
                        "data-string.json",
                        new Metadata(
                                "aws:cdk:asset",
                                "data",
                                Collections.singletonList("trace")
                        )
                },
                {
                        "data-object-container-image.json",
                        new ContainerImageMetadata(
                                "aws:cdk:asset",
                                new ContainerImageData("repository", "latest", "container-image"),
                                Collections.singletonList("trace")
                        )
                },
                {
                        "data-object-packaging-missing.json",
                        new Metadata(
                                "aws:cdk:asset",
                                new HashMap<String, String>(){{
                                    put("path", "file");
                                    put("hash", "file-hash");
                                }},
                                Collections.singletonList("trace")
                        )
                },
                {
                        "data-object-packaging-null.json",
                        new Metadata(
                                "aws:cdk:asset",
                                new HashMap<String, Object>(){{
                                    put("path", "file");
                                    put("packaging", null);
                                    put("hash", "file-hash");
                                }},
                                Collections.singletonList("trace")
                        )
                },
                {
                        "data-object-packaging-object.json",
                        new Metadata(
                                "aws:cdk:asset",
                                new HashMap<String, Object>(){{
                                    put("path", "file");
                                    put("packaging", Collections.singletonMap("type", "file"));
                                    put("hash", "file-hash");
                                }},
                                Collections.singletonList("trace")
                        )
                },

        };
    }

    @Test(dataProvider = "dataProvider")
    public void test(String filename, Metadata expectedValue) throws IOException {
        Metadata metadata = readMetadata(filename);
        Assert.assertEquals(metadata, expectedValue);
    }

    private Metadata readMetadata(String filename) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            return OBJECT_MAPPER.readValue(inputStream, Metadata.class);
        }
    }

    private static class Artifact {
        private Map<String, List<Metadata>> metadata;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "data.packaging", defaultImpl = Metadata.class, visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AssetMetadata.class, name = "file"),
            @JsonSubTypes.Type(value = ContainerImageMetadata.class, name = "container-image"),
    })
    @JsonTypeResolver(NestedPropertyTypeResolver.class)
    private static class Metadata {

        private final String type;
        private final Object data;
        private final List<String> trace;

        @JsonCreator
        public Metadata(@JsonProperty("type") String type,
                        @JsonProperty("data") Object data,
                        @JsonProperty("trace") List<String> trace) {
            this.type = type;
            this.data = data;
            this.trace = trace;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Metadata metadata = (Metadata) o;
            return Objects.equals(type, metadata.type) &&
                    Objects.equals(data, metadata.data) &&
                    Objects.equals(trace, metadata.trace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, data, trace);
        }

        @Override
        public String toString() {
            return "Metadata{" +
                    "type='" + type + '\'' +
                    ", data=" + data +
                    ", trace=" + trace +
                    '}';
        }
    }

    private static class AssetMetadata extends Metadata {

        public AssetMetadata(@JsonProperty("type") String type,
                             @JsonProperty("data") AssetData data,
                             @JsonProperty("trace") List<String> value) {
            super(type, data, value);
        }

    }

    private static class AssetData {

        private final String path;
        private final String packaging;
        private final String hash;

        @JsonCreator
        public AssetData(@JsonProperty("path") String path,
                         @JsonProperty("packaging") String packaging,
                         @JsonProperty("hash") String hash) {
            this.path = path;
            this.packaging = packaging;
            this.hash = hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AssetData assetData = (AssetData) o;
            return Objects.equals(path, assetData.path) &&
                    Objects.equals(packaging, assetData.packaging) &&
                    Objects.equals(hash, assetData.hash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, packaging, hash);
        }

        @Override
        public String toString() {
            return "AssetData{" +
                    "path='" + path + '\'' +
                    ", packaging='" + packaging + '\'' +
                    ", hash='" + hash + '\'' +
                    '}';
        }
    }

    private static class ContainerImageMetadata extends Metadata {

        public ContainerImageMetadata(@JsonProperty("type") String type,
                                      @JsonProperty("data") ContainerImageData data,
                                      @JsonProperty("trace") List<String> value) {
            super(type, data, value);
        }

    }

    private static class ContainerImageData {

        private final String repositoryName;
        private final String imageTag;
        private final String packaging;

        @JsonCreator
        public ContainerImageData(@JsonProperty("repositoryName") String repositoryName,
                                  @JsonProperty("imageTag") String imageTag,
                                  @JsonProperty("packaging")String packaging) {
            this.repositoryName = repositoryName;
            this.imageTag = imageTag;
            this.packaging = packaging;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContainerImageData that = (ContainerImageData) o;
            return Objects.equals(repositoryName, that.repositoryName) &&
                    Objects.equals(imageTag, that.imageTag) &&
                    Objects.equals(packaging, that.packaging);
        }

        @Override
        public int hashCode() {
            return Objects.hash(repositoryName, imageTag, packaging);
        }

        @Override
        public String toString() {
            return "ContainerImageData{" +
                    "repositoryName='" + repositoryName + '\'' +
                    ", imageTag='" + imageTag + '\'' +
                    ", packaging='" + packaging + '\'' +
                    '}';
        }
    }

}