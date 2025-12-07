package dev.neovoxel.jarflow.util;

import dev.neovoxel.jarflow.JarFlow;
import lombok.Getter;
import lombok.Setter;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ExternalLoader implements Loader {

    private static MethodHandle addURLMethodHandle;

    @Setter
    @Getter
    private static ClassLoader classLoader = JarFlow.class.getClassLoader();

    private static final Object theUnsafe;

    static {
        Object unsafe = null; // Used to make theUnsafe field final

        Class<?> unsafeClass = null;
        try {
            unsafeClass = Class.forName("sun.misc.Unsafe");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // getDeclaredField("theUnsafe") is not used to avoid breakage on JVMs with changed field name
        for (Field f : unsafeClass.getDeclaredFields()) {
            try {
                if (f.getType() == unsafeClass && Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    unsafe = f.get(null);
                }
            } catch (Exception ignored) {
            }
        }
        theUnsafe = unsafe;
    }

    public static void init() {
        try {
            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);

            try {
                openUrlClassLoaderModule();
            } catch (Exception ignored) {
            }

            try {
                addURLMethod.setAccessible(true);
            } catch (Exception exception) {
                if (exception.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
                    if (theUnsafe != null)
                        try {
                            addURLMethodHandle = getPrivilegedMethodHandle(addURLMethod).bindTo(classLoader);
                            return; // We're done
                        } catch (Exception ignored) {
                            addURLMethodHandle = null; // Just to be sure the field is set to null
                        }
                    try {
                        addOpensWithAgent();
                        addURLMethod.setAccessible(true);
                    } catch (Exception e) {
                        System.err.println("Cannot access URLClassLoader#addURL(URL), if you are using Java 9+ try to add the following option to your java command: --add-opens java.base/java.net=ALL-UNNAMED");
                        throw new RuntimeException("Cannot access URLClassLoader#addURL(URL)", e);
                    }
                } else {
                    throw new RuntimeException("Cannot set accessible URLClassLoader#addURL(URL)", exception);
                }
            }
            addURLMethodHandle = MethodHandles.lookup().unreflect(addURLMethod).bindTo(classLoader);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void openUrlClassLoaderModule() throws Exception {
        Class<?> moduleClass = Class.forName("java.lang.Module");
        Method getModuleMethod = Class.class.getMethod("getModule");
        Method addOpensMethod = moduleClass.getMethod("addOpens", String.class, moduleClass);

        Object urlClassLoaderModule = getModuleMethod.invoke(URLClassLoader.class);
        Object thisModule = getModuleMethod.invoke(JarFlow.class);

        addOpensMethod.invoke(urlClassLoaderModule, URLClassLoader.class.getPackage().getName(), thisModule);
    }

    private static void addOpensWithAgent() throws Exception {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        Method redefineModule = instrumentation.getClass().getDeclaredMethod("redefineModule", Class.forName("java.lang.Module"), Set.class, Map.class, Map.class, Set.class, Map.class);
        Method getModule = Class.class.getDeclaredMethod("getModule");
        Map<String, Set<?>> toOpen = Collections.singletonMap("java.net", Collections.singleton(getModule.invoke(JarFlow.class)));
        redefineModule.invoke(instrumentation, getModule.invoke(URLClassLoader.class), Collections.emptySet(), Collections.emptyMap(), toOpen, Collections.emptySet(), Collections.emptyMap());
    }

    private static MethodHandle getPrivilegedMethodHandle(Method method) throws Exception {
        // Try to get a MethodHandle to URLClassLoader#addURL.
        // The Unsafe class is used to get a privileged MethodHandles.Lookup instance.

        // Looking for MethodHandles.Lookup#IMPL_LOOKUP private static field
        // getDeclaredField("IMPL_LOOKUP") is not used to avoid breakage on JVMs with changed field name
        for (Field trustedLookup : MethodHandles.Lookup.class.getDeclaredFields()) {
            if (trustedLookup.getType() != MethodHandles.Lookup.class || !Modifier.isStatic(trustedLookup.getModifiers()) || trustedLookup.isSynthetic())
                continue;

            try {
                Method staticFieldBase = theUnsafe.getClass().getDeclaredMethod("staticFieldBase", Field.class);
                Method staticFieldOffset = theUnsafe.getClass().getDeclaredMethod("staticFieldOffset", Field.class);
                Method getObject = theUnsafe.getClass().getDeclaredMethod("getObject", Object.class, long.class);
                MethodHandles.Lookup lookup = (MethodHandles.Lookup) getObject.invoke(theUnsafe,
                        staticFieldBase.invoke(theUnsafe, trustedLookup),
                        staticFieldOffset.invoke(theUnsafe, trustedLookup));
                return lookup.unreflect(method);
            } catch (Exception ignored) {
                // Unreflect went wrong, trying the next field
            }
        }

        // Every field has been tried
        throw new RuntimeException("Cannot get privileged method handle.");
    }

    public void load(File file) throws Throwable {
        addURLMethodHandle.invokeWithArguments(file.toURI().toURL());
    }

}