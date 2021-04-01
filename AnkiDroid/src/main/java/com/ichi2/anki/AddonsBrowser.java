package com.ichi2.anki;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.async.Connection;
import com.ichi2.themes.Themes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.RIGHT;

@SuppressWarnings("deprecation")
public class AddonsBrowser extends NavigationDrawerActivity implements DeckDropDownAdapter.SubtitleListener, AddonsAdapter.OnAddonClickListener {

    private RecyclerView addonsListRecyclerView;
    private MenuItem mInstallAddon;
    private MenuItem mGetMoreAddons;
    private MenuItem mReviewerAddons;
    private MenuItem mNoteEditorAddons;

    private long queueId;
    DownloadManager downloadManager;

    private String currentAnkiDroidDirectory;
    private File addonsHomeDir;
    private Dialog downloadDialog;

    // Update if api get updated
    private String AnkiDroidJsAPI = "0.0.1";
    private String AnkiDroidJsAddonKeywords = "ankidroid-js-addon";

    private List<AddonModel> addonsNames = new ArrayList<AddonModel>();

    private SharedPreferences preferences;

    private String[] addonTypes = {"reviewer", "note_editor"};
    // default list addons in addons browser
    private String listAddonType = addonTypes[0];


    private LinearLayout addonsMessageLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.setThemeLegacy(this);
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        super.onCreate(savedInstanceState);
        Timber.d("onCreate()");
        setContentView(R.layout.addons_browser);
        initNavigationDrawer(findViewById(android.R.id.content));

        addonsMessageLayout = findViewById(R.id.addons_download_message_ll);
        addonsMessageLayout.setOnClickListener(v -> {
            openUrl(Uri.parse(getResources().getString(R.string.ankidroid_js_addon_npm_search_url)));
        });

        // Add a home button to the actionbar
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        showBackIcon();

        addonsListRecyclerView = findViewById(R.id.addons);
        addonsListRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        addonsListRecyclerView.addItemDecoration(dividerItemDecoration);

        downloadDialog = new Dialog(this);

        currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this);
        addonsHomeDir = new File(currentAnkiDroidDirectory, "addons");

        preferences = AnkiDroidApp.getSharedPrefs(this);

        // default list reviewer addons
        getSupportActionBar().setTitle(getString(R.string.reviewer_addons));
        listAddonsFromDir(addonTypes[0]);
    }


    @Override
    public String getSubtitleText() {
        return getResources().getString(R.string.addons);
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishWithAnimation(RIGHT);

        if (downloadDialog.isShowing()) {
            downloadDialog.dismiss();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.addon_browser, menu);
        mInstallAddon = menu.findItem(R.id.action_install_addon);
        mGetMoreAddons = menu.findItem(R.id.action_get_more_addons);
        mReviewerAddons = menu.findItem(R.id.action_addon_reviewer);
        mNoteEditorAddons = menu.findItem(R.id.action_addon_note_editor);

        mReviewerAddons.setOnMenuItemClickListener(item -> {
            getSupportActionBar().setTitle(getString(R.string.reviewer_addons));
            listAddonsFromDir(addonTypes[0]);
            return true;
        });

        mNoteEditorAddons.setOnMenuItemClickListener(item -> {
            getSupportActionBar().setTitle(getString(R.string.note_editor_addons));
            listAddonsFromDir(addonTypes[1]);
            return true;
        });

        mInstallAddon.setOnMenuItemClickListener(item -> {

            downloadDialog.setCanceledOnTouchOutside(true);
            downloadDialog.setContentView(R.layout.addon_install_from_npm);

            EditText downloadEditText = downloadDialog.findViewById(R.id.addon_download_edit_text);
            Button downloadButton = downloadDialog.findViewById(R.id.addon_download_button);

            downloadButton.setOnClickListener(v -> {
                String npmAddonName = downloadEditText.getText().toString();

                // if string is:  npm i ankidroid-js-addon-progress-bar
                if (npmAddonName.startsWith("npm i")) {
                    npmAddonName = npmAddonName.replace("npm i", "");
                }

                // if containing space
                npmAddonName = npmAddonName.trim();
                npmAddonName = npmAddonName.replaceAll("\u00A0", "");

                UIUtils.showThemedToast(this, getString(R.string.downloading_addon), true);
                getLatestPackageJson(npmAddonName);

                downloadDialog.dismiss();

            });

            downloadDialog.show();
            return true;
        });

        mGetMoreAddons.setOnMenuItemClickListener(item -> {
            openUrl(Uri.parse(getResources().getString(R.string.ankidroid_js_addon_npm_search_url)));
            return true;
        });

        return super.onCreateOptionsMenu(menu);
    }


    /*
       list addons with valid package.json, i.e contains
       ankidroid_js_api = 0.0.1
       keywords='ankidroid-js-addon'
       and non empty string.
       Then that addon will available for enable/disable
     */
    public void listAddonsFromDir(String addonType) {
        boolean success = true;
        if (!addonsHomeDir.exists()) {
            success = addonsHomeDir.mkdirs();
        }

        if (success) {

            // reset for new view create
            addonsNames.clear();

            File directory = new File(String.valueOf(addonsHomeDir));
            File[] files = directory.listFiles();
            for (File file : files) {
                Timber.d("Addons:%s", file.getName());

                // Read package.json from
                // AnkiDroid/addons/ankidroid-addon-../package/package.json
                File addonsPackageDir = new File(file, "package");
                File addonsPackageJson = new File(addonsPackageDir, "package.json");

                JSONObject jsonObject = packageJsonReader(addonsPackageJson);

                AddonModel addonModel;
                if (isValidAddonPackage(jsonObject)) {

                    String addonName = jsonObject.optString("name", "");
                    String addonVersion = jsonObject.optString("version", "");
                    String addonDev = jsonObject.optString("author", "");
                    String addonAnkiDroidAPI = jsonObject.optString("ankidroid_js_api", "");
                    String addonHomepage = jsonObject.optString("homepage", "");
                    String typeOfAddon = jsonObject.optString("addon_type", "");

                    if (addonType.equals(typeOfAddon)) {
                        addonModel = new AddonModel(addonName, addonVersion, addonDev, addonAnkiDroidAPI, addonHomepage, addonType);
                        addonsNames.add(addonModel);
                    }
                }
            }

            addonsListRecyclerView.setAdapter(new AddonsAdapter(addonsNames, this));

            if (addonsNames.isEmpty()) {
                addonsMessageLayout.setVisibility(View.VISIBLE);
            } else {
                addonsMessageLayout.setVisibility(View.GONE);
            }

        } else {
            UIUtils.showThemedToast(this, getString(R.string.error_listing_addons), true);
        }
        hideProgressBar();
    }

    /*
      Get info about npm package from npm registry
     */
    public void getLatestPackageJson(String npmAddonName) {
        showProgressBar();

        String url = "https://registry.npmjs.org/" + npmAddonName + "/latest";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.e("js addon %s", e.toString());
            }


            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String strResponse = response.body().string();
                    runOnUiThread(() -> parseJsonData(strResponse, npmAddonName));
                } catch (IOException | NullPointerException e) {
                    Timber.e(e.getLocalizedMessage());
                }
            }
        });

    }

    /*
      Parse npm package info from package.json. If valid ankidroid-js-addon package then download it
     */
    public void parseJsonData(String strResponse, String npmAddonName) {
        try {
            Timber.d("json::%s", strResponse);

            JSONObject jsonObject = new JSONObject(strResponse);
            if (isValidAddonPackage(jsonObject)) {
                downloadAddonPackageFile(jsonObject, npmAddonName);
            } else {
                hideProgressBar();
                UIUtils.showThemedToast(getApplicationContext(), getString(R.string.not_valid_package), false);
            }

        } catch (JSONException e) {
            hideProgressBar();
            Timber.e(e.getLocalizedMessage());
            UIUtils.showThemedToast(getApplicationContext(), getString(R.string.not_valid_package), false);
        }
    }


    /*
      Download ankidroid-js-addon...tgz file from npmjs.org
      The download link parsed from info of npm package registry
     */
    public void downloadAddonPackageFile(JSONObject jsonObject, String npmAddonName) {
        if (Connection.isOnline()) {
            try {
                listAddonType = jsonObject.optString("addon_type", "");
                JSONObject dist = jsonObject.getJSONObject("dist");
                String tarballUrl = dist.get("tarball").toString();
                Timber.d("tarball link %s", tarballUrl);

                if (!tarballUrl.isEmpty() && tarballUrl.startsWith("https://") && tarballUrl.endsWith(".tgz")) {
                    downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(tarballUrl));
                    request.setTitle(npmAddonName);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, npmAddonName + ".tgz");

                    queueId = downloadManager.enqueue(request);
                    registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                } else {
                    hideProgressBar();
                    UIUtils.showThemedToast(getApplicationContext(), getString(R.string.not_valid_package), true);
                }

            } catch (JSONException e) {
                hideProgressBar();
                Timber.e(e.getLocalizedMessage());
                UIUtils.showThemedToast(getApplicationContext(), getString(R.string.error_downloading_file), true);
            }

        } else {
            hideProgressBar();
            UIUtils.showThemedToast(getApplicationContext(), getString(R.string.network_no_connection), true);
        }
    }


    /*
      Download the package in download directory, extract and copy to ANkiDroid/addons/...
     */
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(queueId);

                Cursor cursor = downloadManager.query(query);

                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                    if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                        String addonName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE));

                        File addonsDir = new File(addonsHomeDir, addonName);
                        String tarballPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                + "/" + Uri.parse(addonName).getPath() + ".tgz";
                        File tarballFile = new File(tarballPath);

                        if (tarballFile.exists()) {
                            // extracting using library https://github.com/thrau/jarchivelib
                            try {
                                Archiver archiver = ArchiverFactory.createArchiver(new File(tarballPath));
                                archiver.extract(tarballFile, addonsDir);

                                hideProgressBar();
                                listAddonsFromDir(listAddonType);
                                UIUtils.showThemedToast(context, getString(R.string.addon_download_success), true);
                            } catch (IOException e) {
                                Timber.e(e);
                                UIUtils.showThemedToast(context, getString(R.string.error_downloading_file), true);
                            } finally {
                                if (tarballFile.exists()) {
                                    tarballFile.delete();
                                }
                            }
                        } else {
                            hideProgressBar();
                        }

                    } else if (DownloadManager.STATUS_FAILED == cursor.getInt(columnIndex)) {
                        hideProgressBar();
                        downloadManager.remove(queueId);
                        UIUtils.showThemedToast(context, getString(R.string.error_downloading_file_check_name), false);
                    }
                }

            } else {
                hideProgressBar();
                downloadManager.remove(queueId);
                UIUtils.showThemedToast(context, getString(R.string.error_downloading_file_check_name), false);
            }
        }
    };


    /*
      read package.json file of ankidroid-js-addon...
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


    /*
      is package.json of ankidroid-js-addon... contains valid
      ankidroid_js_api==0.0.1 and keywords 'ankidroid-js-addon'
     */
    public boolean isValidAddonPackage(JSONObject jsonObject) {

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

    /*
      Show info about clicked addons with update and delete options
     */

    @Override
    public void onAddonClick(int position) {
        AddonModel addonModel = addonsNames.get(position);

        Dialog infoDialog = new Dialog(this);
        infoDialog.setCanceledOnTouchOutside(true);
        infoDialog.setContentView(R.layout.addon_info_popup);

        TextView name = infoDialog.findViewById(R.id.popup_addon_name_info);
        TextView ver = infoDialog.findViewById(R.id.popup_addon_version_info);
        TextView dev = infoDialog.findViewById(R.id.popup_addon_dev_info);
        TextView jsApi = infoDialog.findViewById(R.id.popup_js_api_info);
        TextView homepage = infoDialog.findViewById(R.id.popup_addon_homepage_info);
        Button buttonDelete = infoDialog.findViewById(R.id.btn_addon_delete);
        Button buttonUpdate = infoDialog.findViewById(R.id.btn_addon_update);

        name.setText(addonModel.getName());
        ver.setText(addonModel.getVersion());
        dev.setText(addonModel.getDeveloper());
        jsApi.setText(addonModel.getJsApiVersion());

        String link = "<a href='" + addonModel.getHomepage() + "'>" + addonModel.getHomepage() + "</a>";
        homepage.setClickable(true);
        homepage.setText(HtmlCompat.fromHtml(link, HtmlCompat.FROM_HTML_MODE_LEGACY));

        buttonDelete.setOnClickListener(v -> {

            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setTitle(addonModel.getName());
            alertBuilder.setMessage(getString(R.string.confirm_remove_addon, addonModel.getName()));
            alertBuilder.setCancelable(true);

            alertBuilder.setPositiveButton(
                    getString(R.string.dialog_ok),
                    (dialog, id) -> {
                        // remove the js addon folder
                        File dir = new File(addonsHomeDir, addonModel.getName());
                        deleteDirectory(dir);

                        // remove enabled status
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.remove(addonModel.getType() + "_addon:" + addonModel.getName());
                        editor.apply();

                        addonsNames.remove(position);
                        Objects.requireNonNull(addonsListRecyclerView.getAdapter()).notifyItemRemoved(position);
                        addonsListRecyclerView.getAdapter().notifyItemRangeChanged(position, addonsNames.size());
                        addonsListRecyclerView.getAdapter().notifyDataSetChanged();
                    });

            alertBuilder.setNegativeButton(
                    getString(R.string.dialog_cancel),
                    (dialog, id) -> dialog.cancel());

            AlertDialog deleteAlert = alertBuilder.create();
            deleteAlert.show();
            infoDialog.dismiss();
        });

        buttonUpdate.setOnClickListener(v -> {
            UIUtils.showThemedToast(this, getString(R.string.checking_addon_update), false);
            getLatestPackageJson(addonModel.getName());
        });

        infoDialog.show();
    }


    public static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                File child = new File(dir, children[i]);

                if (child.isDirectory()) {
                    deleteDirectory(child);
                    child.delete();
                } else {
                    child.delete();
                }
            }

            dir.delete();
        }
    }
}