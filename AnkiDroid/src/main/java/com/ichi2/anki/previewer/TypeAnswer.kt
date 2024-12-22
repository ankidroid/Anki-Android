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

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.libanki.Card
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject

// Desktop source: https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L669

@VisibleForTesting
val typeAnsRe = Regex("\\[\\[type:(.+?)]]")

/** removes `[[type:]]` tags */
@VisibleForTesting
fun removeTypeAnswerTags(text: String) = typeAnsRe.replace(text, "")

suspend fun getTypeAnswerField(
    card: Card,
    text: String,
): JSONObject? {
    val match = typeAnsRe.find(text) ?: return null

    val typeAnsFieldName =
        match.groups[1]!!.value.let {
            if (it.startsWith("cloze:")) {
                it.split(":")[1]
            } else {
                it
            }
        }

    val fields = withCol { card.noteType(this).flds }
    for (i in 0 until fields.length()) {
        val field = fields.get(i) as JSONObject
        if (field.getString("name") == typeAnsFieldName) {
            return field
        }
    }
    return null
}

suspend fun getExpectedTypeInAnswer(
    card: Card,
    field: JSONObject,
): String? {
    val fieldName = field.getString("name")
    val expected = withCol { card.note(this@withCol).getItem(fieldName) }
    return if (fieldName.startsWith("cloze:")) {
        val clozeIdx = card.ord + 1
        withCol {
            extractClozeForTyping(expected, clozeIdx).takeIf { it.isNotBlank() }
        }
    } else {
        expected
    }
}

fun getFontSize(field: JSONObject): String = field.getString("size")

/** Adapted from the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L720) */
suspend fun typeAnsAnswerFilter(
    card: Card,
    text: String,
): String {
    val typeAnswerField =
        getTypeAnswerField(card, text)
            ?: return typeAnsRe.replace(text, "")
    val expectedAnswer =
        getExpectedTypeInAnswer(card, typeAnswerField)
            ?: return typeAnsRe.replace(text, "")
    val typeFont = typeAnswerField.getString("font")
    val typeSize = getFontSize(typeAnswerField)
    val answerComparison = withCol { compareAnswer(expectedAnswer, provided = "") }

    @Language("HTML")
    val output =
        """<div style="font-family: '$typeFont'; font-size: ${typeSize}px">$answerComparison</div>"""
    return typeAnsRe.replace(text, output)
}
