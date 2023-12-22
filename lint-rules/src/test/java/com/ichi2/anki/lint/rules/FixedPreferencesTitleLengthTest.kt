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
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Test

class FixedPreferencesTitleLengthTest {
    companion object {
        @Language("XML")
        val stringsXmlValid = """<resources>
    <string name="app_name" maxLength="41">app_name</string>
    <string name="pref_cat_general_summ">pref_cat_general_summ</string>
    <string name="button_sync" maxLength="41">button_sync</string>
    <string name="sync_account" maxLength="41">sync_account</string>
    <string name="sync_account_summ_logged_out">sync_account_summ</string>
    <string name="sync_fetch_missing_media_summ">sync_fetch_missing</string>
    <string name="less_characters_than_limit" maxLength="41">Less than limit</string></resources>"""

        @Language("XML")
        val stringsXmlInvalid = """<resources>
<!--Not contains maxLength=41 attribute-->
    <string name="app_name">app_name</string>
    <string name="pref_cat_general_summ">pref_cat_general_summ</string>
<!--Not contains maxLength attribute but having value 55-->
    <string name="button_sync" maxLength="55">button_sync</string>
<!--This is correct because it contains maxLength=41 and character length is less than 42-->
    <string name="sync_account" maxLength="41">sync_account</string>
    <string name="sync_account_summ_logged_out">sync_account_summ</string>
    <string name="sync_fetch_missing_media_summ">sync_fetch_missing</string>
<!--Not contains maxLength=41 attribute but character length is greater than 32 and contains UTF-8 character-->
    <string name="more_characters_than_limit"  maxLength="41">This String contains character more than limit £</string>
</resources>"""

        @Language("XML")
        val invalidString =
            """<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
                    android:title="@string/app_name"
                    android:summary="@string/pref_cat_general_summ" >
                    <PreferenceCategory android:title="@string/button_sync" >
                        <Preference
                            android:dialogTitle="@string/sync_account"
                            android:key="syncAccount"
                            android:summary="@string/sync_account_summ_logged_out"
                            android:title="@string/sync_account" >
                            <intent
                                android:targetClass="com.ichi2.anki.MyAccount"
                                android:targetPackage="com.ichi2.anki" />
                        </Preference>
                        <CheckBoxPreference
                            android:defaultValue="true"
                            android:key="syncFetchesMedia"
                            android:summary="@string/sync_fetch_missing_media_summ"
                            android:title = "@string/more_characters_than_limit" />   
                     </PreferenceCategory>
                </PreferenceScreen>"""

        @Language("XML")
        val validString =
            """<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    android:title="@string/app_name"
    android:summary="@string/pref_cat_general_summ" >
    <PreferenceCategory android:title="@string/button_sync" >
        <Preference
            android:dialogTitle="@string/sync_account"
            android:key="syncAccount"
            android:summary="@string/sync_account_summ_logged_out"
            android:title="@string/sync_account" >
            <intent
                android:targetClass="com.ichi2.anki.MyAccount"
                android:targetPackage="com.ichi2.anki" />
        </Preference>
        <CheckBoxPreference
            android:defaultValue="true"
            android:key="syncFetchesMedia"
            android:summary="@string/sync_fetch_missing_media_summ"
            android:title="@string/less_characters_than_limit" />   </PreferenceCategory></PreferenceScreen>"""
    }

    @Test
    fun showsErrorForInvalidFile() {
        TestLintTask.lint().allowMissingSdk()
            .allowCompilationErrors()
            .files(
                TestFiles.xml("res/xml/preference_general_invalid.xml", invalidString),
                TestFiles.xml("res/values/10-preferences.xml", stringsXmlInvalid),
            )
            .issues(
                FixedPreferencesTitleLength.ISSUE_TITLE_LENGTH,
                FixedPreferencesTitleLength.ISSUE_MAX_LENGTH,
            )
            .run()
            .expectErrorCount(3)
            .expect(
                """res/values/10-preferences.xml:12: Error: Preference title 'more_characters_than_limit' must be less than 41 characters (currently 48) [FixedPreferencesTitleLength]
    <string name="more_characters_than_limit"  maxLength="41">This String contains character more than limit £</string>
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
res/values/10-preferences.xml:3: Error: Preference title 'app_name' is missing "maxLength=41" attribute [PreferencesTitleMaxLengthAttr]
    <string name="app_name">app_name</string>
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
res/values/10-preferences.xml:6: Error: Preference title 'button_sync' is having maxLength=55 it should contain maxLength=41 [PreferencesTitleMaxLengthAttr]
    <string name="button_sync" maxLength="55">button_sync</string>
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
3 errors, 0 warnings""",
            )
    }

    @Test
    fun showsNoErrorForValidFile() {
        TestLintTask.lint().allowMissingSdk()
            .allowCompilationErrors()
            .files(
                TestFiles.xml("res/xml/preference_general_valid.xml", validString),
                TestFiles.xml("res/values/10-preferences.xml", stringsXmlValid),
            )
            .issues(
                FixedPreferencesTitleLength.ISSUE_MAX_LENGTH,
                FixedPreferencesTitleLength.ISSUE_TITLE_LENGTH,
            )
            .run()
            .expectClean()
    }
}
