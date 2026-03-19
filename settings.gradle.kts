pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // CloudburstMC — required for Bedrock protocol + RakNet
        maven("https://repo.opencollab.dev/maven-snapshots/")
        maven("https://repo.opencollab.dev/maven-releases/")
    }
}

rootProject.name = "Manes"
include(":app")
