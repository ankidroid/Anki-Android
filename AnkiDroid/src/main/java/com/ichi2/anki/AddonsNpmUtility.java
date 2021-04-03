package com.ichi2.anki;

import android.content.Context;

import com.ichi2.libanki.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

import static com.ichi2.anki.web.HttpFetcher.downloadFileToSdCardMethod;

public class AddonsNpmUtility extends AnkiActivity{

    // Update if api get updated
    private static String AnkiDroidJsAPI = "0.0.1";
    private static String AnkiDroidJsAddonKeywords = "ankidroid-js-addon";

    private static String[] addonTypes = {"reviewer", "note_editor"};
    // default list addons in addons browser
    private static String listAddonType = addonTypes[0];

    private static final String addonType = "reviewer";

    /**
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     */
    public static void getPackageJson(String npmAddonName, Context context) {
        String url = "https://registry.npmjs.org/" + npmAddonName + "/latest";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.e("js addon %s", e.toString());
                call.cancel();
            }


            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String strResponse = response.body().string();
                    parseJsonData(strResponse, npmAddonName, context);

                    call.cancel();
                    response.close();
                } catch (IOException | NullPointerException e) {
                    Timber.e(e.getLocalizedMessage());
                }
            }
        });
    }

    /*
      Parse npm package info from package.json. If valid ankidroid-js-addon package then download it
    */
    public static void parseJsonData(String strResponse, String npmAddonName, Context context) {
        try {
            Timber.d("json::%s", strResponse);

            JSONObject jsonObject = new JSONObject(strResponse);
            if (isValidAddonPackage(jsonObject)) {

                listAddonType = jsonObject.optString("addon_type", "");
                JSONObject dist = jsonObject.getJSONObject("dist");
                String tarballUrl = dist.get("tarball").toString();
                Timber.d("tarball link %s", tarballUrl);

                downloadAddonPackageFile(tarballUrl, npmAddonName, context);
            }

        } catch (JSONException e) {
            Timber.e(e.getLocalizedMessage());
        }
    }

    /*
      is package.json of ankidroid-js-addon... contains valid
      ankidroid_js_api==0.0.1 and keywords 'ankidroid-js-addon'
    */
    public static boolean isValidAddonPackage(JSONObject jsonObject) {

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
            Timber.e(e.getLocalizedMessage());
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


    /**
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     * @param tarballUrl tarball url of addon.tgz package file
     */
    public static void downloadAddonPackageFile(String tarballUrl, String npmAddonName, Context context) {
        String downloadFilePath = downloadFileToSdCardMethod(tarballUrl, context, "addons", "GET");
        extractAndCopyAddonTgz(downloadFilePath, npmAddonName, context);
    }

    /**
     * extract downloaded .tgz files and copy to AnkiDroid/addons/ folder
     */
    public static void extractAndCopyAddonTgz(String tarballPath, String npmAddonName, Context context) {

        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context);
        File addonsHomeDir = new File(currentAnkiDroidDirectory, "addons");

        File addonsDir = new File(addonsHomeDir, npmAddonName);
        File tarballFile = new File(tarballPath);

        if (!tarballFile.exists()) {
            return;
        }

        // extracting using library https://github.com/thrau/jarchivelib
        try {
            Archiver archiver = ArchiverFactory.createArchiver(new File(tarballPath));
            archiver.extract(tarballFile, addonsDir);
            Timber.d("js addon .tgz extracted");
        } catch (IOException e) {
            Timber.e(e.getLocalizedMessage());
        } finally {
            if (tarballFile.exists()) {
                tarballFile.delete();
            }
        }
    }

    /**
     * read package.json file of ankidroid-js-addon...
     */
    public static JSONObject packageJsonReader(File addonsFiles) {
        JSONObject jsonObject = null;
        try {

            InputStream is = new FileInputStream(addonsFiles);
            String stream = Utils.convertStreamToString(is);
            jsonObject = new JSONObject(stream);

        } catch (IOException | JSONException e) {
            Timber.e(e.getLocalizedMessage());
        }
        return jsonObject;
    }
}