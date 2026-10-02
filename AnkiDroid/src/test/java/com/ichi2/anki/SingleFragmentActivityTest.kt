// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.account.AccountActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SingleFragmentActivityTest : RobolectricTest() {
    @Test
    fun `launch without a fragment name shows an error and finishes`() {
        val intent = Intent(targetContext, AccountActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val activity = startRegularActivity<AccountActivity>(intent)

        assertTrue(activity.isFinishing)
        assertEquals(targetContext.getString(R.string.something_wrong), ShadowToast.getTextOfLatestToast())
    }
}
