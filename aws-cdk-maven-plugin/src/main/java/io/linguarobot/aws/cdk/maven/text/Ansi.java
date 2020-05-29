package io.linguarobot.aws.cdk.maven.text;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class Ansi {

    private static final char ESC = 27; // Escape character used to start an ANSI code
    private static final String PREFIX = ESC + "[";
    private static final String SEPARATOR = ";";
    private static final String POSTFIX = "m";

    private static final String CLEAR = PREFIX + 0 + POSTFIX;

    public static String format(Parameter... parameters) {
        return format(Arrays.asList(parameters));
    }

    public static String format(Collection<Parameter> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }

        return parameters.stream()
                .map(Parameter::code)
                .map(String::valueOf)
                .collect(Collectors.joining(SEPARATOR, PREFIX, POSTFIX));
    }

    public static String clear() {
        return CLEAR;
    }

    public interface Parameter {

        int code();

    }

    public enum Color implements Parameter {

        BLACK(30), RED(31), GREEN(32), YELLOW(33), BLUE(34), MAGENTA(35), CYAN(36), WHITE(37),
        BRIGHT_BLACK(90), BRIGHT_RED(91), BRIGHT_GREEN(92), BRIGHT_YELLOW(93), BRIGHT_BLUE(94),
        BRIGHT_MAGENTA(95), BRIGHT_CYAN(96), BRIGHT_WHITE(97);

        private final int code;

        Color(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return this.code;
        }

    }

    public enum BackgroundColor implements Parameter {

        BLACK(40), RED(41), GREEN(42), YELLOW(43), BLUE(44), MAGENTA(45), CYAN(46), WHITE(47),
        BRIGHT_BLACK(100), BRIGHT_RED(101), BRIGHT_GREEN(102), BRIGHT_YELLOW(103), BRIGHT_BLUE(104),
        BRIGHT_MAGENTA(105), BRIGHT_CYAN(106), BRIGHT_WHITE(107);

        private final int code;

        BackgroundColor(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return this.code;
        }

    }

    public enum Decoration implements Parameter {

        CLEAR(0), BOLD(1), LIGHT(1), DARK(2), UNDERLINE(4), REVERSE(7), HIDDEN(8);

        private final int code;

        Decoration(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return this.code;
        }

    }
}
