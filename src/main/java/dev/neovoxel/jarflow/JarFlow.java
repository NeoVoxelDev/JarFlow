package dev.neovoxel.jarflow;

import dev.neovoxel.jarflow.dependency.Dependency;
import dev.neovoxel.jarflow.repository.Repository;
import dev.neovoxel.jarflow.util.DependencyDownloader;
import dev.neovoxel.jarflow.util.ExternalLoader;
import dev.neovoxel.jarflow.util.Loader;
import dev.neovoxel.jarflow.util.MetadataParser;
import lombok.Getter;
import lombok.Setter;
import me.lucko.jarrelocator.JarRelocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarFlow {

    private static final List<Repository> repositories = new ArrayList<>();

    private static Map<Dependency, Repository> dependencies = new HashMap<>();

    private static final List<Dependency> loaded = new ArrayList<>();

    @Setter
    private static int threadCount = 4;

    @Setter
    private static File libDir = new File("libs");

    @Setter
    @Getter
    private static Loader loader = new ExternalLoader();

    private static Logger logger = LoggerFactory.getLogger("JarFlow");

    static {
        ExternalLoader.init();
    }


    public static void addRepository(Repository repository) {
        repositories.add(repository);
    }

    public static void addRepositories(Collection<Repository> repositories) {
        JarFlow.repositories.addAll(repositories);
    }

    public static void addRepositories(Repository... repositories) {
        for (Repository repository : repositories) {
            addRepository(repository);
        }
    }

    public static void loadDependency(Dependency dependency) throws Throwable {
        logger.info("Loading dependency: {}", dependency.toString());
        dependencies = MetadataParser.resolve(Collections.singletonList(dependency), dependencies, repositories);
        for (Dependency dependency2 : dependencies.keySet()) {
            if (loaded.contains(dependency2)) {
                continue;
            }
            String fileName = dependency2.getArtifactId() + "-" + dependency2.getVersion();
            Path path = libDir.toPath()
                    .resolve(dependency2.getGroupId())
                    .resolve(dependency2.getArtifactId())
                    .resolve(dependency2.getVersion())
                    .resolve(fileName + ".jar");
            if (!hasDownloaded(dependency2)) DependencyDownloader.download(JarFlow.dependencies.get(dependency2), dependency2, libDir, threadCount);
            if (dependency.getRelocations().isEmpty()) {
                loader.load(path.toFile());
            } else {
                fileName += "-relocated.jar";
                JarRelocator jarRelocator = new JarRelocator(path.toFile(), path.resolve("../" + fileName).toFile(), dependency.getRelocations());
                jarRelocator.run();
                loader.load(path.resolve("../" + fileName).toFile());
            }
            loaded.add(dependency2);
        }
    }
    
    public static List<Class<?>> searchClasses(String prefix) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        for (Dependency dependency : dependencies.keySet()) {
            String fileName = dependency.getArtifactId() + "-" + dependency.getVersion();
            Path path = libDir.toPath()
                    .resolve(dependency.getGroupId())
                    .resolve(dependency.getArtifactId())
                    .resolve(dependency.getVersion())
                    .resolve(fileName + ".jar");
            if (!path.toFile().exists()) {
                path = libDir.toPath()
                        .resolve(dependency.getGroupId())
                        .resolve(dependency.getArtifactId())
                        .resolve(dependency.getVersion())
                        .resolve(fileName + "-relocated.jar");
                if (!path.toFile().exists()) {
                    continue;
                }
            }
            File jarFile = path.toFile();
            // 读取 jarFile 所有类
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName()
                                .replace("/", ".")
                                .replace(".class", "");
                        if (className.startsWith(prefix)) classes.add(Class.forName(className));
                    }
                }
            }
        }
        return classes;
    }

    public static List<Class<?>> searchClasses(Dependency dependency, String prefix) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        String fileName = dependency.getArtifactId() + "-" + dependency.getVersion();
        Path path = libDir.toPath()
                .resolve(dependency.getGroupId())
                .resolve(dependency.getArtifactId())
                .resolve(dependency.getVersion())
                .resolve(fileName + ".jar");
        if (!path.toFile().exists()) {
            path = libDir.toPath()
                    .resolve(dependency.getGroupId())
                    .resolve(dependency.getArtifactId())
                    .resolve(dependency.getVersion())
                    .resolve(fileName + "-relocated.jar");
            if (!path.toFile().exists()) {
                return classes;
            }
        }
        File jarFile = path.toFile();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                            .replace("/", ".")
                            .replace(".class", "");
                    if (className.startsWith(prefix)) classes.add(Class.forName(className));
                }
            }
        }
        return classes;
    }

    public static void loadDependencies(Collection<Dependency> dependencies) throws Throwable {
        logger.info("Loading dependencies: {}", Arrays.toString(dependencies.toArray()));
        JarFlow.dependencies = MetadataParser.resolve(dependencies, JarFlow.dependencies, repositories);
        for (Dependency dependency : JarFlow.dependencies.keySet()) {
            if (loaded.contains(dependency)) {
                continue;
            }
            String fileName = dependency.getArtifactId() + "-" + dependency.getVersion();
            Path path = libDir.toPath()
                    .resolve(dependency.getGroupId())
                    .resolve(dependency.getArtifactId())
                    .resolve(dependency.getVersion())
                    .resolve(fileName + ".jar");
            if (!hasDownloaded(dependency)) DependencyDownloader.download(JarFlow.dependencies.get(dependency), dependency, libDir, threadCount);
            if (dependency.getRelocations().isEmpty()) {
                loader.load(path.toFile());
            } else {
                fileName += "-relocated.jar";
                JarRelocator jarRelocator = new JarRelocator(path.toFile(), path.resolve("../" + fileName).toFile(), dependency.getRelocations());
                jarRelocator.run();
                loader.load(path.resolve("../" + fileName).toFile());
            }
            loaded.add(dependency);
        }
    }

    private static boolean hasDownloaded(Dependency dependency) {
        Path path = libDir.toPath()
                .resolve(dependency.getGroupId())
                .resolve(dependency.getArtifactId())
                .resolve(dependency.getVersion())
                .resolve(dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar");
        if (path.toFile().exists()) {
            return true;
        }
        return false;
    }
}
