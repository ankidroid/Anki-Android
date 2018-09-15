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
import android.view.View;
import android.widget.RemoteViews;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.IntentHandler;
import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;

import timber.log.Timber;

public class AnkiDroidWidgetSmall extends AppWidgetProvider {

    private static BroadcastReceiver mMountReceiver = null;
    private static boolean remounted = false;


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Timber.d("SmallWidget: onUpdate");
        WidgetStatus.update(context);
    }



    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Timber.d("SmallWidget: Widget enabled");
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetSmallEnabled", true).commit();
    }


    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Timber.d("SmallWidget: Widget disabled");
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetSmallEnabled", false).commit();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().contentEquals("com.sec.android.widgetapp.APPWIDGET_RESIZE")) {
            CompatHelper.getCompat().updateWidgetDimensions(context, new RemoteViews(context.getPackageName(), R.layout.widget_small), AnkiDroidWidgetSmall.class);
        }
        super.onReceive(context, intent);
    }

    public static class UpdateService extends Service {

        /** The cached number of total due cards. */
        private int dueCardsCount;


        /** The cached estimated reviewing time. */
        private int eta;

        public void doUpdate(Context context) {
            AppWidgetManager.getInstance(context)
                    .updateAppWidget(new ComponentName(context, AnkiDroidWidgetSmall.class), buildUpdate(context, true));
        }

        @Override
        public void onStart(Intent intent, int startId) {
            Timber.i("SmallWidget: OnStart");

            RemoteViews updateViews = buildUpdate(this, true);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidgetSmall.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }


        private RemoteViews buildUpdate(Context context, boolean updateDueDecksNow) {
            Timber.d("buildUpdate");

            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_small);

            boolean mounted = AnkiDroidApp.isSdCardMounted();
            if (!mounted) {
                updateViews.setViewVisibility(R.id.widget_due, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.GONE);

                if (mMountReceiver == null) {
                    mMountReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            String action = intent.getAction();
                            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                                Timber.d("mMountReceiver - Action = Media Mounted");
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
                    // Compute the total number of cards due.
                    int[] counts = WidgetStatus.fetchSmall(context);
                    dueCardsCount = counts[0];
                    eta = counts[1];
                    if (dueCardsCount <= 0) {
                        if (dueCardsCount == 0) {
                            updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.VISIBLE);
                        } else {
                            updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.INVISIBLE);
                        }
                        updateViews.setViewVisibility(R.id.widget_due, View.INVISIBLE);
                    } else {
                        updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.INVISIBLE);
                        updateViews.setViewVisibility(R.id.widget_due, View.VISIBLE);
                        updateViews.setTextViewText(R.id.widget_due, Integer.toString(dueCardsCount));
                        updateViews.setContentDescription(R.id.widget_due, context.getResources().getQuantityString(R.plurals.widget_cards_due, dueCardsCount, dueCardsCount));
                    }
                    if (eta <= 0 || dueCardsCount <= 0) {
                        updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE);
                    } else {
                        updateViews.setViewVisibility(R.id.widget_eta, View.VISIBLE);
                        updateViews.setTextViewText(R.id.widget_eta, Integer.toString(eta));
                        updateViews.setContentDescription(R.id.widget_eta, context.getResources().getQuantityString(R.plurals.widget_eta, eta, eta));
                    }
                }
            }

            // Add a click listener to open Anki from the icon.
            // This should be always there, whether there are due cards or not.
            Intent ankiDroidIntent = new Intent(context, IntentHandler.class);
            ankiDroidIntent.setAction(Intent.ACTION_MAIN);
            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingAnkiDroidIntent = PendingIntent.getActivity(context, 0, ankiDroidIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            updateViews.setOnClickPendingIntent(R.id.ankidroid_widget_small_button, pendingAnkiDroidIntent);

            CompatHelper.getCompat().updateWidgetDimensions(context, updateViews, AnkiDroidWidgetSmall.class);

            return updateViews;
        }

        @Override
        public IBinder onBind(Intent arg0) {
            Timber.d("onBind");
            return null;
        }

    }

}
