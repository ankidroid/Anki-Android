package com.ichi2.anki;

public class AddonModel {
    private String id;
    private String name;
    private String version;
    private String developer;
    private String ankidroid_api;

    public AddonModel(String id, String name, String version, String developer, String ankidroid_api) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.developer = developer;
        this.ankidroid_api = ankidroid_api;
    }

    public String getId() {
        return id;
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
}
