package io.linguarobot.aws.cdk.maven.process;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * A runner of external processes.
 */
public interface ProcessRunner {

    /**
     * Starts an external process using the given command.
     *
     * @param command the command to execute
     * @throws ProcessExecutionException in case the process fails or returns an exit code that is different from zero.
     * @return the output of the process (including error).
     */
    default String run(List<String> command) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProcessContext processContext = ProcessContext.builder()
                .withOutput(output)
                .build();
        run(command, processContext);
        return output.toString();
    }

    /**
     * Starts an external process using the given command.
     *
     * @param command the command to execute
     * @param processContext the process context
     * @throws ProcessExecutionException in case the process fails or returns an exit code that is different from zero.
     * @return the process exit code
     */
    int run(List<String> command, ProcessContext processContext);

}
