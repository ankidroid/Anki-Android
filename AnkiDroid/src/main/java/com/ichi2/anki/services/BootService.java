package com.ichi2.anki.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.Preferences;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.libanki.Collection;
import com.ichi2.utils.Permissions;

import com.ichi2.utils.JSONObject;

import java.util.Calendar;

import androidx.annotation.NonNull;
import timber.log.Timber;

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
        catchAlarmManagerErrors(context, () -> scheduleNotification(context));
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
            error = R.string.boot_service_too_many_notifications;
        } catch (Exception e) {
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
        for (JSONObject deck : CollectionHelper.getInstance().getCol(context).getDecks().all()) {
            Collection col = CollectionHelper.getInstance().getCol(context);
            if (col.getDecks().isDyn(deck.getLong("id"))) {
                continue;
            }
            final long deckConfigurationId = deck.getLong("conf");
            final JSONObject deckConfiguration = col.getDecks().getConf(deckConfigurationId);

            if (deckConfiguration.has("reminder")) {
                final JSONObject reminder = deckConfiguration.getJSONObject("reminder");

                if (reminder.getBoolean("enabled")) {
                    final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                                                                                    context,
                                                                                    (int) deck.getLong("id"),
                                                                                    new Intent(context, ReminderService.class).putExtra(ReminderService.EXTRA_DECK_ID,
                                                                                                                                        deck.getLong("id")),
                                                                                    0
                                                                                    );
                    final Calendar calendar = Calendar.getInstance();

                    calendar.set(Calendar.HOUR_OF_DAY, reminder.getJSONArray("time").getInt(0));
                    calendar.set(Calendar.MINUTE, reminder.getJSONArray("time").getInt(1));
                    calendar.set(Calendar.SECOND, 0);

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

    public static void scheduleNotification(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        // Don't schedule a notification if the due reminders setting is not enabled
        if (Integer.parseInt(sp.getString("minimumCardsDueForNotification", Integer.toString(Preferences.PENDING_NOTIFICATIONS_ONLY))) >= Preferences.PENDING_NOTIFICATIONS_ONLY) {
            return;
        }

        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, sp.getInt("dayOffset", 0));
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        final PendingIntent notificationIntent =
                PendingIntent.getBroadcast(context, 0, new Intent(context, NotificationService.class), 0);
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                notificationIntent
        );
    }
}
