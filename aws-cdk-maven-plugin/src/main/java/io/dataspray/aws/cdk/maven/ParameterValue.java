package io.dataspray.aws.cdk.maven;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Represents CloudFormation stack parameter value.
 */
public class ParameterValue {

    private static final ParameterValue UNCHANGED = new ParameterValue(null);

    private final String value;

    private ParameterValue(@Nullable String value) {
        this.value = value;
    }

    @Nullable
    public String get() {
        return value;
    }

    public boolean isUpdated() {
        return value != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterValue that = (ParameterValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        if (value == null) {
            return "unchanged()";
        }

        return "value(\"" + value + "\")";
    }

    /**
     * Creates a parameter value that will preserve its previous value after update.
     */
    public static ParameterValue unchanged() {
        return UNCHANGED;
    }

    /**
     * Creates a parameter value with the given {@code value}.
     */
    public static ParameterValue value(String value) {
        return new ParameterValue(Objects.requireNonNull(value, "The value cannot be null"));
    }
}
