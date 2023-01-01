/****************************************************************************************
 * Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Test

class DuplicateTextInPreferencesXmlTest {
    @Language("XML")
    private val invalidXmlFile =
        """
            <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://arbitrary.app.namespace/com.ichi2.anki"
            android:title="@string/pref_cat_advanced_summ"
            android:summary="@string/pref_cat_advanced_summ"
            android:key="pref_screen_advanced">
                <EditTextPreference
                    android:defaultValue="/sdcard/AnkiDroid"
                    android:key="deckPath"
                    android:summary="@string/preference_summary_literal"
                    android:title="@string/preference_summary_literal" />
                    <com.ichi2.ui.ConfirmationPreference
                        android:key="force_full_sync"
                        android:title="@string/force_full_sync_title"
                        android:summary="@string/force_full_sync_summary" />
            </PreferenceScreen>
        """.trimIndent()

    @Language("XML")
    private val validXmlFile =
        """
            <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://arbitrary.app.namespace/com.ichi2.anki"
            android:title="@string/pref_cat_advanced"
            android:summary="@string/pref_cat_advanced_summ"
            android:key="pref_screen_advanced">
                 <EditTextPreference
                     android:defaultValue="/sdcard/AnkiDroid"
                     android:key="deckPath"
                     android:summary="@string/preference_summary_literal"
                     android:title="@string/preference_literal" />
                     <com.ichi2.ui.ConfirmationPreference
                         android:key="force_full_sync"
                         android:title="@string/force_full_sync_title"
                         android:summary="@string/force_full_sync_summary" />
            </PreferenceScreen>
        """.trimIndent()

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
                """
                          res/xml/invalidpreference.xml:1: Error: Do not use the same string for the title and summary of a preference [DuplicateTextInPreferencesXml]
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
^
res/xml/invalidpreference.xml:6: Error: Do not use the same string for the title and summary of a preference [DuplicateTextInPreferencesXml]
    <EditTextPreference
    ^
2 errors, 0 warnings
                """.trimIndent()
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
