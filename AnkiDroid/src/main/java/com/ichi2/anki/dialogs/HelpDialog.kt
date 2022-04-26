/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.dialogs

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.HelpDialog.FunctionItem.ActivityConsumer
import com.ichi2.anki.dialogs.RecursivePictureMenu.Companion.createInstance
import com.ichi2.anki.dialogs.RecursivePictureMenu.Companion.removeFrom
import com.ichi2.anki.dialogs.RecursivePictureMenu.ItemHeader
import com.ichi2.anki.exception.UserSubmittedException
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.IntentUtil.canOpenIntent
import com.ichi2.utils.IntentUtil.tryOpenIntent
import com.ichi2.utils.KotlinCleanup
import org.acra.ACRA
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.LimiterData
import org.acra.config.LimiterData.ReportMetadata
import timber.log.Timber
import java.io.Serializable
import java.util.*

object HelpDialog {
    private fun openManual(ankiActivity: AnkiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.getManualUrl()))
    }

    private fun openFeedback(ankiActivity: AnkiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.getFeedbackUrl()))
    }

    @JvmStatic
    fun createInstance(context: Context?): DialogFragment {
        val exceptionReportItem = ExceptionReportItem(R.string.help_title_send_exception, R.drawable.ic_round_assignment_24, UsageAnalytics.Actions.EXCEPTION_REPORT)
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, UsageAnalytics.Actions.OPENED_HELPDIALOG)
        val rateAppItem = RateAppItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_star_black_24, UsageAnalytics.Actions.OPENED_RATE)
        val allItems = arrayOf<RecursivePictureMenu.Item>(
            ItemHeader(
                R.string.help_title_using_ankidroid, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_USING_ANKIDROID,
                FunctionItem(
                    R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL,
                    object : ActivityConsumer {
                        override fun consume(activity: AnkiActivity) {
                            openManual(activity)
                        }
                    }
                ),
                LinkItem(R.string.help_item_anki_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKI_MANUAL, R.string.link_anki_manual),
                LinkItem(R.string.help_item_ankidroid_faq, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_FAQ, R.string.link_ankidroid_faq)
            ),
            ItemHeader(
                R.string.help_title_get_help, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_GET_HELP,
                LinkItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_MAILING_LIST, R.string.link_forum),
                FunctionItem(
                    R.string.help_item_report_bug, R.drawable.ic_bug_report_black_24dp, UsageAnalytics.Actions.OPENED_REPORT_BUG,
                    object : ActivityConsumer {
                        override fun consume(activity: AnkiActivity) {
                            openFeedback(activity)
                        }
                    }
                ),
                exceptionReportItem
            ),
            ItemHeader(
                R.string.help_title_community, R.drawable.ic_people_black_24dp, UsageAnalytics.Actions.OPENED_COMMUNITY,
                LinkItem(R.string.help_item_anki_forums, R.drawable.ic_forum_black_24dp, UsageAnalytics.Actions.OPENED_ANKI_FORUMS, R.string.link_anki_forum),
                LinkItem(R.string.help_item_reddit, R.drawable.reddit, UsageAnalytics.Actions.OPENED_REDDIT, R.string.link_reddit),
                LinkItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_MAILING_LIST, R.string.link_forum),
                LinkItem(R.string.help_item_discord, R.drawable.discord, UsageAnalytics.Actions.OPENED_DISCORD, R.string.link_discord),
                LinkItem(R.string.help_item_facebook, R.drawable.facebook, UsageAnalytics.Actions.OPENED_FACEBOOK, R.string.link_facebook),
                LinkItem(R.string.help_item_twitter, R.drawable.twitter, UsageAnalytics.Actions.OPENED_TWITTER, R.string.link_twitter)
            ),
            ItemHeader(
                R.string.help_title_privacy, R.drawable.ic_baseline_privacy_tip_24, UsageAnalytics.Actions.OPENED_PRIVACY,
                LinkItem(R.string.help_item_ankidroid_privacy_policy, R.drawable.ic_baseline_policy_24, UsageAnalytics.Actions.OPENED_ANKIDROID_PRIVACY_POLICY, R.string.link_ankidroid_privacy_policy),
                LinkItem(R.string.help_item_ankiweb_privacy_policy, R.drawable.ic_baseline_policy_24, UsageAnalytics.Actions.OPENED_ANKIWEB_PRIVACY_POLICY, R.string.link_ankiweb_privacy_policy),
                LinkItem(R.string.help_item_ankiweb_terms_and_conditions, R.drawable.ic_baseline_description_24, UsageAnalytics.Actions.OPENED_ANKIWEB_TERMS_AND_CONDITIONS, R.string.link_ankiweb_terms_and_conditions)
            )
        )
        val itemList = ArrayList(listOf(*allItems))
        if (!canOpenIntent(context!!, AnkiDroidApp.getMarketIntent(context))) {
            removeFrom(itemList, rateAppItem)
        }
        return createInstance(itemList, R.string.help)
    }

    @JvmStatic
    fun createInstanceForSupportAnkiDroid(context: Context?): DialogFragment {
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, UsageAnalytics.Actions.OPENED_SUPPORT_ANKIDROID)
        val rateAppItem = RateAppItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_star_black_24, UsageAnalytics.Actions.OPENED_RATE)
        val allItems = arrayOf(
            LinkItem(R.string.help_item_support_opencollective_donate, R.drawable.ic_donate_black_24dp, UsageAnalytics.Actions.OPENED_DONATE, R.string.link_opencollective_donate),
            LinkItem(R.string.multimedia_editor_trans_translate, R.drawable.ic_language_black_24dp, UsageAnalytics.Actions.OPENED_TRANSLATE, R.string.link_translation),
            LinkItem(R.string.help_item_support_develop_ankidroid, R.drawable.ic_build_black_24, UsageAnalytics.Actions.OPENED_DEVELOP, R.string.link_ankidroid_development_guide),
            rateAppItem,
            LinkItem(R.string.help_item_support_other_ankidroid, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_OTHER, R.string.link_contribution),
            FunctionItem(
                R.string.send_feedback, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_SEND_FEEDBACK,
                object : ActivityConsumer {
                    override fun consume(activity: AnkiActivity) {
                        openFeedback(activity)
                    }
                }
            )
        )
        val itemList = ArrayList(listOf(*allItems))
        if (!canOpenIntent(context!!, AnkiDroidApp.getMarketIntent(context))) {
            removeFrom(itemList, rateAppItem)
        }
        return createInstance(itemList, R.string.help_title_support_ankidroid)
    }

    @KotlinCleanup("Convert to @Parcelize")
    class RateAppItem : RecursivePictureMenu.Item, Parcelable {
        constructor(@StringRes titleRes: Int, @DrawableRes iconRes: Int, analyticsRes: String?) : super(titleRes, iconRes, analyticsRes)

        override fun onClicked(activity: AnkiActivity) {
            tryOpenIntent(activity, AnkiDroidApp.getMarketIntent(activity))
        }

        override fun remove(toRemove: RecursivePictureMenu.Item?) {
            // intentionally blank - no children
        }

        private constructor(`in`: Parcel?) : super(`in`!!)

        companion object {
            @JvmField val CREATOR: Parcelable.Creator<RateAppItem?> = object : Parcelable.Creator<RateAppItem?> {
                override fun createFromParcel(`in`: Parcel): RateAppItem {
                    return RateAppItem(`in`)
                }

                override fun newArray(size: Int): Array<RateAppItem?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    @KotlinCleanup("Convert to @Parcelize")
    class LinkItem : RecursivePictureMenu.Item, Parcelable {
        @StringRes
        private val mUrlLocationRes: Int

        constructor(@StringRes titleRes: Int, @DrawableRes iconRes: Int, analyticsRes: String?, @StringRes urlLocation: Int) : super(titleRes, iconRes, analyticsRes) {
            mUrlLocationRes = urlLocation
        }

        override fun onClicked(activity: AnkiActivity) {
            activity.openUrl(getUrl(activity))
        }

        private fun getUrl(ctx: Context): Uri {
            return Uri.parse(ctx.getString(mUrlLocationRes))
        }

        private constructor(`in`: Parcel) : super(`in`) {
            mUrlLocationRes = `in`.readInt()
        }

        override fun remove(toRemove: RecursivePictureMenu.Item?) {
            // intentionally blank - no children
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(mUrlLocationRes)
        }

        companion object {
            @JvmField val CREATOR: Parcelable.Creator<LinkItem?> = object : Parcelable.Creator<LinkItem?> {
                override fun createFromParcel(`in`: Parcel): LinkItem {
                    return LinkItem(`in`)
                }

                override fun newArray(size: Int): Array<LinkItem?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    @KotlinCleanup("Convert to @Parcelize")
    class FunctionItem : RecursivePictureMenu.Item, Parcelable {

        private val mFunc: ActivityConsumer

        constructor(@StringRes titleRes: Int, @DrawableRes iconRes: Int, analyticsRes: String?, func: ActivityConsumer) : super(titleRes, iconRes, analyticsRes) {
            mFunc = func
        }

        override fun onClicked(activity: AnkiActivity) {
            mFunc.consume(activity)
        }

        private constructor(`in`: Parcel) : super(`in`) {
            mFunc = `in`.readSerializable() as ActivityConsumer
        }

        override fun remove(toRemove: RecursivePictureMenu.Item?) {
            // intentionally blank - no children
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeSerializable(mFunc)
        }

        fun interface ActivityConsumer : Serializable {
            fun consume(activity: AnkiActivity)
        }

        companion object {
            @JvmField val CREATOR: Parcelable.Creator<FunctionItem?> = object : Parcelable.Creator<FunctionItem?> {
                override fun createFromParcel(`in`: Parcel): FunctionItem {
                    return FunctionItem(`in`)
                }

                override fun newArray(size: Int): Array<FunctionItem?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    private class ExceptionReportItem : RecursivePictureMenu.Item, Parcelable {
        constructor(@StringRes titleRes: Int, @DrawableRes iconRes: Int, analyticsRes: String) : super(titleRes, iconRes, analyticsRes)

        override fun onClicked(activity: AnkiActivity) {
            val preferences = AnkiDroidApp.getSharedPrefs(activity)
            val reportMode = preferences.getString(CrashReportService.FEEDBACK_REPORT_KEY, "")
            if (isUserATestClient) {
                showThemedToast(activity, activity.getString(R.string.user_is_a_robot), false)
                return
            }
            if (CrashReportService.FEEDBACK_REPORT_NEVER == reportMode) {
                preferences.edit().putBoolean(ACRA.PREF_DISABLE_ACRA, false).apply()
                CrashReportService.getAcraCoreConfigBuilder()
                    .getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java)
                    .setEnabled(true)
                sendReport(activity)
                CrashReportService.getAcraCoreConfigBuilder()
                    .getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java)
                    .setEnabled(false)
                preferences.edit().putBoolean(ACRA.PREF_DISABLE_ACRA, true).apply()
            } else {
                sendReport(activity)
            }
        }

        /**
         * Check the ACRA report store and return the timestamp of the last report.
         *
         * @param activity the Activity used for Context access when interrogating ACRA reports
         * @return the timestamp of the most recent report, or -1 if no reports at all
         */
        // Upstream issue for access to field/method: https://github.com/ACRA/acra/issues/843
        private fun getTimestampOfLastReport(activity: AnkiActivity): Long {
            try {
                // The ACRA LimiterData holds a timestamp for every generated report
                val limiterData = LimiterData.load(activity)
                val limiterDataListField = limiterData.javaClass.getDeclaredField("list")
                limiterDataListField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val limiterDataList = limiterDataListField[limiterData] as List<ReportMetadata>
                for (report in limiterDataList) {
                    if (report.exceptionClass != UserSubmittedException::class.java.name) {
                        continue
                    }
                    val timestampMethod = report.javaClass.getDeclaredMethod("getTimestamp")
                    timestampMethod.isAccessible = true
                    val timestamp = timestampMethod.invoke(report) as Calendar
                    // Limiter ensures there is only one report for the class, so if we found it, return it
                    return timestamp.timeInMillis
                }
            } catch (e: Exception) {
                Timber.w(e, "Unexpected exception checking for recent reports")
            }
            return -1
        }

        private fun sendReport(activity: AnkiActivity) {
            val currentTimestamp = activity.col.time.intTimeMS()
            val lastReportTimestamp = getTimestampOfLastReport(activity)
            if (currentTimestamp - lastReportTimestamp > MIN_INTERVAL_MS) {
                CrashReportService.deleteACRALimiterData(activity)
                CrashReportService.sendExceptionReport(
                    UserSubmittedException(EXCEPTION_MESSAGE),
                    "AnkiDroidApp.HelpDialog"
                )
            } else {
                showThemedToast(
                    activity, activity.getString(R.string.help_dialog_exception_report_debounce),
                    true
                )
            }
        }

        private constructor(`in`: Parcel) : super(`in`)

        override fun remove(toRemove: RecursivePictureMenu.Item?) {}

        companion object {
            private const val MIN_INTERVAL_MS = 60000
            private const val EXCEPTION_MESSAGE = "Exception report sent by user manually"
            val CREATOR: Parcelable.Creator<ExceptionReportItem?> = object : Parcelable.Creator<ExceptionReportItem?> {
                override fun createFromParcel(`in`: Parcel): ExceptionReportItem {
                    return ExceptionReportItem(`in`)
                }

                override fun newArray(size: Int): Array<ExceptionReportItem?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
}
