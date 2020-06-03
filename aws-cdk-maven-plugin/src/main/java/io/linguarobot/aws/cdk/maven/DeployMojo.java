package io.linguarobot.aws.cdk.maven;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import io.linguarobot.aws.cdk.ArtifactType;
import io.linguarobot.aws.cdk.AssetMetadata;
import io.linguarobot.aws.cdk.CloudManifest;
import io.linguarobot.aws.cdk.MetadataType;
import io.linguarobot.aws.cdk.StackArtifact;
import io.linguarobot.aws.cdk.maven.process.DefaultProcessRunner;
import io.linguarobot.aws.cdk.maven.process.ProcessRunner;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deploys the synthesized templates to the AWS.
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY)
public class DeployMojo extends AbstractCdkMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Toolkit stack configuration.
     */
    @Parameter(defaultValue = "${toolkit}")
    private ToolkitConfiguration toolkit;

    @Override
    public void execute(String app, Path cloudAssemblyDirectory, EnvironmentResolver environmentResolver) {
        ProcessRunner processRunner = new DefaultProcessRunner(project.getBasedir());
        if (!Files.exists(cloudAssemblyDirectory)) {
            throw new CdkPluginException("The cloud assembly directory " + cloudAssemblyDirectory + " doesn't exist. " +
                    "Did you forget to add 'synth' goal to the execution?");
        }

        Map<String, List<StackDefinition>> stacks = getStackDefinitions(cloudAssemblyDirectory).stream()
                .collect(Collectors.groupingBy(StackDefinition::getEnvironment));

        stacks.forEach((environment, environmentStacks) -> {
            ResolvedEnvironment resolvedEnvironment = environmentResolver.resolve(environment);
            DockerImageAssetPublisher dockerImagePublisher = new DockerImageAssetPublisher(resolvedEnvironment, processRunner);
            FileAssetPublisher filePublisher = new FileAssetPublisher(resolvedEnvironment);
            StackDeployer stackDeployer = new StackDeployer(cloudAssemblyDirectory, resolvedEnvironment, toolkit,
                    filePublisher, dockerImagePublisher);
            environmentStacks.forEach(stack -> {
                if (!stack.getResources().isEmpty()) {
                    stackDeployer.deploy(stack);
                } else {
                    stackDeployer.destroy(stack);
                }
            });

        });
    }

    private List<StackDefinition> getStackDefinitions(Path cloudAssemblyDirectory) {
        CloudManifest manifest;
        try {
            manifest = CloudManifest.create(cloudAssemblyDirectory);
        } catch (IOException e) {
            throw new CdkPluginException("Failed to read the cloud manifest", e);
        }

        return manifest.getArtifacts().entrySet().stream()
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
                            .filter(metadata -> metadata.getType() == MetadataType.ASSET)
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
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Map<String, ParameterDefinition> getParameterDefinitions(Map<String, Object> template) {
        Map<String, Map<String, Object>> parameters = (Map<String, Map<String, Object>>) template.getOrDefault("Parameters", Collections.emptyMap());

        return parameters.entrySet().stream()
                .map(parameter -> {
                    String name = parameter.getKey();
                    String defaultValue = (String) parameter.getValue().get("Default");
                    return new ParameterDefinition(name, defaultValue);
                })
                .collect(Collectors.toMap(ParameterDefinition::getName, Function.identity()));
    }

    private Map<String, Object> readTemplate(Path template) {
        try {
            return OBJECT_MAPPER.readValue(template.toFile(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new CdkPluginException("Failed to read the stack template: " + template);
        }
    }

}
