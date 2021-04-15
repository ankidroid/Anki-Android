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

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.END;

@SuppressWarnings("deprecation")
public class AddonsBrowser extends NavigationDrawerActivity implements DeckDropDownAdapter.SubtitleListener, AddonsAdapter.OnAddonClickListener {

    private RecyclerView mAddonsListRecyclerView;

    private File mAddonsHomeDir;
    private Dialog mDownloadDialog;

    private final List<AddonModel> mAddonsNames = new ArrayList<AddonModel>();

    private SharedPreferences mPreferences;

    private final String mADDON_TYPE = "reviewer";

    private LinearLayout mAddonsDownloadButton;

    AddonsNpmUtility mNpmUtility;


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

        mNpmUtility = new AddonsNpmUtility(AddonsBrowser.this, this);

        mAddonsDownloadButton = findViewById(R.id.addons_download_message_ll);
        mAddonsDownloadButton.setOnClickListener(v -> {
            openUrl(Uri.parse(getString(R.string.ankidroid_js_addon_npm_search_url)));
        });

        mAddonsListRecyclerView = findViewById(R.id.addons);
        mAddonsListRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mAddonsListRecyclerView.addItemDecoration(dividerItemDecoration);

        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this);
        mAddonsHomeDir = new File(currentAnkiDroidDirectory, "addons");

        mPreferences = AnkiDroidApp.getSharedPrefs(this);

        listAddonsFromDir(mADDON_TYPE);
    }


    @Override
    public String getSubtitleText() {
        return getResources().getString(R.string.addons);
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishWithAnimation(END);

        if (mDownloadDialog.isShowing()) {
            mDownloadDialog.dismiss();
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
            listAddonsFromDir(mADDON_TYPE);
            return true;
        });

        mDownloadDialog = new Dialog(this);

        installAddon.setOnMenuItemClickListener(item -> {

            mDownloadDialog.setCanceledOnTouchOutside(true);
            mDownloadDialog.setContentView(R.layout.addon_install_from_npm);

            EditText downloadEditText = mDownloadDialog.findViewById(R.id.addon_download_edit_text);
            Button downloadButton = mDownloadDialog.findViewById(R.id.addon_download_button);

            downloadButton.setOnClickListener(v -> {
                String npmAddonName = downloadEditText.getText().toString();

                // if string is:  npm i ankidroid-js-addon-progress-bar
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
                mNpmUtility.getPackageJson(npmAddonName, () -> runOnUiThread(() -> listAddonsFromDir(mADDON_TYPE)));

                mDownloadDialog.dismiss();

            });

            mDownloadDialog.show();
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
        if (!mAddonsHomeDir.exists()) {
            success = mAddonsHomeDir.mkdirs();
        }

        if (success) {

            // reset for new view create
            mAddonsNames.clear();

            File[] files = mAddonsHomeDir.listFiles();
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
                mAddonsNames.add(addonModel);
            }

            mAddonsListRecyclerView.setAdapter(new AddonsAdapter(mAddonsNames, this));

            mAddonsDownloadButton.setVisibility(mAddonsNames.isEmpty() ? View.VISIBLE : View.GONE);

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
        AddonModel addonModel = mAddonsNames.get(position);

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
                        File dir = new File(mAddonsHomeDir, addonModel.getName());
                        BackupManager.removeDir(dir);

                        // remove enabled status
                        addonModel.updatePrefs(mPreferences, AddonModel.getReviewerAddonKey(), addonModel.getName(), true);

                        mAddonsNames.remove(position);
                        mAddonsListRecyclerView.getAdapter().notifyItemRangeChanged(position, mAddonsNames.size());
                        mAddonsListRecyclerView.getAdapter().notifyDataSetChanged();

                        if (mAddonsNames.isEmpty()) {
                            mAddonsDownloadButton.setVisibility(View.VISIBLE);
                        } else {
                            mAddonsDownloadButton.setVisibility(View.GONE);
                        }

                    });

            alertBuilder.show();
            infoDialog.dismiss();
        });

        buttonUpdate.setOnClickListener(v -> {
            UIUtils.showThemedToast(this, getString(R.string.checking_addon_update), false);
            mNpmUtility.getPackageJson(addonModel.getName(), () -> runOnUiThread(() -> listAddonsFromDir(mADDON_TYPE)));
        });

        infoDialog.show();
    }
}