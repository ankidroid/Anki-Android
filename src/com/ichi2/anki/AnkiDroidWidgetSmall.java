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

import android.app.Notification;
import android.app.NotificationManager;
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
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class AnkiDroidWidgetSmall extends AppWidgetProvider {
	
    private static BroadcastReceiver mMountReceiver = null;
    private static boolean remounted = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(AnkiDroidApp.TAG, "SmallWidget: onUpdate");
        WidgetStatus.update(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.i(AnkiDroidApp.TAG, "SmallWidget: Widget enabled");
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetSmallEnabled", true).commit();
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.i(AnkiDroidApp.TAG, "SmallWidget: Widget disabled");
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetSmallEnabled", false).commit();
    }

    public static class UpdateService extends Service {

        /**
         * Update the state of the widget.
         */
        public static final String ACTION_UPDATE = "org.ichi2.anki.AnkiDroidWidgetSmall.UPDATE";

        /** The cached number of total due cards. */
        private int dueCardsCount;

        /** The cached amount of today's yes reps. */
        private int reps;

        /** The cached estimated reviewing time. */
        private int eta;

        /** The id of the notification for due cards. */
        private static final int WIDGET_NOTIFY_ID = 1;

        /** The notification service to show notifications of due cards. */
        private NotificationManager mNotificationManager;

        @Override
        public void onCreate() {
            super.onCreate();
            mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }


        @Override
        public void onStart(Intent intent, int startId) {
            Log.i(AnkiDroidApp.TAG, "SmallWidget: OnStart");

            RemoteViews updateViews = buildUpdate(this, true);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidgetSmall.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }


        private RemoteViews buildUpdate(Context context, boolean updateDueDecksNow) {
            Log.i(AnkiDroidApp.TAG, "buildUpdate");

            // Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_small);

            // Add a click listener to open Anki from the icon.
            // This should be always there, whether there are due cards or not.
            Intent ankiDroidIntent = new Intent(context, StudyOptions.class);
            ankiDroidIntent.setAction(Intent.ACTION_MAIN);
            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingAnkiDroidIntent = PendingIntent.getActivity(context, 0, ankiDroidIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            updateViews.setOnClickPendingIntent(R.id.ankidroid_widget_small_layout, pendingAnkiDroidIntent);

            boolean mounted = AnkiDroidApp.isSdCardMounted();
            if (!mounted) {
                updateViews.setViewVisibility(R.id.widget_due, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.widget_progress_frame, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.GONE);

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
            } else {
                // If we do not have a cached version, always update.
                if (dueCardsCount == 0 || updateDueDecksNow) {
                    // Build a list of decks with due cards.
                    // Also compute the total number of cards due.
                	int[] counts = WidgetStatus.fetchSmall(context);
                	
                	dueCardsCount = counts[0];
                	reps = counts[1];
                	eta = counts[2];
                	int totalreps = reps + dueCardsCount;
                	int progress = 0;
                	if (totalreps != 0) {
                		progress = (int) Math.round((100.0d * reps) / totalreps);
                	}
        			if (dueCardsCount <= 0) {
        				if (dueCardsCount == 0) {
    		                updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.VISIBLE);        					
        				} else {
    		                updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.INVISIBLE);        					
        				}
		                updateViews.setViewVisibility(R.id.widget_due, View.INVISIBLE);
		                updateViews.setViewVisibility(R.id.widget_progress_frame, View.INVISIBLE);
        			} else {
		                updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.INVISIBLE);
		                updateViews.setViewVisibility(R.id.widget_due, View.VISIBLE);
		                updateViews.setViewVisibility(R.id.widget_progress_frame, View.VISIBLE);
	                    updateViews.setTextViewText(R.id.widget_due, Integer.toString(dueCardsCount));
	                    updateViews.setProgressBar(R.id.widget_progress, 100, progress, false);
					}
        			if (eta <= 0 || dueCardsCount <= 0) {
		                updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE);        				
        			} else {
		                updateViews.setViewVisibility(R.id.widget_eta, View.VISIBLE);        				
	                    updateViews.setTextViewText(R.id.widget_eta, Integer.toString(eta));
        			}
                }
            }

            SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
            int minimumCardsDueForNotification = Integer.parseInt(preferences.getString(
                    "minimumCardsDueForNotification", "25"));

            if (mounted && dueCardsCount >= minimumCardsDueForNotification) {
                // Show a notification
                int icon = R.drawable.anki;
                CharSequence tickerText = String.format(
                        getString(R.string.widget_minimum_cards_due_notification_ticker_text),
                        dueCardsCount);
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

                mNotificationManager.notify(WIDGET_NOTIFY_ID, notification);
            } else {
                // Cancel the existing notification, if any.
                mNotificationManager.cancel(WIDGET_NOTIFY_ID);
            }            	

            return updateViews;
        }

        @Override
        public IBinder onBind(Intent arg0) {
            Log.i(AnkiDroidApp.TAG, "onBind");
            return null;
        }
    }
}
