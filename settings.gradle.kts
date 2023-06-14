pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://jitpack.io") // only needed for the "amazonappstorepublisher" plugin
    }
}

include(":lint-rules", ":api", ":AnkiDroid")
