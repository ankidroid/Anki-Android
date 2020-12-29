package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.themes.Themes;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import timber.log.Timber;

@SuppressWarnings("deprecation")
public class AddonsBrowser extends NavigationDrawerActivity implements DeckDropDownAdapter.SubtitleListener {

    private RecyclerView addonsList;
    @Nullable
    private Menu mActionBarMenu;
    private MenuItem mInstallAddon;
    private int ADDON_REQUEST_CODE = 100;

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

        // Add a home button to the actionbar
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(getResources().getText(R.string.addons));

        addonsList = (RecyclerView) findViewById(R.id.addons);
        addonsList.setLayoutManager(new LinearLayoutManager(this));

        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this);
        File addonsDir = new File(currentAnkiDroidDirectory, "addons" );

        boolean success = true;
        if (!addonsDir.exists()) {
            success = addonsDir.mkdirs();
        }

        List<AddonModel> addonsNames = new ArrayList<AddonModel>();

        if (success) {
            File directory = new File(String.valueOf(addonsDir));
            File[] files = directory.listFiles();
            for (int i = 0; i < files.length; i++) {
                Timber.d("Addons:%s", files[i].getName());

                File addonsFiles = new File(files[i], "manifest.json" );

                AddonModel addonModel;

                if (addonsFiles.exists()) {
                   Timber.d("Exist");

                   try {
                       FileReader fileReader = new FileReader(addonsFiles);
                       BufferedReader bufferedReader = new BufferedReader(fileReader);
                       StringBuilder stringBuilder = new StringBuilder();
                       String line = bufferedReader.readLine();
                       while (line != null){
                           stringBuilder.append(line).append("\n");
                           line = bufferedReader.readLine();
                       }
                       bufferedReader.close();

                       String response = stringBuilder.toString();

                       JSONObject jsonObject  = new JSONObject(response);
                       String addonId = jsonObject.optString("id", "");
                       String addonName = jsonObject.optString("name", "");
                       String addonVersion = jsonObject.optString("version", "");
                       String addonDev = jsonObject.optString("developer", "");
                       String addonAnkiDroidAPI = jsonObject.optString("ankidroid_api", "");

                       addonModel = new AddonModel(addonId, addonName, addonVersion, addonDev, addonAnkiDroidAPI);
                       addonsNames.add(addonModel);
                   } catch (IOException | JSONException e) {
                       e.printStackTrace();
                   }
                }
            }

            addonsList.setAdapter(new AddonsAdapter(addonsNames));
            hideProgressBar();
        } else {
            UIUtils.showThemedToast(this, getString(R.string.error_listing_addons), true);
        }
    }


    @Override
    public String getSubtitleText() {
        return getResources().getString(R.string.addons);
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mActionBarMenu = menu;
        getMenuInflater().inflate(R.menu.addon_browser, menu);
        mInstallAddon = menu.findItem(R.id.action_install_addon);
        mInstallAddon.setOnMenuItemClickListener(item -> {

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent = Intent.createChooser(intent, "Choose a file");
            startActivityForResult(intent, ADDON_REQUEST_CODE);

            return true;
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ADDON_REQUEST_CODE) {
            if(resultCode == Activity.RESULT_OK){
                Uri selectedFileuri = data.getData();

                // Getting addon file path using RealPathUtil
                String addonFilePath = RealPathUtil.getRealPath(this, selectedFileuri);

                if (addonFilePath.endsWith(".jsaddon")) {
                    try {
                        startInstallAddon(addonFilePath);
                    } catch (IOException e) {
                        Timber.e(e.getLocalizedMessage(), "AddonBrowser::onActivityResult");
                    }
                } else {
                    UIUtils.showThemedToast(this, getString(R.string.not_valid_jsaddon), false);
                }
            }
        }
    }


    private void startInstallAddon(String addonFile) throws IOException {
        File file = new File(addonFile);

        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this);
        File addonsDir = new File(currentAnkiDroidDirectory, "addons" );

        // Extract .jsaddon zip file to AnkiDroid/addons folder
        unzip(file, addonsDir);
        UIUtils.showThemedToast(this, getString(R.string.importing_addon), false);
    }


    // https://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android/27050680#27050680
    // Extract .jsaddon zip file
    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));

        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];

            while ((ze = zis.getNextEntry()) != null) {

                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();

                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new FileNotFoundException("Failed to ensure directory: " + dir.getAbsolutePath());
                }

                if (ze.isDirectory()) {
                    continue;
                }

                FileOutputStream fout = new FileOutputStream(file);

                try {
                    while ((count = zis.read(buffer)) != -1) {
                        fout.write(buffer, 0, count);
                    }
                } finally {
                    fout.close();
                }
            }
        } finally {
            zis.close();
        }
    }

}