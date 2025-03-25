/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.anki.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.WebSettings
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.brsanthu.googleanalytics.GoogleAnalytics
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig
import com.brsanthu.googleanalytics.httpclient.OkHttpClientImpl
import com.brsanthu.googleanalytics.request.DefaultRequest
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.utils.DisplayUtils
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.WebViewDebugging.hasSetDataDirectory
import org.acra.ACRA
import org.acra.util.Installation
import timber.log.Timber

@KotlinCleanup("see if we can make variables lazy, or properties without the `s` prefix")
object UsageAnalytics {
    const val ANALYTICS_OPTIN_KEY = "analytics_opt_in"

    @KotlinCleanup("lateinit")
    private var sAnalytics: GoogleAnalytics? = null
    private var sOriginalUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var sOptIn = false
    private var sAnalyticsTrackingId: String? = null
    private var sAnalyticsSamplePercentage = -1

    @FunctionalInterface
    fun interface AnalyticsLoggingExceptionHandler : Thread.UncaughtExceptionHandler

    var uncaughtExceptionHandler =
        AnalyticsLoggingExceptionHandler { thread: Thread?, throwable: Throwable ->
            sendAnalyticsException(throwable, true)
            if (thread == null) {
                Timber.w("unexpected: thread was null")
                return@AnalyticsLoggingExceptionHandler
            }
            sOriginalUncaughtExceptionHandler!!.uncaughtException(thread, throwable)
        }

    /**
     * Initialize the analytics provider - must be called prior to sending anything.
     * Usage after that is static
     * Note: may need to implement sampling strategy internally to limit hits, or not track Reviewer...
     *
     * @param context required to look up the analytics codes for the app
     */
    @Synchronized
    fun initialize(context: Context): GoogleAnalytics? {
        Timber.i("initialize()")
        if (sAnalytics == null) {
            Timber.d("App tracking id 'tid' = %s", getAnalyticsTag(context))
            val gaConfig =
                GoogleAnalyticsConfig()
                    .setBatchingEnabled(true)
                    .setSamplePercentage(getAnalyticsSamplePercentage(context))
                    .setBatchSize(1) // until this handles application termination we will lose hits if batch>1
            sAnalytics =
                GoogleAnalytics
                    .builder()
                    .withTrackingId(getAnalyticsTag(context))
                    .withConfig(gaConfig)
                    .withDefaultRequest(
                        AndroidDefaultRequest()
                            .setAndroidRequestParameters(context)
                            .applicationName(context.getString(R.string.app_name))
                            .applicationVersion(BuildConfig.VERSION_CODE.toString())
                            .applicationId(BuildConfig.APPLICATION_ID)
                            .trackingId(getAnalyticsTag(context))
                            .clientId(Installation.id(context))
                            .anonymizeIp(context.resources.getBoolean(R.bool.ga_anonymizeIp)),
                    ).withHttpClient(OkHttpClientImpl(gaConfig))
                    .build()
        }
        installDefaultExceptionHandler()
        val userPrefs = context.sharedPrefs()
        optIn = userPrefs.getBoolean(ANALYTICS_OPTIN_KEY, false)
        userPrefs.registerOnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences, key: String? ->
            if (key == ANALYTICS_OPTIN_KEY) {
                val newValue = sharedPreferences.getBoolean(key, false)
                Timber.i("Setting analytics opt-in to: %b", newValue)
                optIn = newValue
            }
        }
        initializePrefKeys(context)
        return sAnalytics
    }

    private fun getAnalyticsTag(context: Context): String? {
        if (sAnalyticsTrackingId == null) {
            sAnalyticsTrackingId = context.getString(R.string.ga_trackingId)
        }
        return sAnalyticsTrackingId
    }

    private fun getAnalyticsSamplePercentage(context: Context): Int {
        if (sAnalyticsSamplePercentage == -1) {
            sAnalyticsSamplePercentage = context.resources.getInteger(R.integer.ga_sampleFrequency)
        }
        return sAnalyticsSamplePercentage
    }

    fun setDevMode() {
        Timber.d("setDevMode() re-configuring for development analytics tagging")
        sAnalyticsTrackingId = "UA-125800786-2"
        sAnalyticsSamplePercentage = 100
        reInitialize()
    }

    /**
     * We want to send an analytics hit on any exception, then chain to other handlers (e.g., ACRA)
     */
    @Synchronized
    @VisibleForTesting
    fun installDefaultExceptionHandler() {
        sOriginalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Timber.d("Chaining to uncaughtExceptionHandler (%s)", sOriginalUncaughtExceptionHandler)
        Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler)
    }

    /**
     * Reset the default exception handler
     */
    @Synchronized
    @VisibleForTesting
    fun unInstallDefaultExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(sOriginalUncaughtExceptionHandler)
        sOriginalUncaughtExceptionHandler = null
    }

    /**
     * Allow users to enable or disable analytics
     */
    @set:Synchronized
    private var optIn: Boolean
        get() {
            Timber.d("getOptIn() status: %s", sOptIn)
            return sOptIn
        }
        private set(optIn) {
            Timber.i("setOptIn(): from %s to %s", sOptIn, optIn)
            sOptIn = optIn
            sAnalytics!!.flush()
            sAnalytics!!.config.isEnabled = optIn
            sAnalytics!!.performSamplingElection()
            Timber.d("setOptIn() optIn / sAnalytics.config().enabled(): %s/%s", sOptIn, sAnalytics!!.config.isEnabled)
        }

    /**
     * Set the analytics up to log things, goes to hit validator. Experimental.
     *
     * @param dryRun set to true if you want to log analytics hit but not dispatch
     */
    @Synchronized
    fun setDryRun(dryRun: Boolean) {
        Timber.i("setDryRun(): %s, warning dryRun is experimental", dryRun)
    }

    /**
     * Re-Initialize the analytics provider
     */
    @Synchronized
    fun reInitialize() {
        // send any pending async hits, re-chain default exception handlers and re-init
        Timber.i("reInitialize()")
        sAnalytics!!.flush()
        sAnalytics = null
        unInstallDefaultExceptionHandler()
        initialize(AnkiDroidApp.instance.applicationContext)
    }

    /**
     * Submit a screen for aggregation / analysis.
     * Intended for use to determine if / how features are being used
     *
     * @param screen the result of [Class.simpleName] will be used as the screen tag
     */
    fun sendAnalyticsScreenView(screen: Any) {
        sendAnalyticsScreenView(screen.javaClass.simpleName)
    }

    /**
     * Submit a screen display with a synthetic name for aggregation / analysis
     * Intended for use if your class handles multiple screens you want to track separately
     *
     * @param screenName screenName the name to show in analysis reports
     */
    fun sendAnalyticsScreenView(screenName: String) {
        Timber.d("sendAnalyticsScreenView(): %s", screenName)
        if (!optIn) {
            return
        }
        sAnalytics!!.screenView().screenName(screenName).sendAsync()
    }

    /**
     * Send a detailed arbitrary analytics event, with noun/verb pairs and extra data if needed
     *
     * @param category the category of event, make your own but use a constant so reporting is good
     * @param action   the action the user performed
     * @param value    A value for the event, Integer.MIN_VALUE signifies caller shouldn't send the value
     * @param label    A label for the event, may be null
     */
    fun sendAnalyticsEvent(
        category: String,
        action: String,
        value: Int? = null,
        label: String? = null,
    ) {
        Timber.d("sendAnalyticsEvent() category/action/value/label: %s/%s/%s/%s", category, action, value, label)
        if (!optIn) {
            return
        }
        val event = sAnalytics!!.event().eventCategory(category).eventAction(action)
        if (label != null) {
            event.eventLabel(label)
        }
        if (value != null) {
            event.eventValue(value)
        }
        event.sendAsync()
    }

    /**
     * Send an exception event out for aggregation/analysis, parsed from the exception information
     *
     * @param t     Throwable to send for analysis
     * @param fatal whether it was fatal or not
     */
    fun sendAnalyticsException(
        t: Throwable,
        fatal: Boolean,
    ) {
        sendAnalyticsException(getCause(t).toString(), fatal)
    }

    @KotlinCleanup("convert to sequence")
    fun getCause(t: Throwable): Throwable {
        var cause: Throwable?
        var result = t
        while (null != result.cause.also { cause = it } && result != cause) {
            result = cause!!
        }
        return result
    }

    /**
     * Send an exception event out for aggregation/analysis
     *
     * @param description API limited to 100 characters, truncated here to 100 if needed
     * @param fatal       whether it was fatal or not
     */
    fun sendAnalyticsException(
        description: String,
        fatal: Boolean,
    ) {
        Timber.d("sendAnalyticsException() description/fatal: %s/%s", description, fatal)
        if (!sOptIn) {
            return
        }
        sAnalytics!!
            .exception()
            .exceptionDescription(description)
            .exceptionFatal(fatal)
            .sendAsync()
    }

    internal fun canGetDefaultUserAgent(): Boolean {
        // #5502 - getDefaultUserAgent starts a WebView. We can't have two WebViews with the same data directory.
        // But ACRA starts an :acra process which does not terminate when AnkiDroid is restarted. https://crbug.com/558377

        // if we're not under the ACRA process then we're fine to initialize a WebView
        return if (!ACRA.isACRASenderServiceProcess()) {
            true
        } else {
            hasSetDataDirectory()
        }

        // If we have a custom data directory, then the crash will not occur.
    }

    // A listener on this preference handles the rest
    var isEnabled: Boolean
        get() {
            val userPrefs = AnkiDroidApp.instance.sharedPrefs()
            return userPrefs.getBoolean(ANALYTICS_OPTIN_KEY, false)
        }
        set(value) {
            // A listener on this preference handles the rest
            AnkiDroidApp.instance.sharedPrefs().edit {
                putBoolean(ANALYTICS_OPTIN_KEY, value)
            }
        }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun resetForTests() {
        sAnalytics = null
    }

    /**
     * An Android-specific device config generator. Without this it's "Desktop" and unknown for all hardware.
     * It is interesting to us what devices people use though (for instance: is Amazon Kindle support worth it?
     * Is anyone still using e-ink devices? How many people are on tablets? ChromeOS?)
     */
    private class AndroidDefaultRequest : DefaultRequest() {
        fun setAndroidRequestParameters(context: Context): DefaultRequest {
            // Are we running on really large screens or small screens? Send raw screen size
            try {
                val size = DisplayUtils.getDisplayDimensions(context)
                this.screenResolution(size.x.toString() + "x" + size.y)
            } catch (e: RuntimeException) {
                Timber.w(e)
                // nothing much to do here, it means we couldn't get WindowManager
            }

            // We can have up to 20 of these - there might be other things we want to know
            // but simply seeing what hardware we are running on should be useful
            this.customDimension(1, Build.VERSION.RELEASE) // systemVersion, e.g. "7.1.1"  for Android 7.1.1
            this.customDimension(2, Build.BRAND) // brand e.g. "OnePlus"
            this.customDimension(3, Build.MODEL) // model e.g. "ONEPLUS A6013" for the 6T
            this.customDimension(4, Build.BOARD) // deviceId e.g. "sdm845" for the 6T

            // This is important for google to auto-fingerprint us for default reporting
            // It is not possible to set operating system explicitly, there is no API or analytics parameter for it
            // Instead they respond that they auto-parse User-Agent strings for analytics attribution
            // For maximum analytics built-in report compatibility we will send the official WebView User-Agent string
            try {
                if (canGetDefaultUserAgent()) {
                    this.userAgent(WebSettings.getDefaultUserAgent(context))
                } else {
                    this.userAgent(System.getProperty("http.agent"))
                }
            } catch (e: RuntimeException) {
                Timber.w(e)
                // Catch RuntimeException as WebView initialization blows up in unpredictable ways
                // but analytics should never be a show-stopper
                this.userAgent(System.getProperty("http.agent"))
            }
            return this
        }
    }

    object Category {
        const val SYNC = "Sync"
        const val LINK_CLICKED = "LinkClicked"
        const val SETTING = "Setting"
    }

    /**
     * These Strings must not be changed as they are used for analytic comparisons between AnkiDroid versions.
     * If a new string is added here then the respective changes must also be made in AnalyticsConstantsTest.java
     * All the constant strings added here must be annotated with @AnalyticsConstant.
     */
    object Actions {
        // Analytics actions used in Help Dialog
        @AnalyticsConstant
        val OPENED_HELP_DIALOG = "Opened HelpDialogBox"

        @AnalyticsConstant
        val OPENED_USING_ANKIDROID = "Opened Using AnkiDroid"

        @AnalyticsConstant
        val OPENED_GET_HELP = "Opened Get Help"

        @AnalyticsConstant
        val OPENED_SUPPORT_ANKIDROID = "Opened Support AnkiDroid"

        @AnalyticsConstant
        val OPENED_COMMUNITY = "Opened Community"

        @AnalyticsConstant
        val OPENED_PRIVACY = "Opened Privacy"

        @AnalyticsConstant
        val OPENED_ANKIWEB_TERMS_AND_CONDITIONS = "Opened AnkiWeb Terms and Conditions"

        @AnalyticsConstant
        val OPENED_ANKIDROID_PRIVACY_POLICY = "Opened AnkiDroid Privacy Policy"

        @AnalyticsConstant
        val OPENED_ANKIWEB_PRIVACY_POLICY = "Opened AnkiWeb Privacy Policy"

        @AnalyticsConstant
        val OPENED_ANKIDROID_MANUAL = "Opened AnkiDroid Manual"

        @AnalyticsConstant
        val OPENED_ANKI_MANUAL = "Opened Anki Manual"

        @AnalyticsConstant
        val OPENED_ANKIDROID_FAQ = "Opened AnkiDroid FAQ"

        @AnalyticsConstant
        val OPENED_MAILING_LIST = "Opened Mailing List"

        @AnalyticsConstant
        val OPENED_REPORT_BUG = "Opened Report a Bug"

        @AnalyticsConstant
        val OPENED_DONATE = "Opened Donate"

        @AnalyticsConstant
        val OPENED_TRANSLATE = "Opened Translate"

        @AnalyticsConstant
        val OPENED_DEVELOP = "Opened Develop"

        @AnalyticsConstant
        val OPENED_RATE = "Opened Rate"

        @AnalyticsConstant
        val OPENED_OTHER = "Opened Other"

        @AnalyticsConstant
        val OPENED_SEND_FEEDBACK = "Opened Send Feedback"

        @AnalyticsConstant
        val OPENED_ANKI_FORUMS = "Opened Anki Forums"

        @AnalyticsConstant
        val OPENED_REDDIT = "Opened Reddit"

        @AnalyticsConstant
        val OPENED_DISCORD = "Opened Discord"

        @AnalyticsConstant
        val OPENED_FACEBOOK = "Opened Facebook"

        @AnalyticsConstant
        val OPENED_TWITTER = "Opened Twitter"

        @AnalyticsConstant
        val EXCEPTION_REPORT = "Exception Report"

        @AnalyticsConstant
        val IMPORT_APKG_FILE = "Import APKG"

        @AnalyticsConstant
        val IMPORT_COLPKG_FILE = "Import COLPKG"

        @AnalyticsConstant
        val IMPORT_CSV_FILE = "Import CSV"

        @AnalyticsConstant
        val TAPPED_SETTING = "Tapped setting"

        @AnalyticsConstant
        val CHANGED_SETTING = "Changed setting"
    }

    @VisibleForTesting
    val reportablePrefKeys =
        setOf(
            // ******************************** General ************************************************
            R.string.error_reporting_mode_key, // Error reporting mode
            R.string.paste_png_key, // Paste clipboard images as PNG
            R.string.deck_for_new_cards_key, // Deck for new cards
            R.string.exit_via_double_tap_back_key, // Press back twice to go back/exit
            R.string.anki_card_external_context_menu_key, // ‘Anki Card’ Menu
            R.string.card_browser_external_context_menu_key, // ‘Card Browser’ Menu
            // ******************************** Reviewing **********************************************
            R.string.day_offset_preference, // Start of next day
            R.string.learn_cutoff_preference, // Learn ahead limit
            R.string.time_limit_preference, // Timebox time limit
            R.string.keep_screen_on_preference, // Disable screen timeout
            R.string.double_tap_time_interval_preference, // Double tap time interval (milliseconds)
            // ******************************** Sync ***************************************************
            R.string.sync_fetch_media_key, // Fetch media on sync
            R.string.automatic_sync_choice_key, // Automatic synchronization
            R.string.sync_status_badge_key, // Display synchronization status
            R.string.metered_sync_key, // Allow sync on metered connections
            R.string.one_way_sync_key, // One-way sync
            // ******************************** Backup *************************************************
            R.string.pref_minutes_between_automatic_backups_key,
            R.string.pref_daily_backups_to_keep_key,
            R.string.pref_weekly_backups_to_keep_key,
            R.string.pref_monthly_backups_to_keep_key,
            // ******************************** Appearance *********************************************
            R.string.app_theme_key, // Theme
            R.string.day_theme_key, // Day theme
            R.string.night_theme_key, // Night theme
            R.string.pref_deck_picker_background_key, // Background image
            R.string.fullscreen_mode_preference, // Fullscreen mode
            R.string.center_vertically_preference, // Center align
            R.string.show_estimates_preference, // Show button time
            R.string.answer_buttons_position_preference, // Answer buttons position
            R.string.show_topbar_preference, // Show top bar
            R.string.show_progress_preference, // Show remaining
            R.string.show_eta_preference, // Show ETA
            R.string.show_audio_play_buttons_key, // Show play buttons on cards with audio (reversed in collection: HIDE_AUDIO_PLAY_BUTTONS)
            R.string.pref_display_filenames_in_browser_key, // Display filenames in card browser
            R.string.show_deck_title_key, // Show deck title
            // ******************************** Controls *********************************************
            R.string.gestures_preference, // Enable gestures
            R.string.gestures_corner_touch_preference, // 9-point touch
            R.string.nav_drawer_gesture_key, // Full screen navigation drawer
            R.string.pref_swipe_sensitivity_key, // Swipe sensitivity
            R.string.show_answer_command_key,
            R.string.answer_again_command_key,
            R.string.answer_hard_command_key,
            R.string.answer_good_command_key,
            R.string.answer_easy_command_key,
            R.string.undo_command_key,
            R.string.redo_command_key,
            R.string.edit_command_key,
            R.string.mark_command_key,
            R.string.bury_card_command_key,
            R.string.suspend_card_command_key,
            R.string.delete_command_key,
            R.string.play_media_command_key,
            R.string.abort_command_key,
            R.string.bury_note_command_key,
            R.string.suspend_note_command_key,
            R.string.flag_red_command_key,
            R.string.flag_orange_command_key,
            R.string.flag_green_command_key,
            R.string.flag_blue_command_key,
            R.string.flag_pink_command_key,
            R.string.flag_turquoise_command_key,
            R.string.flag_purple_command_key,
            R.string.remove_flag_command_key,
            R.string.page_up_command_key,
            R.string.page_down_command_key,
            R.string.tag_command_key,
            R.string.card_info_command_key,
            R.string.abort_and_sync_command_key,
            R.string.record_voice_command_key,
            R.string.replay_voice_command_key,
            R.string.save_voice_command_key,
            R.string.toggle_whiteboard_command_key,
            R.string.clear_whiteboard_command_key,
            R.string.change_whiteboard_pen_color_command_key,
            R.string.toggle_auto_advance_command_key,
            R.string.show_hint_command_key,
            R.string.show_all_hints_command_key,
            R.string.add_note_command_key,
            R.string.reschedule_command_key,
            R.string.user_action_1_key,
            R.string.user_action_2_key,
            R.string.user_action_3_key,
            R.string.user_action_4_key,
            R.string.user_action_5_key,
            R.string.user_action_6_key,
            R.string.user_action_7_key,
            R.string.user_action_8_key,
            R.string.user_action_9_key,
            // ******************************** Accessibility ******************************************
            R.string.card_zoom_preference,
            R.string.image_zoom_preference,
            R.string.answer_button_size_preference,
            R.string.show_large_answer_buttons_preference,
            R.string.pref_card_browser_font_scale_key,
            R.string.pref_card_minimal_click_time,
            // ******************************** Advanced ***********************************************
            R.string.pref_ankidroid_directory_key, // AnkiDroid directory
            R.string.double_scrolling_gap_key, // Double scrolling
            R.string.disable_hardware_render_key, // Disable card hardware render
            R.string.safe_display_key, // Safe display mode
            R.string.use_input_tag_key, // Type answer into the card
            R.string.disable_single_field_edit_key, // Disable Single-Field Edit Mode
            R.string.note_editor_newline_replace_key, // Replace newlines with HTML
            R.string.type_in_answer_focus_key, // Focus ‘type in answer’
            R.string.media_import_allow_all_files_key, // Allow all files in media imports
            R.string.enable_api_key, // Enable AnkiDroid API
            // ******************************** App bar buttons ****************************************
            R.string.reset_custom_buttons_key,
            R.string.custom_button_undo_key,
            R.string.custom_button_redo_key,
            R.string.custom_button_schedule_card_key,
            R.string.custom_button_flag_key,
            R.string.custom_button_edit_card_key,
            R.string.custom_button_tags_key,
            R.string.custom_button_add_card_key,
            R.string.custom_button_replay_key,
            R.string.custom_button_card_info_key,
            R.string.custom_button_select_tts_key,
            R.string.custom_button_deck_options_key,
            R.string.custom_button_mark_card_key,
            R.string.custom_button_toggle_mic_toolbar_key,
            R.string.custom_button_bury_key,
            R.string.custom_button_suspend_key,
            R.string.custom_button_delete_key,
            R.string.custom_button_enable_whiteboard_key,
            R.string.custom_button_toggle_stylus_key,
            R.string.custom_button_save_whiteboard_key,
            R.string.custom_button_whiteboard_pen_color_key,
            R.string.custom_button_show_hide_whiteboard_key,
            R.string.custom_button_clear_whiteboard_key,
            R.string.custom_button_user_action_1_key,
            R.string.custom_button_user_action_2_key,
            R.string.custom_button_user_action_3_key,
            R.string.custom_button_user_action_4_key,
            R.string.custom_button_user_action_5_key,
            R.string.custom_button_user_action_6_key,
            R.string.custom_button_user_action_7_key,
            R.string.custom_button_user_action_8_key,
            R.string.custom_button_user_action_9_key,
        )

    lateinit var preferencesWhoseChangesShouldBeReported: Set<String>

    @Suppress("ktlint:standard:discouraged-comment-location") // lots of work for little gain
    private fun initializePrefKeys(context: Context) {
        preferencesWhoseChangesShouldBeReported =
            reportablePrefKeys.mapTo(mutableSetOf()) { resId ->
                context.getString(resId)
            }
    }
}
