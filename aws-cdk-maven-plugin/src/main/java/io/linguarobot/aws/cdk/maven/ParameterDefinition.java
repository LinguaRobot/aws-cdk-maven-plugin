package io.linguarobot.aws.cdk.maven;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents CloudFormation stack parameter.
 */
public class ParameterDefinition {

    private final String name;
    private final String defaultValue;

    public ParameterDefinition(String name, @Nullable String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getDefaultValue() {
        return Optional.ofNullable(defaultValue);
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
