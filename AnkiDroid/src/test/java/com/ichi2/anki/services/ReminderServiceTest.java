/*
 *  Copyright (c) 2021 Mike Hardy <github@mikehardy.net>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.services;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowNotificationManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.services.ReminderService.EXTRA_DECK_ID;
import static com.ichi2.anki.services.ReminderService.EXTRA_DECK_OPTION_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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


    /**
     * #8264: Crash on sync - getSched().getDueTree() failed
     */
    @Test
    public void testDatabaseFailureWhileSyncingDoesNotCrash() {
        // If getCol() fails, it triggers different exception handling in the service.
        // The cause was getSched().deckDueTree()
        Collection baseCol = getCol();
        Collection mockCol = spy(baseCol);
        when(mockCol.getSched()).thenThrow(new IllegalStateException("Unit test: simulating database exception"));

        CollectionHelper.getInstance().setColForTests(mockCol);

        buildDefaultDeckReminders();

        // We retry after a database timeout so getSched is called twice
        //noinspection ResultOfMethodCallIgnored
        verify(mockCol, times(2)).getSched();
    }


    private ShadowNotificationManager getNotificationManagerShadow() {
        return shadowOf((NotificationManager) getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE));
    }


    private void buildDefaultDeckReminders() {
        Intent defaultDeckIntent = new Intent();
        defaultDeckIntent.putExtra(EXTRA_DECK_ID, 1L);
        defaultDeckIntent.putExtra(EXTRA_DECK_OPTION_ID, 1L);
        ReminderService reminders = new ReminderService();
        reminders.onReceive(getTargetContext(), defaultDeckIntent);
    }
}
