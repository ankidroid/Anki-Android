//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.Preferences;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.utils.Time;
import com.ichi2.utils.Permissions;

import com.ichi2.utils.JSONObject;

import java.util.Calendar;

import androidx.annotation.NonNull;
import timber.log.Timber;

import static com.ichi2.anki.DeckOptions.reminderToCalendar;
import static com.ichi2.anki.Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION;

public class BootService extends BroadcastReceiver {

    /**
     * This service is also run when the app is started (from {@link com.ichi2.anki.AnkiDroidApp},
     * so we need to make sure that it isn't run twice.
     */
    private static boolean sWasRun = false;

    private boolean mFailedToShowNotifications = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (sWasRun) {
            Timber.d("BootService - Already run");
            return;
        }
        if (!Permissions.hasStorageAccessPermission(context)) {
            Timber.w("Boot Service did not execute - no permissions");
            return;
        }
        // There are cases where the app is installed, and we have access, but nothing exist yet
        Collection col = getColSafe(context);
        if (col == null || col.getDecks() == null) {
            Timber.w("Boot Service did not execute - error loading collection");
            return;
        }

        Timber.i("Executing Boot Service");
        catchAlarmManagerErrors(context, () -> scheduleDeckReminder(context));
        catchAlarmManagerErrors(context, () -> scheduleNotification(col.getTime(), context));
        mFailedToShowNotifications = false;
        sWasRun = true;
    }

    private void catchAlarmManagerErrors(@NonNull Context context, @NonNull Runnable runnable) {
        //#6332 - Too Many Alarms on Samsung Devices - this stops a fatal startup crash.
        //We warn the user if they breach this limit
        Integer error = null;
        try {
            runnable.run();
        } catch (SecurityException ex) {
            Timber.w(ex);
            error = R.string.boot_service_too_many_notifications;
        } catch (Exception e) {
            Timber.w(e);
            error = R.string.boot_service_failed_to_schedule_notifications;
        }
        if (error != null) {
            if (!mFailedToShowNotifications) {
                UIUtils.showThemedToast(context, context.getString(error), false);
            }
            mFailedToShowNotifications = true;
        }
    }


    private Collection getColSafe(Context context) {
        //#6239 - previously would crash if ejecting, we don't want a report if this happens so don't use
        //getInstance().getColSafe
        try {
            return CollectionHelper.getInstance().getCol(context);
        } catch (Exception e) {
            Timber.e(e, "Failed to get collection for boot service - possibly media ejecting");
            return null;
        }
    }


    private void scheduleDeckReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (DeckConfig deckConfiguration : CollectionHelper.getInstance().getCol(context).getDecks().allConf()) {
            Collection col = CollectionHelper.getInstance().getCol(context);
            if (deckConfiguration.has("reminder")) {
                final JSONObject reminder = deckConfiguration.getJSONObject("reminder");

                if (reminder.getBoolean("enabled")) {
                    final PendingIntent reminderIntent = CompatHelper.getCompat().getImmutableBroadcastIntent(
                                                                                    context,
                                                                                    (int) deckConfiguration.getLong("id"),
                                                                                    new Intent(context, ReminderService.class).putExtra(ReminderService.EXTRA_DECK_OPTION_ID,
                                                                                            deckConfiguration.getLong("id")),
                                                                                    0
                                                                                    );
                    final Calendar calendar = reminderToCalendar(col.getTime(), reminder);

                    alarmManager.setRepeating(
                                              AlarmManager.RTC_WAKEUP,
                                              calendar.getTimeInMillis(),
                                              AlarmManager.INTERVAL_DAY,
                                              reminderIntent
                                              );
                }
            }
        }
    }

    public static void scheduleNotification(Time time, Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences sp = AnkiDroidApp.getSharedPrefs(context);
        // Don't schedule a notification if the due reminders setting is not enabled
        if (Integer.parseInt(sp.getString(MINIMUM_CARDS_DUE_FOR_NOTIFICATION, Integer.toString(Preferences.PENDING_NOTIFICATIONS_ONLY))) >= Preferences.PENDING_NOTIFICATIONS_ONLY) {
            return;
        }

        final Calendar calendar = time.calendar();
        calendar.set(Calendar.HOUR_OF_DAY, getRolloverHourOfDay(context));
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        final PendingIntent notificationIntent =
                CompatHelper.getCompat().getImmutableBroadcastIntent(context, 0, new Intent(context, NotificationService.class), 0);
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                notificationIntent
        );
    }

    /** Returns the hour of day when rollover to the next day occurs */
    protected static int getRolloverHourOfDay(Context context) {
        // TODO; We might want to use the BootService retry code here when called from preferences.

        int defValue = 4;

        try {
            Collection col = CollectionHelper.getInstance().getCol(context);
            switch (col.schedVer()) {
                default:
                case 1:
                    SharedPreferences sp = AnkiDroidApp.getSharedPrefs(context);
                    return sp.getInt("dayOffset", defValue);
                case 2:
                    return col.get_config("rollover", defValue);
            }
        } catch (Exception e) {
            Timber.w(e);
            return defValue;
        }
    }
}
