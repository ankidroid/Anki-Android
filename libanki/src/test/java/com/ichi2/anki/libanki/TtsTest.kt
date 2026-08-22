// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/** Test for [TtsPlayer.voiceForTag] */
class TtsTest {
    @Test
    fun `locale-only match prefers an installed voice over an unavailable one`() {
        val unavailable = testVoice(name = "fr-unavailable", lang = "fr_FR", unavailable = true)
        val installed = testVoice(name = "fr-installed", lang = "fr_FR", unavailable = false)
        val player = testPlayer(unavailable, installed)

        val match = player.voiceForTag(ttsTag("fr_FR"))

        assertThat(match, notNullValue())
        assertThat("locale-only selection should skip the unavailable voice", match!!.voice, equalTo(installed))
    }

    @Test
    fun `locale-only match falls back to an unavailable voice when nothing else is installed`() {
        val unavailable = testVoice(name = "fr-unavailable", lang = "fr_FR", unavailable = true)
        val player = testPlayer(unavailable)

        val match = player.voiceForTag(ttsTag("fr_FR"))

        assertThat(match?.voice, equalTo(unavailable))
    }

    @Test
    fun `locale-only match keeps the first installed voice when several are installed`() {
        val first = testVoice(name = "fr-1", lang = "fr_FR", unavailable = false)
        val second = testVoice(name = "fr-2", lang = "fr_FR", unavailable = false)
        val player = testPlayer(first, second)

        assertThat(player.voiceForTag(ttsTag("fr_FR"))?.voice, equalTo(first))
    }

    @Test
    fun `locale-only fallback keeps the first unavailable voice when none are installed`() {
        val first = testVoice(name = "fr-1", lang = "fr_FR", unavailable = true)
        val second = testVoice(name = "fr-2", lang = "fr_FR", unavailable = true)
        val player = testPlayer(first, second)

        assertThat(player.voiceForTag(ttsTag("fr_FR"))?.voice, equalTo(first))
    }

    @Test
    fun `no match when no voice shares the language`() {
        val player = testPlayer(testVoice(name = "en", lang = "en_US", unavailable = false))

        assertThat(player.voiceForTag(ttsTag("fr_FR")), nullValue())
    }

    @Test
    fun `no match when there are no voices at all`() {
        val player = testPlayer()

        assertThat(player.voiceForTag(ttsTag("fr_FR")), nullValue())
    }

    private fun ttsTag(lang: String) =
        TTSTag(fieldText = "bonjour", lang = lang, voices = emptyList(), speed = null, otherArgs = emptyList())

    private fun testVoice(
        name: String,
        lang: String,
        unavailable: Boolean,
    ) = object : TtsVoice(name = name, lang = lang) {
        override fun unavailable(): Boolean = unavailable
    }

    private fun testPlayer(vararg voices: TtsVoice) =
        object : TtsPlayer() {
            override fun getAvailableVoices(): List<TtsVoice> = voices.toList()

            override suspend fun play(tag: TTSTag): TtsCompletionStatus = TtsCompletionStatus.success()

            override fun close() {}
        }
}
