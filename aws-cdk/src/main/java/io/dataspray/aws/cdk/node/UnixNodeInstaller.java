package io.dataspray.aws.cdk.node;

import io.dataspray.aws.cdk.process.ProcessRunner;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
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
 * Node.js installer for unix-like operating systems.
 */
public class UnixNodeInstaller extends AbstractNodeInstaller {

    private static final Logger logger = LoggerFactory.getLogger(UnixNodeInstaller.class);

    private final ProcessRunner processRunner;

    public UnixNodeInstaller(ProcessRunner processRunner, Path localRepositoryDirectory, String os, String arch) {
        super(localRepositoryDirectory, os, arch);
        this.processRunner = processRunner;
    }

    protected void download(NodeVersion version, String os, String arch, Path destination) {
        if (!Files.isDirectory(destination)) {
            throw new IllegalArgumentException(destination + " is not a directory");
        }

        String filename = String.join("-", "node", version.toString(), os, arch + ".tar.gz");
        String url = String.join("/", BASE_DOWNLOAD_URL, "dist", version.toString(), filename);
        logger.info("Downloading Node.js {} from {}", version, url);
        try (
                BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
                TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(in))
        ) {
            TarArchiveEntry tarEntry;
            while ((tarEntry = tarArchiveInputStream.getNextTarEntry()) != null) {
                Path tarEntryPath = Paths.get(tarEntry.getName());
                if (tarEntryPath.getNameCount() > 1) {
                    Path path = destination.resolve(tarEntryPath.subpath(1, tarEntryPath.getNameCount()));
                    if (tarEntry.isSymbolicLink()) {
                        Files.createSymbolicLink(path, Paths.get(tarEntry.getLinkName()));
                    } else {
                        Set<PosixFilePermission> filePermissions = getFilePermissions(tarEntry.getMode());
                        FileAttribute<?> permissionFileAttribute = PosixFilePermissions.asFileAttribute(filePermissions);
                        if (tarEntry.isDirectory()) {
                            Files.createDirectories(path, permissionFileAttribute);
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
    }

    private Set<PosixFilePermission> getFilePermissions(int mode) {
        StringBuilder permissions = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            int shift = (2 - i) * 3;
            permissions.append(getClassPermissions((mode >> shift) & 7));
        }

        return PosixFilePermissions.fromString(permissions.toString());
    }

    private String getClassPermissions(int p) {
        if (p < 0 || p > 7) {
            throw new IllegalArgumentException("Invalid class permission: " + p);
        }

        return new String(new char[]{
                (p & 4) != 0 ? 'r' : '-',
                (p & 2) != 0 ? 'w' : '-',
                (p & 1) != 0 ? 'x' : '-'
        });
    }

    protected NodeProcessRunner toNodeProcessRunner(Path homeDirectory) {
        Path path = homeDirectory.resolve("bin");
        Path node = path.resolve("node");
        Path npmBinDirectory = homeDirectory.resolve("lib/node_modules/npm/bin");
        Path npmCli = npmBinDirectory.resolve("npm-cli.js");
        Path npxCli = npmBinDirectory.resolve("npx-cli.js");
        return new NodeProcessRunner(processRunner, path, node, npmCli, npxCli);
    }

}
