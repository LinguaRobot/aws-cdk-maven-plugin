package io.dataspray.aws.cdk.maven.text.table;

import com.google.common.collect.ImmutableList;
import io.dataspray.aws.cdk.maven.text.Ansi;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TableWriter {

    private final List<Column> columns;
    private final Consumer<String> writer;
    private int rowsPrinted;

    private TableWriter(Consumer<String> writer, List<Column> columns) {
        this.columns = columns;
        this.writer = writer;
        this.rowsPrinted = 0;
    }

    public void print(Object[] row) {
        List<Cell> cells = Arrays.stream(row)
                .map(String::valueOf)
                .map(value -> Cell.of(value))
                .collect(Collectors.toList());
        print(cells);
    }

    public void print(Cell... row) {
        print(Arrays.asList(row));
    }

    public void print(List<Cell> row) {
        if (row.size() != columns.size()) {
            throw new IllegalArgumentException("The number of cells in the row is not the same as the number of " +
                    "columns defined for the table");
        }
        if (rowsPrinted == 0) {
            printHorizontalBorder();
            printRow(columns);
        }

        printRow(row);
        rowsPrinted++;
    }

    private void printRow(List<? extends Cell> row) {
        int height = IntStream.range(0, columns.size())
                .map(i -> ceilDiv(row.get(i).getValue().length(), columns.get(i).getWidth()))
                .max()
                .orElse(1);

        for (int i = 0; i < height; i++) {
            StringBuilder line = new StringBuilder();
            line.append('|');
            for (int j = 0; j < columns.size(); j++) {
                Column column = columns.get(j);
                Cell cell = row.get(j);
                int cellHeight = ceilDiv(cell.getValue().length(), column.getWidth());
                int topPadding = (height - cellHeight) / 2;
                int bottomPadding = ceilDiv(height - cellHeight, 2);
                line.append(' ');
                if (i < topPadding || i >= height - bottomPadding) {
                    append(line, ' ', column.getWidth());
                } else {
                    int start = (i - topPadding) * column.getWidth();
                    int end = Math.min(start + column.getWidth(), cell.getValue().length());
                    String textValue = cell.getValue().substring(start, end).trim();

                    int leftPadding;
                    int rightPadding;
                    Alignment alignment = cell.getAlignment()
                            .orElseGet(() -> column.getAlignment().orElse(Alignment.LEFT));
                    if (alignment == Alignment.LEFT) {
                        leftPadding = 0;
                        rightPadding = column.getWidth() - textValue.length();
                    } else if (alignment == Alignment.CENTER) {
                        leftPadding = ceilDiv(column.getWidth() - textValue.length(), 2);
                        rightPadding = (column.getWidth() - textValue.length()) / 2;
                    } else {
                        leftPadding = column.getWidth() - textValue.length();
                        rightPadding = 0;
                    }

                    append(line, ' ', leftPadding);
                    line.append(Ansi.format(cell.getAnsiParameters()));
                    line.append(textValue);
                    line.append(Ansi.clear());
                    append(line, ' ', rightPadding);
                }
                line.append(' ').append('|');
            }
            line.append('\n');
            writer.accept(line.toString());
        }

        printHorizontalBorder();
    }

    private void printHorizontalBorder() {
        StringBuilder line = new StringBuilder();
        appendHorizontalBorder(line);
        writer.accept(line.toString());
    }

    private void appendHorizontalBorder(StringBuilder out) {
        out.append('+');
        for (Column column : columns) {
            append(out, '-', column.getWidth() + 2);
            out.append('+');
        }
        out.append('\n');
    }

    private void append(StringBuilder out, char c, int n) {
        for (int i = 0; i < n; i++) {
            out.append(c);
        }
    }

    private int ceilDiv(int x, int y) {
        return (x + (x >= 0 ? 1 : -1) * (y - 1)) / y;
    }

    public static TableWriter of(Consumer<String> lineWriter, Column... columns) {
        return of(lineWriter, Arrays.asList(columns));
    }

    public static TableWriter of(Consumer<String> lineWriter, List<Column> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one column must be defined");
        }
        return new TableWriter(lineWriter, ImmutableList.copyOf(columns));
    }

}
