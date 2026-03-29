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
        maven { url = uri("${rootDir}/../Anki-Android-Backend/build/localMaven") }
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

include(":lint-rules", ":api", ":AnkiDroid", ":testlib", ":common", ":libanki", ":libanki:testutils", ":vbpd")