package io.linguarobot.aws.cdk.maven;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.linguarobot.aws.cdk.*;
import org.apache.commons.lang3.ObjectUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CloudDefinition {

    private final List<StackDefinition> stacks;

    private CloudDefinition(List<StackDefinition> stacks) {
        this.stacks = stacks;
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

    @Override
    public String toString() {
        return "CloudDefinition{" +
                "stacks=" + stacks +
                '}';
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JSR353Module());

    public static CloudDefinition create(Path cloudAssemblyDirectory) {
        CloudManifest manifest;
        try {
            manifest = CloudManifest.create(cloudAssemblyDirectory);
        } catch (IOException e) {
            throw new CdkPluginException("Failed to read the cloud manifest", e);
        }

        Map<String, StackDefinition> stacks = manifest.getArtifacts().entrySet().stream()
                .filter(artifact -> artifact.getValue().getType() == ArtifactType.STACK)
                .map(artifact -> {
                    String artifactId = artifact.getKey();
                    StackArtifact stackArtifact = (StackArtifact) artifact.getValue();
                    String stackName = ObjectUtils.firstNonNull(stackArtifact.getProperties().getStackName(), artifactId);
                    Path templateFile = cloudAssemblyDirectory.resolve(stackArtifact.getProperties().getTemplateFile());
                    Integer requiredToolkitStackVersion = Optional.ofNullable(stackArtifact.getProperties().getRequiredToolkitStackVersion())
                            .map(Number::intValue)
                            .orElse(null);
                    Map<String, Object> template = readTemplate(templateFile);
                    Map<String, ParameterDefinition> parameters = getParameterDefinitions(template);
                    List<AssetMetadata> assets = stackArtifact.getMetadata().values().stream()
                            .flatMap(List::stream)
                            .filter(metadata -> MetadataTypes.ASSET.equals(metadata.getType()))
                            .map(metadata -> (AssetMetadata) metadata)
                            .collect(Collectors.toList());
                    Map<String, Map<String, Object>> resources = (Map<String, Map<String, Object>>) template.getOrDefault("Resources", ImmutableMap.of());
                    return StackDefinition.builder()
                            .withStackName(stackName)
                            .withTemplateFile(templateFile)
                            .withEnvironment(stackArtifact.getEnvironment())
                            .withRequiredToolkitStackVersion(requiredToolkitStackVersion)
                            .withParameters(parameters)
                            .withParameterValues(stackArtifact.getProperties().getParameters())
                            .withAssets(assets)
                            .withResources(resources)
                            .withDependencies(stackArtifact.getDependencies())
                            .build();
                })
                .collect(Collectors.toMap(StackDefinition::getStackName, Function.identity()));

        Set<String> visited = new HashSet<>();
        List<StackDefinition> orderedStacks = new ArrayList<>();
        stacks.keySet().forEach(stackName -> sortTopologically(stackName, stacks, visited, orderedStacks::add));
        return new CloudDefinition(ImmutableList.copyOf(orderedStacks));
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
            return OBJECT_MAPPER.readValue(template.toFile(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new CdkPluginException("Failed to read the stack template: " + template);
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
