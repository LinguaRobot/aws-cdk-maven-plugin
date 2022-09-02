package io.dataspray.aws.cdk;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deploys the synthesized templates to the AWS.
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY)
public class DeployMojo extends AbstractCdkMojo {

    /**
     * The name of the CDK toolkit stack
     */
    @Parameter(property = "aws.cdk.toolkit.stack.name", defaultValue = AwsCdk.DEFAULT_TOOLKIT_STACK_NAME)
    private String toolkitStackName;

    /**
     * Stacks to be deployed. By default, all the stacks defined in the cloud application will be deployed.
     */
    @Parameter(property = "aws.cdk.stacks")
    private Set<String> stacks;

    /**
     * Input parameters for the stacks. For the new stacks, all the parameters without a default value must be
     * specified. In the case of an update, existing values will be reused.
     */
    @Parameter
    private Map<String, String> parameters;

    /**
     * Tags that will be added to the stacks.
     */
    @Parameter
    private Map<String, String> tags;

    @Override
    public void execute(Path cloudAssemblyDirectory, Optional<String> profileOpt, boolean isInteractive) {
        AwsCdk.deploy().execute(cloudAssemblyDirectory, toolkitStackName, stacks, parameters, tags, profileOpt, isInteractive);
    }
}
