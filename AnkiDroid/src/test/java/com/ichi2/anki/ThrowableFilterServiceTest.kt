// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.servicelayer.ThrowableFilterService
import com.ichi2.anki.servicelayer.ThrowableFilterService.safeFromPII
import com.ichi2.testutils.JvmTest
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendSchedulerUpgradeRequiredException
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncServerMessageException
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ThrowableFilterServiceTest : JvmTest() {
    @Test
    fun `scheduler upgrade required exceptions are discarded`() {
        val exception = BackendSchedulerUpgradeRequiredException(backendError {})
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception))
    }

    @Test
    fun `other backend exceptions are retained`() {
        assertFalse(ThrowableFilterService.shouldDiscardThrowable(BackendException(backendError {})))
    }

    @Test
    fun `Normal exceptions are flagged as PII-safe`() {
        val exception = BackendDeckIsFilteredException(backendError {})
        assertTrue(exception.safeFromPII(), "Exception reported as safe from PII")
    }

    @Test
    fun `BackendSyncServerMessage exceptions are flagged as PII-unsafe`() {
        val exception1 = BackendSyncServerMessageException(backendError { })
        assertFalse(exception1.safeFromPII(), "Exception reported as not safe from PII")

        val exception2 = Exception("", Exception("", exception1))
        assertFalse(exception2.safeFromPII(), "Nested exception reported as not safe from PII")
    }

    @Test
    fun `exceptions are discarded correctly by type`() {
        // regular exceptions should go through
        assertFalse(ThrowableFilterService.shouldDiscardThrowable(Exception("wanted")))

        // exceptions of known unwanted types should not go through
        val exception1 = BackendNetworkException(backendError {})
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception1))
        val exception2 = BackendSyncException(backendError {})
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception2))
        val exception3 = StorageAccessException("test exception")
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception3))
    }
}
