package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.ProgressBar;

import com.ichi2.async.Connection;
import com.ichi2.libanki.Utils;

import org.apache.commons.compress.utils.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import java8.util.StringJoiner;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

import static com.ichi2.anki.web.HttpFetcher.downloadFileToSdCardMethod;

public class AddonsNpmUtility {

    public static final String ADDON_TYPE = "reviewer";

    private final Activity mActivity;
    private final Context mContext;


    public AddonsNpmUtility(Context context, Activity activity) {
        this.mContext = context;
        this.mActivity = activity;
    }

    /**
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     * @param runnable for calling listAddonsFromDir in AddonsBrowser after download/extract of js-addons.tgz to addons folder
     */
    public void getPackageJson(String npmAddonName, Runnable runnable) {
        // if not connected to internet
        if (!Connection.isOnline()) {
            showToast(mContext.getString(R.string.network_no_connection));
            hideProgressBar();
            return;
        }

        showProgressBar();
        String url = mContext.getString(R.string.ankidroid_js_addon_npm_registry, npmAddonName);
        Timber.i("npm url: %s", url);

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
                hideProgressBar();
                Timber.e("js addon %s", e.toString());
                showToast(mContext.getString(R.string.error_downloading_file_check_name));
                call.cancel();
            }


            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    try {
                        String strResponse = response.body().string();
                        parseJsonData(strResponse, npmAddonName);
                    } catch (IOException | NullPointerException e) {
                        // do not show progress bar indefinitely
                        hideProgressBar();
                        Timber.e(e.getLocalizedMessage());
                    } finally {
                        runnable.run();
                    }
                } else {
                    hideProgressBar();
                    showToast(mContext.getString(R.string.error_downloading_file_check_name));
                }
            }
        });
    }


    /**
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     * @param strResponse response from registry.npmjs.org
     * Parse npm package info from package.json. If valid ankidroid-js-addon package then download it
     */
    public void parseJsonData(String strResponse, String npmAddonName) {
        try {
            Timber.v("json:: %s", strResponse);

            JSONObject jsonObject = new JSONObject(strResponse);
            AddonModel addonModel = AddonModel.isValidAddonPackage(jsonObject, ADDON_TYPE);

            if (addonModel == null) {
                hideProgressBar();
                showToast(mContext.getString(R.string.not_valid_package));
                return;
            }

            JSONObject dist = jsonObject.getJSONObject("dist");
            String tarballUrl = dist.getString("tarball");
            Timber.d("tarball link %s", tarballUrl);
            downloadAddonPackageFile(tarballUrl, npmAddonName);

        } catch (JSONException e) {
            Timber.w(e);
        }
    }


    /**
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     * @param tarballUrl   tarball url of addon.tgz package file
     */
    public void downloadAddonPackageFile(String tarballUrl, String npmAddonName) {
        String downloadFilePath = downloadFileToSdCardMethod(tarballUrl, mContext, "addons", "GET");
        Timber.d("download path %s", downloadFilePath);
        extractAndCopyAddonTgz(downloadFilePath, npmAddonName);
    }


    /**
     * @param tarballPath path to downloaded js-addon.tgz file
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     * extract downloaded .tgz files and copy to AnkiDroid/addons/ folder
     */
    public void extractAndCopyAddonTgz(String tarballPath, String npmAddonName) {

        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(mContext);

        // AnkiDroid/addons/js-addons
        // here npmAddonName is id of npm package which may not contain ../ or other bad path
        StringJoiner joinedPath = new StringJoiner("/")
                .add(currentAnkiDroidDirectory)
                .add("addons")
                .add(npmAddonName);

        File addonsDir = new File(joinedPath.toString());
        File tarballFile = new File(tarballPath);

        if (!tarballFile.exists()) {
            return;
        }

        // extracting using library https://github.com/thrau/jarchivelib
        try {
            Archiver archiver = ArchiverFactory.createArchiver(tarballFile);
            archiver.extract(tarballFile, addonsDir);
            Timber.d("js addon .tgz extracted");
            showToast(mContext.getString(R.string.addon_installed));
        } catch (IOException e) {
            Timber.e(e.getLocalizedMessage());
        } finally {
            hideProgressBar();
            if (tarballFile.exists()) {
                tarballFile.delete();
            }
        }
    }


    /**
     * @param addonsFiles package.json file in ankidroid-js-addon/package/...
     * @return jsonObject json object of the addon files content
     */
    @Nullable
    public static JSONObject packageJsonReader(File addonsFiles) {
        try(InputStream is = new FileInputStream(addonsFiles)) {
            String stream = Utils.convertStreamToString(is);
            return new JSONObject(stream);
        } catch (JSONException | IOException e) {
            Timber.e(e.getLocalizedMessage());
        }
        return null;
    }


    /**
     * progress bar on ui thread
     */
    public void showProgressBar() {
        mActivity.runOnUiThread(() -> {
            ProgressBar progressBar = mActivity.findViewById(R.id.progress_bar);
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
        });
    }


    public void hideProgressBar() {
        mActivity.runOnUiThread(() -> {
            ProgressBar progressBar = mActivity.findViewById(R.id.progress_bar);
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }


    // showing toast on ui thread
    public void showToast(String msg) {
        mActivity.runOnUiThread(() -> {
            UIUtils.showThemedToast(mContext, msg, false);
        });
    }


    /**
     * @param context context for calling the method
     *                get enabled js addon contents from AnkiDroid/addons/...
     *                This function will be called in AbstractFlashcardViewer
     *                <p>
     *                1. get enabled status of addons in SharedPreferences with key containing 'reviewer_addon', read only enabled addons
     *                2. split value and get latter part as it stored in SharedPreferences
     *                e.g: reviewer_addon:ankidroid-js-addon-12345...  -->  ankidroid-js-addon-12345...
     *                It is same as folder name for the addon.
     *                3. Read index.js file from  AnkiDroid/addons/js-addons/package/index.js
     *                </p>
     * @return content of index.js file for every enabled addons in script tag.
     */
    public static String getEnabledAddonsContent(Context context) {
        StringBuilder content = new StringBuilder();

        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context);
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);

        // set of enabled addons only
        Set<String> reviewerEnabledAddonSet = preferences.getStringSet(AddonModel.getReviewerAddonKey(), new HashSet<String>());

        // even if preferences enabled then the key exist in 'reviewerEnabledAddonSet' but when addons does not exist on disk then
        // 'readIndexJs()' check if index.js i.e 'joinedPath' exist or not. If exist then read other wise return empty strings
        for (String addonDir : reviewerEnabledAddonSet) {
            try {

                // AnkiDroid/addons/js-addons/package/index.js
                // here addonDir is id of npm package which may not contain ../ or other bad path
                StringJoiner joinedPath = new StringJoiner("/")
                        .add(currentAnkiDroidDirectory)
                        .add("addons")
                        .add(addonDir)
                        .add("package")
                        .add("index.js");

                String indexJsPath = joinedPath.toString();

                File addonsContentFile = new File(indexJsPath);

                // wrap index.js content in script tag for each enabled addons
                String addonsContent = readIndexJs(addonsContentFile);

                // it seems addons does not exists, so remove key from prefs
                if (addonsContent.isEmpty()) {
                    AddonModel.updatePrefs(preferences, addonDir, AddonModel.getReviewerAddonKey(), true);
                } else {
                    content.append(addonsContent);
                }

                Timber.d("addon content path: %s", addonsContentFile);

            } catch (ArrayIndexOutOfBoundsException e) {
                Timber.w(e, "AbstractFlashcardViewer::Exception");
            }
        }
        return content.toString();
    }


    /**
     * @param addonsContentFile index.js file in js-addons/package folder
     * @return String, js code inside index.js
     */
    public static String readIndexJs(File addonsContentFile) {
        StringBuilder content = new StringBuilder();
        if (!addonsContentFile.exists()) {
            return content.toString();
        }

        try(BufferedReader br = new BufferedReader(new FileReader(addonsContentFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
                content.append('\n');
            }
        } catch (IOException e) {
            Timber.e(e.getLocalizedMessage());
        }
        return content.toString();
    }
}