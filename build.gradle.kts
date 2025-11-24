import org.jreleaser.model.Active

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.1"
    id("maven-publish")
    id("org.jreleaser") version "1.20.0"
}

group = "dev.neovoxel.jarflow"
version = "1.5.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.json:json:20250517")
    implementation("net.bytebuddy:byte-buddy-agent:1.12.1")
    compileOnly("org.jetbrains:annotations:24.0.1")
    annotationProcessor("org.jetbrains:annotations:24.0.1")
    implementation("me.lucko:jar-relocator:1.7")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])

            pom {
                name = project.name
                description = "A library used to load dependencies dynamically"
                url = "https://github.com/NeoVoxelDev/JarFlow"
                inceptionYear = "2025"
                licenses {
                    license {
                        name = "LGPL-3.0-or-later"
                        url = "https://spdx.org/licenses/LGPL-3.0-or-later.html"
                    }
                }
                developers {
                    developer {
                        id = "aurelian2842"
                        name = "Aurelian2842"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/NeoVoxelDev/JarFlow.git"
                    developerConnection = "scm:git:ssh://github.com/NeoVoxelDev/JarFlow.git"
                    url = "http://github.com/NeoVoxelDev/JarFlow"
                }
            }
        }
    }

    repositories {
        maven {
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
    }
}

tasks.publish {
    dependsOn(tasks.named("publishMavenPublicationToMavenLocal"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

jreleaser {
    signing {
        active = Active.ALWAYS
        armored = true
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active = Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}