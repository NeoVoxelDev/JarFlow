package dev.neovoxel.jarflow.dependency;

import dev.neovoxel.jarflow.repository.Repository;
import lombok.Getter;
import lombok.ToString;
import me.lucko.jarrelocator.Relocation;
import org.apache.maven.model.Model;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@ToString
public class Dependency {
    @NotNull
    private final String groupId;

    @NotNull
    private final String artifactId;

    @NotNull
    private final String version;

    @NotNull
    private final List<Relocation> relocations = new ArrayList<>();

    @NotNull
    private final List<Exclusion> exclusions = new ArrayList<>();

    @NotNull
    private final List<Repository> repositories = new ArrayList<>();

    protected Dependency(@NotNull String groupId, @NotNull String artifactId, @NotNull String version, @NotNull List<Relocation> relocations, @NotNull List<Exclusion> exclusions, @NotNull List<Repository> repositories) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.relocations.addAll(relocations);
        this.exclusions.addAll(exclusions);
        this.repositories.addAll(repositories);
    }

    public static DependencyBuilder builder() {
        return new DependencyBuilder();
    }

    public boolean isExcluded(Dependency dependency) {
        for (Exclusion exclusion : exclusions) {
            if (exclusion.matches(dependency)) return true;
        }
        return false;
    }

    public String getLocation() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public String getUrlLocation() {
        return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/";
    }

    public String getPomFile() {
        return artifactId + "-" + version + ".pom";
    }

    public static List<Dependency> from(Model model) {
        List<org.apache.maven.model.Dependency> origins = model.getDependencies();
        if (origins == null || origins.isEmpty()) {
            return Collections.emptyList();
        }
        return origins.stream()
                .filter(d -> {
                    String scope = d.getScope();
                    return !"test".equalsIgnoreCase(scope) && !"provided".equalsIgnoreCase(scope);
                })
                .filter(d -> !d.isOptional())
                .map(dependency -> {
                    String version = dependency.getVersion();
                    if (version == null && model.getParent() != null) {
                        version = model.getParent().getVersion();
                    }
                    return new Dependency(
                            dependency.getGroupId(),
                            dependency.getArtifactId(),
                            version,
                            new ArrayList<>(),
                            new ArrayList<>(),
                            model.getRepositories().stream().map(Repository::from).collect(Collectors.toList())
                    );
                })
                .collect(Collectors.toList());
    }

    public boolean checkHas(Collection<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            if (dependency.getLocation().equals(this.getLocation())) return true;
        }
        return false;
    }
}
