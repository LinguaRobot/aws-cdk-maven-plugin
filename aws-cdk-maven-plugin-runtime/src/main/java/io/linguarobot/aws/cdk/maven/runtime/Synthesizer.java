package io.linguarobot.aws.cdk.maven.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Optional;

/**
 * Synthesizes a cloud assembly from the CDK app class.
 *
 * The app class must either define a main method or extend {@code software.amazon.awscdk.core.App} class and have a
 * default constructor.
 */
public class Synthesizer {

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("The 'app' argument is missing");
        }

        try {
            run(args[0], Arrays.copyOfRange(args, 1, args.length));
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String appClassName, String[] args) throws Throwable {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> appClass = classLoader.loadClass(appClassName);
        MethodHandle mainMethod = lookupMainMethodHandle(appClass).orElse(null);
        if (mainMethod != null) {
            mainMethod.invoke((Object) args);
        } else {
            Class<?> appType = classLoader.loadClass("software.amazon.awscdk.core.App");
            if (!appType.isAssignableFrom(appClass)) {
                throw new IllegalArgumentException("The app class must either be an instance of App class or have a " +
                        "main method with appropriate signature which will synthesize the CloudFormation template");
            }

            MethodHandle constructor = lookupDefaultConstructor(appClass)
                    .orElseThrow(() -> new IllegalArgumentException("Cannot instantiate the application class " +
                            appClass + ". It must have a default constructor."));
            Object app = constructor.invoke();
            Class<?> cloudAssemblyType = classLoader.loadClass("software.amazon.awscdk.cxapi.CloudAssembly");
            MethodHandles.publicLookup().findVirtual(appClass, "synth", MethodType.methodType(cloudAssemblyType))
                    .bindTo(app)
                    .invoke();
        }
    }

    /**
     * Returns a {@code MethodHandle} for the main method ({@code public static void main(String[] args)}) if it's
     * defined in the given class or an empty {@code Optional} otherwise.
     */
    private static Optional<MethodHandle> lookupMainMethodHandle(Class<?> type) {
        try {
            return Optional.of(MethodHandles.publicLookup().findStatic(type, "main", MethodType.methodType(void.class, String[].class)));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return Optional.empty();
        }
    }


    /**
     * Returns a {@code MethodHandle} for the no-argument constructor defined in the given class or an empty
     * {@code Optional} if it doesn't have one.
     */
    private static Optional<MethodHandle> lookupDefaultConstructor(Class<?> type) {
        try {
            return Optional.of(MethodHandles.lookup().findConstructor(type, MethodType.methodType(void.class)));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return Optional.empty();
        }
    }
}
