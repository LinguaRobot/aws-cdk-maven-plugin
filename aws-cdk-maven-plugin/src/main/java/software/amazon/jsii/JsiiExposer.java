package software.amazon.jsii;

public class JsiiExposer {
    public static JsiiEngine getEngineFor(final Object instance) {
        return JsiiEngine.getEngineFor(instance);
    }

    public static String getInterfacesToken() {
        return JsiiObjectRef.TOKEN_INTERFACES;
    }
}
