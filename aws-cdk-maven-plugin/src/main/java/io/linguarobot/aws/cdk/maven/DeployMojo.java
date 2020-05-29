package io.linguarobot.aws.cdk.maven;

import io.linguarobot.aws.cdk.maven.process.DefaultProcessRunner;
import io.linguarobot.aws.cdk.maven.process.ProcessRunner;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import software.amazon.awscdk.cxapi.CloudAssembly;
import software.amazon.awscdk.cxapi.CloudFormationStackArtifact;
import software.amazon.awscdk.cxapi.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

        CloudAssembly cloudAssembly = new CloudAssembly(cloudAssemblyDirectory.toString());
        Map<Environment, List<CloudFormationStackArtifact>> stacks = cloudAssembly.getStacks().stream()
                .collect(Collectors.groupingBy(CloudFormationStackArtifact::getEnvironment));
        if (!stacks.isEmpty()) {
            stacks.forEach((environment, environmentStacks) -> {
                ResolvedEnvironment resolvedEnvironment = environmentResolver.resolve(environment);
                DockerImageAssetPublisher dockerImagePublisher = new DockerImageAssetPublisher(resolvedEnvironment, processRunner);
                FileAssetPublisher filePublisher = new FileAssetPublisher(resolvedEnvironment);
                StackDeployer stackDeployer = new StackDeployer(cloudAssemblyDirectory, resolvedEnvironment, toolkit,
                        filePublisher, dockerImagePublisher);
                for (CloudFormationStackArtifact stack : environmentStacks) {
                    Map<String, Object> resources = Optional.of((Map<String, Object>) stack.getTemplate())
                            .map(template -> (Map<String, Object>) template.get("Resources"))
                            .orElse(Collections.emptyMap());
                    if (!resources.isEmpty()) {
                        stackDeployer.deploy(stack);
                    } else {
                        stackDeployer.destroy(stack);
                    }
                }
            });
        }
    }

}
