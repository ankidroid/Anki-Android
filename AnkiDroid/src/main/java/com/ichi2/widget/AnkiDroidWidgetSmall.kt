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

package com.ichi2.widget

import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.res.Configuration
import android.os.IBinder
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.annotations.KotlinCleanup
import timber.log.Timber
import kotlin.math.sqrt

class AnkiDroidWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Timber.d("SmallWidget: onUpdate")
        WidgetStatus.updateInBackground(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Timber.d("SmallWidget: Widget enabled")
        val preferences = context.sharedPrefs()
        preferences.edit(commit = true) { putBoolean("widgetSmallEnabled", true) }
        UsageAnalytics.sendAnalyticsEvent(this.javaClass.simpleName, "enabled")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Timber.d("SmallWidget: Widget disabled")
        val preferences = context.sharedPrefs()
        preferences.edit(commit = true) { putBoolean("widgetSmallEnabled", false) }
        UsageAnalytics.sendAnalyticsEvent(this.javaClass.simpleName, "disabled")
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.contentEquals("com.sec.android.widgetapp.APPWIDGET_RESIZE")) {
            updateWidgetDimensions(context, RemoteViews(context.packageName, R.layout.widget_small), AnkiDroidWidgetSmall::class.java)
        }
        super.onReceive(context, intent)
    }

    class UpdateService : Service() {
        /** The cached number of total due cards.  */
        private var mDueCardsCount = 0
        fun doUpdate(context: Context) {
            AppWidgetManager.getInstance(context)
                .updateAppWidget(ComponentName(context, AnkiDroidWidgetSmall::class.java), buildUpdate(context, true))
        }

        override fun onStart(intent: Intent, startId: Int) {
            Timber.i("SmallWidget: OnStart")
            val updateViews = buildUpdate(this, true)
            val thisWidget = ComponentName(this, AnkiDroidWidgetSmall::class.java)
            val manager = AppWidgetManager.getInstance(this)
            manager.updateAppWidget(thisWidget, updateViews)
        }

        @KotlinCleanup("Fix param updateDueDecksNow always true")
        @Suppress("SameParameterValue")
        private fun buildUpdate(context: Context, updateDueDecksNow: Boolean): RemoteViews {
            Timber.d("buildUpdate")
            val updateViews = RemoteViews(context.packageName, R.layout.widget_small)
            val mounted = AnkiDroidApp.isSdCardMounted
            if (!mounted) {
                updateViews.setViewVisibility(R.id.widget_due, View.INVISIBLE)
                updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE)
                updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.GONE)
                if (mMountReceiver == null) {
                    mMountReceiver = object : BroadcastReceiver() {
                        @KotlinCleanup("Change parameter context name below, should not be used")
                        override fun onReceive(context: Context, intent: Intent) {
                            // baseContext() is null, applicationContext() throws a NPE,
                            // context may not have the locale override from AnkiDroidApp
                            val action = intent.action
                            if (action != null && action == Intent.ACTION_MEDIA_MOUNTED) {
                                Timber.d("mMountReceiver - Action = Media Mounted")
                                if (remounted) {
                                    WidgetStatus.updateInBackground(AnkiDroidApp.instance)
                                    remounted = false
                                    if (mMountReceiver != null) {
                                        AnkiDroidApp.instance.unregisterReceiver(mMountReceiver)
                                    }
                                } else {
                                    remounted = true
                                }
                            }
                        }
                    }
                    val iFilter = IntentFilter()
                    iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED)
                    iFilter.addDataScheme("file")
                    AnkiDroidApp.instance.registerReceiver(mMountReceiver, iFilter)
                }
            } else {
                // If we do not have a cached version, always update.
                if (mDueCardsCount == 0 || updateDueDecksNow) {
                    // Compute the total number of cards due.
                    val counts = WidgetStatus.fetchSmall(context)
                    mDueCardsCount = counts[0]
                    /* The cached estimated reviewing time. */
                    val eta = counts[1]
                    if (mDueCardsCount <= 0) {
                        if (mDueCardsCount == 0) {
                            updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.VISIBLE)
                        } else {
                            updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.INVISIBLE)
                        }
                        updateViews.setViewVisibility(R.id.widget_due, View.INVISIBLE)
                    } else {
                        updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.INVISIBLE)
                        updateViews.setViewVisibility(R.id.widget_due, View.VISIBLE)
                        updateViews.setTextViewText(R.id.widget_due, mDueCardsCount.toString())
                        updateViews.setContentDescription(R.id.widget_due, context.resources.getQuantityString(R.plurals.widget_cards_due, mDueCardsCount, mDueCardsCount))
                    }
                    if (eta <= 0 || mDueCardsCount <= 0) {
                        updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE)
                    } else {
                        updateViews.setViewVisibility(R.id.widget_eta, View.VISIBLE)
                        updateViews.setTextViewText(R.id.widget_eta, eta.toString())
                        updateViews.setContentDescription(R.id.widget_eta, context.resources.getQuantityString(R.plurals.widget_eta, eta, eta))
                    }
                }
            }

            // Add a click listener to open Anki from the icon.
            // This should be always there, whether there are due cards or not.
            val ankiDroidIntent = Intent(context, IntentHandler::class.java)
            ankiDroidIntent.action = Intent.ACTION_MAIN
            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val pendingAnkiDroidIntent = PendingIntentCompat.getActivity(
                context,
                0,
                ankiDroidIntent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false
            )
            updateViews.setOnClickPendingIntent(R.id.ankidroid_widget_small_button, pendingAnkiDroidIntent)
            updateWidgetDimensions(context, updateViews, AnkiDroidWidgetSmall::class.java)
            return updateViews
        }

        override fun onBind(arg0: Intent): IBinder? {
            Timber.d("onBind")
            return null
        }
    }

    companion object {
        private var mMountReceiver: BroadcastReceiver? = null
        private var remounted = false
        private fun updateWidgetDimensions(context: Context, updateViews: RemoteViews, cls: Class<*>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            for (id in ids) {
                val scale = context.resources.displayMetrics.density
                val options = manager.getAppWidgetOptions(id)
                var width: Float
                var height: Float
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
                    height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
                } else {
                    width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
                    height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
                }
                var horizontal: Int
                var vertical: Int
                var text: Float
                if (width / height > 0.8) {
                    horizontal = (((width - height * 0.8) / 2 + 4) * scale + 0.5f).toInt()
                    vertical = (4 * scale + 0.5f).toInt()
                    text = (sqrt(height * 0.8 / width) * 18).toFloat()
                } else {
                    vertical = (((height - width * 1.25) / 2 + 4) * scale + 0.5f).toInt()
                    horizontal = (4 * scale + 0.5f).toInt()
                    text = (sqrt(width * 1.25 / height) * 18).toFloat()
                }
                updateViews.setTextViewTextSize(R.id.widget_due, TypedValue.COMPLEX_UNIT_SP, text)
                updateViews.setTextViewTextSize(R.id.widget_eta, TypedValue.COMPLEX_UNIT_SP, text)
                updateViews.setViewPadding(R.id.ankidroid_widget_text_layout, horizontal, vertical, horizontal, vertical)
            }
        }
    }
}
