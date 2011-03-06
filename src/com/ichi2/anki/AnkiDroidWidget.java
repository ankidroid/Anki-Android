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

package com.ichi2.anki;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.SQLException;
import android.graphics.Color;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.RemoteViews;

import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnkiDroidWidget extends AppWidgetProvider {


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(AnkiDroidApp.TAG, "onUpdate");
        context.startService(new Intent(context, UpdateService.class));
    }

    public static class UpdateService extends Service {
        /** If this action is used when starting the service, it will move to the next due deck. */
        private static final String ACTION_NEXT = "org.ichi2.anki.AnkiDroidWidget.NEXT";
        /**
         * If this action is used when starting the service, it will move to the previous due
         * deck.
         */
        private static final String ACTION_PREV = "org.ichi2.anki.AnkiDroidWidget.PREV";

        /**
         * The current due deck that is shown in the widget.
         *
         * <p>This value is kept around until as long as the service is running and it is shared
         * by all instances of the widget.
         */
        private int currentDueDeck = 0;

        /** The cached information about the decks with due cards. */
        private List<DeckInformation> dueDecks;
        /** The cached number of total due cards. */
        private int dueCardsCount;

        // Simple class to hold the deck information for the widget
        private class DeckInformation {

            private String mDeckName;
            private int mNewCards;
            private int mDueCards;
            private int mFailedCards;


            private DeckInformation(String deckName, int newCards, int dueCards, int failedCards) {
                mDeckName = deckName;
                mNewCards = newCards;       // Blue
                mDueCards = dueCards;       // Black
                mFailedCards = failedCards; // Red
            }


            @Override
            public String toString() {
                return String.format("%s %d %d", mDeckName, mNewCards, mDueCards);
            }


            private CharSequence getDeckStatus() {
                SpannableStringBuilder sb = new SpannableStringBuilder();

                SpannableString red = new SpannableString(Integer.toString(mFailedCards));
                red.setSpan(new ForegroundColorSpan(Color.RED), 0, red.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                SpannableString black = new SpannableString(Integer.toString(mDueCards));
                black.setSpan(new ForegroundColorSpan(Color.BLACK), 0, black.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                SpannableString blue = new SpannableString(Integer.toString(mNewCards));
                blue.setSpan(new ForegroundColorSpan(Color.BLUE), 0, blue.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                sb.append(red);
                sb.append(" ");
                sb.append(black);
                sb.append(" ");
                sb.append(blue);

                return sb;
            }

        }


        @Override
        public void onStart(Intent intent, int startId) {
            Log.i(AnkiDroidApp.TAG, "OnStart");

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
                }
            }
            RemoteViews updateViews = buildUpdate(this, updateDueDecksNow);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidget.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }

        private RemoteViews buildUpdate(Context context, boolean updateDueDecksNow) {
            Log.i(AnkiDroidApp.TAG, "buildUpdate");

            // Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget);

            // Add a click listener to open Anki from the icon.
            // This should be always there, whether there are due cards or not.
            Intent ankiDroidIntent = new Intent(context, StudyOptions.class);
            ankiDroidIntent.setAction(Intent.ACTION_MAIN);
            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingAnkiDroidIntent =
                PendingIntent.getActivity(context, 0, ankiDroidIntent, 0);
            updateViews.setOnClickPendingIntent(R.id.anki_droid_logo,
                    pendingAnkiDroidIntent);

            if (!AnkiDroidApp.isSdCardMounted()) {
                updateViews.setTextViewText(R.id.anki_droid_title,
                    context.getText(R.string.sdcard_missing_message));
                updateViews.setTextViewText(R.id.anki_droid_name, "");
                updateViews.setTextViewText(R.id.anki_droid_status, "");
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
                String decksText = resources.getQuantityString(
                        R.plurals.widget_decks, dueDecks.size(), dueDecks.size());
                String text = resources.getQuantityString(
                        R.plurals.widget_cards_in_decks_due, dueCardsCount, dueCardsCount, decksText);
                updateViews.setTextViewText(R.id.anki_droid_title, text);
                // If the current due deck is out of bound, go back to the first one.
                if (currentDueDeck < 0 || currentDueDeck > dueDecks.size() - 1) {
                    currentDueDeck = 0;
                }
                // Show the name and info from the current due deck.
                DeckInformation deckInformation = dueDecks.get(currentDueDeck);
                updateViews.setTextViewText(R.id.anki_droid_name,
                    deckInformation.mDeckName);
                updateViews.setTextViewText(R.id.anki_droid_status,
                    deckInformation.getDeckStatus());
                // Enable or disable the prev and next buttons.
                if (currentDueDeck > 0) {
                    updateViews.setImageViewResource(R.id.anki_droid_prev, R.drawable.widget_left_arrow);
                    updateViews.setOnClickPendingIntent(R.id.anki_droid_prev, getPrevPendingIntent(context));
                } else {
                    updateViews.setImageViewResource(R.id.anki_droid_prev, R.drawable.widget_left_arrow_disabled);
                }
                if (currentDueDeck < dueDecks.size() - 1) {
                    updateViews.setImageViewResource(R.id.anki_droid_next, R.drawable.widget_right_arrow);
                    updateViews.setOnClickPendingIntent(R.id.anki_droid_next, getNextPendingIntent(context));
                } else {
                    updateViews.setImageViewResource(R.id.anki_droid_next, R.drawable.widget_right_arrow_disabled);
                }
            } else {
                // No card is currently due.
                updateViews.setTextViewText(R.id.anki_droid_title,
                    context.getString(R.string.widget_no_cards_due));
                updateViews.setTextViewText(R.id.anki_droid_name, "");
                updateViews.setTextViewText(R.id.anki_droid_status, "");
            }

            SharedPreferences preferences = PrefSettings.getSharedPrefs(context);

            int minimumCardsDueForNotification = Integer.parseInt(preferences.getString(
                    "minimumCardsDueForNotification", "25"));

            if (dueCardsCount >= minimumCardsDueForNotification) {
                // Raise a notification
                String ns = Context.NOTIFICATION_SERVICE;
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

                int icon = R.drawable.anki;
                CharSequence tickerText = String.format(
                        getString(R.string.widget_minimum_cards_due_notification_ticker_text), dueCardsCount);
                long when = System.currentTimeMillis();

                Notification notification = new Notification(icon, tickerText, when);

                if (preferences.getBoolean("widgetVibrate", false)) {
                    notification.defaults |= Notification.DEFAULT_VIBRATE;
                }
                if (preferences.getBoolean("widgetBlink", false)) {
                    notification.defaults |= Notification.DEFAULT_LIGHTS;
                }

                Context appContext = getApplicationContext();
                CharSequence contentTitle = getText(R.string.widget_minimum_cards_due_notification_ticker_title);

                notification.setLatestEventInfo(appContext, contentTitle, tickerText, pendingAnkiDroidIntent);

                final int WIDGET_NOTIFY_ID = 1;
                mNotificationManager.notify(WIDGET_NOTIFY_ID, notification);
            }

            return updateViews;
        }


        private void updateDueDecks() {
            Deck currentDeck = AnkiDroidApp.deck();
            if (currentDeck != null) {
                // Close the current deck, otherwise we'll have problems
                currentDeck.closeDeck();
            }

            // Fetch the deck information, sorted by due cards
            ArrayList<DeckInformation> decks = fetchDeckInformation();

            if (currentDeck != null) {
                AnkiDroidApp.setDeck(currentDeck);
                Deck.openDeck(currentDeck.getDeckPath());
            }

            if (dueDecks == null) {
                dueDecks = new ArrayList<DeckInformation>();
            } else {
                dueDecks.clear();
            }
            dueCardsCount = 0;
            for (int i = 0; i < decks.size(); i++) {
                DeckInformation deck = decks.get(i);
                if (deck.mDueCards > 0) {
                  dueCardsCount += deck.mDueCards;
                  dueDecks.add(deck);
                }
            }
        }

//        @SuppressWarnings("unused")
//        private ArrayList<DeckInformation> mockFetchDeckInformation() {
//            final int maxDecks = 10;
//            ArrayList<DeckInformation> information = new ArrayList<DeckInformation>(maxDecks);
//
//            for (int i = 0; i < maxDecks; i++) {
//                String deckName = String.format("my anki deck number %d", i);
//                information.add(new DeckInformation(deckName, i * 20, i * 25, i * 5));
//            }
//
//            Collections.sort(information, new ByDueComparator());
//            Collections.reverse(information);
//
//            return information;
//        }

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

        private ArrayList<DeckInformation> fetchDeckInformation() {
            Log.i(AnkiDroidApp.TAG, "fetchDeckInformation");

            SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
            String deckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory() + "/AnkiDroid");

            File dir = new File(deckPath);

            File[] fileList = dir.listFiles(new AnkiFileFilter());

            if (fileList == null || fileList.length == 0) {
                return new ArrayList<DeckInformation>();
            }

            // For the deck information
            ArrayList<DeckInformation> information = new ArrayList<DeckInformation>(fileList.length);

            for (File file : fileList) {
                try {
                    // Run through the decks and get the information
                    String absPath = file.getAbsolutePath();
                    String deckName = file.getName().replaceAll(".anki", "");

                    Deck deck = Deck.openDeck(absPath);
                    int dueCards = deck.getDueCount();
                    int newCards = deck.getNewCountToday();
                    int failedCards = deck.getFailedSoonCount();
                    deck.closeDeck();

                    // Add the information about the deck
                    information.add(new DeckInformation(deckName, newCards, dueCards, failedCards));
                } catch (SQLException e) {
                    Log.i(AnkiDroidApp.TAG, "Could not open deck");
                    Log.e(AnkiDroidApp.TAG, e.toString());
                }
            }

            if (!information.isEmpty() && information.size() > 1) {
                // Sort and reverse the list if there are decks
                Log.i(AnkiDroidApp.TAG, "Sorting deck");

                // Ordered by reverse due cards number
                Collections.sort(information, new ByDueComparator());
            }

            return information;
        }

        // Sorter for the decks based on number due
        public class ByDueComparator implements java.util.Comparator<DeckInformation> {
            @Override
            public int compare(DeckInformation deck1, DeckInformation deck2) {
                // Reverse due cards number order
                return deck2.mDueCards - deck1.mDueCards;
            }
        }

        private static final class AnkiFileFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getName().endsWith(".anki");
            }
        }


        @Override
        public IBinder onBind(Intent arg0) {
            Log.i(AnkiDroidApp.TAG, "onBind");
            return null;
        }
    }
}
