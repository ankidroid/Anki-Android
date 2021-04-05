package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;

import com.ichi2.libanki.Utils;

import org.json.JSONException;
import org.json.JSONObject;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java8.util.StringJoiner;
import timber.log.Timber;

import static com.ichi2.anki.web.HttpFetcher.downloadFileToSdCardMethod;

public class AddonsNpmUtility {

    private static final String ADDON_TYPE = "reviewer";

    private final Activity activity;
    private final Context context;


    public AddonsNpmUtility(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    /**
     * Parse npm package info from package.json. If valid ankidroid-js-addon package then download it
     */
    public void parseJsonData(String strResponse, String npmAddonName) {
        try {
            Timber.d("json::%s", strResponse);

            JSONObject jsonObject = new JSONObject(strResponse);
            if (AddonModel.isValidAddonPackage(jsonObject, ADDON_TYPE)) {

                JSONObject dist = jsonObject.getJSONObject("dist");
                String tarballUrl = dist.get("tarball").toString();
                Timber.d("tarball link %s", tarballUrl);
                downloadAddonPackageFile(tarballUrl, npmAddonName);
            } else {
                hideProgressBar();
                showToast(context.getString(R.string.not_valid_package));
            }

        } catch (JSONException e) {
            Timber.w(e);
        }
    }


    /**
     * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
     * @param tarballUrl   tarball url of addon.tgz package file
     */
    public void downloadAddonPackageFile(String tarballUrl, String npmAddonName) {
        String downloadFilePath = downloadFileToSdCardMethod(tarballUrl, context, "addons", "GET");
        extractAndCopyAddonTgz(downloadFilePath, npmAddonName);
    }


    /**
     * extract downloaded .tgz files and copy to AnkiDroid/addons/ folder
     */
    public void extractAndCopyAddonTgz(String tarballPath, String npmAddonName) {

        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context);

        // AnkiDroid/addons/js-addons
        // here npmAddonName is id of npm package which may not contain ../ or other bad path
        StringJoiner joinedPath = new StringJoiner("/")
                .add(currentAnkiDroidDirectory)
                .add("addons")
                .add(npmAddonName);

        File addonsDir = new File(joinedPath.toString());
        File tarballFile = new File(tarballPath);

        if (!tarballFile.exists() || !addonsDir.exists()) {
            return;
        }

        // extracting using library https://github.com/thrau/jarchivelib
        try {
            Archiver archiver = ArchiverFactory.createArchiver(tarballFile);
            archiver.extract(tarballFile, addonsDir);
            Timber.d("js addon .tgz extracted");
            showToast(context.getString(R.string.addon_installed));
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
     * read package.json file of ankidroid-js-addon...
     */
    public static JSONObject packageJsonReader(File addonsFiles) {
        JSONObject jsonObject = null;
        try {

            InputStream is = new FileInputStream(addonsFiles);
            String stream = Utils.convertStreamToString(is);
            jsonObject = new JSONObject(stream);

        } catch (FileNotFoundException | JSONException e) {
            Timber.e(e.getLocalizedMessage());
        }
        return jsonObject;
    }


    /**
     * progress bar on ui thread
     */
    public void showProgressBar() {
        activity.runOnUiThread(() -> {
            ProgressBar progressBar = activity.findViewById(R.id.progress_bar);
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
        });
    }


    public void hideProgressBar() {
        activity.runOnUiThread(() -> {
            ProgressBar progressBar = activity.findViewById(R.id.progress_bar);
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }


    // showing toast on ui thread
    public void showToast(String msg) {
        activity.runOnUiThread(() -> {
            UIUtils.showThemedToast(context, msg, false);
        });
    }

}