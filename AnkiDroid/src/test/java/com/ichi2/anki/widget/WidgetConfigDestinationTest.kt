// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.WidgetConfigDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.testutils.EmptyApplication
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import com.ichi2.widget.deckpicker.DeckPickerWidgetConfig
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class WidgetConfigDestinationTest {
    @Test
    fun `card analysis widget opens its config screen`() =
        assertOpensConfigScreen(WidgetConfigDestination.CardAnalysis(appWidgetId = 7), CardAnalysisWidgetConfig::class.java)

    @Test
    fun `deck picker widget opens its config screen`() =
        assertOpensConfigScreen(WidgetConfigDestination.DeckPicker(appWidgetId = 7), DeckPickerWidgetConfig::class.java)

    private fun assertOpensConfigScreen(
        destination: WidgetConfigDestination,
        configScreen: Class<*>,
    ) {
        val intent = with(DeferredNavigation) { destination.toIntent() }

        assertEquals(configScreen.name, intent.component?.className)
        assertEquals(7, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, intent.flags)
    }
}
