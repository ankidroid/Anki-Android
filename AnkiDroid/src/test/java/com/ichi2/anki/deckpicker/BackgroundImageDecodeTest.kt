// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.BitmapUtil
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class BackgroundImageDecodeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun decodePassesPowerOfTwoSampleSizeOnOptions() {
        val file = writePng(width = 400, height = 400, color = Color.BLUE)
        val options = captureDecodeOptions(file, srcWidth = 400, srcHeight = 400, reqWidth = 100, reqHeight = 100)
        assertThat(options.inSampleSize, equalTo(4))
        assertThat(options.inSampleSize, equalTo(BitmapUtil.calculateInSampleSize(400, 400, 100, 100)))
    }

    @Test
    fun bitmapToBackgroundDrawableReturnsNullWhenDecodeFails() {
        assertThat(bitmapToBackgroundDrawable(context, null), nullValue())
    }

    @Test
    fun decodeCanReturnNullFromDecoder() {
        val file = writePng(width = 10, height = 10, color = Color.BLUE)
        val bitmap =
            decodeSubsampledBackgroundBitmap(file, 10, 10, 10, 10) { _, _ ->
                null
            }
        assertThat(bitmap, nullValue())
    }

    @Test
    fun decodeDrawableUsesDisplayMetricsAndWrapsBitmap() {
        val metrics = context.resources.displayMetrics
        metrics.widthPixels = 100
        metrics.heightPixels = 100
        val file = writePng(width = 400, height = 400, color = Color.RED)
        val drawable = decodeDeckPickerBackground(context, file, 400, 400)
        assertThat(drawable, instanceOf(BitmapDrawable::class.java))
        val bitmap = (drawable as BitmapDrawable).bitmap
        assertThat(bitmap.width, equalTo(100))
        assertThat(bitmap.height, equalTo(100))
    }

    @Test
    fun jpegDecodeSetsRgb565OnOptions() {
        val file = writeJpeg(width = 80, height = 80)
        val options = captureDecodeOptions(file, srcWidth = 80, srcHeight = 80, reqWidth = 80, reqHeight = 80)
        assertThat(options.inPreferredConfig, equalTo(Bitmap.Config.RGB_565))
        assertThat(options.inSampleSize, equalTo(1))
    }

    @Test
    fun pngWithTransparencySetsArgb8888OnOptions() {
        val file = writePng(width = 80, height = 80, color = Color.TRANSPARENT)
        val options = captureDecodeOptions(file, srcWidth = 80, srcHeight = 80, reqWidth = 80, reqHeight = 80)
        assertThat(options.inPreferredConfig, equalTo(Bitmap.Config.ARGB_8888))
        assertThat(options.inSampleSize, equalTo(1))
    }

    private fun captureDecodeOptions(
        file: File,
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): BitmapFactory.Options {
        var captured: BitmapFactory.Options? = null
        decodeSubsampledBackgroundBitmap(file, srcWidth, srcHeight, reqWidth, reqHeight) { _, options ->
            captured = options
            null
        }
        assertThat(captured, notNullValue())
        return captured!!
    }

    private fun writePng(
        width: Int,
        height: Int,
        color: Int,
    ): File {
        val file = temporaryFolder.newFile("bg.png")
        val bitmap = createBitmap(width, height)
        bitmap.eraseColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun writeJpeg(
        width: Int,
        height: Int,
    ): File {
        val file = temporaryFolder.newFile("bg.jpg")
        val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmap.eraseColor(Color.GREEN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }
}

@RunWith(AndroidJUnit4::class)
class BackgroundImageResolveTest : RobolectricTest() {
    @Test
    fun resolveDecodesSubsampledReadyResult() =
        runTest {
            Prefs.isBackgroundEnabled = true
            val dir = CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
            dir.mkdirs()
            val file = File(dir, BackgroundImage.FILENAME)
            val bitmap = createBitmap(400, 400)
            bitmap.eraseColor(Color.CYAN)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()

            val metrics = targetContext.resources.displayMetrics
            metrics.widthPixels = 100
            metrics.heightPixels = 100

            val result = BackgroundImage.resolve(targetContext)
            assertThat(result, instanceOf(BackgroundImage.ResolveResult.Ready::class.java))
            val decoded = ((result as BackgroundImage.ResolveResult.Ready).drawable as BitmapDrawable).bitmap
            assertThat(decoded.width, equalTo(100))
            assertThat(decoded.height, equalTo(100))
        }

    @Test
    fun resolveReturnsTooLargeWhenSourceExceedsMaxEvenIfSubsampleWouldFit() =
        runTest {
            Prefs.isBackgroundEnabled = true
            val dir = CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
            dir.mkdirs()
            val file = File(dir, BackgroundImage.FILENAME)
            val bitmap = createBitmap(400, 400)
            bitmap.eraseColor(Color.CYAN)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()

            val srcWidth = 8192
            val srcHeight = 8192
            assertThat(
                srcWidth.toLong() * srcHeight * BITMAP_BYTES_PER_PIXEL > BackgroundImage.MAX_BITMAP_SIZE,
                equalTo(true),
            )
            val metrics = targetContext.resources.displayMetrics
            metrics.widthPixels = 100
            metrics.heightPixels = 100
            val sampled =
                srcWidth / BitmapUtil.calculateInSampleSize(srcWidth, srcHeight, metrics.widthPixels, metrics.heightPixels)
            assertThat(
                sampled.toLong() * sampled * BITMAP_BYTES_PER_PIXEL > BackgroundImage.MAX_BITMAP_SIZE,
                equalTo(false),
            )

            @Suppress("DEPRECATION")
            ShadowBitmapFactory.provideWidthAndHeightHints(file.absolutePath, srcWidth, srcHeight)

            val result = BackgroundImage.resolve(targetContext)
            assertThat(result, instanceOf(BackgroundImage.ResolveResult.TooLarge::class.java))
        }
}
