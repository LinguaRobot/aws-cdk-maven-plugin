package io.dataspray.aws.cdk;

import software.amazon.awscdk.cxapi.CloudAssembly;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Deploys toolkit stacks required by the CDK application.
 */
public interface Bootstrap {

    /**
     * Bootstrap CDK
     *
     * @param cloudAssemblyDirectory Directory of synthesized stack(s)
     * @param toolkitStackName The name of the CDK toolkit stack.
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     * @param profileOpt Optional AWS account profile name
     * @param isInteractive Whether session is interactive and logs should be printed
     */
    void execute(
            Path cloudAssemblyDirectory,
            String toolkitStackName,
            Set<String> stacks,
            Optional<String> profileOpt,
            boolean isInteractive);

    /**
     * Bootstrap CDK for all stacks
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     */
    void execute(CloudAssembly cloudAssembly);

    /**
     * Bootstrap CDK
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     */
    void execute(
            CloudAssembly cloudAssembly,
            String... stacks);

    /**
     * Bootstrap CDK
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     * @param profile AWS account profile name
     */
    void execute(
            CloudAssembly cloudAssembly,
            Set<String> stacks,
            String profile);

    /**
     * Bootstrap CDK
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     * @param toolkitStackName The name of the CDK toolkit stack.
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     * @param profileOpt Optional AWS account profile name
     * @param isInteractive Whether session is interactive and logs should be printed
     */
    void execute(
            CloudAssembly cloudAssembly,
            String toolkitStackName,
            Set<String> stacks,
            Optional<String> profileOpt,
            boolean isInteractive);
}
