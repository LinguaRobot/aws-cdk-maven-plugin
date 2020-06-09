package io.linguarobot.aws.cdk.maven.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public abstract class AbstractNodeInstaller implements NodeInstaller {

    private static final Logger logger = LoggerFactory.getLogger(UnixNodeInstaller.class);

    private static final byte[] INSTALLED_STATE = new byte[]{1};

    private final String os;
    private final String arch;
    private final Path localRepositoryDirectory;

    protected AbstractNodeInstaller(Path localRepositoryDirectory, String os, String arch) {
        this.os = os;
        this.arch = arch;
        this.localRepositoryDirectory = localRepositoryDirectory;
    }

    @Override
    public NodeClient install(NodeVersion version) {
        String artifactName = String.join("-", "node", os, arch);
        Path homeDirectory = localRepositoryDirectory.resolve(Paths.get("io", "linguarobot", artifactName, version.toString()));
        Path stateFile = homeDirectory.resolve(".state");
        if (!isInstallationCompleted(stateFile)) {
            try {
                Files.createDirectories(homeDirectory);
            } catch (IOException e) {
                throw new NodeInstallationException("Failed to create directory structure for Node.js in the local " +
                        "Maven repository");
            }

            try (FileChannel fileChannel = FileChannel.open(stateFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                fileChannel.lock();
                ByteBuffer buffer = ByteBuffer.allocate(1);
                if (fileChannel.read(buffer) == -1) {
                    logger.info("Node.js {} wasn't found in the local Maven repository. It will be downloaded " +
                            "from {}", version, BASE_DOWNLOAD_URL);
                    Files.walk(homeDirectory)
                            .filter(file -> !file.equals(stateFile) && !file.equals(homeDirectory))
                            .forEach(file -> {
                                try {
                                    Files.deleteIfExists(file);
                                } catch (IOException e) {
                                    throw new NodeInstallationException(e);
                                }
                            });
                    download(version, os, arch, homeDirectory);
                    buffer.put(INSTALLED_STATE);
                    buffer.flip();
                    fileChannel.write(buffer);
                    logger.info("The Node.js {} has been successfully installed to {}", version, homeDirectory);
                }
            } catch (IOException e) {
                throw new NodeInstallationException(e);
            }
        }

        return toNodeProcessRunner(homeDirectory);
    }


    private boolean isInstallationCompleted(Path state) {
        try {
            return Files.exists(state) && Files.readAllBytes(state).length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Downloads the required Node.js version (taking into account the operating system and its architecture) to the
     * specified directory.
     *
     * @param version Node.js version
     * @param os operating system name
     * @param arch operating system architecture
     * @param destination destination directory
     */
    protected abstract void download(NodeVersion version, String os, String arch, Path destination);

    /**
     * Creates a {@code NodeProcessRunner}.
     *
     * @param homeDirectory the directory where Node.js is installed
     * @return a new instance of a {@code NodeProcessRunner}.
     */
    protected abstract NodeProcessRunner toNodeProcessRunner(Path homeDirectory);
}
