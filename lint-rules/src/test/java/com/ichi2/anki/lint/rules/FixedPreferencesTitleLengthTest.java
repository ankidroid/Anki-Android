/*
 *  Copyright (c) 2021 Prateek Singh <prateeksingh3212@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.lint.rules;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestFiles.xml;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;

public class FixedPreferencesTitleLengthTest {

    @Language("XML")
    public static final String stringsXmlValid = "" +
            "<resources>\n" +
            "    <string name=\"app_name\" maxLength=\"41\">app_name</string>\n" +
            "    <string name=\"pref_cat_general_summ\">pref_cat_general_summ</string>\n" +
            "    <string name=\"button_sync\" maxLength=\"41\">button_sync</string>\n" +
            "    <string name=\"sync_account\" maxLength=\"41\">sync_account</string>\n" +
            "    <string name=\"sync_account_summ_logged_out\">sync_account_summ</string>\n" +
            "    <string name=\"sync_fetch_missing_media_summ\">sync_fetch_missing</string>\n" +
            "    <string name=\"less_characters_than_limit\" maxLength=\"41\">Less than limit</string>" +
            "</resources>";

    @Language("XML")
    public static final String stringsXmlInvalid = "" +
            "<resources>\n" +
            "<!--Not contains maxLength=41 attribute-->\n" +
            "    <string name=\"app_name\">app_name</string>\n" +
            "    <string name=\"pref_cat_general_summ\">pref_cat_general_summ</string>\n" +
            "<!--Not contains maxLength attribute but having value 55-->\n" +
            "    <string name=\"button_sync\" maxLength=\"55\">button_sync</string>\n" +
            "<!--This is correct because it contains maxLength=41 and character length is less than 42-->\n" +
            "    <string name=\"sync_account\" maxLength=\"41\">sync_account</string>\n" +
            "    <string name=\"sync_account_summ_logged_out\">sync_account_summ</string>\n" +
            "    <string name=\"sync_fetch_missing_media_summ\">sync_fetch_missing</string>\n" +
            "<!--Not contains maxLength=41 attribute but character length is greater than 32 and contains UTF-8 character-->\n" +
            "    <string name=\"more_characters_than_limit\"  maxLength=\"41\">This String contains character more than limit \u00A3</string>\n" +
            "</resources>";

    @Language("XML")
    public static final String invalidString = "" +
            "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:title=\"@string/app_name\"\n" +
            "    android:summary=\"@string/pref_cat_general_summ\" >\n" +
            "    <PreferenceCategory android:title=\"@string/button_sync\" >\n" +
            "        <Preference\n" +
            "            android:dialogTitle=\"@string/sync_account\"\n" +
            "            android:key=\"syncAccount\"\n" +
            "            android:summary=\"@string/sync_account_summ_logged_out\"\n" +
            "            android:title=\"@string/sync_account\" >\n" +
            "            <intent\n" +
            "                android:targetClass=\"com.ichi2.anki.MyAccount\"\n" +
            "                android:targetPackage=\"com.ichi2.anki\" />\n" +
            "        </Preference>\n" +
            "        <CheckBoxPreference\n" +
            "            android:defaultValue=\"true\"\n" +
            "            android:key=\"syncFetchesMedia\"\n" +
            "            android:summary=\"@string/sync_fetch_missing_media_summ\"\n" +
            "            android:title = \"@string/more_characters_than_limit\" />" +
            "   </PreferenceCategory>" +
            "</PreferenceScreen>";


    @Language("XML")
    public static final String validString = "" +
            "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:title=\"@string/app_name\"\n" +
            "    android:summary=\"@string/pref_cat_general_summ\" >\n" +
            "    <PreferenceCategory android:title=\"@string/button_sync\" >\n" +
            "        <Preference\n" +
            "            android:dialogTitle=\"@string/sync_account\"\n" +
            "            android:key=\"syncAccount\"\n" +
            "            android:summary=\"@string/sync_account_summ_logged_out\"\n" +
            "            android:title=\"@string/sync_account\" >\n" +
            "            <intent\n" +
            "                android:targetClass=\"com.ichi2.anki.MyAccount\"\n" +
            "                android:targetPackage=\"com.ichi2.anki\" />\n" +
            "        </Preference>\n" +
            "        <CheckBoxPreference\n" +
            "            android:defaultValue=\"true\"\n" +
            "            android:key=\"syncFetchesMedia\"\n" +
            "            android:summary=\"@string/sync_fetch_missing_media_summ\"\n" +
            "            android:title=\"@string/less_characters_than_limit\" />" +
            "   </PreferenceCategory>" +
            "</PreferenceScreen>";


    @Test
    public void showsErrorForInvalidFile() {
        lint().allowMissingSdk()
                .allowCompilationErrors()
                .files(xml("res/xml/preference_general_invalid.xml", invalidString), xml("res/values/10-preferences.xml", stringsXmlInvalid))
                .issues(FixedPreferencesTitleLength.ISSUE_TITLE_LENGTH, FixedPreferencesTitleLength.ISSUE_MAX_LENGTH)
                .run()
                .expectErrorCount(3)
                .expect("res/values/10-preferences.xml:12: Error: Preference title 'more_characters_than_limit' must be less than 41 characters (currently 48) [FixedPreferencesTitleLength]\n" +
                        "    <string name=\"more_characters_than_limit\"  maxLength=\"41\">This String contains character more than limit \u00A3</string>\n" +
                        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "res/values/10-preferences.xml:3: Error: Preference title 'app_name' is missing \"maxLength=41\" attribute [PreferencesTitleMaxLengthAttr]\n" +
                        "    <string name=\"app_name\">app_name</string>\n" +
                        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "res/values/10-preferences.xml:6: Error: Preference title 'button_sync' is having maxLength=55 it should contain maxLength=41 [PreferencesTitleMaxLengthAttr]\n" +
                        "    <string name=\"button_sync\" maxLength=\"55\">button_sync</string>\n" +
                        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "3 errors, 0 warnings");
    }


    @Test
    public void showsNoErrorForValidFile() {
        lint().allowMissingSdk()
                .allowCompilationErrors()
                .files(xml("res/xml/preference_general_valid.xml", validString), xml("res/values/10-preferences.xml", stringsXmlValid))
                .issues(FixedPreferencesTitleLength.ISSUE_MAX_LENGTH , FixedPreferencesTitleLength.ISSUE_TITLE_LENGTH)
                .run()
                .expectClean();
    }
}