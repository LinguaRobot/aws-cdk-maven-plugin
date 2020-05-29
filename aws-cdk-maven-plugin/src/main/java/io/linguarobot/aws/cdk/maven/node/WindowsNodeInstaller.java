package io.linguarobot.aws.cdk.maven.node;

import io.linguarobot.aws.cdk.maven.process.ProcessRunner;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Node.js installer for Windows.
 */
public class WindowsNodeInstaller implements NodeInstaller {

    private static final Logger logger = LoggerFactory.getLogger(WindowsNodeInstaller.class);

    private final ProcessRunner processRunner;
    private final Path localRepositoryPath;

    public WindowsNodeInstaller(ProcessRunner processRunner, Path localRepositoryPath) {
        this.processRunner = processRunner;
        this.localRepositoryPath = localRepositoryPath;
    }

    @Override
    public NodeClient install(NodeVersion version) {
        String arch = System.getenv("ProgramFiles(x86)") != null ? "x64" : "x86";
        String artifactName = String.join("-", "node", "win", arch);
        Path homeDirectory = localRepositoryPath.resolve(Paths.get("io", "linguarobot", artifactName, version.toString()));
        if (!Files.exists(homeDirectory) || !Files.exists(homeDirectory.resolve("node.exe"))) {
            logger.info("Node.js {} wasn't found in the local Maven repository. Installing Node.js {}", version, version);
            try {
                Files.createDirectories(homeDirectory);
            } catch (IOException e) {
                throw new NodeInstallationException("Failed to create directory structure for Node.js in the local Maven " +
                        "repository");
            }
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
                        Path path = homeDirectory.resolve(entryPath.subpath(1, entryPath.getNameCount()));
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
        logger.info("The Node.js {} has been successfully installed in {}", version, homeDirectory);

        Path node = homeDirectory.resolve("node.exe");
        Path npmBinDirectory = homeDirectory.resolve("node_modules/npm/bin");
        Path npmCli = npmBinDirectory.resolve("npm-cli.js");
        Path npxCli = npmBinDirectory.resolve("npx-cli.js");
        return new NodeProcessRunner(processRunner, homeDirectory, node, npmCli, npxCli);
    }

}
