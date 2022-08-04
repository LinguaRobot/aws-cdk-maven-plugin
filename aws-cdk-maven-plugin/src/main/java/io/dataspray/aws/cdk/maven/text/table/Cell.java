package io.dataspray.aws.cdk.maven.text.table;

import com.google.common.collect.ImmutableList;
import io.dataspray.aws.cdk.maven.text.Ansi;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class Cell {

    private static final Cell BLANK_CELL = new Cell("", null, ImmutableList.of());

    private final String value;
    private final Alignment alignment;
    private final List<Ansi.Parameter> ansiParameters;

    protected Cell(String value, @Nullable Alignment alignment, List<Ansi.Parameter> ansiParameters) {
        this.value = value;
        this.alignment = alignment;
        this.ansiParameters = ansiParameters;
    }

    public String getValue() {
        return value;
    }

    public Optional<Alignment> getAlignment() {
        return Optional.ofNullable(alignment);
    }

    public List<Ansi.Parameter> getAnsiParameters() {
        return ansiParameters;
    }

    @Override
    public String toString() {
        return "Cell{" +
                "value='" + value + '\'' +
                ", alignment=" + alignment +
                ", ansiParameters=" + ansiParameters +
                '}';
    }


    public static Cell of(String value, Ansi.Parameter... parameters) {
        return of(value, null, parameters);
    }

    public static Cell of(String value, @Nullable Alignment alignment, Ansi.Parameter... parameters) {
        return new Cell(value, alignment, ImmutableList.copyOf(parameters));
    }

    public static Cell blank() {
        return BLANK_CELL;
    }

}
