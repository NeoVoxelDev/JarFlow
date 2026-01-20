package dev.neovoxel.jarflow;

import dev.neovoxel.jarflow.dependency.Dependency;
import dev.neovoxel.jarflow.pom.PomParser;
import dev.neovoxel.jarflow.repository.Repository;
import dev.neovoxel.jarflow.remote.DependencyDownloader;
import dev.neovoxel.jarflow.loader.ExternalLoader;
import dev.neovoxel.jarflow.util.DependencyNode;
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
import java.util.stream.Collectors;

public class JarFlow {

    private static final List<Repository> repositories = new ArrayList<>();

    private static final List<DependencyNode> dependencies = new ArrayList<>();

    private static final List<Dependency> loaded = new ArrayList<>();

    @Setter
    private static int threadCount = 4;

    @Setter
    private static File libDir = new File("libs");

    @Setter
    @Getter
    private static ExternalLoader loader = new ExternalLoader();

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

    private static void load(DependencyNode dependencyNode) throws IOException, InterruptedException {
        List<DependencyNode> origins = new ArrayList<>();
        origins.add(dependencyNode);
        Collection<DependencyNode> flatNodes = PomParser.filter(PomParser.getAllNodes(origins));
        for (DependencyNode node : flatNodes) {
            if (!node.getDependency().checkHas(loaded)) {
                Dependency dependency = node.getDependency();
                String fileName = dependency.getArtifactId() + "-" + dependency.getVersion();
                Path path = libDir.toPath()
                        .resolve(dependency.getGroupId())
                        .resolve(dependency.getArtifactId())
                        .resolve(dependency.getVersion())
                        .resolve(fileName + ".jar");
                if (!node.getDownloadUrl().isEmpty()) {
                    if (!hasDownloaded(dependency)) {
                        DependencyDownloader.download(node.getDownloadUrl(), dependency, libDir, threadCount);
                    }
                    if (dependency.getRelocations().isEmpty()) {
                        loader.load(path.toFile());
                    } else {
                        fileName += "-relocated.jar";
                        JarRelocator jarRelocator = new JarRelocator(path.toFile(), path.resolve("../" + fileName).toFile(), dependency.getRelocations());
                        jarRelocator.run();
                        loader.load(path.resolve("../" + fileName).toFile());
                    }
                }
                loaded.add(dependency);
            }
        }
    }

    public static void loadDependency(Dependency dependency) throws IOException, InterruptedException {
        logger.info("Loading dependency: {}", dependency.toString());
        DependencyNode node = PomParser.resolve(dependency, repositories);
        dependencies.add(node);
        load(node);
    }
    
    public static List<Class<?>> searchClasses(String prefix) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        for (Dependency dependency : loaded) {
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

    public static void loadDependencies(Collection<Dependency> dependencies) throws IOException, InterruptedException {
        for (Dependency dependency : dependencies) {
            loadDependency(dependency);
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
