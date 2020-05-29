package io.linguarobot.aws.cdk.maven.it;

import static spark.Spark.get;
import static spark.SparkBase.setPort;

public class RestApp {

    public static void main(String[] args) {
        setPort(8080);
        get("/", (request, response) -> "SUCCESS");
    }

}
