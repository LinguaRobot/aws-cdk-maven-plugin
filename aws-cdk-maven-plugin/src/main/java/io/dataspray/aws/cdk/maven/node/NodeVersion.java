package io.dataspray.aws.cdk.maven.node;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents Node.js version.
 */
public class NodeVersion implements Comparable<NodeVersion> {

    private static final Comparator<NodeVersion> COMPARATOR = Comparator.comparing(NodeVersion::getMajorVersion)
            .thenComparing(NodeVersion::getMinorVersion)
            .thenComparing(NodeVersion::getRevisionVersion);

    private final int[] versions;

    private NodeVersion(int[] versions) {
        this.versions = versions;
    }

    /**
     * @return major version number
     */
    public int getMajorVersion() {
        return versions[0];
    }

    /**
     * @return minor version number
     */
    public int getMinorVersion() {
        return versions[1];
    }

    /**
     * @return revision version number
     */
    public int getRevisionVersion() {
        return versions[2];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeVersion that = (NodeVersion) o;
        return Arrays.equals(versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(versions);
    }

    @Override
    public String toString() {
        return Arrays.stream(versions)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(".", "v", ""));
    }

    /**
     * @param version version to parse
     * @return an {@code Optional} with a {@code NodeVersion} representing the version of Node.js parsed from the given
     * value or an empty {@code Optional} in case the given value cannot be parsed.
     */
    public static Optional<NodeVersion> parse(String version) {
        version = version.trim();
        if (!version.startsWith("v")) {
            return Optional.empty();
        }

        try {
            int[] versions = Arrays.stream(version.substring(1).split("\\."))
                    .mapToInt(Integer::parseInt)
                    .toArray();

            return versions.length == 3 ? Optional.of(new NodeVersion(versions)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Creates {@code NodeVersion} from the given versions.
     * @param major major version number
     * @param minor minor version number
     * @param revision revision version number
     * @return node version
     */
    public static NodeVersion of(int major, int minor, int revision) {
        return new NodeVersion(new int[]{major, minor, revision});
    }

    @Override
    public int compareTo(@NotNull NodeVersion nodeVersion) {
        return COMPARATOR.compare(this, nodeVersion);
    }
}
