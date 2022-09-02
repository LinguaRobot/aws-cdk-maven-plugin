package io.dataspray.aws.cdk;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Deploys toolkit stacks required by the CDK application.
 */
@Mojo(name = "bootstrap", defaultPhase = LifecyclePhase.DEPLOY)
public class BootstrapMojo extends AbstractCdkMojo {

    /**
     * The name of the CDK toolkit stack.
     */
    @Parameter(property = "aws.cdk.toolkit.stack.name", defaultValue = AwsCdk.DEFAULT_TOOLKIT_STACK_NAME)
    private String toolkitStackName;

    /**
     * Stacks, for which bootstrapping will be performed if it's required.
     */
    @Parameter(property = "aws.cdk.stacks")
    private Set<String> stacks;

    @Override
    public void execute(Path cloudAssemblyDirectory, Optional<String> profileOpt, boolean isInteractive) {
        AwsCdk.bootstrap().execute(cloudAssemblyDirectory, toolkitStackName, stacks, profileOpt, isInteractive);
    }
}
