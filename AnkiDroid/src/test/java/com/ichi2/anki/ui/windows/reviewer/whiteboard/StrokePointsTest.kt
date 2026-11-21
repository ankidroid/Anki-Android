// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StrokePointsTest {
    private val strokes = mutableListOf<Path>()
    private lateinit var whiteboardView: WhiteboardView

    @Before
    fun setUp() {
        whiteboardView = WhiteboardView(ApplicationProvider.getApplicationContext())
        whiteboardView.onNewPath = { strokes.add(it) }
    }

    @Test
    fun `rebuilt path matches the drawn stroke`() {
        touch(MotionEvent.ACTION_DOWN, 10f to 10f)
        touch(MotionEvent.ACTION_MOVE, 20.5f to 14f, 31f to 22.25f, 45f to 40f)
        touch(MotionEvent.ACTION_MOVE, 60f to 41f)
        touch(MotionEvent.ACTION_UP, 60f to 41f)

        val stroke = assertIs<StrokePath>(strokes.single())
        assertEquals(
            listOf("MOVE 10.0,10.0", "SAMPLE 20.5,14.0", "SAMPLE 31.0,22.25", "SAMPLE 45.0,40.0", "SAMPLE 60.0,41.0"),
            stroke.points.describe(),
        )
        assertTrue(render(stroke.points.toPath()).sameAs(render(stroke)))
    }

    @Test
    fun `a tap keeps its tiny line`() {
        touch(MotionEvent.ACTION_DOWN, 30f to 40f)
        touch(MotionEvent.ACTION_UP, 30f to 40f)

        val stroke = assertIs<StrokePath>(strokes.single())
        assertEquals(listOf("MOVE 30.0,40.0", "LINE 30.2,40.2"), stroke.points.describe())
        assertTrue(render(stroke.points.toPath()).sameAs(render(stroke)))
    }

    @Test
    fun `each stroke keeps only its own points`() {
        touch(MotionEvent.ACTION_DOWN, 10f to 10f)
        touch(MotionEvent.ACTION_MOVE, 20f to 20f)
        touch(MotionEvent.ACTION_UP, 20f to 20f)
        touch(MotionEvent.ACTION_DOWN, 50f to 50f)
        touch(MotionEvent.ACTION_MOVE, 60f to 50f)
        touch(MotionEvent.ACTION_UP, 60f to 50f)

        assertEquals(listOf("MOVE 50.0,50.0", "SAMPLE 60.0,50.0"), assertIs<StrokePath>(strokes[1]).points.describe())
    }

    private fun touch(
        action: Int,
        vararg points: Pair<Float, Float>,
    ) {
        val (firstX, firstY) = points.first()
        val event = MotionEvent.obtain(0L, 0L, action, firstX, firstY, 0)
        try {
            for ((x, y) in points.drop(1)) {
                event.addBatch(0L, x, y, 1f, 1f, 0)
            }
            whiteboardView.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun render(path: Path): Bitmap =
        createBitmap(100, 100).also { bitmap ->
            val paint =
                Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    strokeCap = Paint.Cap.ROUND
                }
            Canvas(bitmap).drawPath(path, paint)
        }
}

internal fun StrokePoints.describe() = (0 until size).map { "${kind(it)} ${x(it)},${y(it)}" }
