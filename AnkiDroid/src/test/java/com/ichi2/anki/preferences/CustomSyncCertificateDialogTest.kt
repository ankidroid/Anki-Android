// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CustomSyncCertificateDialogTest : RobolectricTest() {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val field get() = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
    private val certificateKey get() = targetContext.getString(R.string.custom_sync_certificate_key)
    private val certificate get() =
        targetContext.assets
            .open("USERTrust_RSA.crt")
            .bufferedReader()
            .use { it.readText() }

    private fun openSettings(): ActivityController<PreferencesActivity> =
        Robolectric
            .buildActivity(
                PreferencesActivity::class.java,
                PreferencesActivity.getIntent(targetContext, CustomSyncServerSettingsFragment::class),
            ).setup()
            .also {
                saveControllerForCleanup(it)
                advanceRobolectricLooper()
            }

    private fun openCertificate(controller: ActivityController<PreferencesActivity>) {
        val preferences = controller.get().fragment as PreferencesFragment
        val settings = preferences.childFragmentManager.findFragmentById(R.id.settings_container) as CustomSyncServerSettingsFragment
        settings.requirePreference<Preference>(R.string.custom_sync_certificate_key).performClick()
        advanceRobolectricLooper()
    }

    private fun save() {
        compose.onNodeWithText(targetContext.getString(CommonString.save)).performClick()
        advanceRobolectricLooper()
        field.assertDoesNotExist()
    }

    @Test
    fun `multiline PEM is validated saved and cleared independently of server settings`() {
        Prefs.sharedPrefs.edit {
            putString(targetContext.getString(R.string.custom_sync_server_collection_url_key), "https://sync.example.com/")
        }
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val controller = openSettings()
        openCertificate(controller)

        field.performTextReplacement(certificate)
        save()

        assertEquals(certificate, Prefs.customSyncCertificate)
        openCertificate(controller)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(certificate)))
        field.performTextReplacement("")
        save()

        assertEquals("", Prefs.customSyncCertificate)
        assertFalse(Prefs.isCustomSyncEnabled)
        assertEquals("https://sync.example.com/", Prefs.customSyncUri)
        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
    }

    @Test
    fun `cancel discards edits and reopening loads the saved certificate`() {
        Prefs.sharedPrefs.edit { putString(certificateKey, certificate) }
        val controller = openSettings()
        openCertificate(controller)
        field.performTextReplacement("unsaved\ncertificate")

        compose.onNodeWithText(targetContext.getString(CommonString.dialog_cancel)).performClick()
        advanceRobolectricLooper()
        field.assertDoesNotExist()

        assertEquals(certificate, Prefs.customSyncCertificate)
        openCertificate(controller)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(certificate)))
    }

    @Test
    fun `invalid PEM shows the existing error and preserves the saved certificate`() {
        Prefs.sharedPrefs.edit { putString(certificateKey, certificate) }
        openCertificate(openSettings())
        field.performTextReplacement("invalid PEM")
        save()

        assertEquals(certificate, Prefs.customSyncCertificate)
        val error = ShadowDialog.getLatestDialog()
        assertTrue(error.isShowing)
        assertEquals(
            targetContext.getString(CommonString.dialog_invalid_custom_certificate),
            error.findViewById<TextView>(android.R.id.message).text,
        )
    }

    @Test
    fun `recreation restores the open dialog and its unsaved draft`() {
        Prefs.sharedPrefs.edit { putString(certificateKey, certificate) }
        val controller = openSettings()
        openCertificate(controller)
        val draft = "unsaved\ncertificate"
        field.performTextReplacement(draft)

        controller.recreate()
        advanceRobolectricLooper()

        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
        assertEquals(certificate, Prefs.customSyncCertificate)
    }
}
