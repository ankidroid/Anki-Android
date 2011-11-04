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

import com.ichi2.anki.WidgetStatus.WidgetDeckTaskData;
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
        Intent intent;
        intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);            	
        context.startService(intent);
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
            	if (intent.getAction() != null) {
                	Log.e("intentaction", "a " + intent.getAction());            		
            	}
                if (ACTION_OPENDECK.equals(intent.getAction())) {
                	WidgetStatus.deckOperation(WidgetStatus.TASK_OPEN_DECK, new WidgetDeckTaskData(this, "/emmc/AnkiDroid/Fra-2-Red.anki"));
                } else if (ACTION_CLOSEDECK.equals(intent.getAction())) {
                	Deck deck = WidgetStatus.getDeck();
                	if (deck != null) {
                    	WidgetStatus.deckOperation(WidgetStatus.TASK_CLOSE_DECK, new WidgetDeckTaskData(this, deck));                		
                	}
                } else if (ACTION_OPEN.equals(intent.getAction())) {
                	startActivity(StudyOptions.getLoadDeckIntent(this, intent.getData().getPath()));
                } else if (ACTION_ANSWER.equals(intent.getAction())) {
                	Deck deck = WidgetStatus.getDeck();
                	Card card = WidgetStatus.getCard();
                	WidgetStatus.deckOperation(WidgetStatus.TASK_ANSWER_CARD, new WidgetDeckTaskData(this, deck, card, intent.getIntExtra(EXTRA_ANSWER_EASE, 0)));
                } else if (ACTION_UPDATE.equals(intent.getAction())) {
                	
                }
            }
            RemoteViews updateViews = buildUpdate(this, updateDueDecksNow);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidgetBig.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }

        private RemoteViews buildUpdate(Context context, boolean updateDueDecksNow) {
            Log.i(AnkiDroidApp.TAG, "BigWidget: buildUpdate");

            // Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_big);

            Deck deck = WidgetStatus.getDeck();
        	if (deck != null) {
        		updateViews.setTextViewText(R.id.widget_big_deckname, deck.getDeckName());
        		PendingIntent closeDeckPendingIntent = getCloseDeckPendingIntent(context);
                updateViews.setOnClickPendingIntent(R.id.widget_big_open, closeDeckPendingIntent);
                updateViews.setTextViewText(R.id.widget_big_question, WidgetStatus.getQuestion());
                updateViews.setTextViewText(R.id.widget_big_answer, WidgetStatus.getAnswer());
                // answer buttons
        		PendingIntent ease1PendindIntent = getAnswerPendingIntent(context, 1);
                updateViews.setOnClickPendingIntent(R.id.widget_big_ease1, ease1PendindIntent);
        		PendingIntent ease2PendindIntent = getAnswerPendingIntent(context, 2);
                updateViews.setOnClickPendingIntent(R.id.widget_big_ease2, ease2PendindIntent);
        		PendingIntent ease3PendindIntent = getAnswerPendingIntent(context, 3);
                updateViews.setOnClickPendingIntent(R.id.widget_big_ease3, ease3PendindIntent);
        		PendingIntent ease4PendindIntent = getAnswerPendingIntent(context, 4);
                updateViews.setOnClickPendingIntent(R.id.widget_big_ease4, ease4PendindIntent);
        	} else {
        		PendingIntent openPendingIntent = getOpenDeckPendingIntent(context);
                updateViews.setOnClickPendingIntent(R.id.widget_big_open, openPendingIntent);
        	}
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
