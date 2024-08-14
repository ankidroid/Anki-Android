pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // TODO enforce repositories declared here, currently it clashes with robolectricDownloader.gradle
    //  which uses a local maven repository
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

include(":lint-rules", ":api", ":AnkiDroid", ":testlib", ":common")