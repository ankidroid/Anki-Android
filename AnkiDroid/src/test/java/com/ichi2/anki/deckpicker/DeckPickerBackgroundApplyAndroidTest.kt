// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.R
import com.ichi2.testutils.EmptyApplication
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class DeckPickerBackgroundApplyAndroidTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun nullResultClearsImageAndKeepsHasBackground() {
        val imageView = ImageView(context)
        imageView.setImageDrawable(Color.RED.toDrawable())
        val toastState = BackgroundFailureToastState()

        val next =
            applyLoadedDeckPickerBackground(
                context = context,
                imageView = imageView,
                result = null,
                toastState = toastState,
                currentHasBackground = true,
            )

        assertThat(next, equalTo(true))
        assertThat(imageView.drawable, nullValue())
        assertThat(ShadowToast.shownToastCount(), equalTo(0))
    }

    @Test
    fun noneResultClearsHasBackgroundWithoutToast() {
        val imageView = ImageView(context)
        val next =
            applyLoadedDeckPickerBackground(
                context = context,
                imageView = imageView,
                result = BackgroundImage.ResolveResult.None,
                toastState = BackgroundFailureToastState(),
                currentHasBackground = true,
            )

        assertThat(next, equalTo(false))
        assertThat(ShadowToast.shownToastCount(), equalTo(0))
    }

    @Test
    fun readyResultSetsHasBackground() {
        val imageView = ImageView(context)
        val drawable = Color.BLUE.toDrawable()
        val next =
            applyLoadedDeckPickerBackground(
                context = context,
                imageView = imageView,
                result = BackgroundImage.ResolveResult.Ready(drawable),
                toastState = BackgroundFailureToastState(),
                currentHasBackground = false,
            )

        assertThat(next, equalTo(true))
        assertThat(imageView.drawable, equalTo<Drawable>(drawable))
        assertThat(ShadowToast.shownToastCount(), equalTo(0))
    }

    @Test
    fun failureToastsOnce() {
        val imageView = ImageView(context)
        val toastState = BackgroundFailureToastState()
        val first =
            applyLoadedDeckPickerBackground(
                context = context,
                imageView = imageView,
                result = BackgroundImage.ResolveResult.TooLarge,
                toastState = toastState,
                currentHasBackground = false,
            )
        val second =
            applyLoadedDeckPickerBackground(
                context = context,
                imageView = imageView,
                result = BackgroundImage.ResolveResult.Failed("decode"),
                toastState = toastState,
                currentHasBackground = first,
            )

        assertThat(first, equalTo(false))
        assertThat(second, equalTo(false))
        assertThat(ShadowToast.shownToastCount(), equalTo(1))
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(context.getString(R.string.background_image_too_large)),
        )
    }

    @Test
    fun failedToastsCause() {
        val imageView = ImageView(context)
        applyLoadedDeckPickerBackground(
            context = context,
            imageView = imageView,
            result = BackgroundImage.ResolveResult.Failed("decode"),
            toastState = BackgroundFailureToastState(),
            currentHasBackground = false,
        )

        assertThat(ShadowToast.shownToastCount(), equalTo(1))
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(context.getString(R.string.failed_to_apply_background_image, "decode")),
        )
    }

    @Test
    fun oomToastsTooLargeOnce() {
        val imageView = OomImageView(context)
        val toastState = BackgroundFailureToastState()
        val drawable = Color.GREEN.toDrawable()

        val first =
            applyLoadedDeckPickerBackground(
                context = context,
                imageView = imageView,
                result = BackgroundImage.ResolveResult.Ready(drawable),
                toastState = toastState,
                currentHasBackground = false,
            )
        val second =
            applyLoadedDeckPickerBackground(
                context = context,
                imageView = imageView,
                result = BackgroundImage.ResolveResult.Ready(drawable),
                toastState = toastState,
                currentHasBackground = first,
            )

        assertThat(first, equalTo(false))
        assertThat(second, equalTo(false))
        assertThat(ShadowToast.shownToastCount(), equalTo(1))
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(context.getString(R.string.background_image_too_large)),
        )
    }

    @Test
    fun installObserverRunsOnLifecycleStartAndStop() =
        runTest {
            val applied = mutableListOf<String?>()
            val loader =
                DeckPickerBackgroundLoader(
                    scope = this,
                    load = { "background" },
                    apply = { applied.add(it) },
                )
            val owner =
                object : LifecycleOwner {
                    override val lifecycle = LifecycleRegistry(this)
                }
            installDeckPickerBackgroundLoader(owner.lifecycle, loader)

            owner.lifecycle.currentState = Lifecycle.State.STARTED
            advanceUntilIdle()
            owner.lifecycle.currentState = Lifecycle.State.CREATED

            assertThat(applied, equalTo(listOf("background", null)))
        }

    private class OomImageView(
        context: Context,
    ) : ImageView(context) {
        override fun setImageDrawable(drawable: Drawable?): Unit = throw OutOfMemoryError("test")
    }
}
