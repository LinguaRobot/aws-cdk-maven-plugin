package io.linguarobot.aws.cdk.maven;

import io.linguarobot.aws.cdk.maven.process.DefaultProcessRunner;
import io.linguarobot.aws.cdk.maven.process.ProcessRunner;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deploys the synthesized templates to the AWS.
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY)
public class DeployMojo extends AbstractCdkMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The name of the CDK toolkit stack
     */
    @Parameter(defaultValue = "CDKToolkit")
    private String toolkitStackName;

    @Override
    public void execute(Path cloudAssemblyDirectory, EnvironmentResolver environmentResolver) {
        if (!Files.exists(cloudAssemblyDirectory)) {
            throw new CdkPluginException("The cloud assembly directory " + cloudAssemblyDirectory + " doesn't exist. " +
                    "Did you forget to add 'synth' goal to the execution?");
        }

        CloudDefinition cloudDefinition = CloudDefinition.create(cloudAssemblyDirectory);
        Map<String, List<StackDefinition>> stacks = cloudDefinition.getStacks().stream()
                .collect(Collectors.groupingBy(StackDefinition::getEnvironment));
        ProcessRunner processRunner = new DefaultProcessRunner(project.getBasedir());
        stacks.forEach((environment, environmentStacks) -> {
            ResolvedEnvironment resolvedEnvironment = environmentResolver.resolve(environment);
            DockerImageAssetPublisher dockerImagePublisher = new DockerImageAssetPublisher(resolvedEnvironment, processRunner);
            FileAssetPublisher filePublisher = new FileAssetPublisher(resolvedEnvironment);
            ToolkitConfiguration toolkitConfiguration = new ToolkitConfiguration(toolkitStackName);
            StackDeployer stackDeployer = new StackDeployer(cloudAssemblyDirectory, resolvedEnvironment,
                    toolkitConfiguration, filePublisher, dockerImagePublisher);
            environmentStacks.forEach(stack -> {
                if (!stack.getResources().isEmpty()) {
                    stackDeployer.deploy(stack);
                } else {
                    stackDeployer.destroy(stack);
                }
            });

        });
    }

}
