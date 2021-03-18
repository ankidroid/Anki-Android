package com.ichi2.anki;

public class AddonModel {
    private String name;
    private String version;
    private String developer;
    private String jsApiVersion;
    private String homepage;
    private String type;

    public AddonModel(String name, String version, String developer, String jsApiVersion, String homepage, String type) {
        this.name = name;
        this.version = version;
        this.developer = developer;
        this.jsApiVersion = jsApiVersion;
        this.homepage = homepage;
        this.type = type;
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

    public String getJsApiVersion() {
        return jsApiVersion;
    }

    public String getHomepage() {
        return homepage;
    }

    public String getType() {
        return type;
    }
}
