package io.dataspray.aws.cdk.maven;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.aws.cdk.AssetMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StackDefinition {

    @Nonnull
    private final String stackName;

    @Nonnull
    private final Path templateFile;

    @Nonnull
    private final String environment;

    @Nullable
    private final Integer requiredToolkitStackVersion;

    @Nonnull
    private final Map<String, ParameterDefinition> parameters;

    @Nonnull
    private final Map<String, String> parameterValues;

    @Nonnull
    private final List<AssetMetadata> assets;

    @Nonnull
    private final Map<String, Map<String, Object>> resources;

    @Nonnull
    private final List<String> dependencies;

    private StackDefinition(@Nonnull String stackName,
                            @Nonnull Path templateFile,
                            @Nonnull String environment,
                            @Nullable Integer requiredToolkitStackVersion,
                            @Nullable Map<String, ParameterDefinition> parameters,
                            @Nullable Map<String, String> parameterValues, List<AssetMetadata> assets,
                            @Nullable Map<String, Map<String, Object>> resources,
                            @Nullable List<String> dependencies) {
        this.stackName = Objects.requireNonNull(stackName, "Stack name can't be null");
        this.templateFile = Objects.requireNonNull(templateFile, "Template file can't be null");
        this.environment = Objects.requireNonNull(environment, "Environment can't be null");
        this.requiredToolkitStackVersion = requiredToolkitStackVersion;
        this.parameters = parameters != null ? ImmutableMap.copyOf(parameters) : ImmutableMap.of();
        this.parameterValues = parameterValues != null ? ImmutableMap.copyOf(parameterValues) : ImmutableMap.of();
        this.assets = assets != null ? ImmutableList.copyOf(assets) : ImmutableList.of();
        this.resources = resources != null ? ImmutableMap.copyOf(resources) : ImmutableMap.of();
        this.dependencies = dependencies != null ? ImmutableList.copyOf(dependencies) : ImmutableList.of();
    }

    @Nonnull
    public String getStackName() {
        return stackName;
    }

    @Nonnull
    public Path getTemplateFile() {
        return templateFile;
    }

    @Nonnull
    public String getEnvironment() {
        return environment;
    }

    @Nullable
    public Integer getRequiredToolkitStackVersion() {
        return requiredToolkitStackVersion;
    }

    @Nonnull
    public Map<String, ParameterDefinition> getParameters() {
        return parameters;
    }

    @Nonnull
    public Map<String, String> getParameterValues() {
        return parameterValues;
    }

    @Nonnull
    public List<AssetMetadata> getAssets() {
        return assets;
    }

    @Nonnull
    public Map<String, Map<String, Object>> getResources() {
        return resources;
    }

    @Nonnull
    public List<String> getDependencies() {
        return dependencies;
    }

    @Override
    public String toString() {
        return "StackDefinition{" +
                "stackName='" + stackName + '\'' +
                ", templateFile=" + templateFile +
                ", environment='" + environment + '\'' +
                ", requiredToolkitStackVersion=" + requiredToolkitStackVersion +
                ", parameters=" + parameters +
                ", parameterValues=" + parameterValues +
                ", assets=" + assets +
                ", resources=" + resources +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String stackName;
        private Path templateFile;
        private String environment;
        private Integer requiredToolkitStackVersion;
        private Map<String, ParameterDefinition> parameters;
        private Map<String, String> parameterValues;
        private List<AssetMetadata> assets;
        private Map<String, Map<String, Object>> resources;
        private List<String> dependencies;

        private Builder() {
        }

        public Builder withStackName(@Nonnull String stackName) {
            this.stackName = stackName;
            return this;
        }

        public Builder withTemplateFile(@Nonnull Path templateFile) {
            this.templateFile = templateFile;
            return this;
        }

        public Builder withEnvironment(@Nonnull String environment) {
            this.environment = environment;
            return this;
        }

        public Builder withRequiredToolkitStackVersion(@Nullable Integer requiredToolkitStackVersion) {
            this.requiredToolkitStackVersion = requiredToolkitStackVersion;
            return this;
        }

        public Builder withParameters(@Nullable Map<String, ParameterDefinition> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder withParameterValues(@Nullable Map<String, String> parameterValues) {
            this.parameterValues = parameterValues;
            return this;
        }

        public Builder withAssets(@Nullable List<AssetMetadata> assets) {
            this.assets = assets;
            return this;
        }

        public Builder withResources(@Nullable Map<String, Map<String, Object>> resources) {
            this.resources = resources;
            return this;
        }

        public Builder withDependencies(@Nullable List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public StackDefinition build() {
            return new StackDefinition(stackName, templateFile, environment, requiredToolkitStackVersion, parameters,
                    parameterValues, assets, resources, dependencies);
        }
    }
}
