package com.ichi2.anki;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.utils.StringUtil;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.RIGHT;

@SuppressWarnings("deprecation")
public class AddonsBrowser extends NavigationDrawerActivity implements DeckDropDownAdapter.SubtitleListener, AddonsAdapter.OnAddonClickListener {

    private RecyclerView addonsListRecyclerView;

    private File addonsHomeDir;
    private Dialog downloadDialog;

    private final List<AddonModel> addonsNames = new ArrayList<AddonModel>();

    private SharedPreferences preferences;

    private final String addonType = "reviewer";

    private LinearLayout addonsDownloadButton;

    AddonsNpmUtility npmUtility;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        super.onCreate(savedInstanceState);
        Timber.d("onCreate()");
        setContentView(R.layout.addons_browser);
        initNavigationDrawer(findViewById(android.R.id.content));

        // Add a home button to the actionbar
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.reviewer_addons));
        showBackIcon();

        npmUtility = new AddonsNpmUtility(AddonsBrowser.this, this);

        addonsDownloadButton = findViewById(R.id.addons_download_message_ll);
        addonsDownloadButton.setOnClickListener(v -> {
            openUrl(Uri.parse(getString(R.string.ankidroid_js_addon_npm_search_url)));
        });

        addonsListRecyclerView = findViewById(R.id.addons);
        addonsListRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        addonsListRecyclerView.addItemDecoration(dividerItemDecoration);

        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this);
        addonsHomeDir = new File(currentAnkiDroidDirectory, "addons");

        preferences = AnkiDroidApp.getSharedPrefs(this);

        listAddonsFromDir(addonType);
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
        MenuItem installAddon, getMoreAddons, reviewerAddons;

        getMenuInflater().inflate(R.menu.addon_browser, menu);
        installAddon = menu.findItem(R.id.action_install_addon);
        getMoreAddons = menu.findItem(R.id.action_get_more_addons);
        reviewerAddons = menu.findItem(R.id.action_addon_reviewer);

        reviewerAddons.setOnMenuItemClickListener(item -> {
            getSupportActionBar().setTitle(getString(R.string.reviewer_addons));
            listAddonsFromDir(addonType);
            return true;
        });

        downloadDialog = new Dialog(this);

        installAddon.setOnMenuItemClickListener(item -> {

            downloadDialog.setCanceledOnTouchOutside(true);
            downloadDialog.setContentView(R.layout.addon_install_from_npm);

            EditText downloadEditText = downloadDialog.findViewById(R.id.addon_download_edit_text);
            Button downloadButton = downloadDialog.findViewById(R.id.addon_download_button);

            downloadButton.setOnClickListener(v -> {
                String npmAddonName = downloadEditText.getText().toString();

                // if string is:  npm i ankidroid-js-addon-progress-bar
                npmAddonName = StringUtil.trimRight("npm i");
                if (npmAddonName.startsWith("npm i")) {
                    npmAddonName = npmAddonName.substring("npm i".length());
                }

                // if containing space
                npmAddonName = npmAddonName.trim();
                npmAddonName = npmAddonName.replaceAll("\u00A0", "");

                if (npmAddonName.isEmpty()) {
                    return;
                }

                UIUtils.showThemedToast(this, getString(R.string.downloading_addon), true);
                // download npm package for AnkiDroid as addons
                npmUtility.getPackageJson(npmAddonName);

                downloadDialog.dismiss();

            });

            downloadDialog.show();
            return true;
        });

        getMoreAddons.setOnMenuItemClickListener(item -> {
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

            File[] files = addonsHomeDir.listFiles();
            for (File file : files) {
                Timber.d("Addons: %s", file.getName());

                // Read package.json from
                // AnkiDroid/addons/ankidroid-addon-../package/package.json
                File addonsPackageJson = new File(file, "package/package.json");

                JSONObject jsonObject = AddonsNpmUtility.packageJsonReader(addonsPackageJson);

                // if valid addon then return addonModel else return null
                AddonModel addonModel = AddonModel.isValidAddonPackage(jsonObject, addonType);
                if (addonModel == null) {
                    continue;
                }
                addonsNames.add(addonModel);
            }

            addonsListRecyclerView.setAdapter(new AddonsAdapter(addonsNames, this));

            addonsDownloadButton.setVisibility(addonsNames.isEmpty() ? View.VISIBLE : View.GONE);

        } else {
            UIUtils.showThemedToast(this, getString(R.string.error_listing_addons), false);
        }

        hideProgressBar();
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

            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this)
                    .setTitle(addonModel.getName())
                    .setMessage(getString(R.string.confirm_remove_addon, addonModel.getName()))
                    .setCancelable(true)
                    .setNegativeButton(getString(R.string.dialog_cancel), (dialog, id) -> dialog.cancel());

            alertBuilder.setPositiveButton(
                    getString(R.string.dialog_ok),
                    (dialog, id) -> {
                        // remove the js addon folder
                        File dir = new File(addonsHomeDir, addonModel.getName());
                        BackupManager.removeDir(dir);

                        // remove enabled status
                        addonModel.updatePrefs(preferences, AddonModel.getReviewerAddonKey(), addonModel.getName(), true);

                        addonsNames.remove(position);
                        addonsListRecyclerView.getAdapter().notifyItemRangeChanged(position, addonsNames.size());
                        addonsListRecyclerView.getAdapter().notifyDataSetChanged();

                        if (addonsNames.isEmpty()) {
                            addonsDownloadButton.setVisibility(View.VISIBLE);
                        } else {
                            addonsDownloadButton.setVisibility(View.GONE);
                        }

                    });

            alertBuilder.show();
            infoDialog.dismiss();
        });

        buttonUpdate.setOnClickListener(v -> {
            UIUtils.showThemedToast(this, getString(R.string.checking_addon_update), false);
            npmUtility.getPackageJson(addonModel.getName());
        });

        infoDialog.show();
    }
}