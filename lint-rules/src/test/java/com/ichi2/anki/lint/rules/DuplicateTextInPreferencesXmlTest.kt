package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.ichi2.anki.lint.utils.KotlinCleanup
import org.intellij.lang.annotations.Language
import org.junit.Test

@KotlinCleanup("Use kotlin multiline strings but make sure the tests pass!")
class DuplicateTextInPreferencesXmlTest {
    @Language("XML")
    private val invalidXmlFile = "" +
        "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
        "    xmlns:app=\"http://arbitrary.app.namespace/com.ichi2.anki\"" +
        "    android:title=\"@string/pref_cat_advanced_summ\"                           " +
        "    android:summary=\"@string/pref_cat_advanced_summ\"                         " +
        "    android:key=\"pref_screen_advanced\">                                      " +
        "        <EditTextPreference                                                    " +
        "            android:defaultValue=\"/sdcard/AnkiDroid\"                         " +
        "            android:key=\"deckPath\"                                           " +
        "            android:summary=\"@string/preference_summary_literal\"             " +
        "            android:title=\"@string/preference_summary_literal\" />            " +
        "        <com.ichi2.ui.ConfirmationPreference                                   " +
        "            android:key=\"force_full_sync\"                                    " +
        "            android:title=\"@string/force_full_sync_title\"                    " +
        "            android:summary=\"@string/force_full_sync_summary\" />             " +
        "</PreferenceScreen>"

    @Language("XML")
    private val validXmlFile = "" +
        "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
        "    xmlns:app=\"http://arbitrary.app.namespace/com.ichi2.anki\"" +
        "    android:title=\"@string/pref_cat_advanced\"                                " +
        "    android:summary=\"@string/pref_cat_advanced_summ\"                         " +
        "    android:key=\"pref_screen_advanced\">                                      " +
        "        <EditTextPreference                                                    " +
        "            android:defaultValue=\"/sdcard/AnkiDroid\"                         " +
        "            android:key=\"deckPath\"                                           " +
        "            android:summary=\"@string/preference_summary_literal\"             " +
        "            android:title=\"@string/preference_literal\" />                    " +
        "        <com.ichi2.ui.ConfirmationPreference                                   " +
        "            android:key=\"force_full_sync\"                                    " +
        "            android:title=\"@string/force_full_sync_title\"                    " +
        "            android:summary=\"@string/force_full_sync_summary\" />             " +
        "</PreferenceScreen>"

    @Test
    fun showsErrorForInvalidFile() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/xml/invalidpreference.xml", invalidXmlFile))
            .issues(DuplicateTextInPreferencesXml.ISSUE)
            .run()
            .expectErrorCount(2)
            .expect(
                """res/xml/invalidpreference.xml:1: Error: Do not use the same string for the title and summary of a preference [DuplicateTextInPreferencesXml]
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"    xmlns:app="http://arbitrary.app.namespace/com.ichi2.anki"    android:title="@string/pref_cat_advanced_summ"                               android:summary="@string/pref_cat_advanced_summ"                             android:key="pref_screen_advanced">                                              <EditTextPreference                                                                android:defaultValue="/sdcard/AnkiDroid"                                     android:key="deckPath"                                                       android:summary="@string/preference_summary_literal"                         android:title="@string/preference_summary_literal" />                    <com.ichi2.ui.ConfirmationPreference                                               android:key="force_full_sync"                                                android:title="@string/force_full_sync_title"                                android:summary="@string/force_full_sync_summary" />             </PreferenceScreen>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
res/xml/invalidpreference.xml:1: Error: Do not use the same string for the title and summary of a preference [DuplicateTextInPreferencesXml]
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"    xmlns:app="http://arbitrary.app.namespace/com.ichi2.anki"    android:title="@string/pref_cat_advanced_summ"                               android:summary="@string/pref_cat_advanced_summ"                             android:key="pref_screen_advanced">                                              <EditTextPreference                                                                android:defaultValue="/sdcard/AnkiDroid"                                     android:key="deckPath"                                                       android:summary="@string/preference_summary_literal"                         android:title="@string/preference_summary_literal" />                    <com.ichi2.ui.ConfirmationPreference                                               android:key="force_full_sync"                                                android:title="@string/force_full_sync_title"                                android:summary="@string/force_full_sync_summary" />             </PreferenceScreen>
                                                                                                                                                                                                                                                                                                                                                                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
2 errors, 0 warnings"""
            )
    }

    @Test
    fun showsNoErrorForValidFile() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/xml/validpreference.xml", validXmlFile))
            .issues(DuplicateTextInPreferencesXml.ISSUE)
            .run()
            .expectClean()
    }
}
