package com.ichi2.anki;


import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import timber.log.Timber;

public class AddonModel {
    private String mName;
    private String mVersion;
    private String mDeveloper;
    private String mJsApiVersion;
    private String mHomepage;
    private String mType;
    private static String REVIEWER_ADDON_KEY = "enabled_reviewer_addon";


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

    public static String getReviewerAddonKey() {
        return REVIEWER_ADDON_KEY;
    }

    public static AddonModel tryParse(JSONObject jsonObject, String type) {

        String addonName = jsonObject.optString("name", "");
        String addonVersion = jsonObject.optString("version", "");
        String addonDev = jsonObject.optString("author", "");
        String addonAnkiDroidAPI = jsonObject.optString("ankidroid_js_api", "");
        String addonHomepage = jsonObject.optString("homepage", "");
        String addonType = jsonObject.optString("addon_type", "");

        AddonModel addonModel = new AddonModel(addonName, addonVersion, addonDev, addonAnkiDroidAPI, addonHomepage, addonType);
        if (type.equals(addonType) && !isKeyEmpty(addonModel)) {
            return addonModel;
        }
        return null;
    }

    /**
     * @param jsonObject json object with addons info
     * @param addonType type of addons, reviewer or note editor
     * @return addonModel/null, if valid addon then return addon model else return null
     * is package.json of ankidroid-js-addon... contains valid
     * ankidroid_js_api = 0.0.1 and keywords 'ankidroid-js-addon'
     */
    public static AddonModel isValidAddonPackage(JSONObject jsonObject, String addonType) {
        // Update if api get updated
        // TODO Extract to resources from other classes
        String AnkiDroidJsAPI = "0.0.1";
        String AnkiDroidJsAddonKeywords = "ankidroid-js-addon";

        if (jsonObject == null) {
            return null;
        }

        AddonModel addonModel = AddonModel.tryParse(jsonObject, addonType);
        if (addonModel == null) {
            return null;
        }

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

        // js addons have full access to AnkiDroid JS API so needs to check the version
        // jsAddonKeywords help in distinguish from other npm package
        if (!addonModel.getJsApiVersion().equals(AnkiDroidJsAPI) || !jsAddonKeywordsPresent) {
            return null;
        }

        return addonModel;
    }

    public static boolean isKeyEmpty(AddonModel addonModel) {
        if (addonModel.getName().isEmpty() && addonModel.getVersion().isEmpty() && addonModel.getDeveloper().isEmpty() && addonModel.getHomepage().isEmpty()) {
            return true;
        }
        return false;
    }

    // https://stackoverflow.com/questions/19949182/android-sharedpreferences-string-set-some-items-are-removed-after-app-restart/19949833
    public static void updatePrefs(SharedPreferences preferences, String reviewerAddonKey, String addonName, boolean remove) {
        Set<String> reviewerEnabledAddonSet = preferences.getStringSet(reviewerAddonKey, new HashSet<String>());
        Set<String> newStrSet = new HashSet<String>(reviewerEnabledAddonSet);

        if (remove) {
            newStrSet.remove(addonName);
        } else {
            newStrSet.add(addonName);
        }
        preferences.edit().putStringSet(reviewerAddonKey, newStrSet).apply();
    }
}
