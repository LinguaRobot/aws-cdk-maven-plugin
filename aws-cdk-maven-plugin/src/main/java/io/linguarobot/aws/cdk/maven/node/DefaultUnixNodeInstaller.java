package io.linguarobot.aws.cdk.maven.node;

import io.linguarobot.aws.cdk.maven.process.ProcessRunner;

import java.nio.file.Path;

public class DefaultUnixNodeInstaller extends AbstractUnixNodeInstaller {

    private final String osName;
    private final String arch;

    public DefaultUnixNodeInstaller(ProcessRunner processRunner, Path localRepositoryDirectory, String osName, String arch) {
        super(processRunner, localRepositoryDirectory);
        this.osName = osName;
        this.arch = arch;
    }

    @Override
    protected String getArch() {
        return arch;
    }

    @Override
    protected String getOperationSystemName() {
        return osName;
    }

}
