package io.dataspray.aws.cdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.cloudassembly.schema.ArtifactManifest;
import software.amazon.awscdk.cloudassembly.schema.ArtifactType;
import software.amazon.awscdk.cloudassembly.schema.AssemblyManifest;
import software.amazon.awscdk.cloudassembly.schema.AssetManifestProperties;
import software.amazon.awscdk.cloudassembly.schema.AwsCloudFormationStackProperties;
import software.amazon.awscdk.cloudassembly.schema.DockerImageAsset;
import software.amazon.awscdk.cloudassembly.schema.FileAsset;
import software.amazon.awscdk.cloudassembly.schema.Manifest;
import software.amazon.awscdk.cxapi.CloudAssembly;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CloudDefinition {

    private static final Logger logger = LoggerFactory.getLogger(CloudDefinition.class);

    private final Path cloudAssemblyDirectory;
    private final List<StackDefinition> stacks;
    private final Map<String, FileAsset> fileAssets;
    private final Map<String, DockerImageAsset> imageAssets;

    private CloudDefinition(Path cloudAssemblyDirectory, List<StackDefinition> stacks,
                            Map<String, FileAsset> fileAssets, Map<String, DockerImageAsset> imageAssets) {
        this.cloudAssemblyDirectory = cloudAssemblyDirectory;
        this.stacks = ImmutableList.copyOf(stacks);
        this.fileAssets = ImmutableMap.copyOf(fileAssets);
        this.imageAssets = ImmutableMap.copyOf(imageAssets);
    }

    /**
     * Returns the stacks defined in the cloud application. The stacks are sorted to correspond the deployment order,
     * i.e. the stack that should be deployed first will be first in the returned {@code List}.
     *
     * @return the stacks defined in the cloud application
     */
    @Nonnull
    public List<StackDefinition> getStacks() {
        return stacks;
    }

    @Nonnull
    public Map<String, FileAsset> getFileAssets() {
        return fileAssets;
    }

    @Nonnull
    public Map<String, DockerImageAsset> getImageAssets() {
        return imageAssets;
    }

    @Nonnull
    public Path getCloudAssemblyDirectory() {
        return cloudAssemblyDirectory;
    }

    @Override
    public String toString() {
        return "CloudDefinition{" +
                "stacks=" + stacks +
                "fileAssets=" + fileAssets +
                "imageAssets=" + imageAssets +
                '}';
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JSR353Module());

    public static CloudDefinition create(Path cloudAssemblyDirectory) {
        if (!Files.exists(cloudAssemblyDirectory)) {
            throw new CdkException("The cloud assembly directory " + cloudAssemblyDirectory + " doesn't exist. " +
                    "Did you forget to add 'synth' goal to the execution?");
        }
        return create(new CloudAssembly(cloudAssemblyDirectory.toString()));
    }

    public static CloudDefinition create(CloudAssembly cloudAssembly) {
        Path cloudAssemblyDirectory = Paths.get(cloudAssembly.getDirectory());
        AssemblyManifest assemblyManifest = cloudAssembly.getManifest();

        Map<String, FileAsset> fileAssets = Maps.newHashMap();
        Map<String, DockerImageAsset> imageAssets = Maps.newHashMap();
        if (assemblyManifest.getArtifacts() != null) {
            assemblyManifest.getArtifacts().values().stream()
                    .filter(artifactManifest -> ArtifactType.ASSET_MANIFEST.equals(artifactManifest.getType()))
                    .map(artifactManifest -> JsiiUtil.getProperty(artifactManifest, "properties", AssetManifestProperties.class))
                    .map(AssetManifestProperties::getFile)
                    .map(assetManifestFile -> Manifest.loadAssetManifest(cloudAssemblyDirectory.resolve(assetManifestFile).toString()))
                    .forEach(assetManifest -> {
                        if (assetManifest.getFiles() != null) {
                            fileAssets.putAll(assetManifest.getFiles());
                        }
                        if (assetManifest.getDockerImages() != null) {
                            imageAssets.putAll(assetManifest.getDockerImages());
                        }
                    });
        }

        Map<String, StackDefinition> stacks = assemblyManifest.getArtifacts().entrySet().stream()
                .filter(artifact -> artifact.getValue().getType() == ArtifactType.AWS_CLOUDFORMATION_STACK)
                .map(artifact -> {
                    String artifactId = artifact.getKey();
                    ArtifactManifest stackArtifact = artifact.getValue();
                    String stackName = ObjectUtils.firstNonNull(stackArtifact.getDisplayName(), artifactId);
                    AwsCloudFormationStackProperties properties = JsiiUtil.getProperty(stackArtifact, "properties", AwsCloudFormationStackProperties.class);
                    Path templateFile = cloudAssemblyDirectory.resolve(properties.getTemplateFile());
                    Integer requiredToolkitStackVersion = Optional.ofNullable(properties.getRequiresBootstrapStackVersion())
                            .map(Number::intValue)
                            .orElse(null);
                    Map<String, Object> template = readTemplate(templateFile);
                    Map<String, ParameterDefinition> parameters = getParameterDefinitions(template);
                    Map<String, String> parameterValues = properties.getParameters();

                    Map<String, Map<String, Object>> resources = (Map<String, Map<String, Object>>) template.getOrDefault("Resources", ImmutableMap.of());
                    return StackDefinition.builder()
                            .withStackName(stackName)
                            .withTemplateFile(templateFile)
                            .withEnvironment(stackArtifact.getEnvironment())
                            .withRequiredToolkitStackVersion(requiredToolkitStackVersion)
                            .withParameters(parameters)
                            .withParameterValues(parameterValues)
                            .withResources(resources)
                            .withDependencies(stackArtifact.getDependencies())
                            .build();
                })
                .collect(Collectors.toMap(StackDefinition::getStackName, Function.identity()));

        Set<String> visited = new HashSet<>();
        List<StackDefinition> sortedStacks = new ArrayList<>();
        stacks.keySet().forEach(stackName -> sortTopologically(stackName, stacks, visited, sortedStacks::add));
        return new CloudDefinition(cloudAssemblyDirectory, sortedStacks, fileAssets, imageAssets);
    }

    private static void sortTopologically(String stackName,
                                          Map<String, StackDefinition> stacks,
                                          Set<String> visited,
                                          Consumer<StackDefinition> consumer) {
        if (!visited.contains(stackName)) {
            visited.add(stackName);
            StackDefinition definition = stacks.get(stackName);
            if (definition != null) {
                for (String dependency : definition.getDependencies()) {
                    sortTopologically(dependency, stacks, visited, consumer);
                }
                consumer.accept(definition);
            }
        }
    }

    private static Map<String, Object> readTemplate(Path template) {
        try {
            return OBJECT_MAPPER.readValue(template.toFile(), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new CdkException("Failed to read the stack template: " + template);
        }
    }

    private static Map<String, ParameterDefinition> getParameterDefinitions(Map<String, Object> template) {
        Map<String, Map<String, Object>> parameters = (Map<String, Map<String, Object>>) template.getOrDefault("Parameters", Collections.emptyMap());

        return parameters.entrySet().stream()
                .map(parameter -> {
                    String name = parameter.getKey();
                    Object defaultValue = parameter.getValue().get("Default");
                    return new ParameterDefinition(name, defaultValue);
                })
                .collect(Collectors.toMap(ParameterDefinition::getName, Function.identity()));
    }
}
