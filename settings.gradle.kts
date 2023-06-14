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

include(":lint-rules", ":api", ":AnkiDroid")
