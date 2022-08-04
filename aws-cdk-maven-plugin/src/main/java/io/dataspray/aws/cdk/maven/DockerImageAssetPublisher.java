package io.dataspray.aws.cdk.maven;

import com.google.common.collect.ImmutableList;
import io.dataspray.aws.cdk.maven.process.ProcessExecutionException;
import io.dataspray.aws.cdk.maven.process.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.ecr.model.CreateRepositoryResponse;
import software.amazon.awssdk.services.ecr.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ecr.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesRequest;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesResponse;
import software.amazon.awssdk.services.ecr.model.ImageDetail;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ImageNotFoundException;
import software.amazon.awssdk.services.ecr.model.Repository;
import software.amazon.awssdk.services.ecr.model.RepositoryNotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class DockerImageAssetPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DockerImageAssetPublisher.class);

    private final ResolvedEnvironment environment;
    private final ProcessRunner processRunner;

    private EcrClient ecrClient;

    public DockerImageAssetPublisher(ResolvedEnvironment environment,ProcessRunner processRunner) {
        this.environment = environment;
        this.processRunner = processRunner;
    }

    /**
     * Builds the given using the specified build parameters and published the built image to ECR.
     *
     * @param repositoryName the name of the repository
     * @param tag image tag
     * @param imageBuild build definition
     */
    public void publish(String repositoryName, String tag, ImageBuild imageBuild) {
        ImageDetail image = findImage(repositoryName, tag).orElse(null);
        if (image == null) {
            ensureDockerInstalled();

            AuthorizationData authorizationData = getAuthorizationData()
                    .orElseThrow(() -> new CdkPluginException("Unable to retrieve authorization token from ECR"));
            try {
                processRunner.run(toDockerLoginCommand(authorizationData));
            } catch (ProcessExecutionException e) {
                throw new CdkPluginException("Unable to add ECR authorization data");
            }

            logger.info("Building docker image before publishing it to the ECR, dockerFile={}", imageBuild.getDockerfile());
            try {
                processRunner.run(toBuildCommand(imageBuild));
            } catch (ProcessExecutionException e) {
                throw new CdkPluginException("Failed to build the docker image from " + imageBuild.getDockerfile() +
                        ". Please make sure that the Docker daemon is running");
            }

            Repository repository = findRepository(repositoryName)
                    .orElseGet(() -> createRepository(repositoryName));
            String imageUri = String.join(":", repository.repositoryUri(), tag);
            processRunner.run(ImmutableList.of("docker", "tag", imageBuild.getImageTag(), imageUri));

            logger.info("Publishing docker image, imageUri={}", imageUri);
            try {
                processRunner.run(ImmutableList.of("docker", "push", imageUri));
            } catch (ProcessExecutionException e) {
                throw new CdkPluginException("Unable to push the image " + imageUri + " to the ECR repository");
            }
        }
    }

    private void ensureDockerInstalled() {
        try {
            processRunner.run(Arrays.asList("docker", "--version"));
        } catch (ProcessExecutionException e) {
            throw new CdkPluginException("Docker is required in order to build container assets");
        }
    }

    private List<String> toBuildCommand(ImageBuild build) {
        List<String> buildCommand = new ArrayList<>();
        buildCommand.add("docker");
        buildCommand.add("build");
        buildCommand.add("--tag");
        buildCommand.add(build.getImageTag());

        build.getArguments().forEach((name, value) -> {
            buildCommand.add("--build-arg");
            buildCommand.add(String.join("=", name, value));
        });
        if (build.getTarget() != null) {
            buildCommand.add("--target");
            buildCommand.add(build.getTarget());
        }
        buildCommand.add("--file");
        buildCommand.add(build.getDockerfile().toString());
        buildCommand.add(build.getContextDirectory().toString());

        return buildCommand;
    }

    private EcrClient getEcrClient() {
        if (this.ecrClient == null) {
            this.ecrClient = EcrClient.builder()
                    .region(environment.getRegion())
                    .credentialsProvider(environment.getCredentialsProvider())
                    .build();
        }

        return ecrClient;
    }

    private List<String> toDockerLoginCommand(AuthorizationData authorizationData) {
        String[] userPassword = new String(Base64.getDecoder().decode(authorizationData.authorizationToken())).split(":");
        return ImmutableList.of("docker", "login",
                "--username", userPassword[0],
                "--password", userPassword[1],
                authorizationData.proxyEndpoint()
        );
    }

    private Optional<ImageDetail> findImage(String repositoryName, String imageTag) {
        DescribeImagesRequest describeRequest = DescribeImagesRequest.builder()
                .repositoryName(repositoryName)
                .imageIds(ImageIdentifier.builder()
                        .imageTag(imageTag)
                        .build())
                .build();
        try {
            DescribeImagesResponse response = getEcrClient().describeImages(describeRequest);
            return response.imageDetails().stream().findFirst();
        } catch (ImageNotFoundException|RepositoryNotFoundException e) {
            return Optional.empty();
        }
    }

    private Optional<Repository> findRepository(String name) {
        DescribeRepositoriesRequest describeRequest = DescribeRepositoriesRequest.builder()
                .repositoryNames(name)
                .build();
        try {
            DescribeRepositoriesResponse response = getEcrClient().describeRepositories(describeRequest);
            return response.repositories().stream()
                    .findFirst();
        } catch (RepositoryNotFoundException e) {
            return Optional.empty();
        }
    }

    private Repository createRepository(String name) {
        CreateRepositoryRequest createRequest = CreateRepositoryRequest.builder()
                .repositoryName(name)
                .build();
        CreateRepositoryResponse response = getEcrClient().createRepository(createRequest);
        return response.repository();
    }

    private Optional<AuthorizationData> getAuthorizationData() {
        return getEcrClient().getAuthorizationToken().authorizationData().stream().findFirst();
    }

}
