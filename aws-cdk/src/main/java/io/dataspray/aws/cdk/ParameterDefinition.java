package io.dataspray.aws.cdk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Represents CloudFormation stack parameter.
 */
public class ParameterDefinition {

    private final String name;
    private final Object defaultValue;

    public ParameterDefinition(String name, @Nullable Object defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nullable
    public Object getDefaultValue() {
        return defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterDefinition that = (ParameterDefinition) o;
        return name.equals(that.name) &&
                Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, defaultValue);
    }

    @Override
    public String toString() {
        return "ParameterDefinition{" +
                "name='" + name + '\'' +
                ", defaultValue='" + defaultValue + '\'' +
                '}';
    }
}
