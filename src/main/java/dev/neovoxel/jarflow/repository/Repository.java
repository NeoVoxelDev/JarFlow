package dev.neovoxel.jarflow.repository;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class Repository {
    @NotNull
    private final String url;

    @Nullable
    private final String name;

    protected Repository(@NotNull String url, @Nullable String name) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        this.url = url;
        this.name = name;
    }

    public static RepositoryBuilder builder() {
        return new RepositoryBuilder();
    }

    public static Repository mavenCentral() {
        return new RepositoryBuilder().url("https://repo1.maven.org/maven2/").name("Maven Central").build();
    }

    public static Repository jCenter() {
        return new RepositoryBuilder().url("https://jcenter.bintray.com/").name("JCenter").build();
    }

    public static Repository sonatype() {
        return new RepositoryBuilder().url("https://oss.sonatype.org/content/repositories/snapshots/").name("Sonatype").build();
    }

    public static Repository jitPack() {
        return new RepositoryBuilder().url("https://jitpack.io/").name("JitPack").build();
    }

    public static Repository from(org.apache.maven.model.Repository repository) {
        return new RepositoryBuilder().url(repository.getUrl()).name(repository.getName()).build();
    }
}
