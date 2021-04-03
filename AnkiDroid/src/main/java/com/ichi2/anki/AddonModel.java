package com.ichi2.anki;

public class AddonModel {
    private String sName;
    private String sVersion;
    private String sDeveloper;
    private String sJsApiVersion;
    private String sHomepage;
    private String sType;


    public AddonModel(String name, String version, String developer, String jsApiVersion, String homepage, String type) {
        this.sName = name;
        this.sVersion = version;
        this.sDeveloper = developer;
        this.sJsApiVersion = jsApiVersion;
        this.sHomepage = homepage;
        this.sType = type;
    }


    public String getName() {
        return sName;
    }


    public String getVersion() {
        return sVersion;
    }


    public String getDeveloper() {
        return sDeveloper;
    }


    public String getJsApiVersion() {
        return sJsApiVersion;
    }


    public String getHomepage() {
        return sHomepage;
    }


    public String getType() {
        return sType;
    }
}
