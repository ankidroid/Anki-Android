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
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.anki.dialogs.RecursivePictureMenu.Item;
import com.ichi2.anki.dialogs.RecursivePictureMenu.ItemHeader;
import com.ichi2.utils.IntentUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

public class HelpDialog {

    private static void openManual(AnkiActivity ankiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.getManualUrl()));
    }


    private static void openFeedback(AnkiActivity ankiActivity) {
        ankiActivity.openUrl(Uri.parse(AnkiDroidApp.getFeedbackUrl()));
    }


    public static DialogFragment createInstance(Context context) {

        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED,"Opened");
        RateAppItem rateAppItem = new RateAppItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_star_black_24, R.string.opened_rate);
        Item[] allItems = {
                new ItemHeader(R.string.help_title_using_ankidroid, R.drawable.ic_manual_black_24dp, R.string.opened_using_ankidroid,
                        new FunctionItem(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, R.string.opened_ankidroid_manual, HelpDialog::openManual),
                        new LinkItem(R.string.help_item_anki_manual, R.drawable.ic_manual_black_24dp, R.string.opened_anki_manual, R.string.link_anki_manual),
                        new LinkItem(R.string.help_item_ankidroid_faq, R.drawable.ic_help_black_24dp, R.string.opened_ankidroid_faq, R.string.link_ankidroid_faq)
                ),
                new ItemHeader(R.string.help_title_get_help, R.drawable.ic_help_black_24dp, R.string.opened_get_help,
                        new LinkItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, R.string.opened_mailing_list, R.string.link_forum),
                        new FunctionItem(R.string.help_item_report_bug, R.drawable.ic_bug_report_black_24dp, R.string.opened_report_bug, HelpDialog::openFeedback)
                ),
                new ItemHeader(R.string.help_title_support_ankidroid, R.drawable.ic_heart_black_24dp, R.string.opened_support_ankidroid,
                        new LinkItem(R.string.help_item_support_opencollective_donate, R.drawable.ic_donate_black_24dp, R.string.opened_donate, R.string.link_opencollective_donate),
                        new LinkItem(R.string.multimedia_editor_trans_translate, R.drawable.ic_language_black_24dp, R.string.opened_translate, R.string.link_translation),
                        new LinkItem(R.string.help_item_support_develop_ankidroid, R.drawable.ic_build_black_24, R.string.opened_develop, R.string.link_ankidroid_development_guide),
                        rateAppItem,
                        new LinkItem(R.string.help_item_support_other_ankidroid, R.drawable.ic_help_black_24dp, R.string.opened_other, R.string.link_contribution),
                        new FunctionItem(R.string.send_feedback, R.drawable.ic_email_black_24dp, R.string.opened_send_feedback, HelpDialog::openFeedback)
                ),
                new ItemHeader(R.string.help_title_community, R.drawable.ic_people_black_24dp, R.string.opened_community,
                        new LinkItem(R.string.help_item_anki_forums, R.drawable.ic_forum_black_24dp, R.string.opened_anki_forums, R.string.link_anki_forum),
                        new LinkItem(R.string.help_item_reddit, R.drawable.ic_mail_outline_black_24dp, R.string.opened_reddit, R.string.link_reddit),
                        new LinkItem(R.string.help_item_mailing_list, R.drawable.ic_email_black_24dp, R.string.opened_mailing_list, R.string.link_forum),
                        new LinkItem(R.string.help_item_discord, R.drawable.ic_message_black_24dp, R.string.opened_discord, R.string.link_discord),
                        new LinkItem(R.string.help_item_facebook, R.drawable.ic_link_black_24dp, R.string.opened_facebook, R.string.link_facebook),
                        new LinkItem(R.string.help_item_twitter, R.drawable.ic_link_black_24dp, R.string.opened_twitter, R.string.link_twitter)
                ),
        };

        ArrayList<Item> itemList = new ArrayList<>(Arrays.asList(allItems));

        if (!IntentUtil.canOpenIntent(context, AnkiDroidApp.getMarketIntent(context))) {
            RecursivePictureMenu.removeFrom(itemList, rateAppItem);
        }

        return RecursivePictureMenu.createInstance(itemList, R.string.help);
    }


    public static class RateAppItem extends Item implements Parcelable {

        private @StringRes
        int mAnalyticsRes;


        public RateAppItem(@StringRes int titleRes, @DrawableRes int iconRes, @StringRes int analyticsRes) {
            super(titleRes, iconRes, analyticsRes);
            mAnalyticsRes = analyticsRes;
        }


        @Override
        protected void onClicked(AnkiActivity activity) {
            IntentUtil.tryOpenIntent(activity, AnkiDroidApp.getMarketIntent(activity));
        }


        @Override
        public String getAnalyticsId(Context context) {
            return context.getString(mAnalyticsRes);
        }


        /*This method calls onClicked method to handle click event in a suitable manner and
        * the analytics of the item clicked are send.
        */
        @Override
        public void execute(AnkiActivity activity) {
            onClicked(activity);
            UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, getAnalyticsId(activity));
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
        private final @StringRes
        int mUrlLocationRes;
        private @StringRes
        int mAnalyticsRes;


        public LinkItem(@StringRes int titleRes, @DrawableRes int iconRes, @StringRes int analyticsRes, @StringRes int urlLocation) {
            super(titleRes, iconRes, analyticsRes);

            this.mAnalyticsRes = analyticsRes;
            this.mUrlLocationRes = urlLocation;
        }


        @Override
        protected void onClicked(AnkiActivity activity) {
            activity.openUrl(getUrl(activity));
        }


        @Override
        public String getAnalyticsId(Context context) {
            return context.getString(mAnalyticsRes);
        }

        /*This method calls onClicked method to handle click event in a suitable manner and
         * the analytics of the item clicked are send.
         */
        @Override
        public void execute(AnkiActivity activity) {
            onClicked(activity);
            UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, getAnalyticsId(activity));
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
        private @StringRes
        int mAnalyticsRes;


        public FunctionItem(@StringRes int titleRes, @DrawableRes int iconRes, @StringRes int analyticsRes, ActivityConsumer func) {
            super(titleRes, iconRes, analyticsRes);
            this.mFunc = func;
            this.mAnalyticsRes = analyticsRes;
        }


        @Override
        protected void onClicked(AnkiActivity activity) {
            mFunc.consume(activity);
        }


        @Override
        public String getAnalyticsId(Context context) {
            return context.getString(mAnalyticsRes);
        }

        /*This method calls onClicked method to handle click event in a suitable manner and
         * the analytics of the item clicked are send.
         */
        @Override
        public void execute(AnkiActivity activity) {
            onClicked(activity);
            UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.LINK_CLICKED, getAnalyticsId(activity));
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
}
