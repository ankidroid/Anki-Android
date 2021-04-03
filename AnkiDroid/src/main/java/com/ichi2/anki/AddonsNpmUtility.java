package com.ichi2.anki;

import android.content.Context;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

public class AddonsNpmUtility extends AnkiActivity{

    // Update if api get updated
    private static String AnkiDroidJsAPI = "0.0.1";
    private static String AnkiDroidJsAddonKeywords = "ankidroid-js-addon";

    private static String[] addonTypes = {"reviewer", "note_editor"};
    // default list addons in addons browser
    private static String listAddonType = addonTypes[0];

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

        String addonName = jsonObject.optString("name", "");
        String addonVersion = jsonObject.optString("version", "");
        String addonDev = jsonObject.optString("author", "");
        String addonAnkiDroidAPI = jsonObject.optString("ankidroid_js_api", "");
        String addonHomepage = jsonObject.optString("homepage", "");
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

        if (addonAnkiDroidAPI.equals(AnkiDroidJsAPI) && jsAddonKeywordsPresent) {
            // if other strings are non empty
            if (!addonName.isEmpty() && !addonVersion.isEmpty() && !addonDev.isEmpty() && !addonHomepage.isEmpty()) {
                return true;
            }
        }
        return false;
    }


    /**
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     * @param tarballUrl tarball url of addon.tgz package file
     */
    public static void downloadAddonPackageFile(String tarballUrl, String npmAddonName, Context context) {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(tarballUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.e("js addon %s", e.toString());
            }


            @Override
            public void onResponse(Call call, Response response) {
                InputStream is = null;
                byte[] buf = new byte[2048];
                int len = 0;
                FileOutputStream fos = null;

                try {
                    String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context);
                    File addonsHomeDir = new File(currentAnkiDroidDirectory, "addons");

                    File tarballFile = new File(addonsHomeDir, npmAddonName + ".tgz");

                    is = response.body().byteStream();
                    fos = new FileOutputStream(tarballFile);

                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.flush();

                    extractAndCopyAddonTgz(tarballFile.getPath(), npmAddonName, context);
                } catch (IOException | NullPointerException e) {
                    Timber.e(e.getLocalizedMessage());
                }
            }
        });
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
            FileReader fileReader = new FileReader(addonsFiles);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            StringBuilder stringBuilder = new StringBuilder();
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuilder.append(line).append("\n");
                line = bufferedReader.readLine();
            }
            bufferedReader.close();

            String response = stringBuilder.toString();
            jsonObject = new JSONObject(response);

        } catch (IOException | JSONException e) {
            Timber.e(e.getLocalizedMessage());
        }
        return jsonObject;
    }
}