package dev.neovoxel.jarflow.dependency;

import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Setter
public class Exclusion {
    private String groupId;
    private @Nullable String artifactId;
    private @Nullable String version;
    
    protected Exclusion(String groupId, @Nullable String artifactId, @Nullable String version) {
        this.groupId = groupId;
    }
    
    public static Exclusion.Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String groupId;
        private @Nullable String artifactId;
        private @Nullable String version;
        
        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }
        
        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Exclusion build() {
            return new Exclusion(groupId, artifactId, version);
        }
    }

    public boolean matches(Dependency dependency) {
        if (groupId != null && dependency.getGroupId().equalsIgnoreCase(groupId)) {
            return true;
        }
        if (artifactId != null && dependency.getArtifactId().equalsIgnoreCase(artifactId)) {
            return true;
        }
        if (version != null && dependency.getVersion().equalsIgnoreCase(version)) {
            return true;
        }
        return false;
    }
}
