package com.ichi2.anki;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Switch;

import com.ichi2.anki.widgets.DeckDropDownAdapter;
import com.ichi2.themes.Themes;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import timber.log.Timber;

public class AddonsBrowser extends NavigationDrawerActivity implements DeckDropDownAdapter.SubtitleListener {

    private RecyclerView addonsList;

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
}