// SPDX-FileCopyrightText: 2026 Ayush Patel
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundUtilsTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    /** Regression test for https://github.com/ankidroid/Anki-Android/issues/20668 */
    @Test
    fun videoTagSrcIsRelativeNotFileUri() {
        val renderOutput =
            TemplateRenderOutput(
                questionText = "",
                answerText = "",
                questionAvTags = listOf(SoundOrVideoTag("my#video.mov")),
                answerAvTags = emptyList(),
            )

        val html =
            expandSounds(
                content = "[anki:play:q:0]",
                renderOutput = renderOutput,
                showAudioPlayButtons = true,
                mediaDir = col.media.dir,
            )

        assertThat(html, not(containsString("file://")))
        assertThat(html, containsString("src=\"my%23video.mov\""))
    }
}
