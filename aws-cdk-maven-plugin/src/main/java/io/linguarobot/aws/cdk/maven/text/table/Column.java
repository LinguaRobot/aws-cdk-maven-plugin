package io.linguarobot.aws.cdk.maven.text.table;

import com.google.common.collect.ImmutableList;
import io.linguarobot.aws.cdk.maven.text.Ansi;

import javax.annotation.Nullable;
import java.util.List;

public class Column extends Cell {

    private static final List<Ansi.Parameter> DEFAULT_COLUMN_FORMAT = ImmutableList.of(Ansi.Decoration.BOLD);

    private final int width;

    protected Column(String heading, int width, @Nullable Alignment alignment, List<Ansi.Parameter> format) {
        super(heading, alignment != null ? alignment : Alignment.LEFT, !format.isEmpty() ? format : DEFAULT_COLUMN_FORMAT);
        this.width = width;
    }

    public int getWidth() {
        return width;
    }

    public String getHeading() {
        return getValue();
    }

    public static Column of(String heading, int width, Alignment alignment, Ansi.Parameter... parameters) {
        return new Column(heading, width, alignment, ImmutableList.copyOf(parameters));
    }

    public static Column of(String heading, int width, Ansi.Parameter... parameters) {
        return new Column(heading, width, null, ImmutableList.copyOf(parameters));
    }

    @Override
    public String toString() {
        return "Column{" +
                "heading='" + getHeading() + '\'' +
                ", width='" + getWidth() + '\'' +
                ", alignment=" + getAlignment() +
                ", ansiParameters=" + getAnsiParameters() +
                '}';
    }
}
