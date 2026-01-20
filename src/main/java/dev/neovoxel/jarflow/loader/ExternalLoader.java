package dev.neovoxel.jarflow.loader;

import dev.neovoxel.jarflow.JarFlow;
import lombok.Getter;
import lombok.Setter;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public class ExternalLoader {

    private static Instrumentation instrumentation;

    @Setter
    @Getter
    private static ClassLoader classLoader = JarFlow.class.getClassLoader();

    private static final Logger logger = LoggerFactory.getLogger("JarFlow Loader");

    public static void init() {
        if (instrumentation != null) return;

        try {
            instrumentation = ByteBuddyAgent.install();
        } catch (Exception e) {
            logger.error("Failed to initialize ExternalLoader via ByteBuddyAgent.");
            throw new RuntimeException("Cannot access Instrumentation for dynamic loading", e);
        }
    }

    public void load(File file) {
        if (instrumentation == null) {
            init();
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }
        try (JarFile jarFile = new JarFile(file)) {
            instrumentation.appendToSystemClassLoaderSearch(jarFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to append JAR to class path: " + file.getName(), e);
        }
    }
}