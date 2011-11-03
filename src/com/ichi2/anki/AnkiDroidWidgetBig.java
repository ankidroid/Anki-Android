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

import com.tomgibara.android.veecheck.util.PrefSettings;

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

public class AnkiDroidWidgetBig extends AppWidgetProvider {

    private static BroadcastReceiver mMountReceiver = null;
    private static boolean remounted = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(AnkiDroidApp.TAG, "BigWidget: onUpdate");
//        WidgetStatus.update(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.i(AnkiDroidApp.TAG, "BigWidget: Widget enabled");
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetBigEnabled", true).commit();
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.i(AnkiDroidApp.TAG, "BigWidget: Widget disabled");
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetBigEnabled", false).commit();
    }

    public static class UpdateService extends Service {
    	public static final String ACTION_OPENDECK = "org.ichi2.anki.AnkiDroidWidgetBig.OPENDECK";
        public static final String ACTION_CLOSEDECK = "org.ichi2.anki.AnkiDroidWidgetBig.CLOSEDECK";
        public static final String ACTION_ANSWER = "org.ichi2.anki.AnkiDroidWidgetBig.ANSWER";
        public static final String ACTION_OPEN = "org.ichi2.anki.AnkiDroidWidgetBig.OPEN";
        public static final String ACTION_UPDATE = "org.ichi2.anki.AnkiDroidWidgetBig.UPDATE";
        public static final String EXTRA_ANSWER_EASE = "answerEase";


        private CharSequence getDeckStatusString(DeckStatus deck) {
            SpannableStringBuilder sb = new SpannableStringBuilder();

            SpannableString red = new SpannableString(Integer.toString(deck.mFailedCards));
            red.setSpan(new ForegroundColorSpan(Color.RED), 0, red.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            SpannableString black = new SpannableString(Integer.toString(deck.mDueCards));
            black.setSpan(new ForegroundColorSpan(Color.BLACK), 0, black.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            SpannableString blue = new SpannableString(Integer.toString(deck.mNewCards));
            blue.setSpan(new ForegroundColorSpan(Color.BLUE), 0, blue.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            sb.append(red);
            sb.append(" ");
            sb.append(black);
            sb.append(" ");
            sb.append(blue);

            return sb;
        }


        @Override
        public void onStart(Intent intent, int startId) {
            Log.i(AnkiDroidApp.TAG, "BigWidget: OnStart");

            boolean updateDueDecksNow = true;
            if (intent != null) {
                if (ACTION_OPENDECK.equals(intent.getAction())) {
                	WidgetStatus.deckOperation(this, 0);
                } else if (ACTION_CLOSEDECK.equals(intent.getAction())) {
                } else if (ACTION_OPEN.equals(intent.getAction())) {
                	startActivity(StudyOptions.getLoadDeckIntent(this, intent.getData().getPath()));
                } else if (ACTION_ANSWER.equals(intent.getAction())) {
                } else if (ACTION_UPDATE.equals(intent.getAction())) {

                }
            }
            RemoteViews updateViews = buildUpdate(this, updateDueDecksNow);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidgetMedium.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }

        private RemoteViews buildUpdate(Context context, boolean updateDueDecksNow) {
            Log.i(AnkiDroidApp.TAG, "BigWidget: buildUpdate");

            // Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_big);

            // Add a click listener to open Anki from the icon.
            // This should be always there, whether there are due cards or not.
            Intent ankiDroidIntent = new Intent(context, StudyOptions.class);
            ankiDroidIntent.setAction(Intent.ACTION_MAIN);
            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingAnkiDroidIntent = PendingIntent.getActivity(context, 0, ankiDroidIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            updateViews.setOnClickPendingIntent(R.id.ankidroid_widget_big_layout, pendingAnkiDroidIntent);

//            
//            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_big);
//            PendingIntent openPendingIntent = getOpenDeckPendingIntent(context);
//            updateViews.setOnClickPendingIntent(R.id.ankidroid_widget_big_layout, openPendingIntent);

            
//
//            // Add a click listener to open Anki from the icon.
//            // This should be always there, whether there are due cards or not.
//            Intent ankiDroidIntent = new Intent(context, StudyOptions.class);
//            ankiDroidIntent.setAction(Intent.ACTION_MAIN);
//            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER);
//            PendingIntent pendingAnkiDroidIntent =
//                PendingIntent.getActivity(context, 0, ankiDroidIntent, 0);
//            updateViews.setOnClickPendingIntent(R.id.anki_droid_logo,
//                    pendingAnkiDroidIntent);
//
//            if (!AnkiDroidApp.isSdCardMounted()) {
//                updateViews.setTextViewText(R.id.anki_droid_title,
//                    context.getText(R.string.sdcard_missing_message));
//                updateViews.setTextViewText(R.id.anki_droid_name, "");
//                updateViews.setTextViewText(R.id.anki_droid_status, "");
//                if (mMountReceiver == null) {
//                	mMountReceiver = new BroadcastReceiver() {
//                        @Override
//                        public void onReceive(Context context, Intent intent) {
//                            String action = intent.getAction();
//                        	if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
//                                Log.i(AnkiDroidApp.TAG, "mMountReceiver - Action = Media Mounted");
//                                if (remounted) {
//                                    WidgetStatus.update(getBaseContext());                                	
//                                	remounted = false;
//                                    if (mMountReceiver != null) {
//                                        unregisterReceiver(mMountReceiver);
//                                    }
//                                } else {
//                                	remounted = true;
//                                }
//                            }
//                        }
//                    };
//                    IntentFilter iFilter = new IntentFilter();
//                    iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
//                    iFilter.addDataScheme("file");
//                    registerReceiver(mMountReceiver, iFilter);
//                }
//                return updateViews;
//            }
//
//            // If we do not have a cached version, always update.
//            if (dueDecks == null || updateDueDecksNow) {
//                // Build a list of decks with due cards.
//                // Also compute the total number of cards due.
//                updateDueDecks();
//            }
//
//            if (dueCardsCount > 0) {
//                Resources resources = getResources();
//                String decksText = resources.getQuantityString(
//                        R.plurals.widget_decks, dueDecks.size(), dueDecks.size());
//                String text = resources.getQuantityString(
//                        R.plurals.widget_cards_in_decks_due, dueCardsCount, dueCardsCount, decksText);
//                updateViews.setTextViewText(R.id.anki_droid_title, text);
//                // If the current due deck is out of bound, go back to the first one.
//                if (currentDueDeck < 0 || currentDueDeck > dueDecks.size() - 1) {
//                    currentDueDeck = 0;
//                }
//                // Show the name and info from the current due deck.
//                DeckStatus deckStatus = dueDecks.get(currentDueDeck);
//                updateViews.setTextViewText(R.id.anki_droid_name, deckStatus.mDeckName);
//                updateViews.setTextViewText(R.id.anki_droid_status,
//                    getDeckStatusString(deckStatus));
//
//                // Enable or disable the prev and next buttons.
////                if (currentDueDeck > 0) {
////                    updateViews.setImageViewResource(R.id.anki_droid_prev, R.drawable.widget_left_arrow);
////                    updateViews.setOnClickPendingIntent(R.id.anki_droid_prev, getPrevPendingIntent(context));
////                } else {
////                    updateViews.setImageViewResource(R.id.anki_droid_prev, R.drawable.widget_left_arrow_disabled);
////                    updateViews.setOnClickPendingIntent(R.id.anki_droid_prev, getIgnoredPendingIntent(context));
////                }
////                if (currentDueDeck < dueDecks.size() - 1) {
////                    updateViews.setImageViewResource(R.id.anki_droid_next, R.drawable.widget_right_arrow);
////                    updateViews.setOnClickPendingIntent(R.id.anki_droid_next, getNextPendingIntent(context));
////                } else {
////                    updateViews.setImageViewResource(R.id.anki_droid_next, R.drawable.widget_right_arrow_disabled);
////                    updateViews.setOnClickPendingIntent(R.id.anki_droid_next, getIgnoredPendingIntent(context));
////                }
//                updateViews.setViewVisibility(R.id.anki_droid_name, View.VISIBLE);
//                updateViews.setViewVisibility(R.id.anki_droid_status, View.VISIBLE);
//                updateViews.setViewVisibility(R.id.anki_droid_next, View.VISIBLE);
//                updateViews.setViewVisibility(R.id.anki_droid_prev, View.VISIBLE);
//            } else {
//                // No card is currently due.
//                updateViews.setTextViewText(R.id.anki_droid_title,
//                    context.getString(R.string.widget_no_cards_due));
//                updateViews.setTextViewText(R.id.anki_droid_name, "");
//                updateViews.setTextViewText(R.id.anki_droid_status, "");
//                updateViews.setViewVisibility(R.id.anki_droid_name, View.INVISIBLE);
//                updateViews.setViewVisibility(R.id.anki_droid_status, View.INVISIBLE);
//                updateViews.setViewVisibility(R.id.anki_droid_next, View.INVISIBLE);
//                updateViews.setViewVisibility(R.id.anki_droid_prev, View.INVISIBLE);
//            }

            return updateViews;
        }


        private PendingIntent getAnswerPendingIntent(Context context, int ease) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_ANSWER);
            ankiDroidIntent.putExtra(EXTRA_ANSWER_EASE, ease);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getOpenDeckPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_OPENDECK);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getCloseDeckPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_CLOSEDECK);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getOpenPendingIntent(Context context, String deckPath) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_OPEN);
            //ankiDroidIntent.setData(Uri.parse("file://" + deckPath));
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        @Override
        public IBinder onBind(Intent arg0) {
            Log.i(AnkiDroidApp.TAG, "onBind");
            return null;
        }
    }
}
