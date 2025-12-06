/*
 *  Copyright (c) 2025 Raiyyan <f20241312@pilani.bits-pilani.ac.in>
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

package com.ichi2.anki.services

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Tests for [StoragePermissionsValidator]
 *
 * These tests verify the fix for issue #19553 where ManageSpaceActivity crashes
 * when getExternalFilesDir() returns null on devices with Android/data corruption.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class StoragePermissionsValidatorTest {
    /**
     * Test the normal case: when storage is valid, validation should pass
     *
     * This is the regression test ensuring the fix doesn't break normal operation.
     */
    @Test
    fun `verifyStoragePermissions returns true when directory exists`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Normal Android behavior - getExternalFilesDir returns a valid directory
        val result = StoragePermissionsValidator.verifyStoragePermissions(activity)

        // Validation should pass
        assertTrue("Expected validation to pass when storage is valid", result)
    }

    /**
     * Test the crash scenario: when getExternalFilesDir returns null
     *
     * This simulates the Pixel Android/data corruption bug (issue #19553).
     * The validator should catch this and return false instead of crashing.
     */
    @Test
    fun `verifyStoragePermissions returns false when directory is null without crashing`() {
        // Create a spy activity to mock getExternalFilesDir behavior
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val activitySpy = spy(activity)

        // Simulate the bug: getExternalFilesDir returns null (the crash condition)
        doReturn(null).`when`(activitySpy).getExternalFilesDir(null)

        // The fix should catch this null case gracefully
        val result = StoragePermissionsValidator.verifyStoragePermissions(activitySpy)

        // Should return false (validation failed) but NOT crash
        assertFalse(
            "Expected validation to fail gracefully when getExternalFilesDir returns null",
            result,
        )

        // If we reach this point, the test passed - no SystemStorageException was thrown
    }

    /**
     * Test that validation handles exceptions gracefully
     *
     * Even if an unexpected exception occurs, the validator should catch it
     * and return false instead of crashing the app.
     */
    @Test
    fun `verifyStoragePermissions handles exceptions gracefully`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val activitySpy = spy(activity)

        // Actually throw an exception to test the catch block
        // This ensures the "try { ... } catch (e: Exception)" logic works
        org.mockito.Mockito
            .doThrow(RuntimeException("Simulated disk read error"))
            .`when`(activitySpy)
            .getExternalFilesDir(null)

        // Should catch the RuntimeException and return false without crashing
        val result = StoragePermissionsValidator.verifyStoragePermissions(activitySpy)

        assertFalse("Expected validation to fail gracefully on exception", result)
    }
}
