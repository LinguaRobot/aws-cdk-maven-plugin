package io.dataspray.aws.cdk.maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.cloudassembly.schema.DockerImageAsset;
import software.amazon.awscdk.cloudassembly.schema.DockerImageDestination;
import software.amazon.awscdk.cloudassembly.schema.FileAsset;
import software.amazon.awscdk.cloudassembly.schema.FileAssetPackaging;
import software.amazon.awscdk.cloudassembly.schema.FileDestination;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AssetDeployer {

    private final Path cloudAssemblyDirectory;
    private final FileAssetPublisher fileAssetPublisher;
    private final DockerImageAssetPublisher dockerImagePublisher;
    private final EnvironmentResolver environmentResolver;

    public AssetDeployer(Path cloudAssemblyDirectory,
                         FileAssetPublisher fileAssetPublisher,
                         DockerImageAssetPublisher dockerImagePublisher,
                         EnvironmentResolver environmentResolver) {
        this.cloudAssemblyDirectory = cloudAssemblyDirectory;
        this.fileAssetPublisher = fileAssetPublisher;
        this.dockerImagePublisher = dockerImagePublisher;
        this.environmentResolver = environmentResolver;
    }

    public void deploy(Map<String, DockerImageAsset> imageAssets, Map<String, FileAsset> fileAssets) {
        List<Runnable> publishmentTasks = new ArrayList<>();

        for (Map.Entry<String, DockerImageAsset> imageAssetEntry : imageAssets.entrySet()) {
            for (Map.Entry<String, DockerImageDestination> destinationEntry : imageAssetEntry.getValue().getDestinations().entrySet()) {
                ResolvedEnvironment environment = environmentResolver.resolveFromDestination(destinationEntry.getKey());
                publishmentTasks.add(createImagePublishmentTask(imageAssetEntry.getKey(), imageAssetEntry.getValue(), destinationEntry.getValue(), environment));
            }
        }

        for (Map.Entry<String, FileAsset> entry : fileAssets.entrySet()) {
            FileAsset fileAsset = entry.getValue();
            Objects.requireNonNull(fileAsset.getSource().getPath(),
                    "File asset has no path indicating an executable to be called to produce the asset which is not yet supported");
            for (Map.Entry<String, FileDestination> destinationEntry : fileAsset.getDestinations().entrySet()) {
                ResolvedEnvironment environment = environmentResolver.resolveFromDestination(destinationEntry.getKey());
                String bucketName = environment.resolveVariables(destinationEntry.getValue().getBucketName());
                String objectKey = destinationEntry.getValue().getObjectKey();

                publishmentTasks.add(() -> {
                    Path file = cloudAssemblyDirectory.resolve(fileAsset.getSource().getPath());
                    try {
                        fileAssetPublisher.publish(file, objectKey, bucketName, environment);
                    } catch (IOException e) {
                        throw StackDeploymentException.builder(environment)
                                .withCause("An error occurred while publishing the file asset " + file)
                                .withCause(e)
                                .build();
                    }
                });
            }
        }

        try {
            publishmentTasks.forEach(Runnable::run);
        } catch (CdkPluginException e) {
            throw StackDeploymentException.builder()
                    .withCause(e.getMessage())
                    .withCause(e.getCause())
                    .build();
        } catch (Exception e) {
            throw StackDeploymentException.builder()
                    .withCause(e)
                    .build();
        }
    }

    private Runnable createImagePublishmentTask(String assetId, DockerImageAsset imageAsset, DockerImageDestination destination, ResolvedEnvironment environment) {
        Path contextDirectory = cloudAssemblyDirectory.resolve(imageAsset.getSource().getDirectory());
        if (!Files.exists(contextDirectory)) {
            throw StackDeploymentException.builder(environment)
                    .withCause("The Docker context directory doesn't exist: " + contextDirectory)
                    .build();
        }

        Path dockerfilePath;
        String dockerFile = imageAsset.getSource().getDockerFile();
        if (dockerFile != null) {
            dockerfilePath = contextDirectory.resolve(dockerFile);
            if (!Files.exists(dockerfilePath)) {
                throw StackDeploymentException.builder(environment)
                        .withCause("The Dockerfile doesn't exist: " + dockerfilePath)
                        .build();
            }
        } else {
            dockerfilePath = findDockerfile(contextDirectory)
                    .orElseThrow(() -> StackDeploymentException.builder(environment)
                            .withCause("Unable to find Dockerfile in the context directory " + contextDirectory)
                            .build());
        }

        return () -> {
            String localTag = String.join("-", "cdkasset", assetId.toLowerCase());
            ImageBuild imageBuild = ImageBuild.builder()
                    .withContextDirectory(contextDirectory)
                    .withDockerfile(dockerfilePath)
                    .withImageTag(localTag)
                    .withArguments(imageAsset.getSource().getDockerBuildArgs())
                    .withTarget(imageAsset.getSource().getDockerBuildTarget())
                    .build();
            dockerImagePublisher.publish(destination.getRepositoryName(), destination.getImageTag(), imageBuild, environment);
        };
    }

    private Optional<Path> findDockerfile(Path contextDirectory) {
        Path dockerfile = contextDirectory.resolve("Dockerfile");
        if (!Files.exists(dockerfile)) {
            dockerfile = contextDirectory.resolve("dockerfile");
        }

        return Optional.of(dockerfile).filter(Files::exists);
    }
}
