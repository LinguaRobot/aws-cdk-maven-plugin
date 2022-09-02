package io.dataspray.aws.cdk;

import software.amazon.awscdk.cxapi.CloudAssembly;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public interface Destroy {

    /**
     * Destroys CDK stack(s)
     *
     * @param cloudAssemblyDirectory Directory of synthesized stack(s)
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     * @param profileOpt Optional AWS account profile name
     * @param isInteractive Whether session is interactive and logs should be printed
     */
    void execute(
            Path cloudAssemblyDirectory,
            Set<String> stacks,
            Optional<String> profileOpt,
            boolean isInteractive);

    /**
     * Destroys CDK stack(s) for all stacks defined in cloud assembly
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     */
    void execute(CloudAssembly cloudAssembly);

    /**
     * Destroys CDK stack(s)
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     */
    void execute(
            CloudAssembly cloudAssembly,
            String... stacks);

    /**
     * Destroys CDK stack(s)
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     */
    void execute(
            CloudAssembly cloudAssembly,
            Set<String> stacks,
            String profile);

    /**
     * Destroys CDK stack(s)
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     * @param profileOpt Optional AWS account profile name
     * @param isInteractive Whether session is interactive and logs should be printed
     */
    void execute(
            CloudAssembly cloudAssembly,
            Set<String> stacks,
            Optional<String> profileOpt,
            boolean isInteractive);
}
