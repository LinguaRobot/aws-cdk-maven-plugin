package io.dataspray.aws.cdk.maven;

import java.nio.file.Files;
import java.nio.file.Path;

public abstract class AbstractCloudActionMojo extends AbstractCdkMojo {

    @Override
    public void execute(Path cloudAssemblyDirectory, EnvironmentResolver environmentResolver) {
        if (!Files.exists(cloudAssemblyDirectory)) {
            throw new CdkPluginException("The cloud assembly directory " + cloudAssemblyDirectory + " doesn't exist. " +
                    "Did you forget to add 'synth' goal to the execution?");
        }

        CloudDefinition cloudDefinition = CloudDefinition.create(cloudAssemblyDirectory);
        execute(cloudDefinition, environmentResolver);
    }

    protected abstract void execute(CloudDefinition cloudDefinition, EnvironmentResolver environmentResolver);

}
