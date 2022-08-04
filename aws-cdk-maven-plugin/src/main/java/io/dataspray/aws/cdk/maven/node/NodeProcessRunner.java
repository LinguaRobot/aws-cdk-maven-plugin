package io.dataspray.aws.cdk.maven.node;

import com.google.common.collect.ImmutableList;
import io.dataspray.aws.cdk.maven.process.ProcessContext;
import io.dataspray.aws.cdk.maven.process.ProcessRunner;

import java.nio.file.Path;
import java.util.List;

public class NodeProcessRunner implements NodeClient {

    private final ProcessRunner processRunner;
    private final Path path;
    private final String node;
    private final String npmCli;
    private final String npxCli;

    public NodeProcessRunner(ProcessRunner processRunner, Path path, Path node, Path npmCli, Path npxCli) {
        this.processRunner = processRunner;
        this.path = path;
        this.node = node.toString();
        this.npmCli = npmCli.toString();
        this.npxCli = npxCli.toString();
    }

    @Override
    public int run(List<String> command, ProcessContext processContext) {
        return processRunner.run(prepend(node, command), processContext);
    }

    @Override
    public ProcessRunner npm() {
        return (command, context) -> processRunner.run(concat(ImmutableList.of(node, npmCli), command), context);
    }

    @Override
    public ProcessRunner npx() {
        return (command, context) -> processRunner.run(concat(ImmutableList.of(node, npxCli), command), context);
    }

    @Override
    public Path getPath() {
        return path;
    }

    private <T> List<T> concat(List<T> head, List<T> tail) {
        if (head.isEmpty() || tail.isEmpty()) {
            return head.isEmpty() ? tail : head;
        }

        return ImmutableList.<T>builder()
                .addAll(head)
                .addAll(tail)
                .build();
    }

    private <T> List<T> prepend(T value, List<T> values) {

        return ImmutableList.<T>builder()
                .add(value)
                .addAll(values)
                .build();
    }

}
