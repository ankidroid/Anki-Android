// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.content.pm.ProviderInfo
import android.os.Bundle
import com.ichi2.anki.FlashCardsContract
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * This module's manifest registers no content provider, so by default Robolectric's
 * [android.content.pm.PackageManager] behaves as if AnkiDroid were not installed.
 *
 * Robolectric only shadows `resolveContentProvider(String, int)`, so the Tiramisu+ overload throws
 * `UnsupportedOperationException`. Still the case on Robolectric master and no upstream issue
 * tracks it, so these pin to the pre-Tiramisu branch. Both branches share the same null handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
internal class AddContentApiTest {
    private val api get() = AddContentApi(RuntimeEnvironment.getApplication())

    @Test
    fun apiHostSpecVersionIsMinusOneWhenAnkiDroidIsNotInstalled() {
        assertEquals(-1, api.apiHostSpecVersion)
    }

    @Test
    fun apiHostSpecVersionIsReadFromProviderMetaData() {
        installAnkiDroid(Bundle().apply { putInt("com.ichi2.anki.provider.spec", 2) })
        assertEquals(2, api.apiHostSpecVersion)
    }

    /** An installed AnkiDroid without the meta-data key predates it, so it's spec 1 */
    @Test
    fun apiHostSpecVersionIsOneWhenProviderHasNoMetaData() {
        installAnkiDroid(metaData = null)
        assertEquals(1, api.apiHostSpecVersion)
    }

    private fun installAnkiDroid(metaData: Bundle?) {
        val provider =
            ProviderInfo().apply {
                authority = FlashCardsContract.AUTHORITY
                packageName = "com.ichi2.anki"
                name = "com.ichi2.anki.provider.CardContentProvider"
                this.metaData = metaData
            }
        shadowOf(RuntimeEnvironment.getApplication().packageManager).addOrUpdateProvider(provider)
    }
}
