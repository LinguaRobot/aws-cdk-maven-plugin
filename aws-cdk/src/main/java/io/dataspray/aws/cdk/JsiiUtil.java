package io.dataspray.aws.cdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import software.amazon.jsii.Jsii;
import software.amazon.jsii.JsiiEngine;
import software.amazon.jsii.JsiiExposer;
import software.amazon.jsii.JsiiObject;
import software.amazon.jsii.JsiiObjectRef;

import java.lang.reflect.Constructor;

public class JsiiUtil {

    @SneakyThrows
    public static <T> T getProperty(Object jsiiObject, String propertyName, Class<T> clazz) {
        // Get property by name similar to Kernel.get()
        final JsiiEngine engine = JsiiExposer.getEngineFor(jsiiObject);
        final JsiiObjectRef objRef = engine.nativeToObjRef(jsiiObject);
        final ObjectNode propertyObjectRefNode = (ObjectNode) engine.getClient().getPropertyValue(objRef, propertyName);

        // Ensure we are requesting the correct fully qualified name of our class
        // This is a workaround for a bug in JSII
        Jsii jsiiAnnotation = clazz.getAnnotation(Jsii.class);
        propertyObjectRefNode.set(JsiiExposer.getInterfacesToken(), new ObjectMapper()
                .createArrayNode()
                .add(jsiiAnnotation.fqn()));
        JsiiObjectRef propertyObjectRef = JsiiObjectRef.parse(propertyObjectRefNode);

        // Find out suitable constructor
        Jsii.Proxy proxyAnnotation = clazz.getAnnotation(Jsii.Proxy.class);
        Class<? extends JsiiObject> clazzImpl = proxyAnnotation.value();
        Constructor<? extends JsiiObject> clazzImplConstructor = clazzImpl.getDeclaredConstructor(JsiiObjectRef.class);
        clazzImplConstructor.setAccessible(true);

        // Finally create our instance
        return (T) clazzImplConstructor.newInstance(propertyObjectRef);
    }
}
