package dev.neovoxel.jarflow.util;

import dev.neovoxel.jarflow.dependency.Dependency;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DependencyNode {
    Dependency dependency;
    String downloadUrl;
    DependencyNode parent;
    String newVersion;
    List<DependencyNode> children;

    public DependencyNode(Dependency dependency, String downloadUrl) {
        this.dependency = dependency;
        this.downloadUrl = downloadUrl;
        this.children = new ArrayList<>();
    }
}