package io.linguarobot.aws.cdk.maven.it;

import static spark.Spark.get;
import static spark.Spark.port;

public class RestApp {

    public static void main(String[] args) {
        port(8080);
        get("/", (request, response) -> "SUCCESS");
    }

}
