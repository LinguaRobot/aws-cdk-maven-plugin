package io.linguarobot.aws.cdk.maven.node;

import io.linguarobot.aws.cdk.maven.process.ProcessRunner;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * An abstract Node.js installer for unix-like operation systems.
 */
public abstract class AbstractUnixNodeInstaller implements NodeInstaller {

    private static final Logger logger = LoggerFactory.getLogger(AbstractUnixNodeInstaller.class);

    private final ProcessRunner processRunner;
    private final Path localRepositoryDirectory;

    protected AbstractUnixNodeInstaller(ProcessRunner processRunner, Path localRepositoryDirectory) {
        this.processRunner = processRunner;
        this.localRepositoryDirectory = localRepositoryDirectory;
    }

    @Override
    public NodeClient install(NodeVersion version) {
        String osName = getOperationSystemName();
        String arch = getArch();
        String artifactName = String.join("-", "node", osName, arch);
        Path homeDirectory = localRepositoryDirectory.resolve(Paths.get("io", "linguarobot", artifactName, version.toString()));
        if (!Files.exists(homeDirectory) || !Files.exists(homeDirectory.resolve("bin/node"))) {
            logger.info("Node.js {} wasn't found in the local Maven repository. Installing Node.js {}", version, version);
            try {
                Files.createDirectories(homeDirectory);
            } catch (IOException e) {
                throw new NodeInstallationException("Failed to create directory structure for Node.js in the local Maven " +
                        "repository");
            }

            String filename = String.join("-", "node", version.toString(), osName, arch) + ".tar.gz";
            String downloadUrl = String.join("/", BASE_DOWNLOAD_URL, "dist", version.toString(), filename);
            logger.info("Downloading and installing Node.js {} from {}", version, downloadUrl);
            try (
                    BufferedInputStream in = new BufferedInputStream(new URL(downloadUrl).openStream());
                    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(in))
            ) {
                TarArchiveEntry tarEntry;
                while ((tarEntry = tarArchiveInputStream.getNextTarEntry()) != null) {
                    Path tarEntryPath = Paths.get(tarEntry.getName());
                    if (tarEntryPath.getNameCount() > 1) {
                        Path path = homeDirectory.resolve(tarEntryPath.subpath(1, tarEntryPath.getNameCount()));
                        if (tarEntry.isSymbolicLink()) {
                            Files.createSymbolicLink(path, Paths.get(tarEntry.getLinkName()));
                        } else {
                            Set<PosixFilePermission> filePermissions = getFilePermissions(tarEntry.getMode());
                            FileAttribute<?> permissionFileAttribute = PosixFilePermissions.asFileAttribute(filePermissions);
                            if (tarEntry.isDirectory()) {
                                Files.createDirectory(path, permissionFileAttribute);
                            } else {
                                Files.createFile(path, permissionFileAttribute);
                                try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(path.toFile()))) {
                                    IOUtils.copy(tarArchiveInputStream, outputStream);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new NodeInstallationException(e);
            }

            logger.info("The Node.js {} has been successfully installed in {}", version, homeDirectory);
        }

        Path path = homeDirectory.resolve("bin/node");
        Path node = path.resolve("node");
        Path npmBinDirectory = homeDirectory.resolve("lib/node_modules/npm/bin");
        Path npmCli = npmBinDirectory.resolve("npm-cli.js");
        Path npxCli = npmBinDirectory.resolve("npx-cli.js");
        return new NodeProcessRunner(processRunner, path, node, npmCli, npxCli);
    }

    private static Set<PosixFilePermission> getFilePermissions(int mode) {
        StringBuilder permissions = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            int shift = (2 - i) * 3;
            permissions.append(getClassPermissions((mode >> shift) & 7));
        }

        return PosixFilePermissions.fromString(permissions.toString());
    }

    private static String getClassPermissions(int p) {
        if (p < 0 || p > 7) {
            throw new IllegalArgumentException("Invalid class permission: " + p);
        }

        return new String(new char[]{
                (p & 4) != 0 ? 'r' : '-',
                (p & 2) != 0 ? 'w' : '-',
                (p & 1) != 0 ? 'x' : '-'
        });
    }

    protected abstract String getArch();

    protected abstract String getOperationSystemName();

}
