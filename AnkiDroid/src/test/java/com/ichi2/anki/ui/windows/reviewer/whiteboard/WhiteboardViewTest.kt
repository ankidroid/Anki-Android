// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WhiteboardViewTest {
    private lateinit var whiteboardView: WhiteboardView

    @Before
    fun setUp() {
        whiteboardView = WhiteboardView(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `activeTool updates isEraserActive and eraserMode`() {
        whiteboardView.activeTool = WhiteboardTool.Brush(Color.RED, 15f)
        assertFalse(whiteboardView.isEraserActive)

        whiteboardView.activeTool = WhiteboardTool.Eraser(mode = EraserMode.STROKE, strokeEraserWidth = 25f)
        assertTrue(whiteboardView.isEraserActive)
        assertEquals(EraserMode.STROKE, whiteboardView.eraserMode)
    }
}

private val WhiteboardView.isEraserActive: Boolean
    get() = activeTool is WhiteboardTool.Eraser

private val WhiteboardView.eraserMode: EraserMode
    get() = (activeTool as? WhiteboardTool.Eraser)?.mode ?: EraserMode.INK
