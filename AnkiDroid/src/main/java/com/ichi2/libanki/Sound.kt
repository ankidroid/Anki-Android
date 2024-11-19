/*
 *  Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 *  Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/2.1.34/pylib/anki/sound.py
 *  https://github.com/ankitects/anki/blob/3378e476e6c63f46f6cbaab98ac679c7eb8dc5a0/pylib/anki/sound.py#L4
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.libanki

import android.text.TextUtils
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.preferences.getHidePlayAudioButtons
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.libanki.utils.NotInLibAnki
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.regex.Pattern

/**
 * Records information about a text to speech tag.
 *
 * @param speed speed of speech, where `1.0f` is normal speed. `null`: use system
 */
data class TTSTag(
    val fieldText: String,
    /**
     * Language may be empty if coming from AnkiDroid reading the whole card
     */
    val lang: String,
    val voices: List<String>,
    val speed: Float?,
    /** each arg should be in the form 'foo=bar' */
    val otherArgs: List<String>
) : AvTag()

/**
 * Contains the filename inside a `[sound:...]` tag.
 */
data class SoundOrVideoTag(val filename: String) : AvTag() {

    @NotInLibAnki
    fun getType(mediaDir: String): Type {
        val extension = filename.substringAfterLast(".", "")
        return when (extension) {
            in Sound.VIDEO_ONLY_EXTENSIONS -> Type.VIDEO
            in Sound.AUDIO_OR_VIDEO_EXTENSIONS -> {
                val file = File(mediaDir, filename)
                if (isAudioFileInVideoContainer(file) == true) {
                    Type.AUDIO
                } else {
                    Type.VIDEO
                }
            }
            // assume audio if we don't know. Our audio code is more resilient than HTML video
            else -> Type.AUDIO
        }
    }

    enum class Type {
        AUDIO,
        VIDEO
    }
}

/** In python, this is a union of [TTSTag] and [SoundOrVideoTag] */
open class AvTag

/**
 * [Regex] used to identify the markers for sound files
 */
val SOUND_RE = Pattern.compile("\\[sound:([^\\[\\]]*)]").toRegex()

fun stripAvRefs(text: String, replacement: String = "") = AvRef.REGEX.replace(text, replacement)

// not in libAnki
object Sound {
    val VIDEO_ONLY_EXTENSIONS = setOf("mov", "mkv")

    /** Extensions that can be audio-only using a video wrapper */
    val AUDIO_OR_VIDEO_EXTENSIONS = setOf("mp4", "mpg", "mpeg", "webm")

    /**
     * Takes content with [AvRef]s and expands them to reference the media file
     *
     * * Videos are replaced with `<video>`
     * * Audio is replaced with <a href="playsound:">
     *
     * @param content card content to be rendered that may contain embedded audio
     *
     * @return content with [AvRef]s replaced with HTML to play the file
     */
    @Suppress("HtmlUnknownAttribute", "HtmlDeprecatedAttribute")
    fun expandSounds(
        content: String,
        renderOutput: TemplateRenderOutput,
        showAudioPlayButtons: Boolean,
        mediaDir: String
    ) = replaceAvRefsWith(content, renderOutput) { tag, playTag ->
        fun asAudio(): String {
            if (!showAudioPlayButtons) return ""
            val playsound = "playsound:${playTag.side}:${playTag.index}"

            @Language("HTML")
            val result = """<a class="replay-button soundLink" href=$playsound><span>
                        <svg class="playImage" viewBox="0 0 64 64" version="1.1">
                            <circle cx="32" cy="32" r="29" fill="lightgrey"/>
                            <path d="M56.502,32.301l-37.502,20.101l0.329,-40.804l37.173,20.703Z" fill="black"/>Replay
                        </svg>
                    </span></a>"""
            return result
        }
        fun asVideo(tag: SoundOrVideoTag): String {
            val path = Paths.get(mediaDir, tag.filename).toString()
            val uri = getFileUri(path)

            val playsound = "${playTag.side}:${playTag.index}"

            val onEnded = """window.location.href = "videoended:$playsound";"""
            val onPause = """if (this.currentTime != this.duration) { window.location.href = "videopause:$playsound"; }"""

            // TODO: Make the loading screen nicer if the video doesn't autoplay
            @Language("HTML")
            val result =
                """<video
                    | src="$uri"
                    | controls
                    | data-file="${TextUtils.htmlEncode(tag.filename)}"
                    | onended='$onEnded'
                    | onpause='$onPause'
                    | data-play="$playsound" controlsList="nodownload"></video>
                """.trimMargin()
            return result
        }

        when (tag) {
            is TTSTag -> asAudio()
            is SoundOrVideoTag -> {
                when (tag.getType(mediaDir)) {
                    SoundOrVideoTag.Type.AUDIO -> asAudio()
                    SoundOrVideoTag.Type.VIDEO -> asVideo(tag)
                }
            }
            else -> throw IllegalStateException("unrecognised tag")
        }
    }

    /* Methods */
    val AV_PLAYLINK_RE = Regex("playsound:(.):(\\d+)")

    /**
     * Return card text with play buttons added, or stripped.
     *
     * @param text A string, maybe containing `[anki:play]` tags to replace
     * @param renderOutput Context: whether a file is audio or video
     */
    suspend fun addPlayButtons(
        text: String,
        renderOutput: TemplateRenderOutput
    ): String {
        val mediaDir = CollectionManager.withCol { media.dir }
        val hidePlayButtons = getHidePlayAudioButtons()
        return expandSounds(text, renderOutput, showAudioPlayButtons = !hidePlayButtons, mediaDir)
    }

    /**
     * Replaces `[anki:play:q:0]` with `[sound:...]`
     */
    fun replaceWithSoundTags(
        content: String,
        renderOutput: TemplateRenderOutput
    ): String = replaceAvRefsWith(content, renderOutput) { tag, _ ->
        if (tag !is SoundOrVideoTag) null else "[sound:${tag.filename}]"
    }

    /**
     * Replaces `[anki:play:q:0]` with ` example.mp3 `
     */
    fun replaceWithFileNames(
        content: String,
        renderOutput: TemplateRenderOutput
    ): String = replaceAvRefsWith(content, renderOutput) { tag, _ ->
        if (tag !is SoundOrVideoTag) null else " ${tag.filename} "
    }

    /**
     * Replaces [AvRef]s using the provided [processTag] function
     *
     * @param renderOutput context
     * @param processTag the text to replace the [AvTag] with, or `null` to perform no replacement
     */
    @Language("HTML")
    private fun replaceAvRefsWith(
        content: String,
        renderOutput: TemplateRenderOutput,
        processTag: (AvTag, AvRef) -> String?
    ): String {
        return AvRef.REGEX.replace(content) { match ->
            val avRef = AvRef.from(match) ?: return@replace match.value

            val tag = when (avRef.side) {
                "q" -> renderOutput.questionAvTags.getOrNull(avRef.index)
                "a" -> renderOutput.answerAvTags.getOrNull(avRef.index)
                else -> null
            } ?: return@replace match.value

            return@replace processTag(tag, avRef) ?: match.value
        }
    }

    /** Extract av tag from playsound:q:x link */
    suspend fun getAvTag(card: Card, url: String): AvTag? {
        return AV_PLAYLINK_RE.matchEntire(url)?.let {
            val values = it.groupValues
            val questionSide = values[1] == "q"
            val index = values[2].toInt()
            val tags = CollectionManager.withCol {
                if (questionSide) {
                    card.questionAvTags(this)
                } else {
                    card.answerAvTags(this)
                }
            }
            if (index < tags.size) {
                tags[index]
            } else {
                null
            }
        }
    }
}

/**
 * An [AvTag] partially rendered as `[anki:play:q:100]`
 */
data class AvRef(val side: String, val index: Int) {
    companion object {
        fun from(match: MatchResult): AvRef? {
            val groups = match.groupValues

            val index = groups[3].toIntOrNull() ?: return null

            val side = when (groups[2]) {
                "q" -> "q"
                "a" -> "a"
                else -> return null
            }
            return AvRef(side, index)
        }

        val REGEX = Regex("\\[anki:(play:(.):(\\d+))]")
    }
}

/** Similar to [File.toURI], but doesn't use the absolute file to simplify testing */
@NotInLibAnki
@VisibleForTesting
fun getFileUri(path: String): URI {
    var p = path
    if (File.separatorChar != '/') p = p.replace(File.separatorChar, '/')
    if (!p.startsWith("/")) p = "/$p"
    if (!p.startsWith("//")) p = "//$p"
    return URI("file", p, null)
}

/**
 * Whether a video file only contains an audio stream
 *
 * @return `null` - file is not a video, or not found
 */
@NotInLibAnki
@VisibleForTesting
fun isAudioFileInVideoContainer(file: File): Boolean? {
    if (file.extension !in Sound.VIDEO_ONLY_EXTENSIONS && file.extension !in Sound.AUDIO_OR_VIDEO_EXTENSIONS) {
        return null
    }

    if (file.extension in Sound.VIDEO_ONLY_EXTENSIONS) return false

    // file.extension is in AUDIO_OR_VIDEO_EXTENSIONS
    if (!file.exists()) return null

    // Also check that there is a video thumbnail, as some formats like mp4 can be audio only
    val isVideo = CompatHelper.compat.hasVideoThumbnail(file.absolutePath) ?: return null
    return !isVideo
}
