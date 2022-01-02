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

package com.ichi2.anki.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.dialogs.RecursivePictureMenu.Item;
import com.ichi2.anki.dialogs.RecursivePictureMenu.ItemHeader;
import com.ichi2.anki.exception.UserSubmittedException;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.IntentUtil;

import org.acra.ACRA;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.LimiterData;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import timber.log.Timber;

public class HelpDialog {

    private static void openManual(AnkiActivity ankiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.getManualUrl()));
    }

    private static void openFeedback(AnkiActivity ankiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.getFeedbackUrl()));
    }

    public static DialogFragment createInstance(Context context) {

        ExceptionReportItem exceptionReportItem = new ExceptionReportItem(R.string.help_title_send_exception, R.drawable.ic_round_assignment_24, UsageAnalytics.Actions.EXCEPTION_REPORT);
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, UsageAnalytics.Actions.OPENED_HELPDIALOG);
        RateAppItem rateAppItem = new RateAppItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_star_black_24, UsageAnalytics.Actions.OPENED_RATE);
        Item[] allItems = {
                new ItemHeader(R.string.help_title_using_ankidroid, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_USING_ANKIDROID,
                        new FunctionItem(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL, HelpDialog::openManual),
                        new LinkItem(R.string.help_item_anki_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKI_MANUAL, R.string.link_anki_manual),
                        new LinkItem(R.string.help_item_ankidroid_faq, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_FAQ, R.string.link_ankidroid_faq)
                ),
                new ItemHeader(R.string.help_title_get_help, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_GET_HELP,
                        new LinkItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_MAILING_LIST, R.string.link_forum),
                        new FunctionItem(R.string.help_item_report_bug, R.drawable.ic_bug_report_black_24dp, UsageAnalytics.Actions.OPENED_REPORT_BUG, HelpDialog::openFeedback),
                        exceptionReportItem
                ),
                new ItemHeader(R.string.help_title_community, R.drawable.ic_people_black_24dp, UsageAnalytics.Actions.OPENED_COMMUNITY,
                        new LinkItem(R.string.help_item_anki_forums, R.drawable.ic_forum_black_24dp, UsageAnalytics.Actions.OPENED_ANKI_FORUMS, R.string.link_anki_forum),
                        new LinkItem(R.string.help_item_reddit, R.drawable.reddit, UsageAnalytics.Actions.OPENED_REDDIT, R.string.link_reddit),
                        new LinkItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_MAILING_LIST, R.string.link_forum),
                        new LinkItem(R.string.help_item_discord, R.drawable.discord, UsageAnalytics.Actions.OPENED_DISCORD, R.string.link_discord),
                        new LinkItem(R.string.help_item_facebook, R.drawable.facebook, UsageAnalytics.Actions.OPENED_FACEBOOK, R.string.link_facebook),
                        new LinkItem(R.string.help_item_twitter, R.drawable.twitter, UsageAnalytics.Actions.OPENED_TWITTER, R.string.link_twitter)
                ),
                new ItemHeader(R.string.help_title_privacy, R.drawable.ic_baseline_privacy_tip_24, UsageAnalytics.Actions.OPENED_PRIVACY,
                        new LinkItem(R.string.help_item_ankidroid_privacy_policy, R.drawable.ic_baseline_policy_24, UsageAnalytics.Actions.OPENED_ANKIDROID_PRIVACY_POLICY, R.string.link_ankidroid_privacy_policy),
                        new LinkItem(R.string.help_item_ankiweb_privacy_policy, R.drawable.ic_baseline_policy_24, UsageAnalytics.Actions.OPENED_ANKIWEB_PRIVACY_POLICY, R.string.link_ankiweb_privacy_policy),
                        new LinkItem(R.string.help_item_ankiweb_terms_and_conditions, R.drawable.ic_baseline_description_24, UsageAnalytics.Actions.OPENED_ANKIWEB_TERMS_AND_CONDITIONS, R.string.link_ankiweb_terms_and_conditions)
                )
        };

        ArrayList<Item> itemList = new ArrayList<>(Arrays.asList(allItems));

        if (!IntentUtil.canOpenIntent(context, AnkiDroidApp.getMarketIntent(context))) {
            RecursivePictureMenu.removeFrom(itemList, rateAppItem);
        }

        return RecursivePictureMenu.createInstance(itemList, R.string.help);
    }

    public static DialogFragment createInstanceForSupportAnkiDroid(Context context) {
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, UsageAnalytics.Actions.OPENED_SUPPORT_ANKIDROID);
        RateAppItem rateAppItem = new RateAppItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_star_black_24, UsageAnalytics.Actions.OPENED_RATE);
        Item[] allItems = {
                new LinkItem(R.string.help_item_support_opencollective_donate, R.drawable.ic_donate_black_24dp, UsageAnalytics.Actions.OPENED_DONATE, R.string.link_opencollective_donate),
                new LinkItem(R.string.multimedia_editor_trans_translate, R.drawable.ic_language_black_24dp, UsageAnalytics.Actions.OPENED_TRANSLATE, R.string.link_translation),
                new LinkItem(R.string.help_item_support_develop_ankidroid, R.drawable.ic_build_black_24, UsageAnalytics.Actions.OPENED_DEVELOP, R.string.link_ankidroid_development_guide),
                rateAppItem,
                new LinkItem(R.string.help_item_support_other_ankidroid, R.drawable.ic_help_black_24dp, UsageAnalytics.Actions.OPENED_OTHER, R.string.link_contribution),
                new FunctionItem(R.string.send_feedback, R.drawable.ic_email_black_24dp, UsageAnalytics.Actions.OPENED_SEND_FEEDBACK, HelpDialog::openFeedback)
        };

        ArrayList<Item> itemList = new ArrayList<>(Arrays.asList(allItems));

        if (!IntentUtil.canOpenIntent(context, AnkiDroidApp.getMarketIntent(context))) {
            RecursivePictureMenu.removeFrom(itemList, rateAppItem);
        }
        return RecursivePictureMenu.createInstance(itemList, R.string.help_title_support_ankidroid);
    }

    public static class RateAppItem extends Item implements Parcelable {

        public RateAppItem(@StringRes int titleRes, @DrawableRes int iconRes, String analyticsRes) {
            super(titleRes, iconRes, analyticsRes);
        }

        @Override
        protected void onClicked(AnkiActivity activity) {
            IntentUtil.tryOpenIntent(activity, AnkiDroidApp.getMarketIntent(activity));
        }

        @Override
        public void remove(Item toRemove) {
            // intentionally blank - no children
        }

        protected RateAppItem(Parcel in) {
            super(in);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<RateAppItem> CREATOR = new Parcelable.Creator<RateAppItem>() {
            @Override
            public RateAppItem createFromParcel(Parcel in) {
                return new RateAppItem(in);
            }

            @Override
            public RateAppItem[] newArray(int size) {
                return new RateAppItem[size];
            }
        };
    }

    public static class LinkItem extends Item implements Parcelable {
        @StringRes
        private final int mUrlLocationRes;

        public LinkItem(@StringRes int titleRes, @DrawableRes int iconRes, String analyticsRes, @StringRes int urlLocation) {
            super(titleRes, iconRes, analyticsRes);

            this.mUrlLocationRes = urlLocation;
        }

        @Override
        protected void onClicked(AnkiActivity activity) {
            activity.openUrl(getUrl(activity));
        }

        protected Uri getUrl(Context ctx) {
            return Uri.parse(ctx.getString(mUrlLocationRes));
        }

        protected LinkItem(Parcel in) {
            super(in);
            mUrlLocationRes = in.readInt();
        }

        @Override
        public void remove(Item toRemove) {
            // intentionally blank - no children
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mUrlLocationRes);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<LinkItem> CREATOR = new Parcelable.Creator<LinkItem>() {
            @Override
            public LinkItem createFromParcel(Parcel in) {
                return new LinkItem(in);
            }

            @Override
            public LinkItem[] newArray(int size) {
                return new LinkItem[size];
            }
        };
    }

    public static class FunctionItem extends Item implements Parcelable {
        private final ActivityConsumer mFunc;

        public FunctionItem(@StringRes int titleRes, @DrawableRes int iconRes, String analyticsRes, ActivityConsumer func) {
            super(titleRes, iconRes, analyticsRes);
            this.mFunc = func;
        }

        @Override
        protected void onClicked(AnkiActivity activity) {
            mFunc.consume(activity);
        }

        protected FunctionItem(Parcel in) {
            super(in);
            mFunc = (ActivityConsumer) in.readSerializable();
        }

        @Override
        public void remove(Item toRemove) {
            // intentionally blank - no children
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeSerializable(mFunc);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<FunctionItem> CREATOR = new Parcelable.Creator<FunctionItem>() {
            @Override
            public FunctionItem createFromParcel(Parcel in) {
                return new FunctionItem(in);
            }

            @Override
            public FunctionItem[] newArray(int size) {
                return new FunctionItem[size];
            }
        };

        @FunctionalInterface
        public interface ActivityConsumer extends Serializable {
            void consume(@NonNull AnkiActivity activity);
        }
    }

    private static class ExceptionReportItem extends Item implements Parcelable {

        private final static int MIN_INTERVAL_MS = 60000;
        private final static String EXCEPTION_MESSAGE = "Exception report sent by user manually";

        private ExceptionReportItem(@StringRes int titleRes, @DrawableRes int iconRes, String analyticsRes) {
            super(titleRes, iconRes, analyticsRes);
        }

        @Override
        protected void onClicked(AnkiActivity activity) {
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(activity);
            String reportMode = preferences.getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, "");

            if (AdaptionUtil.isUserATestClient()) {
                UIUtils.showThemedToast(activity, activity.getString(R.string.user_is_a_robot), false);
                return;
            }

            if (AnkiDroidApp.FEEDBACK_REPORT_NEVER.equals(reportMode)) {
                preferences.edit().putBoolean(ACRA.PREF_DISABLE_ACRA, false).apply();
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder()
                        .getPluginConfigurationBuilder(DialogConfigurationBuilder.class)
                        .setEnabled(true);
                sendReport(activity);
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder()
                        .getPluginConfigurationBuilder(DialogConfigurationBuilder.class)
                        .setEnabled(false);
                preferences.edit().putBoolean(ACRA.PREF_DISABLE_ACRA, true).apply();
            } else {
                sendReport(activity);
            }
        }


        /**
         * Check the ACRA report store and return the timestamp of the last report.
         *
         * @param activity the Activity used for Context access when interrogating ACRA reports
         * @return the timestamp of the most recent report, or -1 if no reports at all
         */
        @SuppressWarnings("unchecked") // Upstream issue for access to field/method: https://github.com/ACRA/acra/issues/843
        private long getTimestampOfLastReport(AnkiActivity activity) {
            try {
                // The ACRA LimiterData holds a timestamp for every generated report
                LimiterData limiterData = LimiterData.load(activity);
                Field limiterDataListField = limiterData.getClass().getDeclaredField("list");
                limiterDataListField.setAccessible(true);
                List<LimiterData.ReportMetadata> limiterDataList = (List<LimiterData.ReportMetadata>)limiterDataListField.get(limiterData);
                for (LimiterData.ReportMetadata report : limiterDataList) {
                    if (!report.getExceptionClass().equals(UserSubmittedException.class.getName())) {
                        continue;
                    }
                    Method timestampMethod = report.getClass().getDeclaredMethod("getTimestamp");
                    timestampMethod.setAccessible(true);
                    Calendar timestamp = (Calendar)timestampMethod.invoke(report);
                    // Limiter ensures there is only one report for the class, so if we found it, return it
                    return timestamp.getTimeInMillis();
                }
            } catch (Exception e) {
                Timber.w(e, "Unexpected exception checking for recent reports");
            }

            return -1;
        }

        private void sendReport(AnkiActivity activity) {
            long currentTimestamp = activity.getCol().getTime().intTimeMS();
            long lastReportTimestamp = getTimestampOfLastReport(activity);
            if ((currentTimestamp - lastReportTimestamp) > MIN_INTERVAL_MS) {
                AnkiDroidApp.deleteACRALimiterData(activity);
                AnkiDroidApp.sendExceptionReport(
                        new UserSubmittedException(EXCEPTION_MESSAGE),
                        "AnkiDroidApp.HelpDialog");
            } else {
                UIUtils.showThemedToast(activity, activity.getString(R.string.help_dialog_exception_report_debounce),
                        true);
            }
        }

        private ExceptionReportItem(Parcel in) {
            super(in);
        }

        public static final Creator<ExceptionReportItem> CREATOR = new Creator<ExceptionReportItem>() {
            @Override
            public ExceptionReportItem createFromParcel(Parcel in) {
                return new ExceptionReportItem(in);
            }


            @Override
            public ExceptionReportItem[] newArray(int size) {
                return new ExceptionReportItem[size];
            }
        };

        @Override
        public void remove(Item toRemove) { }
    }
}
