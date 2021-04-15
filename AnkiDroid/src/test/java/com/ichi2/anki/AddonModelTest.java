package com.ichi2.anki;

import com.ichi2.utils.JSONObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@RunWith(AndroidJUnit4.class)
public class AddonModelTest extends RobolectricTest {
    private static final String progrssBarAddonPackageJson = "{\n" +
            "  \"name\": \"ankidroid-js-addon-progress-bar\",\n" +
            "  \"version\": \"1.0.5\",\n" +
            "  \"description\": \"Show progress bar in AnkiDroid, this package may not be used in node_modules. For using this addon view. https://github.com/ankidroid/Anki-Android/pull/7958\",\n" +
            "  \"main\": \"index.js\",\n" +
            "  \"ankidroid_js_api\": \"0.0.1\",\n" +
            "  \"addon_type\": \"reviewer\",\n" +
            "  \"scripts\": {\n" +
            "    \"test\": \"echo \\\"Error: no test specified\\\" && exit 1\"\n" +
            "  },\n" +
            "  \"repository\": {\n" +
            "    \"type\": \"git\",\n" +
            "    \"url\": \"git+https://github.com/infinyte7/Anki-Custom-Card-Layout.git\"\n" +
            "  },\n" +
            "  \"keywords\": [\n" +
            "    \"ankidroid-js-addon\"\n" +
            "  ],\n" +
            "  \"author\": \"infinyte7\",\n" +
            "  \"license\": \"MIT\",\n" +
            "  \"bugs\": {\n" +
            "    \"url\": \"https://github.com/infinyte7/Anki-Custom-Card-Layout/issues\"\n" +
            "  },\n" +
            "  \"homepage\": \"https://github.com/infinyte7/Anki-Custom-Card-Layout#readme\"\n" +
            "}\n";

    /*
      keywords not present
      "keywords": [
        "ankidroid-js-addon"
      ]
     */
    private static final String keywordsEmptyAddonPackageJson =  "{\n" +
            "  \"name\": \"ankidroid-js-addon-test-addon-1\",\n" +
            "  \"version\": \"1.0.1\",\n" +
            "  \"description\": \"Keywords not present\",\n" +
            "  \"main\": \"index.js\",\n" +
            "  \"ankidroid_js_api\": \"0.0.1\",\n" +
            "  \"addon_type\": \"reviewer\",\n" +
            "  \"scripts\": {\n" +
            "    \"test\": \"echo \\\"Error: no test specified\\\" && exit 1\"\n" +
            "  },\n" +
            "  \"repository\": {\n" +
            "    \"type\": \"git\",\n" +
            "    \"url\": \"git+https://github.com/infinyte7/ankidroid-js-addon.git\"\n" +
            "  },\n" +
            "  \"keywords\": [\n" +
            "  ],\n" +
            "  \"author\": \"Mani (infinyte7)\",\n" +
            "  \"license\": \"MIT\",\n" +
            "  \"bugs\": {\n" +
            "    \"url\": \"https://github.com/infinyte7/ankidroid-js-addon/issues\"\n" +
            "  },\n" +
            "  \"homepage\": \"https://github.com/infinyte7/ankidroid-js-addon#readme\"\n" +
            "}\n";

    /*
      all fields empty, only these fields checked for validity of addons
      name, version, ankidroid_js_api, addon_type, author, homepage
     */

    private static final String emptyAddonPackageJson = "{\n" +
            "  \"name\": \"\",\n" +
            "  \"version\": \"\",\n" +
            "  \"description\": \"\",\n" +
            "  \"main\": \"index.js\",\n" +
            "  \"ankidroid_js_api\": \"\",\n" +
            "  \"addon_type\": \"\",\n" +
            "  \"scripts\": {\n" +
            "    \"test\": \"echo \\\"Error: no test specified\\\" && exit 1\"\n" +
            "  },\n" +
            "  \"repository\": {\n" +
            "    \"type\": \"git\",\n" +
            "    \"url\": \"git+https://github.com/infinyte7/ankidroid-js-addon.git\"\n" +
            "  },\n" +
            "  \"keywords\": [\n" +
            "    \"ankidroid-js-addon\"\n" +
            "  ],\n" +
            "  \"author\": \"\",\n" +
            "  \"license\": \"MIT\",\n" +
            "  \"bugs\": {\n" +
            "    \"url\": \"https://github.com/infinyte7/ankidroid-js-addon/issues\"\n" +
            "  },\n" +
            "  \"homepage\": \"\"\n" +
            "}\n";

    private JSONObject progressBarJsonObject;
    private JSONObject emptyPackageJsonObject;
    private JSONObject keywordsEmptyJsonObject;

    @Before
    public void setUp() {
        progressBarJsonObject = new JSONObject(progrssBarAddonPackageJson);
        emptyPackageJsonObject = new JSONObject(emptyAddonPackageJson);
        keywordsEmptyJsonObject = new JSONObject(keywordsEmptyAddonPackageJson);
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
        AddonModel addonModel1 = AddonModel.tryParse(emptyPackageJsonObject, "reviewer");
        AddonModel addonModel2 = AddonModel.tryParse(keywordsEmptyJsonObject, "reviewer");

        assertThat(addonModel1, is(nullValue()));
        assertThat(addonModel2, is(nullValue()));
    }
}
