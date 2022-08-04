package io.dataspray.aws.cdk.maven.node;

import io.dataspray.aws.cdk.maven.process.ProcessRunner;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Node.js installer for Windows.
 */
public class WindowsNodeInstaller extends AbstractNodeInstaller {

    private static final Logger logger = LoggerFactory.getLogger(WindowsNodeInstaller.class);

    private final ProcessRunner processRunner;

    public WindowsNodeInstaller(ProcessRunner processRunner, Path localRepositoryPath) {
        super(localRepositoryPath, "win", System.getenv("ProgramFiles(x86)") != null ? "x64" : "x86");
        this.processRunner = processRunner;
    }

    @Override
    protected void download(NodeVersion version, String os, String arch, Path destination) {
        String filename = String.join("-", "node", version.toString(), "win", arch) + ".zip";
        String downloadUrl = String.join("/", BASE_DOWNLOAD_URL, "dist", version.toString(), filename);
        logger.info("Downloading Node.js {} from {}", version, downloadUrl);
        try (
                BufferedInputStream in = new BufferedInputStream(new URL(downloadUrl).openStream());
                ZipInputStream zipInputStream = new ZipInputStream(in);
        ) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = Paths.get(zipEntry.getName());
                if (entryPath.getNameCount() > 1) {
                    Path path = destination.resolve(entryPath.subpath(1, entryPath.getNameCount()));
                    if (zipEntry.isDirectory()) {
                        Files.createDirectory(path);
                    } else {
                        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(path.toFile()))) {
                            IOUtils.copy(zipInputStream, outputStream);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new NodeInstallationException(e);
        }
    }

    @Override
    protected NodeProcessRunner toNodeProcessRunner(Path homeDirectory) {
        Path node = homeDirectory.resolve("node.exe");
        Path npmBinDirectory = homeDirectory.resolve("node_modules/npm/bin");
        Path npmCli = npmBinDirectory.resolve("npm-cli.js");
        Path npxCli = npmBinDirectory.resolve("npx-cli.js");
        return new NodeProcessRunner(processRunner, homeDirectory, node, npmCli, npxCli);
    }

}
