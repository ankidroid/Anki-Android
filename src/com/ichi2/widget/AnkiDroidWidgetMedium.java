/***************************************************************************************
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

package com.ichi2.widget;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.StudyOptionsFragment;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

public class AnkiDroidWidgetMedium extends AppWidgetProvider {

    private static BroadcastReceiver mMountReceiver = null;
    private static boolean remounted = false;


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(AnkiDroidApp.TAG, "MediumWidget: onUpdate");
        WidgetStatus.update(context);
    }


    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.i(AnkiDroidApp.TAG, "MediumWidget: Widget enabled");
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetMediumEnabled", true).commit();
    }


    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.i(AnkiDroidApp.TAG, "MediumWidget: Widget disabled");
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetMediumEnabled", false).commit();
    }

    public static class UpdateService extends Service {
        /** If this action is used when starting the service, it will move to the next due deck. */
        private static final String ACTION_NEXT = "org.ichi2.anki.AnkiDroidWidget.NEXT";

        /**
         * If this action is used when starting the service, it will move to the previous due deck.
         */
        private static final String ACTION_PREV = "org.ichi2.anki.AnkiDroidWidget.PREV";

        /**
         * When received, this action is ignored by the service.
         * <p>
         * It is used to associate with elements that at some point need to have a pending intent associated with them,
         * but want to clear it off afterwards.
         */
        private static final String ACTION_IGNORE = "org.ichi2.anki.AnkiDroidWidget.IGNORE";

        /**
         * If this action is used when starting the service, it will open the current due deck.
         */
        private static final String ACTION_OPEN = "org.ichi2.anki.AnkiDroidWidget.OPEN";

        /**
         * Update the state of the widget.
         */
        public static final String ACTION_UPDATE = "org.ichi2.anki.AnkiDroidWidget.UPDATE";

        /**
         * The current due deck that is shown in the widget.
         * <p>
         * This value is kept around until as long as the service is running and it is shared by all instances of the
         * widget.
         */
        private int currentDueDeck = 0;

        /** The cached information about the decks with due cards. */
        private List<DeckStatus> dueDecks;
        /** The cached number of total due cards. */
        private int dueCardsCount;


        private CharSequence getDeckStatusString(DeckStatus deck) {
            SpannableStringBuilder sb = new SpannableStringBuilder();

            SpannableString red = new SpannableString(Integer.toString(deck.mLrnCards));
            red.setSpan(new ForegroundColorSpan(Color.RED), 0, red.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            SpannableString black = new SpannableString(Integer.toString(deck.mDueCards));
            black.setSpan(new ForegroundColorSpan(Color.BLACK), 0, black.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            SpannableString blue = new SpannableString(Integer.toString(deck.mNewCards));
            blue.setSpan(new ForegroundColorSpan(Color.BLUE), 0, blue.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            sb.append(red);
            sb.append(" ");
            sb.append(black);
            sb.append(" ");
            sb.append(blue);

            return sb;
        }


        @Override
        public void onStart(Intent intent, int startId) {
            Log.i(AnkiDroidApp.TAG, "MediumWidget: OnStart");

            boolean updateDueDecksNow = true;
            if (intent != null) {
                // Bound checks will be done when updating the widget below.
                if (ACTION_NEXT.equals(intent.getAction())) {
                    currentDueDeck++;
                    // Do not update the due decks on next action.
                    // This causes latency.
                    updateDueDecksNow = false;
                } else if (ACTION_PREV.equals(intent.getAction())) {
                    currentDueDeck--;
                    // Do not update the due decks on prev action.
                    // This causes latency.
                    updateDueDecksNow = false;
                } else if (ACTION_IGNORE.equals(intent.getAction())) {
                    updateDueDecksNow = false;
                } else if (ACTION_OPEN.equals(intent.getAction())) {
                    startActivity(DeckPicker.getLoadDeckIntent(this, intent.getLongExtra(DeckPicker.EXTRA_DECK_ID, 1)));
                    updateDueDecksNow = false;
                } else if (ACTION_UPDATE.equals(intent.getAction())) {
                    // Updating the widget is done below for all actions.
                    Log.d(AnkiDroidApp.TAG, "AnkiDroidWidget.UpdateService: UPDATE");
                }
            }
            RemoteViews updateViews = buildUpdate(this, updateDueDecksNow);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidgetMedium.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }


        private RemoteViews buildUpdate(Context context, boolean updateDueDecksNow) {
            Log.i(AnkiDroidApp.TAG, "MediumWidget: buildUpdate");

            // Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget);

            // Add a click listener to open Anki from the icon.
            // This should be always there, whether there are due cards or not.
            Intent ankiDroidIntent = new Intent(context, DeckPicker.class);
            ankiDroidIntent.setAction(Intent.ACTION_MAIN);
            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingAnkiDroidIntent = PendingIntent.getActivity(context, 0, ankiDroidIntent, 0);
            updateViews.setOnClickPendingIntent(R.id.anki_droid_logo, pendingAnkiDroidIntent);

            if (!AnkiDroidApp.isSdCardMounted()) {
                updateViews.setTextViewText(R.id.anki_droid_title, context.getText(R.string.sdcard_missing_message));
                updateViews.setTextViewText(R.id.anki_droid_name, "");
                updateViews.setTextViewText(R.id.anki_droid_status, "");
                if (mMountReceiver == null) {
                    mMountReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            String action = intent.getAction();
                            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                                Log.i(AnkiDroidApp.TAG, "mMountReceiver - Action = Media Mounted");
                                if (remounted) {
                                    WidgetStatus.update(getBaseContext());
                                    remounted = false;
                                    if (mMountReceiver != null) {
                                        unregisterReceiver(mMountReceiver);
                                    }
                                } else {
                                    remounted = true;
                                }
                            }
                        }
                    };
                    IntentFilter iFilter = new IntentFilter();
                    iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
                    iFilter.addDataScheme("file");
                    registerReceiver(mMountReceiver, iFilter);
                }
                return updateViews;
            }

            // If we do not have a cached version, always update.
            if (dueDecks == null || updateDueDecksNow) {
                // Build a list of decks with due cards.
                // Also compute the total number of cards due.
                updateDueDecks();
            }

            if (dueCardsCount > 0) {
                Resources resources = getResources();
                String decksText = resources
                        .getQuantityString(R.plurals.widget_decks, dueDecks.size(), dueDecks.size());
                String text = resources.getQuantityString(R.plurals.widget_cards_in_decks_due, dueCardsCount,
                        dueCardsCount, decksText);
                updateViews.setTextViewText(R.id.anki_droid_title, text);
                // If the current due deck is out of bound, go back to the first one.
                if (currentDueDeck < 0 || currentDueDeck > dueDecks.size() - 1) {
                    currentDueDeck = 0;
                }
                // Show the name and info from the current due deck.
                DeckStatus deckStatus = dueDecks.get(currentDueDeck);
                updateViews.setTextViewText(R.id.anki_droid_name, deckStatus.mDeckName);
                updateViews.setTextViewText(R.id.anki_droid_status, getDeckStatusString(deckStatus));
                PendingIntent openPendingIntent = getOpenPendingIntent(context, deckStatus.mDeckId);
                updateViews.setOnClickPendingIntent(R.id.anki_droid_name, openPendingIntent);
                updateViews.setOnClickPendingIntent(R.id.anki_droid_status, openPendingIntent);
                // Enable or disable the prev and next buttons.
                if (currentDueDeck > 0) {
                    updateViews.setImageViewResource(R.id.anki_droid_prev, R.drawable.widget_left_arrow);
                    updateViews.setOnClickPendingIntent(R.id.anki_droid_prev, getPrevPendingIntent(context));
                } else {
                    updateViews.setImageViewResource(R.id.anki_droid_prev, R.drawable.widget_left_arrow_disabled);
                    updateViews.setOnClickPendingIntent(R.id.anki_droid_prev, getIgnoredPendingIntent(context));
                }
                if (currentDueDeck < dueDecks.size() - 1) {
                    updateViews.setImageViewResource(R.id.anki_droid_next, R.drawable.widget_right_arrow);
                    updateViews.setOnClickPendingIntent(R.id.anki_droid_next, getNextPendingIntent(context));
                } else {
                    updateViews.setImageViewResource(R.id.anki_droid_next, R.drawable.widget_right_arrow_disabled);
                    updateViews.setOnClickPendingIntent(R.id.anki_droid_next, getIgnoredPendingIntent(context));
                }
                updateViews.setViewVisibility(R.id.anki_droid_name, View.VISIBLE);
                updateViews.setViewVisibility(R.id.anki_droid_status, View.VISIBLE);
                updateViews.setViewVisibility(R.id.anki_droid_next, View.VISIBLE);
                updateViews.setViewVisibility(R.id.anki_droid_prev, View.VISIBLE);
            } else {
                // No card is currently due.
                updateViews.setTextViewText(R.id.anki_droid_title, context.getString(R.string.widget_no_cards_due));
                updateViews.setTextViewText(R.id.anki_droid_name, "");
                updateViews.setTextViewText(R.id.anki_droid_status, "");
                updateViews.setViewVisibility(R.id.anki_droid_name, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.anki_droid_status, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.anki_droid_next, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.anki_droid_prev, View.INVISIBLE);
            }

            return updateViews;
        }


        private void updateDueDecks() {
            // Fetch the deck information, sorted by due cards
            DeckStatus[] decks = WidgetStatus.fetch(getBaseContext());

            if (dueDecks == null) {
                dueDecks = new ArrayList<DeckStatus>();
            } else {
                dueDecks.clear();
            }
            dueCardsCount = 0;
            for (DeckStatus deck : decks) {
                if (deck.mDueCards + deck.mLrnCards + deck.mNewCards > 0) {
                    dueCardsCount += deck.mDueCards + deck.mLrnCards + deck.mNewCards;
                    dueDecks.add(deck);
                }
            }
        }


        /**
         * Returns a pending intent that updates the widget to show the next deck.
         */
        private PendingIntent getNextPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_NEXT);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }


        /**
         * Returns a pending intent that updates the widget to show the previous deck.
         */
        private PendingIntent getPrevPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_PREV);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }


        /**
         * Returns a pending intent that is ignored by the service.
         */
        private PendingIntent getIgnoredPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_IGNORE);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }


        /**
         * Returns a pending intent that opens the current deck.
         */
        private PendingIntent getOpenPendingIntent(Context context, long deckId) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_OPEN);
            ankiDroidIntent.putExtra(DeckPicker.EXTRA_DECK_ID, deckId);
            return PendingIntent.getService(context, 0, ankiDroidIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }


        @Override
        public IBinder onBind(Intent arg0) {
            Log.i(AnkiDroidApp.TAG, "onBind");
            return null;
        }
    }
}
