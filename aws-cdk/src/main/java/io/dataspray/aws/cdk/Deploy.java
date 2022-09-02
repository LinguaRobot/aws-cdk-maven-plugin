package io.dataspray.aws.cdk;

import software.amazon.awscdk.cxapi.CloudAssembly;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deploys the synthesized templates to the AWS.
 */
public interface Deploy {
    /**
     * Deploy the synthesized templates to AWS
     *
     * @param cloudAssemblyDirectory Directory of synthesized stack(s)
     * @param toolkitStackName The name of the CDK toolkit stack.
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     * @param parameters Input parameters for the stacks. For the new stacks, all the parameters without a default value
     * must be
     * specified. In the case of an update, existing values will be reused.
     * @param tags Tags that will be added to the stacks.
     * @param profileOpt Optional AWS account profile name
     * @param isInteractive Whether session is interactive and logs should be printed
     */
    void execute(
            Path cloudAssemblyDirectory,
            String toolkitStackName,
            Set<String> stacks,
            Map<String, String> parameters,
            Map<String, String> tags,
            Optional<String> profileOpt,
            boolean isInteractive);

    /**
     * Deploy the synthesized templates to AWS for all stacks
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     */
    void execute(CloudAssembly cloudAssembly);

    /**
     * Deploy the synthesized templates to AWS
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     */
    void execute(
            CloudAssembly cloudAssembly,
            String... stacks);

    /**
     * Deploy the synthesized templates to AWS
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
     * Deploy the synthesized templates to AWS
     *
     * @param cloudAssembly Cloud assembly created via app.synth()
     * @param toolkitStackName The name of the CDK toolkit stack.
     * @param stacks Stacks, for which bootstrapping will be performed if it's required.
     * @param parameters Input parameters for the stacks. For the new stacks, all the parameters without a default value must be specified. In the case of an update, existing values will be reused.
     * @param tags Tags that will be added to the stacks.
     * @param profileOpt Optional AWS account profile name
     * @param isInteractive Whether session is interactive and logs should be printed
     */
    void execute(
            CloudAssembly cloudAssembly,
            String toolkitStackName,
            Set<String> stacks,
            Map<String, String> parameters,
            Map<String, String> tags,
            Optional<String> profileOpt,
            boolean isInteractive);
}
