/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.libanki.template

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.R
import com.ichi2.libanki.Utils
import com.ichi2.libanki.template.TemplateError.*
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.util.*

/**
 * Represents a template, allow to check in linear time which card is empty/render card.
 */
@KotlinCleanup("fix ide lint issues")
abstract class ParsedNode {
    /**
     * @param nonempty_fields A set of fields that are not empty
     * @return Whether the card is empty. I.e. no non-empty fields are shown
     */
    abstract fun template_is_empty(nonempty_fields: Set<String>): Boolean

    // Used only fot testing
    @VisibleForTesting
    fun template_is_empty(vararg nonempty_fields: String?): Boolean {
        return template_is_empty(nonempty_fields.map { it!! }.toSet())
    }

    @Throws(TemplateError::class)
    abstract fun render_into(
        fields: Map<String, String>,
        nonempty_fields: Set<String>,
        builder: StringBuilder
    )

    fun render(fields: Map<String, String>, question: Boolean, context: Context): String {
        return try {
            val builder = StringBuilder()
            render_into(fields, Utils.nonEmptyFields(fields), builder)
            builder.toString()
        } catch (er: TemplateError) {
            Timber.w(er)
            val side =
                if (question) {
                    context.getString(R.string.card_template_editor_front)
                } else {
                    context.getString(
                        R.string.card_template_editor_back
                    )
                }
            val explanation = context.getString(R.string.has_a_problem, side, er.message(context))
            val more_explanation =
                "<a href=\"" + TEMPLATE_ERROR_LINK + "\">" + context.getString(R.string.more_information) + "</a>"
            "$explanation<br/>$more_explanation"
        }
    }

    companion object {
        const val TEMPLATE_ERROR_LINK =
            "https://anki.tenderapp.com/kb/problems/card-template-has-a-problem"

        /**
         * Associate to each template its node, or the error it generates
         */
        private val parse_inner_cache = WeakHashMap<String, Pair<ParsedNode?, TemplateError?>>()

        /**
         * @param template A question or answer template
         * @return A tree representing the template.
         * @throws TemplateError if the template is not valid
         */
        @Throws(TemplateError::class)
        fun parse_inner(template: String): ParsedNode {
            if (!parse_inner_cache.containsKey(template)) {
                val res: Pair<ParsedNode?, TemplateError?>
                res = try {
                    val node = parse_inner(Tokenizer(template))
                    Pair(node, null)
                } catch (er: TemplateError) {
                    Pair(null, er)
                }
                parse_inner_cache[template] = res
            }
            val res = parse_inner_cache[template]!!
            if (res.first != null) {
                return res.first!!
            }
            throw res.second!!
        }

        /**
         * @param tokens An iterator returning a list of token obtained from a template
         * @return A tree representing the template
         * @throws TemplateError Any reason meaning the data is not valid as a template.
         */
        @Throws(TemplateError::class)
        protected fun parse_inner(tokens: Iterator<Tokenizer.Token>): ParsedNode {
            return parse_inner(tokens, null)!!
        }

        /**
         * @param tokens An iterator returning a list of token obtained from a template
         * @param open_tag The last opened tag that is not yet closed, or null
         * @return A tree representing the template, or null if no text can be generated.
         * @throws TemplateError Any reason meaning the data is not valid as a template.
         */
        @Throws(TemplateError::class)
        private fun parse_inner(tokens: Iterator<Tokenizer.Token>, open_tag: String?): ParsedNode? {
            val nodes: MutableList<ParsedNode> = ArrayList()
            while (tokens.hasNext()) {
                val token = tokens.next()
                when (token.kind) {
                    Tokenizer.TokenKind.TEXT -> {
                        nodes.add(Text(token.text))
                    }
                    Tokenizer.TokenKind.REPLACEMENT -> {
                        val it = token.text.split(":".toRegex()).toTypedArray()
                        val key = it[it.size - 1]
                        val filters: MutableList<String> = ArrayList(it.size - 1)
                        var i = it.size - 2
                        while (i >= 0) {
                            filters.add(it[i])
                            i--
                        }
                        nodes.add(Replacement(key, filters, token.text))
                    }
                    Tokenizer.TokenKind.OPEN_CONDITIONAL -> {
                        val tag = token.text
                        nodes.add(Conditional(tag, parse_inner(tokens, tag)!!))
                    }
                    Tokenizer.TokenKind.OPEN_NEGATED -> {
                        val tag = token.text
                        nodes.add(NegatedConditional(tag, parse_inner(tokens, tag)!!))
                    }
                    Tokenizer.TokenKind.CLOSE_CONDITIONAL -> {
                        val tag = token.text
                        if (open_tag == null) {
                            throw ConditionalNotOpen(tag)
                        }
                        return if (tag != open_tag) { // open_tag may be null, tag is not
                            throw WrongConditionalClosed(tag, open_tag)
                        } else {
                            ParsedNodes.create(nodes)
                        }
                    }
                }
            }
            if (open_tag != null) {
                throw ConditionalNotClosed(open_tag)
            }
            return ParsedNodes.create(nodes)
        }
    }
}
