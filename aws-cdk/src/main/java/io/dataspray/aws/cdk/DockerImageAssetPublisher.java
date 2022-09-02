package io.dataspray.aws.cdk;

import com.google.common.collect.ImmutableList;
import io.dataspray.aws.cdk.process.ProcessExecutionException;
import io.dataspray.aws.cdk.process.ProcessRunner;
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

    private final ProcessRunner processRunner;

    private EcrClient ecrClient;

    public DockerImageAssetPublisher(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Builds the given using the specified build parameters and published the built image to ECR.
     *
     * @param repositoryName the name of the repository
     * @param tag image tag
     * @param imageBuild build definition
     * @param environment resolved environment
     */
    public void publish(String repositoryName, String tag, ImageBuild imageBuild, ResolvedEnvironment environment) {
        ImageDetail image = findImage(repositoryName, tag, environment).orElse(null);
        if (image == null) {
            ensureDockerInstalled();

            AuthorizationData authorizationData = getAuthorizationData(environment)
                    .orElseThrow(() -> new CdkException("Unable to retrieve authorization token from ECR"));
            try {
                processRunner.run(toDockerLoginCommand(authorizationData));
            } catch (ProcessExecutionException e) {
                throw new CdkException("Unable to add ECR authorization data");
            }

            logger.info("Building docker image before publishing it to the ECR, dockerFile={}", imageBuild.getDockerfile());
            try {
                processRunner.run(toBuildCommand(imageBuild));
            } catch (ProcessExecutionException e) {
                throw new CdkException("Failed to build the docker image from " + imageBuild.getDockerfile() +
                        ". Please make sure that the Docker daemon is running");
            }

            Repository repository = findRepository(repositoryName, environment)
                    .orElseGet(() -> createRepository(repositoryName, environment));
            String imageUri = String.join(":", repository.repositoryUri(), tag);
            processRunner.run(ImmutableList.of("docker", "tag", imageBuild.getImageTag(), imageUri));

            logger.info("Publishing docker image, imageUri={}", imageUri);
            try {
                processRunner.run(ImmutableList.of("docker", "push", imageUri));
            } catch (ProcessExecutionException e) {
                throw new CdkException("Unable to push the image " + imageUri + " to the ECR repository");
            }
        }
    }

    private void ensureDockerInstalled() {
        try {
            processRunner.run(Arrays.asList("docker", "--version"));
        } catch (ProcessExecutionException e) {
            throw new CdkException("Docker is required in order to build container assets");
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

    private EcrClient getEcrClient(ResolvedEnvironment environment) {
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

    private Optional<ImageDetail> findImage(String repositoryName, String imageTag, ResolvedEnvironment environment) {
        DescribeImagesRequest describeRequest = DescribeImagesRequest.builder()
                .repositoryName(repositoryName)
                .imageIds(ImageIdentifier.builder()
                        .imageTag(imageTag)
                        .build())
                .build();
        try {
            DescribeImagesResponse response = getEcrClient(environment).describeImages(describeRequest);
            return response.imageDetails().stream().findFirst();
        } catch (ImageNotFoundException | RepositoryNotFoundException e) {
            return Optional.empty();
        }
    }

    private Optional<Repository> findRepository(String name, ResolvedEnvironment environment) {
        DescribeRepositoriesRequest describeRequest = DescribeRepositoriesRequest.builder()
                .repositoryNames(name)
                .build();
        try {
            DescribeRepositoriesResponse response = getEcrClient(environment).describeRepositories(describeRequest);
            return response.repositories().stream()
                    .findFirst();
        } catch (RepositoryNotFoundException e) {
            return Optional.empty();
        }
    }

    private Repository createRepository(String name, ResolvedEnvironment environment) {
        CreateRepositoryRequest createRequest = CreateRepositoryRequest.builder()
                .repositoryName(name)
                .build();
        CreateRepositoryResponse response = getEcrClient(environment).createRepository(createRequest);
        return response.repository();
    }

    private Optional<AuthorizationData> getAuthorizationData(ResolvedEnvironment environment) {
        return getEcrClient(environment).getAuthorizationToken().authorizationData().stream().findFirst();
    }

}
