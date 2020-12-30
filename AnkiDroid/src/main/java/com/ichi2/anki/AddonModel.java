package com.ichi2.anki;

public class AddonModel {
    private String name;
    private String version;
    private String developer;
    private String ankidroid_api;
    private String homepage;

    public AddonModel(String name, String version, String developer, String ankidroid_api, String homepage) {
        this.name = name;
        this.version = version;
        this.developer = developer;
        this.ankidroid_api = ankidroid_api;
        this.homepage = homepage;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDeveloper() {
        return developer;
    }

    public String getAnkidroid_api() {
        return ankidroid_api;
    }

    public String getHomepage() {
        return homepage;
    }
}
