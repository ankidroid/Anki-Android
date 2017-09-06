package com.ichi2.anki.services;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.receiver.NotificationReceiver;
import com.ichi2.anki.receiver.ReminderReceiver;
import com.ichi2.libanki.Collection;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public class BootService extends IntentService {

    /**
     * This service is also run when the app is started (from {@link com.ichi2.anki.AnkiDroidApp},
     * so we need to make sure that it isn't run twice.
     */
    private static boolean sWasRun = false;
    private AlarmManager mAlarmManager;

    public BootService() {
        super("BootService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (sWasRun)
            return;

        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        scheduleDeckReminder();
        scheduleNotification();
        sWasRun = true;
    }

    private void scheduleDeckReminder() {
        try {
            for (JSONObject deck : CollectionHelper.getInstance().getCol(this).getDecks().all()) {
                Collection col = CollectionHelper.getInstance().getCol(this);
                if (col.getDecks().isDyn(deck.getLong("id"))) {
                    continue;
                }
                final long deckConfigurationId = deck.getLong("conf");
                final JSONObject deckConfiguration = col.getDecks().getConf(deckConfigurationId);

                if (deckConfiguration.has("reminder")) {
                    final JSONObject reminder = deckConfiguration.getJSONObject("reminder");

                    if (reminder.getBoolean("enabled")) {
                        final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                                this,
                                (int) deck.getLong("id"),
                                new Intent(this, ReminderReceiver.class).putExtra(ReminderService.EXTRA_DECK_ID,
                                        deck.getLong("id")),
                                0
                        );
                        final Calendar calendar = Calendar.getInstance();

                        calendar.set(Calendar.HOUR_OF_DAY, reminder.getJSONArray("time").getInt(0));
                        calendar.set(Calendar.MINUTE, reminder.getJSONArray("time").getInt(1));
                        calendar.set(Calendar.SECOND, 0);

                        mAlarmManager.setInexactRepeating(
                                AlarmManager.RTC_WAKEUP,
                                calendar.getTimeInMillis(),
                                AlarmManager.INTERVAL_DAY,
                                reminderIntent
                        );
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void scheduleNotification() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (Integer.parseInt(sp.getString("minimumCardsDueForNotification", "1000001")) == 1000001)
            return;

        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, sp.getInt("dayOffset", 0));
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        final PendingIntent notificationIntent =
                PendingIntent.getBroadcast(this, 0, new Intent(this, NotificationReceiver.class), 0);
        mAlarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                notificationIntent
        );
    }
}
