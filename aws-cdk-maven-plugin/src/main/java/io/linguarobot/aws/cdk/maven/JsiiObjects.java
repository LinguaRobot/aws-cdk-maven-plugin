package io.linguarobot.aws.cdk.maven;

import software.amazon.jsii.Jsii;
import software.amazon.jsii.JsiiObjectMapper;
import software.amazon.jsii.JsiiObjectRef;
import software.amazon.jsii.NativeType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An utility class with helper methods for working with Jsii objects.
 */
public final class JsiiObjects {

    private static final Map<Class<?>, String> FULLY_QUALIFIED_NAMES = new ConcurrentHashMap<>();

    private JsiiObjects() {}

    /**
     * Returns true if the given {@code objectRef} is instance of {@code nativeType}.
     */
    public static boolean isInstanceOf(JsiiObjectRef objectRef, Class<?> nativeType) {
        String typeFqn = getTypeFqn(nativeType)
                .orElseThrow(() -> new IllegalArgumentException(nativeType.getSimpleName() + " is not a valid Jsii class"));
        return objectRef.getFqn().equals(typeFqn) || objectRef.getInterfaces().contains(typeFqn);
    }

    /**
     * Casts the given {@code objectRef} to the instance of {@code nativeType}.
     */
    public static <T> T cast(JsiiObjectRef objectRef, Class<T> nativeType) {
        if (!isInstanceOf(objectRef, nativeType)) {
            throw new IllegalArgumentException(objectRef.getFqn() + " cannot be represented as " + nativeType.getSimpleName());
        }
        return JsiiObjectMapper.treeToValue(objectRef.toJson(), NativeType.forClass(nativeType));
    }

    private static Optional<String> getTypeFqn(Class<?> nativeType) {
        String fqn = FULLY_QUALIFIED_NAMES.computeIfAbsent(
                nativeType,
                type -> Optional.ofNullable(type.getAnnotation(Jsii.class))
                        .map(Jsii::fqn)
                        .orElse(null)
        );
        return Optional.ofNullable(fqn);
    }
}
