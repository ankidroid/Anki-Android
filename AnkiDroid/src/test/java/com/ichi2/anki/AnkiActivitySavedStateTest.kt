// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.parcelSize
import com.ichi2.testutils.saveState
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Guards against the activity's export ViewModel duplicating launch arguments in saved state. */
@RunWith(AndroidJUnit4::class)
class AnkiActivitySavedStateTest : RobolectricTest() {
    @Test
    fun `unrelated intent extras do not increase activity saved state`() {
        fun savedSize(payload: ByteArray): Int {
            val intent = Intent(targetContext, EmptyAnkiActivity::class.java).putExtra("payload", payload)
            val size = savedStateSize(EmptyAnkiActivity::class.java, intent)
            assertContentEquals(payload, intent.getByteArrayExtra("payload"))
            return size
        }

        assertEquals(savedSize(ByteArray(0)), savedSize(ByteArray(300_000)))
    }

    @Test
    fun `fragment arguments are saved exactly once`() {
        fun savedSize(payload: ByteArray): Int =
            savedStateSize(
                SingleFragmentActivity::class.java,
                SingleFragmentActivity.getIntent(
                    targetContext,
                    Fragment::class,
                    Bundle().apply { putByteArray("payload", payload) },
                ),
            )

        val payload = ByteArray(300_000)
        assertEquals(
            payload.size,
            savedSize(payload) - savedSize(ByteArray(0)),
            "Saved state should grow by exactly one copy of the fragment arguments",
        )
    }

    private fun <T : AnkiActivity> savedStateSize(
        activityClass: Class<T>,
        intent: Intent,
    ): Int = startActivityControllerNormallyOpenCollectionWithIntent(activityClass, intent).saveState().parcelSize()
}
