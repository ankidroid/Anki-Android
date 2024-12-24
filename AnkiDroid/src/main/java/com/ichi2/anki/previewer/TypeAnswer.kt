/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
 */
package com.ichi2.anki.previewer

import android.os.LocaleList
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.servicelayer.LanguageHintService
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Card
import com.ichi2.utils.jsonObjectIterable
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject

/**
 * Handles `type in the answer card` properties
 *
 * @see [combining]
 * @see [imeHintLocales]
 * */
@NeedsTest("combining and non combining answers are properly parsed")
@NeedsTest("cloze and non cloze 'type in the answer' cards are properly parsed")
class TypeAnswer private constructor(
    private val text: String,
    /** whether combining characters should be compared. Defined by the presence of the
     *   `nc:` specifier in the type answer tag */
    private val combining: Boolean,
    private val field: JSONObject,
    var expectedAnswer: String,
) {
    /** a field property specific to AnkiDroid that allows to automatically select
     *   a language for the keyboard. @see [LanguageHintService] */
    val imeHintLocales: LocaleList? by lazy {
        LanguageHintService.getImeHintLocales(this.field)
    }

    suspend fun answerFilter(typedAnswer: String = ""): String {
        val typeFont = field.getString("font")
        val typeSize = field.getString("size")
        val answerComparison = withCol { compareAnswer(expectedAnswer, provided = typedAnswer, combining = combining) }

        @Language("HTML")
        val repl = """<div style="font-family: '$typeFont'; font-size: ${typeSize}px">$answerComparison</div>"""
        return typeAnsRe.replace(text, repl)
    }

    companion object {
        /** removes `[[type:]]` tags from the given [text] */
        fun removeTags(text: String): String = typeAnsRe.replace(text, "")

        /**
         * @return a [TypeAnswer] instance if [text] contains a `[[type:Field]]` tag
         * with a valid field name, or null if not.
         *
         * ([Source](https://github.com/ankitects/anki/blob/8af63f81eb235b8d21df4e8eeaa6e02f46b3fbf6/qt/aqt/reviewer.py#L702))
         */
        suspend fun getInstance(
            card: Card,
            text: String,
        ): TypeAnswer? {
            val match = typeAnsRe.find(text) ?: return null
            val fld = match.groups[1]?.value ?: return null

            var combining = true
            val typeAnsFieldName =
                if (fld.startsWith("cloze:")) {
                    fld.split(":")[1]
                } else if (fld.startsWith("nc:")) {
                    combining = false
                    fld.split(":")[1]
                } else {
                    fld
                }
            val fields = withCol { card.noteType(this).flds }
            val typeAnswerField =
                fields.jsonObjectIterable().firstOrNull {
                    it.getString("name") == typeAnsFieldName
                } ?: return null
            val expectedAnswer = getExpectedTypeInAnswer(card, typeAnswerField)

            return TypeAnswer(
                text = text,
                combining = combining,
                field = typeAnswerField,
                expectedAnswer = expectedAnswer,
            )
        }

        private suspend fun getExpectedTypeInAnswer(
            card: Card,
            field: JSONObject,
        ): String {
            val fieldName = field.getString("name")
            val expected = withCol { card.note(this@withCol).getItem(fieldName) }
            return if (fieldName.startsWith("cloze:")) {
                val clozeIdx = card.ord + 1
                withCol {
                    extractClozeForTyping(expected, clozeIdx)
                }
            } else {
                expected
            }
        }
    }
}

@VisibleForTesting
val typeAnsRe = Regex("\\[\\[type:(.+?)]]")
