package io.linguarobot.aws.cdk.maven.it;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.HashMap;
import java.util.Map;


public class Function implements RequestHandler<Map<String, Object>, GatewayResponse> {

    @Override
    public GatewayResponse handleRequest(Map<String, Object> input, Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "plain/text");

        GatewayResponse response = new GatewayResponse();
        response.setStatusCode(200);
        response.setBody("SUCCESS");
        response.setHeaders(headers);
        return response;
    }

}
