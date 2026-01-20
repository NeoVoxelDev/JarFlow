package dev.neovoxel.jarflow.dependency;

import me.lucko.jarrelocator.Relocation;

import java.util.ArrayList;
import java.util.List;

public class DependencyBuilder {
    private String groupId;
    private String artifactId;
    private String version;
    private final List<Relocation> relocations = new ArrayList<>();
    private final List<Exclusion> exclusions = new ArrayList<>();

    protected DependencyBuilder() {

    }

    public DependencyBuilder groupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public DependencyBuilder artifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    public DependencyBuilder version(String version) {
        this.version = version;
        return this;
    }

    public DependencyBuilder exclude(String groupId) {
        this.exclusions.add(Exclusion.builder().groupId(groupId).build());
        return this;
    }

    public DependencyBuilder exclude(String groupId, String artifactId) {
        this.exclusions.add(Exclusion.builder().groupId(groupId).artifactId(artifactId).build());
        return this;
    }

    public DependencyBuilder exclude(String groupId, String artifactId, String version) {
        this.exclusions.add(Exclusion.builder().groupId(groupId).artifactId(artifactId).version(version).build());
        return this;
    }

    public DependencyBuilder exclude(Exclusion exclusion) {
        this.exclusions.add(exclusion);
        return this;
    }

    public DependencyBuilder relocate(String from, String to) {
        this.relocations.add(new Relocation(from, to));
        return this;
    }

    public Dependency build() {
        return new Dependency(groupId, artifactId, version, relocations, exclusions, new ArrayList<>());
    }
}
