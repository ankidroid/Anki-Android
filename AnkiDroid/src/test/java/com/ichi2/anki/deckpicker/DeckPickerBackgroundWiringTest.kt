// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.setIntroductionSlidesShown
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
class DeckPickerBackgroundWiringTest : RobolectricTest() {
    @Test
    fun lifecycleAndApplyCoverDeckPickerBackgroundLines() {
        setIntroductionSlidesShown(true)
        val controller =
            Robolectric
                .buildActivity(DeckPicker::class.java, Intent())
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(controller)
        advanceRobolectricLooper()

        val deckPicker = controller.get()
        val imageView = deckPicker.deckPickerBinding.background

        deckPicker.applyDeckPickerBackground(null)
        assertThat(imageView.drawable, nullValue())

        val drawable = ColorDrawable(Color.MAGENTA)
        deckPicker.applyDeckPickerBackground(BackgroundImage.ResolveResult.Ready(drawable))
        assertThat(imageView.drawable, notNullValue())

        deckPicker.applyDeckPickerBackground(BackgroundImage.ResolveResult.TooLarge)
        assertThat(ShadowToast.shownToastCount(), equalTo(1))
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(targetContext.getString(R.string.background_image_too_large)),
        )

        deckPicker.applyDeckPickerBackground(BackgroundImage.ResolveResult.Failed("decode"))
        assertThat(ShadowToast.shownToastCount(), equalTo(1))

        controller.pause().stop()
        assertThat(imageView.drawable, nullValue())
    }
}
