package com.ichi2.anki.services;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowNotificationManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.services.ReminderService.EXTRA_DECK_ID;
import static com.ichi2.anki.services.ReminderService.EXTRA_DECK_OPTION_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
public class ReminderServiceTest extends RobolectricTest {

    @Test
    public void testReminderServiceNothingDue() {
        buildDefaultDeckReminders();
        assertThat("No notifications exist", getNotificationManagerShadow().size() == 0);
    }


    @Test
    public void testReminderServiceReviewsDue() {
        addNoteUsingBasicModel("test front", "test back");
        assertThat("No notifications exist", getNotificationManagerShadow().size() == 0);
        buildDefaultDeckReminders();
        assertThat("No notifications exist", getNotificationManagerShadow().size() == 1);
    }


    @Test
    public void testReminderServiceNullCollection() {
        addNoteUsingBasicModel("test front", "test back");
        enableNullCollection();
        assertThat("No notifications exist", getNotificationManagerShadow().size() == 0);
        buildDefaultDeckReminders();
        // The collection was null so no reminders, but we should get here without exception
        assertThat("No notifications exist", getNotificationManagerShadow().size() == 0);
    }


    private ShadowNotificationManager getNotificationManagerShadow() {
        return shadowOf((NotificationManager) getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE));
    }


    private void buildDefaultDeckReminders() {
        Intent defaultDeckIntent = new Intent();
        defaultDeckIntent.putExtra(EXTRA_DECK_ID, (long) 1);
        defaultDeckIntent.putExtra(EXTRA_DECK_OPTION_ID, (long) 1);
        ReminderService reminders = new ReminderService();
        reminders.onReceive(getTargetContext(), defaultDeckIntent);
    }
}
