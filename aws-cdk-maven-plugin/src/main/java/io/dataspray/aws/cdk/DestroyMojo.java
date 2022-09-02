package io.dataspray.aws.cdk;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

@Mojo(name = "destroy")
public class DestroyMojo extends AbstractCdkMojo {

    /**
     * Stacks to be destroyed. By default, all the stacks defined in the cloud application will be deleted.
     */
    @Parameter(property = "aws.cdk.stacks")
    private Set<String> stacks;

    @Override
    public void execute(Path cloudAssemblyDirectory, Optional<String> profileOpt, boolean isInteractive) {
        AwsCdk.destroy().execute(cloudAssemblyDirectory, stacks, profileOpt, isInteractive);
    }
}
