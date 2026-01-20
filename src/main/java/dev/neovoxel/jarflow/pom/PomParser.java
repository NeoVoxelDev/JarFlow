package dev.neovoxel.jarflow.pom;

import dev.neovoxel.jarflow.dependency.Dependency;
import dev.neovoxel.jarflow.remote.HttpModelResolver;
import dev.neovoxel.jarflow.repository.Repository;
import dev.neovoxel.jarflow.util.DependencyNode;
import dev.neovoxel.jarflow.remote.HttpUtil;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class PomParser {
    private static final Logger logger = LoggerFactory.getLogger("JarFlow Parser");

    private static Model getModel(String content, String repoUrl) {
        try {
            ModelBuilder builder = new DefaultModelBuilderFactory().newInstance();
            ModelBuildingRequest request = new DefaultModelBuildingRequest();
            request.setProcessPlugins(false);
            request.setModelSource(new StringModelSource(content));
            request.setModelResolver(new HttpModelResolver(repoUrl));
            request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
            request.setSystemProperties(System.getProperties());
            return builder.build(request).getEffectiveModel();
        } catch (ModelBuildingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getLatestVersion(Collection<String> versions) {
        if (versions == null || versions.isEmpty()) return null;

        return versions.stream()
                .map(DefaultArtifactVersion::new)
                .max(DefaultArtifactVersion::compareTo)
                .map(Object::toString)
                .orElse(null);
    }

    // will not get children nodes
    public static List<DependencyNode> filter(List<DependencyNode> nodes) {
        Map<String, List<DependencyNode>> repeated = new HashMap<>();
        List<String> repeatedStr = new ArrayList<>();
        for (DependencyNode node : nodes) {
            String key = node.getDependency().getGroupId() + ":" + node.getDependency().getArtifactId();
            if (repeated.containsKey(key)) {
                repeatedStr.add(key);
                repeated.get(key).add(node);
            } else {
                List<DependencyNode> list = new ArrayList<>();
                list.add(node);
                repeated.put(key, list);
            }
        }
        for (String key : repeatedStr) {
            List<String> versions = repeated.get(key).stream().map(node -> node.getDependency().getVersion()).distinct().collect(Collectors.toList());
            String latest = getLatestVersion(versions);
            for (DependencyNode node : repeated.get(key)) {
                if (!node.getDependency().getVersion().equals(latest)) {
                    node.setNewVersion(latest);
                }
            }
        }
        return nodes;
    }

    public static List<DependencyNode> getAllNodes(Collection<DependencyNode> nodes) {
        List<DependencyNode> result = new ArrayList<>();
        collect(nodes, result);
        return result;
    }

    private static void collect(Collection<DependencyNode> nodes, List<DependencyNode> result) {
        for (DependencyNode node : nodes) {
            result.add(node);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                collect(node.getChildren(), result);
            }
        }
    }

    public static DependencyNode resolve(Dependency dependency, Collection<Repository> repositories) {
        return resolve(new DependencyNode(dependency, ""), repositories);
    }
    
    private static DependencyNode resolve(DependencyNode node, Collection<Repository> repositories) {
        for (Repository repository : repositories) {
            String pomUrl = repository.getUrl() +
                    node.getDependency().getUrlLocation() +
                    node.getDependency().getPomFile();
            String content = HttpUtil.get(pomUrl);
            if (content == null) {
                continue;
            }
            Model model = getModel(content, repository.getUrl());
            if (model == null) {
                logger.warn("Failed to resolve dependency {} (failed to parse pom file)", node.getDependency().getLocation());
                continue;
            }
            if (!model.getPackaging().equalsIgnoreCase("pom")) {
                node.setDownloadUrl(repository.getUrl() +
                        node.getDependency().getUrlLocation() +
                        node.getDependency().getArtifactId() + "-" + node.getDependency().getVersion() + ".jar");
            }
            node.getDependency().getRepositories().addAll(model.getRepositories().stream()
                    .map(Repository::from).collect(Collectors.toList()));
            List<Dependency> subDeps = new ArrayList<>();
            subDeps.addAll(Dependency.from(model));
            List<DependencyNode> subDepNodes = subDeps.stream().map(d -> {
                DependencyNode child = new DependencyNode(d, "");
                node.getChildren().add(child);
                child.setParent(node);
                return child;
            }).collect(Collectors.toList());
            List<Repository> repositories1 = new ArrayList<>();
            repositories1.addAll(repositories);
            repositories1.addAll(node.getDependency().getRepositories());
            List<String> repositoryStrings = new ArrayList<>();
            List<Repository> repositories2 = new ArrayList<>();
            for (Repository repository1 : repositories1) {
                if (!repositoryStrings.contains(repository1.getUrl())) {
                    repositoryStrings.add(repository1.getUrl());
                    repositories2.add(repository1);
                }
            }
            subDepNodes.forEach(node1 -> resolve(node1, repositories2));
            return node;
        }
        logger.error("Failed to resolve dependency {}", node.getDependency().getLocation());
        return node;
    }
}
