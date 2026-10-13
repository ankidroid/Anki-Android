// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.widget

import android.content.Intent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.exception.SystemStorageException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPermissionReceiverTest : RobolectricTest() {
    @After
    fun resetStorageFailure() {
        CollectionHelper.systemStorageFailure = null
    }

    @Test
    fun `startup storage failure does not crash the receiver`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        CollectionHelper.systemStorageFailure = SystemStorageException.build("simulated getExternalFilesDir failure")

        WidgetPermissionReceiver().onReceive(targetContext, Intent())
    }
}
