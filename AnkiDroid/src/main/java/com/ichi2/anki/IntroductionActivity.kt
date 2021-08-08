/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroPageTransformerType
import com.ichi2.anki.InitialActivity.StartupFailure
import timber.log.Timber

/**
 * App introduction for new users.
 */
class IntroductionActivity : AppIntro() {
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

        setTransformer(AppIntroPageTransformerType.Zoom)

        val welcomeSlide = IntroductionResources(
            R.string.collection_load_welcome_request_permissions_title,
            R.string.introduction_desc,
            R.drawable.ankidroid_logo
        )

        val decksSlide = IntroductionResources(
            R.string.decks_intro,
            R.string.decks_intro_desc,
            webPage = "DeckList.html",
            localization = listOf(
                "app-name" to R.string.app_name,
                "due-time" to R.string.due_time,
                "deck-1" to R.string.deck_example_name_1,
                "deck-2" to R.string.deck_example_name_2,
                "card-status" to R.string.deck_picker_footer_text
            )
        )

        val cardsSlide = IntroductionResources(
            R.string.create_cards_intro,
            R.string.create_cards_intro_desc,
            webPage = "AddNote.html",
            localization = listOf(
                "selector-title-type" to R.string.CardEditorModel,
                "selector-option-basic" to R.string.basic_model_name,
                "selector-title-deck" to R.string.CardEditorNoteDeck,
                "selector-option-deck-name" to R.string.deck_example_name_1,
                "edit-text-name-front" to R.string.front_field_name,
                "edit-text-name-back" to R.string.back_field_name,
            )
        )

        val reviewerSlide = IntroductionResources(
            R.string.study_time,
            R.string.study_desc,
            webPage = "Reviewer.html",
            localization = listOf(
                "deck-name" to R.string.english_deck,
                "time-left" to R.string.two_minutes_left_text,
                "question" to R.string.commute,
                "answer" to R.string.commute_desc,
                "again" to R.string.ease_button_again,
                "hard" to R.string.ease_button_hard,
                "good" to R.string.ease_button_good,
                "easy" to R.string.ease_button_easy,
            )
        )

        val cardBrowserSlide = IntroductionResources(
            R.string.card_browser,
            R.string.card_browser_desc,
            webPage = "CardBrowser.html",
            localization = listOf(
                "deck-name" to R.string.ease_button_easy,
                "question-heading" to R.string.card_side_question,
                "answer-heading" to R.string.card_side_answer,
            )
        )

        val slidesList = listOf(welcomeSlide, decksSlide, cardsSlide, reviewerSlide, cardBrowserSlide)
        slidesList.forEach {
            addSlide(IntroductionFragment.newInstance(it))
        }
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        startDeckPicker()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        startDeckPicker()
    }

    private fun startDeckPicker() {
        AnkiDroidApp.getSharedPrefs(this).edit().putBoolean(IntentHandler.INTRODUCTION_SLIDES_SHOWN, true).apply()
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(deckPicker)
        finish()
    }

    /**
     * When WebView is not available on a device, then error message indicating
     * the same needs to be shown.
     * @param startupFailure Type of error on startup
     */
    private fun handleStartupFailure(startupFailure: StartupFailure) {
        if (startupFailure == StartupFailure.WEBVIEW_FAILED) {
            MaterialDialog.Builder(this)
                .title(R.string.ankidroid_init_failed_webview_title)
                .content(getString(R.string.ankidroid_init_failed_webview, AnkiDroidApp.getWebViewErrorMessage()))
                .positiveText(R.string.close)
                .onPositive { d: MaterialDialog?, w: DialogAction? -> finish() }
                .cancelable(false)
                .show()
        }
    }

    /**
     * Information required to show a slide during the initial app introduction
     */
    data class IntroductionResources(
        val title: Int,
        val description: Int,
        val image: Int? = null,
        val webPage: String? = null,
        /**
         * List containing pairs of ID used in HTML and the
         * corresponding String resource which needs to be used.
         */
        val localization: List<Pair<String, Int>>? = null
    )

    // This method has been taken from AnkiActivity.
    // Duplication is required since IntroductionActivity doesn't inherit from AnkiActivity. 
    private fun showedActivityFailedScreen(savedInstanceState: Bundle?): Boolean {
        if (AnkiDroidApp.isInitialized()) {
            return false
        }

        // #7630: Can be triggered with `adb shell bmgr restore com.ichi2.anki` after AnkiDroid settings are changed.
        // Application.onCreate() is not called if:
        // * The App was open
        // * A restore took place
        // * The app is reopened (until it exits: finish() does not do this - and removes it from the app list)
        Timber.w("Activity started with no application instance")
        UIUtils.showThemedToast(this, getString(R.string.ankidroid_cannot_open_after_backup_try_again), false)

        // Avoids a SuperNotCalledException
        super.onCreate(savedInstanceState)
        AnkiActivity.finishActivityWithFade(this)

        // If we don't kill the process, the backup is not "done" and reopening the app show the same message.
        Thread {

            // 3.5 seconds sleep, as the toast is killed on process death.
            // Same as the default value of LENGTH_LONG
            try {
                Thread.sleep(3500)
            } catch (e: InterruptedException) {
                Timber.w(e)
            }
            Process.killProcess(Process.myPid())
        }.start()
        return true
    }
}
