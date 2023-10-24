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

import com.ichi2.libanki.Sound.Companion.SOUND_RE
import com.ichi2.libanki.TemplateManager.PartiallyRenderedCard.Companion.avTagsToNative
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.backend.model.toBackendNote
import com.ichi2.libanki.utils.append
import com.ichi2.libanki.utils.len
import com.ichi2.utils.deepClone
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendTemplateException
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import timber.log.Timber

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
    data class TemplateReplacement(val field_name: String, var current_text: String, val filters: List<String>)
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
                                    field_name = node.replacement.fieldName,
                                    current_text = node.replacement.currentText,
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
                        speed = tag.tts.speed
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
        col: Collection,
        card: Card,
        note: Note,
        browser: Boolean = false,
        notetype: NotetypeJson? = null,
        template: JSONObject? = null,
        fill_empty: Boolean = false
    ) {

        @RustCleanup("internal variables should be private, revert them once we're on V16")
        @RustCleanup("this was a WeakRef")
        internal val _col: Collection = col
        internal var _card: Card = card
        internal var _note: Note = note
        internal var _browser: Boolean = browser
        internal var _template: JSONObject? = template
        internal var _fill_empty: Boolean = fill_empty
        private var _fields: HashMap<String, String>? = null
        internal var _note_type: NotetypeJson = notetype ?: note.model()

        companion object {
            fun fromExistingCard(card: Card, browser: Boolean): TemplateRenderContext {
                return TemplateRenderContext(card.col, card, card.note(), browser)
            }

            fun fromCardLayout(
                note: Note,
                card: Card,
                notetype: NotetypeJson,
                template: JSONObject,
                fillEmpty: Boolean
            ): TemplateRenderContext {
                return TemplateRenderContext(
                    note.col,
                    card,
                    note,
                    notetype = notetype,
                    template = template,
                    fill_empty = fillEmpty
                )
            }
        }

        fun col() = _col

        fun fields(): HashMap<String, String> {
            Timber.w(".fields() is obsolete, use .note() or .card()")
            if (_fields == null) {
                // fields from note
                val fields = _note.items().map { Pair(it[0]!!, it[1]!!) }.toMap().toMutableMap()

                // add (most) special fields
                fields["Tags"] = _note.stringTags().trim()
                fields["Type"] = _note_type.name
                fields["Deck"] = _col.decks.name(_card.oDid or _card.did)
                fields["Subdeck"] = Decks.basename(fields["Deck"]!!)
                if (_template != null) {
                    fields["Card"] = _template!!["name"] as String
                } else {
                    fields["Card"] = ""
                }

                val flag = _card.userFlag()
                fields["CardFlag"] = if (flag != 0) "flag$flag" else ""
                _fields = HashMap(fields)
            }
            return _fields!!
        }

        /**
         * Returns the card being rendered.
         * Be careful not to call .q() or .a() on the card, or you'll create an
         * infinite loop.
         */
        fun card() = _card

        fun note() = _note
        fun noteType() = _note_type

        @RustCleanup("legacy")
        fun qfmt(): String {
            return templatesForCard(card(), _browser).first
        }

        @RustCleanup("legacy")
        fun afmt(): String {
            return templatesForCard(card(), _browser).second
        }

        fun render(): TemplateRenderOutput {
            val partial: PartiallyRenderedCard
            try {
                partial = partiallyRender()
            } catch (e: BackendTemplateException) {
                return TemplateRenderOutput(
                    question_text = e.localizedMessage ?: e.toString(),
                    answer_text = e.localizedMessage ?: e.toString(),
                    question_av_tags = emptyList(),
                    answer_av_tags = emptyList()
                )
            }

            /**
             * NOT in libanki.
             *
             * The desktop version handles videos in an external player (mpv)
             * because of old webview codecs in python, and to allow extending the video player.
             * To simplify things and deliver a better result,
             * we use the webview player, like AnkiMobile does
             */
            fun parseVideos(text: String): String {
                return SOUND_RE.replace(text) { match ->
                    val fileName = match.groupValues[1]
                    val extension = fileName.substringAfterLast(".", "")
                    if (extension in VIDEO_EXTENSIONS) {
                        @Language("HTML")
                        val result =
                            """<video src="$fileName" controls controlsList="nodownload"></video>"""
                        result
                    } else {
                        match.value
                    }
                }
            }

            val qtext = parseVideos(applyCustomFilters(partial.qnodes, this, front_side = null))
            val qout = col().backend.extractAvTags(text = qtext, questionSide = true)
            var qoutText = qout.text

            val atext = parseVideos(applyCustomFilters(partial.anodes, this, front_side = qout.text))
            val aout = col().backend.extractAvTags(text = atext, questionSide = false)
            var aoutText = aout.text

            if (!_browser) {
                val svg = _note_type.optBoolean("latexsvg", false)
                qoutText = LaTeX.mungeQA(qoutText, _col, svg)
                aoutText = LaTeX.mungeQA(aoutText, _col, svg)
            }

            val output = TemplateRenderOutput(
                question_text = qoutText,
                answer_text = aoutText,
                question_av_tags = avTagsToNative(qout.avTagsList),
                answer_av_tags = avTagsToNative(aout.avTagsList),
                css = noteType().getString("css")
            )

            return output
        }

        @RustCleanup("Remove when DroidBackend supports named arguments")
        fun partiallyRender(): PartiallyRenderedCard {
            val proto = col().run {
                if (_template != null) {
                    // card layout screen
                    backend.renderUncommittedCardLegacy(
                        _note.toBackendNote(),
                        _card.ord,
                        BackendUtils.to_json_bytes(_template!!.deepClone()),
                        _fill_empty,
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
            @get:JvmName("getQuestionText")
            @set:JvmName("setQuestionText")
            var question_text: String,
            @get:JvmName("getAnswerText")
            @set:JvmName("setAnswerText")
            var answer_text: String,
            val question_av_tags: List<AvTag>,
            val answer_av_tags: List<AvTag>,
            val css: String = ""
        ) {

            fun questionAndStyle() = "<style>$css</style>$question_text"
            fun answerAndStyle() = "<style>$css</style>$answer_text"
        }

        @RustCleanup("legacy")
        fun templatesForCard(card: Card, browser: Boolean): Pair<String, String> {
            val template = card.template()
            var a: String? = null
            var q: String? = null

            if (browser) {
                q = template.getString("bqfmt")
                a = template.getString("bafmt")
            }

            q = q ?: template.getString("qfmt")
            a = a ?: template.getString("afmt")

            return Pair(q!!, a!!)
        }

        /** Complete rendering by applying any pending custom filters. */
        fun applyCustomFilters(
            rendered: TemplateReplacementList,
            @Suppress("unused_parameter") ctx: TemplateRenderContext,
            front_side: String?
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
                    if (node.field_name == "FrontSide" && front_side != null) {
                        node.current_text = front_side
                    }

                    val field_text = node.current_text

                    // AnkiDroid: ignored hook-based code
                    // for (filter_name in node.filters) {
                    //     field_text = hooks.field_filter(field_text, node.field_name, filter_name, ctx
                    //     )
                    //     // legacy hook - the second and fifth argument are no longer used.
                    //     field_text = anki.hooks.runFilter(
                    //             "fmod_" + filter_name,
                    //             field_text,
                    //             "",
                    //             ctx.note().items(),
                    //             node.field_name,
                    //             "",
                    //     )
                    // }

                    res += field_text
                }
            }
            return res
        }
    }
}
