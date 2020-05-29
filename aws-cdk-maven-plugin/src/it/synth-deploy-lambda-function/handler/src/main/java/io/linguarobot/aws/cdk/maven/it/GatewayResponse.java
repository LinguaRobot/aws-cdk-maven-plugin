package io.linguarobot.aws.cdk.maven.it;

import java.util.Map;


public class GatewayResponse {

    private int statusCode;
    private String body;
    private Map<String, String> headers;

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public String toString() {
        return "Response{" +
                "body='" + body + '\'' +
                ", headers=" + headers +
                '}';
    }
}
