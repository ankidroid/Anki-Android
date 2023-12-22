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
import androidx.core.os.ParcelCompat
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.RecursivePictureMenu.Companion.createInstance
import com.ichi2.anki.dialogs.RecursivePictureMenu.ItemHeader
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.IntentUtil.canOpenIntent
import com.ichi2.utils.IntentUtil.tryOpenIntent
import com.ichi2.utils.KotlinCleanup
import java.io.Serializable
import java.util.*

object HelpDialog {
    private fun openManual(ankiActivity: AnkiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.manualUrl))
    }

    fun openFeedback(ankiActivity: AnkiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.feedbackUrl))
    }

    fun createInstance(): DialogFragment {
        val exceptionReportItem =
            ExceptionReportItem(
                R.string.help_title_send_exception,
                R.drawable.ic_round_assignment_24,
                UsageAnalytics.Actions.EXCEPTION_REPORT,
            )
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, UsageAnalytics.Actions.OPENED_HELPDIALOG)
        val allItems =
            arrayOf<RecursivePictureMenu.Item>(
                ItemHeader(
                    R.string.help_title_using_ankidroid,
                    R.drawable.ic_manual_black_24dp,
                    UsageAnalytics.Actions.OPENED_USING_ANKIDROID,
                    FunctionItem(
                        R.string.help_item_ankidroid_manual,
                        R.drawable.ic_manual_black_24dp,
                        UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL,
                    ) { activity -> openManual(activity) },
                    LinkItem(
                        R.string.help_item_anki_manual,
                        R.drawable.ic_manual_black_24dp,
                        UsageAnalytics.Actions.OPENED_ANKI_MANUAL,
                        R.string.link_anki_manual,
                    ),
                    LinkItem(
                        R.string.help_item_ankidroid_faq,
                        R.drawable.ic_help_black_24dp,
                        UsageAnalytics.Actions.OPENED_ANKIDROID_FAQ,
                        R.string.link_ankidroid_faq,
                    ),
                ),
                ItemHeader(
                    R.string.help_title_get_help,
                    R.drawable.ic_help_black_24dp,
                    UsageAnalytics.Actions.OPENED_GET_HELP,
                    LinkItem(
                        R.string.help_item_mailing_list,
                        R.drawable.ic_email_black_24dp,
                        UsageAnalytics.Actions.OPENED_MAILING_LIST,
                        R.string.link_forum,
                    ),
                    FunctionItem(
                        R.string.help_item_report_bug,
                        R.drawable.ic_bug_report_black_24dp,
                        UsageAnalytics.Actions.OPENED_REPORT_BUG,
                    ) { activity -> openFeedback(activity) },
                    exceptionReportItem,
                ),
                ItemHeader(
                    R.string.help_title_community, R.drawable.ic_people_black_24dp, UsageAnalytics.Actions.OPENED_COMMUNITY,
                    LinkItem(
                        R.string.help_item_anki_forums,
                        R.drawable.ic_forum_black_24dp,
                        UsageAnalytics.Actions.OPENED_ANKI_FORUMS,
                        R.string.link_anki_forum,
                    ),
                    LinkItem(R.string.help_item_reddit, R.drawable.reddit, UsageAnalytics.Actions.OPENED_REDDIT, R.string.link_reddit),
                    LinkItem(
                        R.string.help_item_mailing_list,
                        R.drawable.ic_email_black_24dp,
                        UsageAnalytics.Actions.OPENED_MAILING_LIST,
                        R.string.link_forum,
                    ),
                    LinkItem(R.string.help_item_discord, R.drawable.discord, UsageAnalytics.Actions.OPENED_DISCORD, R.string.link_discord),
                    LinkItem(
                        R.string.help_item_facebook,
                        R.drawable.facebook,
                        UsageAnalytics.Actions.OPENED_FACEBOOK,
                        R.string.link_facebook,
                    ),
                    LinkItem(R.string.help_item_twitter, R.drawable.twitter, UsageAnalytics.Actions.OPENED_TWITTER, R.string.link_twitter),
                ),
                ItemHeader(
                    R.string.help_title_privacy,
                    R.drawable.ic_baseline_privacy_tip_24,
                    UsageAnalytics.Actions.OPENED_PRIVACY,
                    LinkItem(
                        R.string.help_item_ankidroid_privacy_policy,
                        R.drawable.ic_baseline_policy_24,
                        UsageAnalytics.Actions.OPENED_ANKIDROID_PRIVACY_POLICY,
                        R.string.link_ankidroid_privacy_policy,
                    ),
                    LinkItem(
                        R.string.help_item_ankiweb_privacy_policy,
                        R.drawable.ic_baseline_policy_24,
                        UsageAnalytics.Actions.OPENED_ANKIWEB_PRIVACY_POLICY,
                        R.string.link_ankiweb_privacy_policy,
                    ),
                    LinkItem(
                        R.string.help_item_ankiweb_terms_and_conditions,
                        R.drawable.ic_baseline_description_24,
                        UsageAnalytics.Actions.OPENED_ANKIWEB_TERMS_AND_CONDITIONS,
                        R.string.link_ankiweb_terms_and_conditions,
                    ),
                ),
            )
        return createInstance(ArrayList(listOf(*allItems)), R.string.help)
    }

    fun createInstanceForSupportAnkiDroid(context: Context?): DialogFragment {
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, UsageAnalytics.Actions.OPENED_SUPPORT_ANKIDROID)
        val rateAppItem =
            RateAppItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_star_black_24, UsageAnalytics.Actions.OPENED_RATE)
        val allItems =
            arrayOf(
                LinkItem(
                    R.string.help_item_support_opencollective_donate,
                    R.drawable.ic_donate_black_24dp,
                    UsageAnalytics.Actions.OPENED_DONATE,
                    R.string.link_opencollective_donate,
                ),
                LinkItem(
                    R.string.multimedia_editor_trans_translate,
                    R.drawable.ic_language_black_24dp,
                    UsageAnalytics.Actions.OPENED_TRANSLATE,
                    R.string.link_translation,
                ),
                LinkItem(
                    R.string.help_item_support_develop_ankidroid,
                    R.drawable.ic_build_black_24,
                    UsageAnalytics.Actions.OPENED_DEVELOP,
                    R.string.link_ankidroid_development_guide,
                ),
                rateAppItem,
                LinkItem(
                    R.string.help_item_support_other_ankidroid,
                    R.drawable.ic_help_black_24dp,
                    UsageAnalytics.Actions.OPENED_OTHER,
                    R.string.link_contribution,
                ),
                FunctionItem(
                    R.string.send_feedback,
                    R.drawable.ic_email_black_24dp,
                    UsageAnalytics.Actions.OPENED_SEND_FEEDBACK,
                ) { activity -> openFeedback(activity) },
            )
        val itemList = ArrayList(listOf(*allItems))
        if (!canOpenIntent(context!!, AnkiDroidApp.getMarketIntent(context))) {
            itemList.remove(rateAppItem)
        }
        return createInstance(itemList, R.string.help_title_support_ankidroid)
    }

    @KotlinCleanup("Convert to @Parcelize")
    class RateAppItem : RecursivePictureMenu.Item, Parcelable {
        constructor(
            @StringRes titleRes: Int,
            @DrawableRes iconRes: Int,
            analyticsRes: String?,
        ) : super(titleRes, iconRes, analyticsRes)

        override fun onClicked(activity: AnkiActivity) {
            tryOpenIntent(activity, AnkiDroidApp.getMarketIntent(activity))
        }

        override fun remove(toRemove: RecursivePictureMenu.Item?) {
            // intentionally blank - no children
        }

        private constructor(source: Parcel?) : super(source!!)

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<RateAppItem?> =
                object : Parcelable.Creator<RateAppItem?> {
                    override fun createFromParcel(source: Parcel): RateAppItem {
                        return RateAppItem(source)
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

        constructor(
            @StringRes titleRes: Int,
            @DrawableRes iconRes: Int,
            analyticsRes: String?,
            @StringRes urlLocation: Int,
        ) : super(titleRes, iconRes, analyticsRes) {
            mUrlLocationRes = urlLocation
        }

        override fun onClicked(activity: AnkiActivity) {
            activity.openUrl(getUrl(activity))
        }

        private fun getUrl(ctx: Context): Uri {
            return Uri.parse(ctx.getString(mUrlLocationRes))
        }

        private constructor(source: Parcel) : super(source) {
            mUrlLocationRes = source.readInt()
        }

        override fun remove(toRemove: RecursivePictureMenu.Item?) {
            // intentionally blank - no children
        }

        override fun writeToParcel(
            dest: Parcel,
            flags: Int,
        ) {
            super.writeToParcel(dest, flags)
            dest.writeInt(mUrlLocationRes)
        }

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<LinkItem?> =
                object : Parcelable.Creator<LinkItem?> {
                    override fun createFromParcel(source: Parcel): LinkItem {
                        return LinkItem(source)
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

        constructor(
            @StringRes titleRes: Int,
            @DrawableRes iconRes: Int,
            analyticsRes: String?,
            func: ActivityConsumer,
        ) : super(titleRes, iconRes, analyticsRes) {
            mFunc = func
        }

        override fun onClicked(activity: AnkiActivity) {
            mFunc.consume(activity)
        }

        private constructor(source: Parcel) : super(source) {
            mFunc =
                ParcelCompat.readSerializable(
                    source,
                    ActivityConsumer::class.java.classLoader,
                    ActivityConsumer::class.java,
                )!!
        }

        override fun remove(toRemove: RecursivePictureMenu.Item?) {
            // intentionally blank - no children
        }

        override fun writeToParcel(
            dest: Parcel,
            flags: Int,
        ) {
            super.writeToParcel(dest, flags)
            dest.writeSerializable(mFunc)
        }

        fun interface ActivityConsumer : Serializable {
            fun consume(activity: AnkiActivity)
        }

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<FunctionItem?> =
                object : Parcelable.Creator<FunctionItem?> {
                    override fun createFromParcel(source: Parcel): FunctionItem {
                        return FunctionItem(source)
                    }

                    override fun newArray(size: Int): Array<FunctionItem?> {
                        return arrayOfNulls(size)
                    }
                }
        }
    }

    private class ExceptionReportItem : RecursivePictureMenu.Item, Parcelable {
        constructor(
            @StringRes titleRes: Int,
            @DrawableRes iconRes: Int,
            analyticsRes: String,
        ) : super(titleRes, iconRes, analyticsRes)

        override fun onClicked(activity: AnkiActivity) {
            if (isUserATestClient) {
                showThemedToast(activity, activity.getString(R.string.user_is_a_robot), false)
                return
            }
            val wasReportSent = CrashReportService.sendReport(activity)
            if (!wasReportSent) {
                showThemedToast(
                    activity,
                    activity.getString(R.string.help_dialog_exception_report_debounce),
                    true,
                )
            }
        }

        private constructor(source: Parcel) : super(source)

        override fun remove(toRemove: RecursivePictureMenu.Item?) {}

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<ExceptionReportItem?> =
                object : Parcelable.Creator<ExceptionReportItem?> {
                    override fun createFromParcel(source: Parcel): ExceptionReportItem {
                        return ExceptionReportItem(source)
                    }

                    override fun newArray(size: Int): Array<ExceptionReportItem?> {
                        return arrayOfNulls(size)
                    }
                }
        }
    }
}
