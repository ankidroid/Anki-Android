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
import com.ichi2.libanki.Collection;
import com.ichi2.utils.Permissions;

import com.ichi2.utils.JSONObject;

import java.util.Calendar;

public class BootService extends BroadcastReceiver {

    /**
     * This service is also run when the app is started (from {@link com.ichi2.anki.AnkiDroidApp},
     * so we need to make sure that it isn't run twice.
     */
    private static boolean sWasRun = false;


    @Override
    public void onReceive(Context context, Intent intent) {
        if (sWasRun) {
            return;
        }
        if (!Permissions.hasStorageAccessPermission(context)) {
            return;
        }
        // There are cases where the app is installed, and we have access, but nothing exist yet
        if (CollectionHelper.getInstance().getCol(context) == null
                || CollectionHelper.getInstance().getCol(context).getDecks() == null) {
            return;
        }

        scheduleDeckReminder(context);
        scheduleNotification(context);
        sWasRun = true;
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
