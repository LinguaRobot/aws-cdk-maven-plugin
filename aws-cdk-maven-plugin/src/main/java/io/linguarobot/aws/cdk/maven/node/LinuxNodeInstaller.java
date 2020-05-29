package io.linguarobot.aws.cdk.maven.node;

import io.linguarobot.aws.cdk.maven.process.ProcessRunner;

import java.nio.file.Path;

public class LinuxNodeInstaller extends AbstractUnixNodeInstaller {

    public LinuxNodeInstaller(ProcessRunner processRunner, Path localRepositoryPath) {
        super(processRunner, localRepositoryPath);
    }

    @Override
    protected String getArch() {
        String arch = System.getProperty("os.arch");
        if (arch.equals("arm")) {
            String osVersion = System.getProperty("os.version");
            if (osVersion.contains("v7")) {
                arch = "armv7l";
            } else {
                throw new NodeInstallationException("The architecture is not supported: " + arch);
            }
        } else if (arch.equals("aarch64")) {
            arch = "arm64";
        } else if (!arch.equals("ppc64le") && !arch.equals("s390x")) {
            if (arch.contains("64")) {
                arch = "x64";
            } else {
                throw new NodeInstallationException("The architecture is not supported: " + arch);
            }
        }

        return arch;
    }

    @Override
    protected String getOperationSystemName() {
        return "linux";
    }

}
