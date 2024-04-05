/*
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
 *   This file incorporates code under the following license
 *   https://github.com/ankitects/anki/blob/2.1.34/pylib/anki/template.py
 *
 *     Copyright: Ankitects Pty Ltd and contributors
 *     License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.libanki

import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Sound.AUDIO_OR_VIDEO_EXTENSIONS
import com.ichi2.libanki.Sound.VIDEO_ONLY_EXTENSIONS
import com.ichi2.libanki.TemplateManager.PartiallyRenderedCard.Companion.avTagsToNative
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.backend.model.toBackendNote
import com.ichi2.libanki.utils.NotInLibAnki
import com.ichi2.libanki.utils.append
import com.ichi2.libanki.utils.len
import com.ichi2.utils.deepClone
import net.ankiweb.rsdroid.exceptions.BackendTemplateException
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document.OutputSettings
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths

private typealias Union<A, B> = Pair<A, B>
private typealias TemplateReplacementList = MutableList<Union<String?, TemplateManager.TemplateReplacement?>>

/**
 * Template.py in python. Called TemplateManager for technical reasons (conflict with Kotlin typealias)
 *
 * This file contains the Kotlin portion of the template rendering code.
 * Templates can have filters applied to field replacements.
 *
 * The Rust template rendering code will apply any built in filters, and stop at the first
 * unrecognized filter. The remaining filters are returned to Kotlin, and applied using the hook system.
 *
 * For example, {{myfilter:hint:text:Field}} will apply the built in text and hint filters,
 * and then attempt to apply myfilter. If no add-ons have provided the filter,
 * the filter is skipped.
 */
class TemplateManager {
    data class TemplateReplacement(val fieldName: String, var currentText: String, val filters: List<String>)
    data class PartiallyRenderedCard(val qnodes: TemplateReplacementList, val anodes: TemplateReplacementList) {
        companion object {
            fun fromProto(out: anki.card_rendering.RenderCardResponse): PartiallyRenderedCard {
                val qnodes = nodesFromProto(out.questionNodesList)
                val anodes = nodesFromProto(out.answerNodesList)

                return PartiallyRenderedCard(qnodes, anodes)
            }

            fun nodesFromProto(nodes: List<anki.card_rendering.RenderedTemplateNode>): TemplateReplacementList {
                val results: TemplateReplacementList = mutableListOf()
                for (node in nodes) {
                    if (node.valueCase == anki.card_rendering.RenderedTemplateNode.ValueCase.TEXT) {
                        results.append(Pair(node.text, null))
                    } else {
                        results.append(
                            Pair(
                                null,
                                TemplateReplacement(
                                    fieldName = node.replacement.fieldName,
                                    currentText = node.replacement.currentText,
                                    filters = node.replacement.filtersList
                                )
                            )
                        )
                    }
                }

                return results
            }

            fun avTagToNative(tag: anki.card_rendering.AVTag): AvTag {
                val value = tag.valueCase
                return if (value == anki.card_rendering.AVTag.ValueCase.SOUND_OR_VIDEO) {
                    SoundOrVideoTag(filename = tag.soundOrVideo)
                } else {
                    TTSTag(
                        fieldText = tag.tts.fieldText,
                        lang = tag.tts.lang,
                        voices = tag.tts.voicesList,
                        otherArgs = tag.tts.otherArgsList,
                        // The backend currently sends speed = 1, even when undefined.
                        // We agreed that '1' should be classed as 'use system' and ignored
                        // https://github.com/ankidroid/Anki-Android/issues/15598#issuecomment-1953653639
                        speed = tag.tts.speed.let { if (it == 1f) null else it }
                    )
                }
            }

            fun avTagsToNative(tags: List<anki.card_rendering.AVTag>): List<AvTag> {
                return tags.map { avTagToNative(it) }.toList()
            }
        }
    }

    /**
     * Holds information for the duration of one card render.
     * This may fetch information lazily in the future, so please avoid
     * using the _private fields directly.
     */
    class TemplateRenderContext(
        card: Card,
        note: Note,
        browser: Boolean = false,
        notetype: NotetypeJson? = null,
        template: JSONObject? = null,
        private var fillEmpty: Boolean = false
    ) {
        private var _card: Card = card
        private var _note: Note = note
        private var _browser: Boolean = browser
        private var _template: JSONObject? = template

        private var noteType: NotetypeJson = notetype ?: note.notetype

        companion object {
            fun fromExistingCard(col: Collection, card: Card, browser: Boolean): TemplateRenderContext {
                return TemplateRenderContext(card, card.note(col), browser)
            }

            fun fromCardLayout(
                note: Note,
                card: Card,
                notetype: NotetypeJson,
                template: JSONObject,
                fillEmpty: Boolean
            ): TemplateRenderContext {
                return TemplateRenderContext(
                    card,
                    note,
                    notetype = notetype,
                    template = template,
                    fillEmpty = fillEmpty
                )
            }
        }

        /**
         * Returns the card being rendered.
         * Be careful not to call .q() or .a() on the card, or you'll create an
         * infinite loop.
         */
        fun card() = _card

        fun note() = _note
        fun noteType() = noteType

        @NeedsTest(
            "TTS tags `fieldText` is correctly extracted when sources are parsed to file scheme"
        )
        fun render(col: Collection): TemplateRenderOutput {
            val partial: PartiallyRenderedCard
            try {
                partial = partiallyRender(col)
            } catch (e: BackendTemplateException) {
                return TemplateRenderOutput(
                    questionText = e.localizedMessage ?: e.toString(),
                    answerText = e.localizedMessage ?: e.toString(),
                    questionAvTags = emptyList(),
                    answerAvTags = emptyList()
                )
            }

            val mediaDir = col.media.dir
            val qtext = parseVideos(
                applyCustomFilters(partial.qnodes, this, frontSide = null),
                mediaDir
            )
            val qout = col.backend.extractAvTags(text = qtext, questionSide = true)
            var qoutText = parseSourcesToFileScheme(qout.text, mediaDir)

            val atext = parseVideos(
                applyCustomFilters(partial.anodes, this, frontSide = qout.text),
                mediaDir
            )
            val aout = col.backend.extractAvTags(text = atext, questionSide = false)
            var aoutText = parseSourcesToFileScheme(aout.text, mediaDir)

            if (!_browser) {
                val svg = noteType.optBoolean("latexsvg", false)
                qoutText = LaTeX.mungeQA(qoutText, col, svg)
                aoutText = LaTeX.mungeQA(aoutText, col, svg)
            }

            return TemplateRenderOutput(
                questionText = qoutText,
                answerText = aoutText,
                questionAvTags = avTagsToNative(qout.avTagsList),
                answerAvTags = avTagsToNative(aout.avTagsList),
                css = noteType().getString("css")
            )
        }

        fun partiallyRender(col: Collection): PartiallyRenderedCard {
            val proto = col.run {
                if (_template != null) {
                    // card layout screen
                    backend.renderUncommittedCardLegacy(
                        _note.toBackendNote(),
                        _card.ord,
                        BackendUtils.to_json_bytes(_template!!.deepClone()),
                        fillEmpty,
                        true
                    )
                } else {
                    // existing card (eg study mode)
                    backend.renderExistingCard(_card.id, _browser, true)
                }
            }
            return PartiallyRenderedCard.fromProto(proto)
        }

        /** Stores the rendered templates and extracted AV tags. */
        data class TemplateRenderOutput(
            var questionText: String,
            var answerText: String,
            val questionAvTags: List<AvTag>,
            val answerAvTags: List<AvTag>,
            val css: String = ""
        ) {

            fun questionAndStyle() = "<style>$css</style>$questionText"
            fun answerAndStyle() = "<style>$css</style>$answerText"
        }

        /** Complete rendering by applying any pending custom filters. */
        fun applyCustomFilters(
            rendered: TemplateReplacementList,
            ctx: TemplateRenderContext,
            frontSide: String?
        ): String {
            // template already fully rendered?
            if (len(rendered) == 1 && rendered[0].first != null) {
                return rendered[0].first!!
            }

            var res = ""
            for (union in rendered) {
                if (union.first != null) {
                    res += union.first!!
                } else {
                    val node = union.second!!
                    // do we need to inject in FrontSide?
                    if (node.fieldName == "FrontSide" && frontSide != null) {
                        node.currentText = frontSide
                    }

                    var fieldText = node.currentText
                    for (filterName in node.filters) {
                        fieldFilters[filterName]?.let {
                            fieldText = it.apply(fieldText, node.fieldName, filterName, ctx)
                        }
                    }

                    res += fieldText
                }
            }
            return res
        }
    }

    /**
     * Defines custom `{{filters:..}}`
     *
     * Custom filters can check `filterName` to decide whether it should modify
     * `fieldText` or not before returning it
     */
    abstract class FieldFilter {
        abstract fun apply(
            fieldText: String,
            fieldName: String,
            filterName: String,
            ctx: TemplateRenderContext
        ): String
    }
    companion object {
        val fieldFilters: MutableMap<String, FieldFilter> = mutableMapOf()
    }
}

/**
 * The desktop version handles videos in an external player (mpv)
 * because of old webview codecs in python, and to allow extending the video player.
 * To simplify things and deliver a better result,
 * we use the webview player, like AnkiMobile does
 *
 * `file:///` is used to enable seeking the video
 */
@NotInLibAnki
@VisibleForTesting
fun parseVideos(text: String, mediaDir: String): String {
    fun toVideoTag(path: String): String {
        val uri = getFileUri(path)
        return """<video src="$uri" controls controlsList="nodownload"></video>"""
    }

    return SOUND_RE.replace(text) { match ->
        val fileName = match.groupValues[1]
        val extension = fileName.substringAfterLast(".", "")
        when (extension) {
            in VIDEO_ONLY_EXTENSIONS -> {
                val path = Paths.get(mediaDir, fileName).toString()
                toVideoTag(path)
            }
            in AUDIO_OR_VIDEO_EXTENSIONS -> {
                val file = File(mediaDir, fileName)
                if (isAudioFileInVideoContainer(file) == true) {
                    match.value
                } else {
                    toVideoTag(file.path)
                }
            }
            else -> match.value
        }
    }
}

/**
 * Parses the sources of the `<img>`, `<video>`, `<audio>` and `<source>` tags
 * to use the `file:///` scheme, which allows seeking audio and videos,
 * and loads faster than using HTTP.
 *
 * Only attribute values that don't have an Uri scheme (http, file, etc) are parsed.
 */
@NotInLibAnki
@VisibleForTesting
fun parseSourcesToFileScheme(content: String, mediaDir: String): String {
    val doc = Jsoup.parseBodyFragment(content)
    doc.outputSettings(OutputSettings().prettyPrint(false))

    fun replaceWithFileScheme(tag: String, attr: String): Boolean {
        var madeChanges = false
        for (elem in doc.select(tag)) {
            val attrValue = elem.attr(attr)
            if (attrValue.isEmpty()) continue

            val attrUri = try {
                URI(attrValue)
            } catch (_: URISyntaxException) {
                continue
            }
            if (attrUri.scheme != null) continue

            // For "legacy reasons" (https://forums.ankiweb.net/t/ankiweb-and-ankidroid-do-not-display-images-containing-pound-hashtag-sharp-symbol/42444/5)
            // anki accepts unencoded `#` in paths.
            val path = buildString {
                append(attrUri.path)
                attrUri.fragment?.let {
                    append("#")
                    append(it)
                }
            }
            val filePath = Paths.get(mediaDir, path).toString()
            val newUri = getFileUri(filePath)

            elem.attr(attr, newUri.toString())
            madeChanges = true
        }
        return madeChanges
    }

    val hasMadeChanges =
        replaceWithFileScheme("img", "src") ||
            replaceWithFileScheme("video", "src") ||
            replaceWithFileScheme("audio", "src") ||
            replaceWithFileScheme("source", "src")

    return if (hasMadeChanges) {
        doc.body().html()
    } else {
        content
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
@VisibleForTesting
fun isAudioFileInVideoContainer(file: File): Boolean? {
    if (file.extension !in VIDEO_ONLY_EXTENSIONS && file.extension !in AUDIO_OR_VIDEO_EXTENSIONS) {
        return null
    }

    if (file.extension in VIDEO_ONLY_EXTENSIONS) return false

    // file.extension is in AUDIO_OR_VIDEO_EXTENSIONS
    if (!file.exists()) return null

    // Also check that there is a video thumbnail, as some formats like mp4 can be audio only
    val isVideo = CompatHelper.compat.hasVideoThumbnail(file.absolutePath) ?: return null
    return !isVideo
}
