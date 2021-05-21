package com.ichi2.anki;

import com.ichi2.anki.tests.InstrumentedTest;
import com.ichi2.anki.tests.Shared;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@RunWith(AndroidJUnit4.class)
public class AddonModelTest extends InstrumentedTest {
    private JSONObject progressBarJsonObject;
    private JSONObject emptyPackageJsonObject;
    private JSONObject keywordsEmptyJsonObject;

    @Before
    public void setUp() throws IOException {
        String progressBarPackageJsonPath = Shared.getTestFilePath(getTestContext(), "addons/ankidroid-js-addon-all-key-empty/package/package.json");
        String allEmptyKeyPackageJsonPath = Shared.getTestFilePath(getTestContext(), "addons/ankidroid-js-addon-progress-bar/package/package.json");
        String keywordsEmptyPackageJsonPath = Shared.getTestFilePath(getTestContext(), "addons/ankidroid-js-addon-empty-keywords/package/package.json");

        progressBarJsonObject = AddonsNpmUtility.packageJsonReader(new File(progressBarPackageJsonPath));
        emptyPackageJsonObject = AddonsNpmUtility.packageJsonReader(new File(allEmptyKeyPackageJsonPath));
        keywordsEmptyJsonObject = AddonsNpmUtility.packageJsonReader(new File(keywordsEmptyPackageJsonPath));
    }

    @Test
    public void isValidAddons() {
        AddonModel addonModel = AddonModel.tryParse(progressBarJsonObject, "reviewer");
        assertThat(addonModel.getName(), is("ankidroid-js-addon-progress-bar"));
        assertThat(addonModel.getVersion(), is("1.0.5"));
        assertThat(addonModel.getJsApiVersion(), is("0.0.1"));
        assertThat(addonModel.getType(), is("reviewer"));
        assertThat(addonModel.getDeveloper(), is("infinyte7"));
    }

    @Test
    public void testNullAddons() {
        AddonModel addonModel1 = AddonModel.isValidAddonPackage(emptyPackageJsonObject, "reviewer");
        AddonModel addonModel2 = AddonModel.isValidAddonPackage(keywordsEmptyJsonObject, "reviewer");

        assertThat(addonModel1, is(nullValue()));
        assertThat(addonModel2, is(nullValue()));
    }
}
