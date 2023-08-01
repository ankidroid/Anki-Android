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
 */

package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.servicelayer.LanguageHint
import com.ichi2.anki.servicelayer.LanguageHintService
import com.ichi2.libanki.Card
import com.ichi2.utils.jsonObjectIterable
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import timber.log.Timber
import java.util.regex.Matcher
import java.util.regex.Pattern

// TODO: investigate whether it's feasible to drop the useInputTag option introduced back
// in 2015. https://github.com/ankidroid/Anki-Android/pull/3921

class TypeAnswer(
    @get:JvmName("useInputTag") val useInputTag: Boolean,
    @get:JvmName("doNotUseCodeFormatting") val doNotUseCodeFormatting: Boolean,
    /** Preference: Whether the user wants to focus "type in answer" */
    val autoFocus: Boolean
) {

    /** The correct answer in the compare to field if answer should be given by learner. Null if no answer is expected. */
    var correct: String? = null
        private set

    /** What the learner actually typed (externally mutable) */
    var input = ""

    /** Font face of the 'compare to' field */
    var font = ""
        private set

    /** The font size of the 'compare to' field */
    var size = 0
        private set

    var languageHint: LanguageHint? = null
        private set

    /**
     * Optional warning for when a typed answer can't be displayed
     *
     * * empty card [R.string.empty_card_warning]
     * * unknown field specified [R.string.unknown_type_field_warning]
     * */
    var warning: String? = null
        private set

    /**
     * @return true If entering input via EditText
     * and if the current card has a {{type:field}} on the card template
     */
    fun validForEditText(): Boolean {
        return !useInputTag && correct != null
    }

    fun autoFocusEditText(): Boolean {
        return validForEditText() && autoFocus
    }

    /**
     * Extract type answer/cloze text and font/size
     * @param card The next card to display
     */
    fun updateInfo(card: Card, res: Resources) {
        correct = null
        val q = card.q(false)
        val m = PATTERN.matcher(q)
        var clozeIdx = 0
        if (!m.find()) {
            return
        }
        var fldTag = m.group(1)!!
        // if it's a cloze, extract data
        if (fldTag.startsWith("cloze:")) {
            // get field and cloze position
            clozeIdx = card.ord + 1
            fldTag = fldTag.split(":").toTypedArray()[1]
        }
        // loop through fields for a match
        val flds: JSONArray = card.model().getJSONArray("flds")
        for (fld in flds.jsonObjectIterable()) {
            val name = fld.getString("name")
            if (name == fldTag) {
                correct = card.note().getItem(name)
                if (clozeIdx != 0) {
                    // narrow to cloze
                    correct = contentForCloze(correct!!, clozeIdx)
                }
                font = fld.getString("font")
                size = fld.getInt("size")
                languageHint = LanguageHintService.getLanguageHintForField(fld)
                break
            }
        }
        when (correct) {
            null -> {
                warning = if (clozeIdx != 0) {
                    res.getString(R.string.empty_card_warning)
                } else {
                    res.getString(R.string.unknown_type_field_warning, fldTag)
                }
            }
            "" -> {
                correct = null
            }
            else -> {
                warning = null
            }
        }
    }

    /**
     * Format question field when it contains typeAnswer or clozes. If there was an error during type text extraction, a
     * warning is displayed
     *
     * @param buf The question text
     * @return The formatted question text
     */
    fun filterQuestion(buf: String): String {
        val m = PATTERN.matcher(buf)
        if (warning != null) {
            return m.replaceFirst(warning!!)
        }
        val sb = java.lang.StringBuilder()
        fun append(@Language("HTML") html: String) = sb.append(html)
        if (useInputTag) {
            // These functions are defined in the JavaScript file assets/scripts/card.js. We get the text back in
            // shouldOverrideUrlLoading() in createWebView() in this file.
            append(
                """<center>
<input type="text" name="typed" id="typeans" onfocus="taFocus();" onblur="taBlur(this);" onKeyPress="return taKey(this, event)" autocomplete="off" """
            )
            // We have to watch out. For the preview we don’t know the font or font size. Skip those there. (Anki
            // desktop just doesn’t show the input tag there. Do it with standard values here instead.)
            if (font.isNotEmpty() && size > 0) {
                append("style=\"font-family: '").append(font).append("'; font-size: ")
                    .append(size).append("px;\" ")
            }
            append(">\n</center>\n")
        } else {
            append("<span id=\"typeans\" class=\"typePrompt")
            append("\">........</span>")
        }
        return m.replaceAll(sb.toString())
    }

    /**
     * Fill the placeholder for the type comparison: `[[type:(.+?)]]`
     *
     * Replaces with the HTML for the correct answer, and the comparison to the correct answer if appropriate.
     *
     * @param answer The card content on the back of the card
     *
     * @return The formatted answer text with `[[type:(.+?)]]` replaced with HTML
     */
    fun filterAnswer(answer: String): String {
        val userAnswer = input
        val correctAnswer = correct
        Timber.d("correct answer = %s", correctAnswer)
        Timber.d("user answer = %s", userAnswer)
        return filterAnswer(answer, userAnswer, correctAnswer ?: "")
    }

    /**
     * Fill the placeholder for the type comparison. Show the correct answer, and the comparison if appropriate.
     *
     * @param answer The answer text
     * @param userAnswer Text typed by the user, or empty.
     * @param correctAnswer The correct answer, taken from the note.
     * @return The formatted answer text
     */
    fun filterAnswer(answer: String, userAnswer: String, correctAnswer: String): String {
        val m: Matcher = PATTERN.matcher(answer)
        val sb = StringBuilder()
        fun append(@Language("HTML") html: String) = sb.append(html)

        var comparisonText = CollectionManager.compareAnswer(correctAnswer, userAnswer)
        if (doNotUseCodeFormatting) {
            comparisonText = "$comparisonText<style>code.typeans { font-family: sans-serif; }</style>"
        }
        append(Matcher.quoteReplacement(comparisonText))
        return m.replaceAll(sb.toString())
    }

    companion object {
        /** Regular expression in card data for a 'type answer' after processing has occurred */
        val PATTERN: Pattern = Pattern.compile("\\[\\[type:(.+?)]]")

        fun createInstance(preferences: SharedPreferences): TypeAnswer {
            return TypeAnswer(
                useInputTag = preferences.getBoolean("useInputTag", false),
                doNotUseCodeFormatting = preferences.getBoolean("noCodeFormatting", false),
                autoFocus = preferences.getBoolean("autoFocusTypeInAnswer", false)
            )
        }

        /**
         * Return the correct answer to use for {{type::cloze::NN}} fields.
         *
         * @param txt The field text with the clozes
         * @param idx The index of the cloze to use
         * @return If the cloze strings are the same, return a single cloze string, otherwise, return
         * a string with a comma-separeted list of strings with the correct index.
         */
        @VisibleForTesting
        fun contentForCloze(txt: String, idx: Int): String? {
            // In Android, } should be escaped
            val re = Pattern.compile("\\{\\{c$idx::(.+?)\\}\\}")
            val m = re.matcher(txt)
            val matches: MutableList<String?> = ArrayList()
            var groupOne: String
            while (m.find()) {
                groupOne = m.group(1)!!
                val colonColonIndex = groupOne.indexOf("::")
                if (colonColonIndex > -1) {
                    // Cut out the hint.
                    groupOne = groupOne.substring(0, colonColonIndex)
                }
                matches.add(groupOne)
            }
            val uniqMatches: Set<String?> = HashSet(matches) // Allow to check whether there are distinct strings

            // Make it consistent with the Desktop version (see issue #8229)
            return if (uniqMatches.size == 1) {
                matches[0]
            } else {
                matches.joinToString(", ")
            }
        }
    }
}
