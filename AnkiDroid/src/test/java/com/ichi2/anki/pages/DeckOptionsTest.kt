// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.webkit.WebView
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.deck_config.UpdateDeckConfigsMode
import anki.deck_config.UpdateDeckConfigsRequest
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.ext.clear
import com.ichi2.testutils.ext.defaultDeckNewCardsPerDay
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test for [DeckOptions] */
@RunWith(AndroidJUnit4::class)
class DeckOptionsTest : RobolectricTest() {
    @After
    override fun tearDown() {
        super.tearDown()
        Prefs.clear()
    }

    @Test
    fun `double tap interval is sent to the page when it is ready`() {
        Prefs.putInt(R.string.double_tap_timeout_pref_key, 800)

        withDeckOptions {
            onWebViewReady()

            assertThat(lastEvaluatedJavascript, containsString("setParameterUnlockClickTimeoutMs"))
            assertThat(lastEvaluatedJavascript, containsString("800"))
        }
    }

    @Test
    fun `optimizing all presets saves without closing options`() =
        runTest {
            withDeckOptions {
                updateDeckConfigs(UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_COMPUTE_ALL_PARAMS, newPerDay = 42)

                assertFalse(requireActivity().isFinishing)
                assertEquals(42, col.defaultDeckNewCardsPerDay)
            }
        }

    @Test
    fun `normal save closes options`() =
        runTest {
            withDeckOptions {
                updateDeckConfigs(UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL, newPerDay = 42)

                assertTrue(requireActivity().isFinishing)
                assertEquals(42, col.defaultDeckNewCardsPerDay)
            }
        }

    private suspend fun DeckOptions.updateDeckConfigs(
        mode: UpdateDeckConfigsMode,
        newPerDay: Int,
    ) {
        val data = col.backend.getDeckConfigsForUpdate(Consts.DEFAULT_DECK_ID)
        val config =
            data.allConfigList
                .single()
                .config
                .toBuilder()
        config.setConfig(config.config.toBuilder().setNewPerDay(newPerDay))
        val request =
            UpdateDeckConfigsRequest
                .newBuilder()
                .setTargetDeckId(Consts.DEFAULT_DECK_ID)
                .setMode(mode)
                .setFsrs(true)
                .addConfigs(config)
                .build()
                .toByteArray()
        requireActivity().updateDeckConfigsRaw(request)
    }

    private inline fun withDeckOptions(block: DeckOptions.() -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                SingleFragmentActivity::class.java,
                DeckOptions.getIntent(targetContext, Consts.DEFAULT_DECK_ID),
            )
        advanceRobolectricLooper()
        block(activity.fragment as DeckOptions)
    }

    /** The last JavaScript evaluated by the WebView of [DeckOptions] */
    private val DeckOptions.lastEvaluatedJavascript: String?
        get() =
            webViewLayout.children
                .filterIsInstance<WebView>()
                .single()
                .let { shadowOf(it).lastEvaluatedJavascript }
}
