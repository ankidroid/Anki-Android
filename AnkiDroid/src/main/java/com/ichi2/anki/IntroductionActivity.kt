/*
 *  Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroPageTransformerType
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.introduction.SetupCollectionFragment
import com.ichi2.anki.introduction.SetupCollectionFragment.*
import com.ichi2.anki.introduction.SetupCollectionFragment.Companion.handleCollectionSetupOption
import com.ichi2.anki.workarounds.AppLoadedFromBackupWorkaround.showedActivityFailedScreen
import com.ichi2.annotations.NeedsTest
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.getColorFromAttr
import com.ichi2.utils.*
import timber.log.Timber

/**
 * App introduction for new users.
 * TODO: Background of introduction_layout does not display on API 25 emulator: https://github.com/ankidroid/Anki-Android/pull/12033#issuecomment-1228429130
 */
@NeedsTest("Ensure that we can get here on first run without an exception dialog shown")
class IntroductionActivity : AppIntro() {

    private val onLoginResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            startDeckPicker(RESULT_SYNC_PROFILE)
        } else {
            Timber.i("login was not successful")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)

        // Check for WebView related error
        val startupFailure = InitialActivity.getStartupFailureType(this)
        startupFailure?.let {
            handleStartupFailure(it)
        }
        Themes.setTheme(this)

        setTransformer(AppIntroPageTransformerType.Zoom)

        addSlide(SetupCollectionFragment())

        handleCollectionSetupOption { option ->
            when (option) {
                CollectionSetupOption.DeckPickerWithNewCollection -> startDeckPicker(RESULT_START_NEW)
                CollectionSetupOption.SyncFromExistingAccount -> openLoginDialog()
            }
        }

        this.setColorDoneText(getColorFromAttr(this, android.R.attr.textColorPrimary))
        this.showSeparator(false)
        isButtonsEnabled = false
    }

    private fun openLoginDialog() {
        onLoginResult.launch(Intent(this, LoginActivity::class.java))
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        startDeckPicker(RESULT_START_NEW)
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        startDeckPicker(RESULT_START_NEW)
    }

    private fun startDeckPicker(result: Int = RESULT_START_NEW) {
        AnkiDroidApp.getSharedPrefs(this).edit { putBoolean(INTRODUCTION_SLIDES_SHOWN, true) }
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (result == RESULT_SYNC_PROFILE) {
            deckPicker.putExtra(DeckPicker.INTENT_SYNC_FROM_LOGIN, true)
        }

        startActivity(deckPicker)
        finish()
    }

    /**
     * When WebView is not available on a device, then error message indicating
     * the same needs to be shown.
     * @param startupFailure Type of error on startup
     */
    // TODO: Factor this into the AppLoadedFromBackupWorkaround class
    private fun handleStartupFailure(startupFailure: StartupFailure) {
        if (startupFailure == StartupFailure.WEBVIEW_FAILED) {
            AlertDialog.Builder(this).show {
                title(R.string.ankidroid_init_failed_webview_title)
                message(R.string.ankidroid_init_failed_webview, AnkiDroidApp.webViewErrorMessage)
                positiveButton(R.string.close) { finish() }
                cancelable(false)
            }
        }
    }

    private fun showedActivityFailedScreen(savedInstanceState: Bundle?) =
        showedActivityFailedScreen(
            savedInstanceState = savedInstanceState,
            activitySuperOnCreate = { state -> super.onCreate(state) }
        )

    companion object {
        const val RESULT_START_NEW = 1
        const val RESULT_SYNC_PROFILE = 2

        const val INTRODUCTION_SLIDES_SHOWN = "IntroductionSlidesShown"
    }
}
