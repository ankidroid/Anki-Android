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
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.analytics.AnkiDroidCrashReportDialog;
import com.ichi2.anki.dialogs.RecursivePictureMenu.Item;
import com.ichi2.anki.dialogs.RecursivePictureMenu.ItemHeader;
import com.ichi2.anki.exception.UserSubmittedException;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.IntentUtil;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import static com.ichi2.anki.AnkiDroidApp.getInstance;
import static com.ichi2.anki.AnkiDroidApp.getSharedPrefs;

public class HelpDialog {

    private static void openManual(AnkiActivity ankiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.getManualUrl()));
    }

    private static void openFeedback(AnkiActivity ankiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.getFeedbackUrl()));
    }

    public static DialogFragment createInstance(Context context) {

        RateAppItem rateAppItem = new RateAppItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_star_black_24);
        ExceptionReportItem exceptionReportItem = new ExceptionReportItem(R.string.help_title_send_exception, R.drawable.ic_round_assignment_24);
        Item[] allItems = {
                new ItemHeader(R.string.help_title_using_ankidroid, R.drawable.ic_manual_black_24dp,
                        new FunctionItem(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, HelpDialog::openManual),
                        new LinkItem(R.string.help_item_anki_manual, R.drawable.ic_manual_black_24dp, R.string.link_anki_manual),
                        new LinkItem(R.string.help_item_ankidroid_faq, R.drawable.ic_help_black_24dp, R.string.link_ankidroid_faq)
                ),
                new ItemHeader(R.string.help_title_get_help, R.drawable.ic_help_black_24dp,
                        new LinkItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, R.string.link_forum),
                        new FunctionItem(R.string.help_item_report_bug, R.drawable.ic_bug_report_black_24dp, HelpDialog::openFeedback),
                        exceptionReportItem
                ),
                new ItemHeader(R.string.help_title_support_ankidroid, R.drawable.ic_heart_black_24dp,
                        new LinkItem(R.string.help_item_support_opencollective_donate, R.drawable.ic_donate_black_24dp, R.string.link_opencollective_donate),
                        new LinkItem(R.string.multimedia_editor_trans_translate, R.drawable.ic_language_black_24dp, R.string.link_translation),
                        new LinkItem(R.string.help_item_support_develop_ankidroid, R.drawable.ic_build_black_24, R.string.link_ankidroid_development_guide),
                        rateAppItem,
                        new LinkItem(R.string.help_item_support_other_ankidroid, R.drawable.ic_help_black_24dp, R.string.link_contribution),
                        new FunctionItem(R.string.send_feedback, R.drawable.ic_email_black_24dp, HelpDialog::openFeedback)
                ),
                new ItemHeader(R.string.help_title_community, R.drawable.ic_people_black_24dp,
                        new LinkItem(R.string.help_item_anki_forums, R.drawable.ic_forum_black_24dp, R.string.link_anki_forum),
                        new LinkItem(R.string.help_item_reddit, R.drawable.ic_mail_outline_black_24dp, R.string.link_reddit),
                        new LinkItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, R.string.link_forum),
                        new LinkItem(R.string.help_item_discord, R.drawable.ic_message_black_24dp, R.string.link_discord),
                        new LinkItem(R.string.help_item_facebook, R.drawable.ic_link_black_24dp, R.string.link_facebook),
                        new LinkItem(R.string.help_item_twitter, R.drawable.ic_link_black_24dp, R.string.link_twitter)
                )
        };

        ArrayList<Item> itemList = new ArrayList<>(Arrays.asList(allItems));

        if (!IntentUtil.canOpenIntent(context, AnkiDroidApp.getMarketIntent(context))) {
            RecursivePictureMenu.removeFrom(itemList, rateAppItem);
        }

        return RecursivePictureMenu.createInstance(itemList, R.string.help);
    }

    public static class RateAppItem extends Item implements Parcelable {

        public RateAppItem(@StringRes int titleRes, @DrawableRes int iconRes) {
            super(titleRes, iconRes);
        }

        @Override
        public void execute(AnkiActivity activity) {
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
        private final @StringRes int mUrlLocationRes;

        public LinkItem(@StringRes int titleRes, @DrawableRes int iconRes, @StringRes int urlLocation) {
            super(titleRes, iconRes);

            this.mUrlLocationRes = urlLocation;
        }

        @Override
        public void execute(AnkiActivity activity) {
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

        public FunctionItem(@StringRes int titleRes, @DrawableRes int iconRes, ActivityConsumer func) {
            super(titleRes, iconRes);
            this.mFunc = func;
        }

        @Override
        public void execute(AnkiActivity activity)  {
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
            void consume(AnkiActivity activity);
        }
    }

    private static class ExceptionReportItem extends Item implements Parcelable{

        private static Long lastClickStamp;
        static final long currentTimestamp = SystemClock.uptimeMillis();
        final int minIntervalMS = 60000;
        final String exceptionMessage = "Exception report sent by user manually";
        String reportMode = getSharedPrefs(getInstance().getApplicationContext()).getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, "");

        public ExceptionReportItem(@StringRes int titleRes, @DrawableRes int iconRes) { super(titleRes, iconRes); }

        @Override
        public void execute(AnkiActivity activity) {
            if (AdaptionUtil.isUserATestClient()) {
                UIUtils.showThemedToast(activity, activity.getString(R.string.user_is_a_robot), false);
                return;
            }

            if (reportMode.equals(AnkiDroidApp.FEEDBACK_REPORT_NEVER)) {
                getSharedPrefs(activity).edit().putBoolean(ACRA.PREF_DISABLE_ACRA, false).apply();
                getInstance().getAcraCoreConfigBuilder().getPluginConfigurationBuilder(DialogConfigurationBuilder.class)
                        .setEnabled(true);
                sendReport(activity);
                getSharedPrefs(activity).edit().putBoolean(ACRA.PREF_DISABLE_ACRA, true).apply();
            } else { sendReport(activity); }
        }

        private void sendReport(AnkiActivity activity) {
            if (lastClickStamp == null || currentTimestamp - lastClickStamp > minIntervalMS) {
                AnkiDroidApp.deleteACRALimiterData(activity);
                AnkiDroidApp.sendExceptionReport(
                        new UserSubmittedException(exceptionMessage),
                        "AnkiDroidApp.HelpDialog");
                lastClickStamp = currentTimestamp;
            } else {
                UIUtils.showThemedToast(activity, activity.getString(R.string.help_dialog_exception_report_debounce),
                        true);
            }
        }

        protected ExceptionReportItem(Parcel in) {
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
