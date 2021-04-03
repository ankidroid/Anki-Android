package com.ichi2.anki;

public class AddonModel {
    private String mName;
    private String mVersion;
    private String mDeveloper;
    private String mJsApiVersion;
    private String mHomepage;
    private String mType;


    public AddonModel(String name, String version, String developer, String jsApiVersion, String homepage, String type) {
        this.mName = name;
        this.mVersion = version;
        this.mDeveloper = developer;
        this.mJsApiVersion = jsApiVersion;
        this.mHomepage = homepage;
        this.mType = type;
    }


    public String getName() {
        return mName;
    }


    public String getVersion() {
        return mVersion;
    }


    public String getDeveloper() {
        return mDeveloper;
    }


    public String getJsApiVersion() {
        return mJsApiVersion;
    }


    public String getHomepage() {
        return mHomepage;
    }


    public String getType() {
        return mType;
    }
}
