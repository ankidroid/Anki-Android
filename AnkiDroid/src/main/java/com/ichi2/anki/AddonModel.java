package com.ichi2.anki;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;

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


    public static AddonModel tryParse(JSONObject jsonObject, String type) {

        String addonName = jsonObject.optString("name", "");
        String addonVersion = jsonObject.optString("version", "");
        String addonDev = jsonObject.optString("author", "");
        String addonAnkiDroidAPI = jsonObject.optString("ankidroid_js_api", "");
        String addonHomepage = jsonObject.optString("homepage", "");
        String addonType = jsonObject.optString("addon_type", "");
        if (type.equals(addonType)) {
            return new AddonModel(addonName, addonVersion, addonDev, addonAnkiDroidAPI, addonHomepage, addonType);
        }
        return null;
    }


    public static String getAddonFullName(AddonModel addonModel) {
        return addonModel.getType() + "_addon:" + addonModel.getName();
    }


    /**
     * @param jsonObject json object with addons info
     * @return true/false, if valid
     * is package.json of ankidroid-js-addon... contains valid
     * ankidroid_js_api = 0.0.1 and keywords 'ankidroid-js-addon'
     */
    public static boolean isValidAddonPackage(JSONObject jsonObject, String addonType) {
        // Update if api get updated
        // TODO Extract to resources from other classes
        String AnkiDroidJsAPI = "0.0.1";
        String AnkiDroidJsAddonKeywords = "ankidroid-js-addon";

        if (jsonObject == null) {
            return false;
        }

        AddonModel addonModel = AddonModel.tryParse(jsonObject, addonType);
        boolean jsAddonKeywordsPresent = false;

        try {
            JSONArray keywords = jsonObject.getJSONArray("keywords");
            for (int j = 0; j < keywords.length(); j++) {
                String addonKeyword = keywords.getString(j);
                if (addonKeyword.equals(AnkiDroidJsAddonKeywords)) {
                    jsAddonKeywordsPresent = true;
                    break;
                }
            }
            Timber.d("keywords %s", keywords.toString());
        } catch (JSONException e) {
            Timber.w(e, e.getLocalizedMessage());
        }

        if (addonModel == null) {
            return false;
        }

        if (!addonModel.getJsApiVersion().equals(AnkiDroidJsAPI) && jsAddonKeywordsPresent) {
            return false;
        }

        // if other strings are non empty
        if (!addonModel.getName().isEmpty() && !addonModel.getVersion().isEmpty() && !addonModel.getDeveloper().isEmpty() && !addonModel.getHomepage().isEmpty()) {
            return true;
        }

        return false;
    }
}
