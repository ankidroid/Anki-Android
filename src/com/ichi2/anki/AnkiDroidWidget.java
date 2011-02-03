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

public class AnkiDroidWidget extends AppWidgetProvider {

    /** The maximum number of decks present in the widget. */
    private static final int MAX_DECK_NUM = 3;

    /** The maximum deck name length allowed. */
    private static final int MAX_NAME_LEN = 13;


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Log.i(AnkiDroidApp.TAG, "onUpdate");
        context.startService(new Intent(context, UpdateService.class));
    }

    public static class UpdateService extends Service {

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
                String deckName = formatDeckName();

                return String.format("%s %d %d", deckName, mNewCards, mDueCards);
            }


            private CharSequence getDeckText() {
                String deckName = formatDeckName();

                SpannableStringBuilder sb = new SpannableStringBuilder();

                sb.append(deckName);
                sb.append(" ");

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


            /**
             * Truncate the deck name if needed.
             * @return an usable (eventually shortened) deck name
             */
            private String formatDeckName() {
                return (mDeckName.length() > MAX_NAME_LEN) ? mDeckName.substring(0, MAX_NAME_LEN) : mDeckName;
            }
        }


        @Override
        public void onStart(Intent intent, int startId) {
            // Log.i(AnkiDroidApp.TAG, "OnStart");

            RemoteViews updateViews = buildUpdate(this);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidget.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }


        private RemoteViews buildUpdate(Context context) {
            // Log.i(AnkiDroidApp.TAG, "buildUpdate");

            // Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget);
            Deck currentDeck = AnkiDroidApp.deck();

            if (!AnkiDroidApp.isSdCardMounted()) {
                updateViews.setTextViewText(R.id.anki_droid_text, context.getText(R.string.sdcard_missing_message));
                return updateViews;
            }

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

            SpannableStringBuilder sb = new SpannableStringBuilder();

            int totalDue = 0;

            // Limit the number of decks shown
            int nbDecks = decks.size();
            for (int i = 0; i < nbDecks && i < MAX_DECK_NUM; i++) {
                DeckInformation deck = decks.get(i);
                sb.append(deck.getDeckText());
                sb.append('\n');

                totalDue += deck.mDueCards;
            }

            if (sb.length() > 1) {
                // Get rid of the trailing \n
                sb.subSequence(0, sb.length() - 1);
            }

            updateViews.setTextViewText(R.id.anki_droid_text, sb);

            SharedPreferences preferences = PrefSettings.getSharedPrefs(context);

            int minimumCardsDueForNotification = Integer.parseInt(preferences.getString(
                    "minimumCardsDueForNotification", "25"));

            if (totalDue >= minimumCardsDueForNotification) {
                // Raise a notification
                String ns = Context.NOTIFICATION_SERVICE;
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

                int icon = R.drawable.anki;
                CharSequence tickerText = String.format(
                        getString(R.string.widget_minimum_cards_due_notification_ticker_text), totalDue);
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
                String contentText = sb.toString();
                Intent notificationIntent = new Intent(this, AnkiDroidApp.class);
                PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

                notification.setLatestEventInfo(appContext, contentTitle, contentText, contentIntent);

                final int WIDGET_NOTIFY_ID = 1;
                mNotificationManager.notify(WIDGET_NOTIFY_ID, notification);
            }

            return updateViews;
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


        private ArrayList<DeckInformation> fetchDeckInformation() {
            // Log.i(AnkiDroidApp.TAG, "fetchDeckInformation");

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
                    // Log.i(AnkiDroidApp.TAG, "Could not open deck");
                    Log.e(AnkiDroidApp.TAG, e.toString());
                }
            }

            if (!information.isEmpty() && information.size() > 1) {
                // Sort and reverse the list if there are decks
                // Log.i(AnkiDroidApp.TAG, "Sorting deck");

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
            // Log.i(AnkiDroidApp.TAG, "onBind");
            return null;
        }
    }
}
