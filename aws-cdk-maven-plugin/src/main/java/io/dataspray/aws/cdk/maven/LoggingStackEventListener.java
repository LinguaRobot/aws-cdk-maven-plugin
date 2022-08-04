package io.dataspray.aws.cdk.maven;

import com.google.common.collect.ImmutableList;
import io.dataspray.aws.cdk.maven.text.Ansi;
import io.dataspray.aws.cdk.maven.text.table.Cell;
import io.dataspray.aws.cdk.maven.text.table.Column;
import io.dataspray.aws.cdk.maven.text.table.TableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.ResourceStatus;
import software.amazon.awssdk.services.cloudformation.model.StackEvent;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Stack event listener that logs new stack events in form of a table.
 */
public class LoggingStackEventListener implements Consumer<StackEvent> {

    private static final Logger logger = LoggerFactory.getLogger(LoggingStackEventListener.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<Column> COLUMNS = ImmutableList.of(
            Column.of("Timestamp", 20),
            Column.of("Logical ID", 32),
            Column.of("Status", 32),
            Column.of("Status Reason", 64)
    );

    private final TableWriter tableWriter;

    public LoggingStackEventListener() {
        this.tableWriter = TableWriter.of(line -> logger.info(line.trim()), COLUMNS);
    }

    @Override
    public void accept(StackEvent event) {
        Ansi.Color color;
        if (event.resourceStatus() == ResourceStatus.UNKNOWN_TO_SDK_VERSION) {
            color = Ansi.Color.BLACK;
        } else {
            String status = event.resourceStatus().toString();
            if (status.endsWith("_IN_PROGRESS")) {
                color = Ansi.Color.BLUE;
            } else if (status.endsWith("_FAILED")) {
                color = Ansi.Color.RED;
            } else {
                color = Ansi.Color.GREEN;
            }
        }

        Cell statusReason = Optional.ofNullable(event.resourceStatusReason())
                .map(reason -> Cell.of(reason, color))
                .orElse(Cell.blank());

        List<Cell> row = ImmutableList.of(
                Cell.of(ZonedDateTime.from(event.timestamp().atZone(ZoneId.systemDefault())).format(DATE_TIME_FORMATTER)),
                Cell.of(event.logicalResourceId()),
                Cell.of(event.resourceStatusAsString(), color),
                statusReason
        );

        tableWriter.print(row);
    }
}
