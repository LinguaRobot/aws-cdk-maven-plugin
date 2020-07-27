package io.linguarobot.aws.cdk.maven;

import com.google.common.collect.ImmutableMap;
import io.linguarobot.aws.cdk.maven.process.DefaultProcessRunner;
import io.linguarobot.aws.cdk.maven.process.ProcessRunner;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Deploys the synthesized templates to the AWS.
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY)
public class DeployMojo extends AbstractCloudActionMojo {

    private static final Logger logger = LoggerFactory.getLogger(DeployMojo.class);

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The name of the CDK toolkit stack
     */
    @Parameter(defaultValue = "CDKToolkit")
    private String toolkitStackName;

    /**
     * Stacks to be deployed. By default, all the stacks defined in the cloud application will be deployed.
     */
    @Parameter
    private Set<String> stacks;

    /**
     * Input parameters for the stacks. For the new stacks, all the parameters without a default value must be
     * specified. In the case of an update, existing values will be reused.
     */
    @Parameter
    private Map<String, String> parameters;

    @Override
    public void execute(CloudDefinition cloudDefinition, EnvironmentResolver environmentResolver) {
        if (stacks != null && logger.isWarnEnabled()) {
            Set<String> undefinedStacks = new HashSet<>(stacks);
            cloudDefinition.getStacks().forEach(stack -> undefinedStacks.remove(stack.getStackName()));
            if (!undefinedStacks.isEmpty()) {
                logger.warn("The following stacks are not defined in the cloud application and can not be deployed: {}",
                        String.join(", ", undefinedStacks));
            }
        }

        ProcessRunner processRunner = new DefaultProcessRunner(project.getBasedir());
        Map<String, StackDeployer> deployers = new HashMap<>();


        for (StackDefinition stack : cloudDefinition.getStacks()) {
            if (this.stacks == null || this.stacks.contains(stack.getStackName())) {
                StackDeployer deployer = deployers.computeIfAbsent(stack.getEnvironment(), environment -> {
                    ResolvedEnvironment resolvedEnvironment = environmentResolver.resolve(environment);
                    DockerImageAssetPublisher dockerImagePublisher = new DockerImageAssetPublisher(resolvedEnvironment, processRunner);
                    FileAssetPublisher filePublisher = new FileAssetPublisher(resolvedEnvironment);
                    ToolkitConfiguration toolkitConfiguration = new ToolkitConfiguration(toolkitStackName);
                    return new StackDeployer(cloudDefinition.getCloudAssemblyDirectory(), resolvedEnvironment,
                            toolkitConfiguration, filePublisher, dockerImagePublisher);
                });

                if (!stack.getResources().isEmpty()) {
                    deployer.deploy(stack, parameters != null ? parameters : ImmutableMap.of());
                } else {
                    deployer.destroy(stack);
                }
            }
        }
    }

}
