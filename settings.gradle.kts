pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://jitpack.io") // only needed for the "amazonappstorepublisher" plugin
    }
    resolutionStrategy {
        // TODO try to find another plugin for this functionality?
        eachPlugin {
            if (requested.id.id == "app.brant.amazonappstorepublisher") {
                useModule("com.github.BrantApps.gradle-amazon-app-store-publisher:amazonappstorepublisher:master-SNAPSHOT")
            }
        }
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

include(":lint-rules", ":api", ":AnkiDroid")
