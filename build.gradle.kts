import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

/*
 * Copyright (c) 2024 Ashley (uwuvern) <uwuvern@outlook.com>
 *
 * This project is licensed under the MIT license, check the root of the project for
 * more information.
 */

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"

    `maven-publish`
}

allprojects {
    group = properties["group"] as String
    version = properties["version"] as String
}

subprojects {
    apply {
        plugin("java")
        plugin("com.github.johnrengelman.shadow")
        plugin("maven-publish")
    }

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    val testImplementation by configurations

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.10.2"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.junit.jupiter:junit-jupiter-api")
        testImplementation("org.junit.jupiter:junit-jupiter-engine")
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group as String
                artifactId = project.name
                version = project.version as String

                from(components["java"])
            }
        }
    }

    tasks {
        named("build") {
            dependsOn("shadowJar")
        }
    }

    tasks {
        withType<JavaCompile> {
            sourceCompatibility = project.properties["java"] as String
            targetCompatibility = project.properties["java"] as String

            options.encoding = "UTF-8"
        }
    }

    tasks {
        named<ShadowJar>("shadowJar") {
            archiveClassifier.set("")
            mergeServiceFiles()

            dependsOn(jar)
        }
    }

    tasks {
        named<Jar>("jar") {
            archiveClassifier.set("original")

            manifest {
                attributes["Implementation-Title"] = project.name
                attributes["Implementation-Version"] = project.version
            }

            from(sourceSets["main"].output)
        }
    }


    tasks {
        test {
            useJUnitPlatform()
        }
    }
}