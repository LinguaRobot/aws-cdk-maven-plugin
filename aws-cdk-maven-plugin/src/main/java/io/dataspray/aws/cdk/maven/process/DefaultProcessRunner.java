package io.dataspray.aws.cdk.maven.process;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class DefaultProcessRunner implements ProcessRunner {

    private final File defaultWorkingDirectory;
    private final Executor executor;

    public DefaultProcessRunner(File defaultWorkingDirectory) {
        this.defaultWorkingDirectory = defaultWorkingDirectory;
        this.executor = createExecutor();
    }

    private static Executor createExecutor() {
        DefaultExecutor executor = new DefaultExecutor();
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        executor.setExitValue(0);
        return executor;
    }

    @Override
    public int run(List<String> command, ProcessContext processContext) {
        CommandLine commandLine =  toCommandLine(command);

        File workingDirectory = processContext.getWorkingDirectory().orElse(defaultWorkingDirectory);
        executor.setWorkingDirectory(workingDirectory);
        OutputStream output = processContext.getOutput().orElse(System.out);
        executor.setStreamHandler(new PumpStreamHandler(output));

        Map<String, String> environment = processContext.getEnvironment().orElse(null);
        try {
            return executor.execute(commandLine, environment);
        } catch (ExecuteException e) {
            throw new ProcessExecutionException(command, e.getExitValue(), e.getCause());
        } catch (IOException e) {
            throw new ProcessExecutionException(command, e);
        }
    }

    private CommandLine toCommandLine(List<String> command) {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("The executable is missing");
        }

        CommandLine commandLine = new CommandLine(command.get(0));
        IntStream.range(1, command.size())
                .forEach(i -> commandLine.addArgument(command.get(i)));
        return commandLine;
    }

}
