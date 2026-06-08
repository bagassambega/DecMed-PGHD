import groovy.json.JsonSlurper

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

fun findRustlsPlatformVerifierMavenDir(): File {
    val manifestPath = rootDir.resolve("../../crypto/decmed-iota/Cargo.toml").canonicalFile
    val stdout = providers.exec {
        commandLine("cargo", "metadata", "--format-version", "1", "--manifest-path", manifestPath.absolutePath)
    }.standardOutput.asText.get()
    val metadata = JsonSlurper().parseText(stdout) as Map<*, *>
    val packages = metadata["packages"] as List<*>
    val verifierPackage = packages
        .filterIsInstance<Map<*, *>>()
        .first { it["name"] == "rustls-platform-verifier-android" }
    val verifierManifest = file(verifierPackage["manifest_path"].toString())
    return verifierManifest.parentFile.resolve("maven")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri(findRustlsPlatformVerifierMavenDir())
            metadataSources {
                artifact()
                mavenPom()
            }
        }
    }
}

rootProject.name = "DecMed"
include(":app")
 
