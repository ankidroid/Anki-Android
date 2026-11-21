// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ui.windows.reviewer.whiteboard.StrokePoints.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawingSvgTest {
    @Test
    fun `writes the expected file`() {
        assertEquals(EXPECTED_FILE, DrawingSvg.write(sampleDrawing()))
    }

    @Test
    fun `reading gives back the strokes`() {
        val drawing = assertNotNull(DrawingSvg.read(EXPECTED_FILE))

        assertEquals(200, drawing.width)
        assertEquals(100, drawing.height)
        assertEquals(Color.WHITE, drawing.background)
        assertEquals(listOf(false, false, true, false), drawing.actions.map { it.isEraser })
        assertEquals(listOf(Color.BLACK, Color.BLACK, Color.BLACK, HALF_RED), drawing.actions.map { it.color })
        assertEquals(listOf(10f, 10f, 30f, 7.25f), drawing.actions.map { it.strokeWidth })
        assertEquals(
            listOf(
                listOf("MOVE 10.0,10.0", "SAMPLE 20.0,14.0", "SAMPLE 31.0,22.0", "SAMPLE 15.0,12.0"),
                listOf("MOVE 50.0,61.0", "LINE 50.6,60.8"),
                listOf("MOVE 12.0,12.0", "SAMPLE 40.0,30.0"),
                listOf("MOVE 5.0,5.0", "SAMPLE 6.0,7.0"),
            ),
            drawing.actions.map { it.points.describe() },
        )
    }

    @Test
    fun `a written file reads back unchanged`() {
        val random = Random(20930)
        val actions =
            List(60) { index ->
                action(color = random.nextInt(), width = random.nextFloat() * 50f, isEraser = index % 7 == 3) {
                    val x = random.coordinate()
                    val y = random.coordinate()
                    moveTo(x, y)
                    when {
                        index % 5 == 0 -> lineTo(x + 0.2f, y + 0.2f)
                        index % 9 == 0 -> {
                            lineTo(x + 0.2f, y + 0.2f)
                            repeat(3) { sample(random.coordinate(), random.coordinate()) }
                        }
                        else -> {
                            repeat(random.nextInt(1, 80)) { sample(random.coordinate(), random.coordinate()) }
                            if (index % 11 == 0) {
                                moveTo(random.coordinate(), random.coordinate())
                                repeat(5) { sample(random.coordinate(), random.coordinate()) }
                            }
                        }
                    }
                }
            }
        val file = DrawingSvg.write(Drawing(1080, 2154, Color.BLACK, actions))

        val drawing = assertNotNull(DrawingSvg.read(file))

        assertEquals(file, DrawingSvg.write(drawing))
        for ((original, restored) in actions.zip(drawing.actions)) {
            if (!original.isEraser) assertEquals(original.color, restored.color)
            assertEquals(original.strokeWidth, restored.strokeWidth, absoluteTolerance = 0.026f)
            val before = original.points
            val after = restored.points
            assertEquals(before.describe().map { it.substringBefore(' ') }, after.describe().map { it.substringBefore(' ') })
            for (index in 0 until before.size) {
                val tolerance = if (before.kind(index) == Kind.LINE) 0.051f else 0.501f
                assertEquals(before.x(index), after.x(index), absoluteTolerance = tolerance)
                assertEquals(before.y(index), after.y(index), absoluteTolerance = tolerance)
            }
        }
    }

    @Test
    fun `files it did not write are refused`() {
        val changedFiles =
            listOf(
                "",
                EXPECTED_FILE.replace("data-ankidroid-drawing=\"1\"", "data-ankidroid-drawing=\"2\""),
                EXPECTED_FILE.replace(" data-ankidroid-drawing=\"1\"", ""),
                "<!DOCTYPE svg [<!ENTITY a \"b\">]>\n$EXPECTED_FILE",
                EXPECTED_FILE.replace("<path d=\"M12 12t14 9\"/>\n", "<path d=\"M12 12t14 9\"/>\n<circle r=\"5\"/>\n"),
                EXPECTED_FILE.replace("t5 2", "t5.0 2"),
                EXPECTED_FILE.replace("t5 2", "t5.03 2"),
                EXPECTED_FILE.replace("class=\"eraser\" stroke=\"#ffffff\"", "class=\"eraser\" stroke=\"#000000\""),
                EXPECTED_FILE.removeSuffix("</svg>\n"),
                "$EXPECTED_FILE<script>alert(1)</script>\n",
                EXPECTED_FILE.replace("\n", "\r\n"),
            )

        for (file in changedFiles) {
            assertNull(DrawingSvg.read(file), file)
        }
    }

    @Test
    fun `every opacity survives a round trip`() {
        for (alpha in 0..255) {
            val color = Color.argb(alpha, 0x12, 0x34, 0x56)
            val drawing =
                Drawing(
                    width = 10,
                    height = 10,
                    background = Color.WHITE,
                    actions = listOf(action(color = color) { moveTo(1f, 1f) }),
                )

            val restored = assertNotNull(DrawingSvg.read(DrawingSvg.write(drawing)))

            assertEquals(color, restored.actions.single().color, "alpha $alpha")
        }
    }

    @Test
    fun `erasers look the same as on the drawing screen`() {
        val actions =
            listOf(
                action(color = Color.BLUE, width = 12f) {
                    moveTo(10f, 50f)
                    sample(60f, 52f)
                    sample(110f, 50f)
                },
                action(width = 20f, isEraser = true) {
                    moveTo(50f, 20f)
                    sample(52f, 60f)
                    sample(50f, 90f)
                },
                action(color = HALF_RED, width = 8f) {
                    moveTo(20f, 80f)
                    sample(70f, 40f)
                    sample(100f, 20f)
                },
            )

        for (background in listOf(Color.WHITE, Color.BLACK)) {
            val expected = renderLikeTheDrawingScreen(actions, background)
            assertEquals(Color.BLUE, expected[30, 51], "the blue stroke should be drawn")
            assertEquals(background, expected[44, 51], "the eraser should clear the blue stroke")

            val file = DrawingSvg.write(Drawing(WIDTH, HEIGHT, background, actions))
            val eraserColor = assertNotNull(ERASER_STROKE.find(file)).groupValues[1].toInt(16) or Color.BLACK
            val restored = assertNotNull(DrawingSvg.read(file))

            assertPixelsClose(expected, renderLikeTheSvg(restored, eraserColor))
        }
    }

    private fun sampleDrawing() =
        Drawing(
            width = 200,
            height = 100,
            background = Color.WHITE,
            actions =
                listOf(
                    action {
                        moveTo(10f, 10f)
                        sample(20f, 14f)
                        sample(31f, 22f)
                        sample(15f, 12f)
                    },
                    action {
                        moveTo(50.4f, 60.6f)
                        lineTo(50.6f, 60.8f)
                    },
                    action(width = 30f, isEraser = true) {
                        moveTo(12f, 12f)
                        sample(40f, 30f)
                    },
                    action(color = HALF_RED, width = 7.25f) {
                        moveTo(5f, 5f)
                        sample(6f, 7f)
                    },
                ),
        )

    private fun action(
        color: Int = Color.BLACK,
        width: Float = 10f,
        isEraser: Boolean = false,
        draw: StrokePoints.Builder.() -> Unit,
    ): DrawingAction {
        val points = StrokePoints.Builder().apply(draw).build()
        return DrawingAction(StrokePath(points.toPath(), points), color, width, isEraser)
    }

    private fun Random.coordinate() = nextFloat() * 1200f - 60f

    private fun renderLikeTheDrawingScreen(
        actions: List<DrawingAction>,
        background: Int,
    ): Bitmap {
        val view = WhiteboardView(ApplicationProvider.getApplicationContext())
        view.layout(0, 0, WIDTH, HEIGHT)
        view.setHistory(actions)
        return createBitmap(WIDTH, HEIGHT).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            view.draw(canvas)
        }
    }

    private fun renderLikeTheSvg(
        drawing: Drawing,
        eraserColor: Int,
    ): Bitmap =
        createBitmap(drawing.width, drawing.height).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(drawing.background)
            val paint =
                Paint().apply {
                    isAntiAlias = true
                    isDither = true
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
            for (action in drawing.actions) {
                paint.color = if (action.isEraser) eraserColor else action.color
                paint.strokeWidth = action.strokeWidth
                canvas.drawPath(action.path, paint)
            }
        }

    private fun assertPixelsClose(
        expected: Bitmap,
        actual: Bitmap,
    ) {
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val want = expected[x, y]
                val got = actual[x, y]
                val difference =
                    maxOf(
                        abs(Color.red(want) - Color.red(got)),
                        abs(Color.green(want) - Color.green(got)),
                        abs(Color.blue(want) - Color.blue(got)),
                    )
                assertTrue(difference <= 2, "pixel $x,$y differs: ${Integer.toHexString(want)} and ${Integer.toHexString(got)}")
            }
        }
    }

    private val DrawingAction.points
        get() = (path as StrokePath).points

    companion object {
        private const val WIDTH = 120
        private const val HEIGHT = 100
        private const val HALF_RED = 0x80FF0000.toInt()
        private val ERASER_STROKE = Regex("""<g class="eraser" stroke="#([0-9a-f]{6})"""")

        private val EXPECTED_FILE =
            listOf(
                """<svg xmlns="http://www.w3.org/2000/svg" data-ankidroid-drawing="1" width="200" height="100" """ +
                    """viewBox="0 0 200 100" fill="none" stroke-linecap="round" stroke-linejoin="round">""",
                """<rect width="200" height="100" fill="#ffffff"/>""",
                """<g stroke="#000000" stroke-width="10">""",
                """<path d="M10 10t5 2 10.5 6-2.5-1"/>""",
                """<path d="M50 61l.6-.2"/>""",
                "</g>",
                """<g class="eraser" stroke="#ffffff" stroke-width="30">""",
                """<path d="M12 12t14 9"/>""",
                "</g>",
                """<g stroke="#ff0000" stroke-opacity="0.502" stroke-width="7.25">""",
                """<path d="M5 5t.5 1"/>""",
                "</g>",
                "</svg>",
            ).joinToString(separator = "\n", postfix = "\n")
    }
}
