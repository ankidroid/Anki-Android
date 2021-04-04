package com.ichi2.anki;

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;

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
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

import static com.ichi2.anki.AddonsBrowser.listAddonsFromDir;
import static com.ichi2.anki.AddonsBrowser.addonsBrowserActivity;
import static com.ichi2.anki.web.HttpFetcher.downloadFileToSdCardMethod;

public class AddonsNpmUtility extends AnkiActivity {

    // Update if api get updated
    private static String AnkiDroidJsAPI = "0.0.1";
    private static String AnkiDroidJsAddonKeywords = "ankidroid-js-addon";

    private static final String addonType = "reviewer";
    private static String listAddonType = addonType;


    /**
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     * @param context      context for calling the method
     */
    public static void getPackageJson(String npmAddonName, Context context) {
        showProgressBarStatic();
        String url = "https://registry.npmjs.org/" + npmAddonName + "/latest";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.e("js addon %s", e.toString());
                hideProgressBarStatic();
                call.cancel();
            }


            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    try {
                        String strResponse = response.body().string();
                        parseJsonData(strResponse, npmAddonName, context);

                        call.cancel();
                        response.close();
                    } catch (IOException | NullPointerException e) {
                        hideProgressBarStatic();
                        Timber.e(e.getLocalizedMessage());
                    }
                } else {
                    hideProgressBarStatic();
                    addonsBrowserActivity.runOnUiThread(() -> {
                        UIUtils.showThemedToast(addonsBrowserActivity, addonsBrowserActivity.getString(R.string.error_downloading_file_check_name), false);
                    });
                }
            }
        });
    }


    /**
     * Parse npm package info from package.json. If valid ankidroid-js-addon package then download it
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
            } else {
                hideProgressBarStatic();
                addonsBrowserActivity.runOnUiThread(() -> {
                    UIUtils.showThemedToast(addonsBrowserActivity, addonsBrowserActivity.getString(R.string.not_valid_package), false);
                });

            }

        } catch (JSONException e) {
            hideProgressBarStatic();
            Timber.e(e.getLocalizedMessage());
        }
    }


    /*
      is package.json of ankidroid-js-addon... contains valid
      ankidroid_js_api = 0.0.1 and keywords 'ankidroid-js-addon'
    */
    public static boolean isValidAddonPackage(JSONObject jsonObject) {

        if (jsonObject == null) {
            return false;
        }

        AddonModel addonModel = AddonModel.tryParse(jsonObject);
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
     * @param tarballUrl   tarball url of addon.tgz package file
     * @param context      context for calling the method
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

            listAddonsFromDir(addonType);
            addonsBrowserActivity.runOnUiThread(() -> {
                UIUtils.showThemedToast(addonsBrowserActivity, addonsBrowserActivity.getString(R.string.addon_installed), false);
            });
        } catch (IOException e) {
            Timber.e(e.getLocalizedMessage());
        } finally {
            hideProgressBarStatic();
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


    // static implementation for calling progress bar on ui thread
    public static void showProgressBarStatic() {
        addonsBrowserActivity.runOnUiThread(() -> {
            ProgressBar progressBar = addonsBrowserActivity.findViewById(R.id.progress_bar);
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
        });
    }


    public static void hideProgressBarStatic() {
        addonsBrowserActivity.runOnUiThread(() -> {
            ProgressBar progressBar = addonsBrowserActivity.findViewById(R.id.progress_bar);
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

}