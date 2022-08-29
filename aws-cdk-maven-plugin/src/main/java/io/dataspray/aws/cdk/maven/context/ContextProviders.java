package io.dataspray.aws.cdk.maven.context;

public final class ContextProviders {

    private ContextProviders() {
    }

    public static String buildEnvironment(String account, String region) {
        return "aws://" + account + "/" + region;
    }

}
