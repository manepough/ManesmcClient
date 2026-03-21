pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.opencollab.dev/maven-snapshots/") }
        maven { url = uri("https://repo.opencollab.dev/maven-releases/") }
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }
}
rootProject.name = "Manes"
include(":app")
