package dev.neovoxel.jarflow.remote;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.building.StringModelSource;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;

public class HttpModelResolver implements ModelResolver {
    private final String repoUrl;

    public HttpModelResolver(String repoUrl) { this.repoUrl = repoUrl; }

    @Override
    public ModelSource resolveModel(String groupId, String artifactId, String version) {
        String path = groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";
        String content = HttpUtil.get(repoUrl + path);
        return new StringModelSource(content);
    }

    @Override
    public ModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
        return resolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    @Override
    public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
        return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    @Override public void addRepository(Repository repository) {}
    @Override public void addRepository(Repository repository, boolean replace) {}
    @Override public ModelResolver newCopy() { return this; }
}