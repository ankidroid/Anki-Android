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

import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.template.TemplateError.NoClosingBrackets
import java.util.NoSuchElementException
import kotlin.Throws

/**
 * This class encodes template.rs's file creating template.
 * Due to the way iterator work in java, it's easier for the class to keep track of the template
 */
class Tokenizer internal constructor(template: String) : Iterator<Tokenizer.Token> {
    /**
     * The remaining of the string to read.
     */
    private var mTemplate: String

    /**
     * Become true if lexing failed. That is, the string start with {{, but no }} is found.
     */
    private var mFailed = false

    /**
     * Whether we consider <% and %> as handlebar
     */
    private val mLegacy: Boolean
    override fun hasNext(): Boolean {
        return mTemplate.length > 0 && !mFailed
    }

    /**
     * The kind of data we can find in a template and may want to consider for card generation
     */
    enum class TokenKind {
        /**
         * Some text, assumed not to contains {{*}}
         */
        TEXT,

        /**
         * {{Field name}}
         */
        REPLACEMENT,

        /**
         * {{#Field name}}
         */
        OPEN_CONDITIONAL,

        /**
         * {{^Field name}}
         */
        OPEN_NEGATED,

        /**
         * {{/Field name}}
         */
        CLOSE_CONDITIONAL
    }

    /**
     * This is equivalent to upstream's template.rs's Token type.
     *
     */
    @VisibleForTesting
    class Token(
        val kind: TokenKind,
        /**
         * If mKind is Text, then this contains the text.
         * Otherwise, it contains the content between "{{" and "}}", without the curly braces.
         */
        val text: String
    ) {

        override fun toString(): String {
            return kind.toString() + "(\"" + text + "\")"
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Token) {
                return false
            }
            val t = other
            return kind == t.kind && text == t.text
        }

        @VisibleForTesting
        fun new_to_legacy(): Token {
            return Token(
                kind,
                new_to_legacy(
                    text
                )
            )
        }
    }

    /**
     * This is similar to template.rs's type IResult<&str, Token>.
     * That is, it contains a token that was parsed, and the remaining of the string that must be read.
     */
    @VisibleForTesting
    internal class IResult(
        val token: Token,
        /**
         * The part of the string that must still be read.
         */
        /*
                This is a substring of the template. Java deal efficiently with substring by encoding it as original string,
                start index and length, so there is no loss in efficiency in using string instead of position.
                 */
        val remaining: String
    ) {
        override fun toString(): String {
            return "($token, \"$remaining\")"
        }

        override fun equals(other: Any?): Boolean {
            if (other !is IResult) {
                return false
            }
            val r = other
            return token == r.token && remaining == r.remaining
        }

        @VisibleForTesting
        fun new_to_legacy(): IResult {
            return IResult(token.new_to_legacy(), new_to_legacy(remaining))
        }
    }

    /**
     * @return The next token.
     * @throws TemplateError.NoClosingBrackets with no message if the template is entirely lexed, and with the remaining string otherwise.
     */
    @Throws(NoClosingBrackets::class)
    override fun next(): Token {
        if (mTemplate.length == 0) {
            throw NoSuchElementException()
        }
        val ir = next_token(mTemplate, mLegacy)
        if (ir == null) {
            // Missing closing }}
            mFailed = true
            throw NoClosingBrackets(mTemplate)
        }
        mTemplate = ir.remaining
        return ir.token
    }

    companion object {
        /**
         * If this text appears at the top of a template (not considering whitespaces and other \s symbols), then the
         * template accept legacy handlebars. That is <% foo %> is interpreted similarly as {{ foo }}.
         * This is used for compatibility with legacy version of anki.
         *
         * Same as rslib/src/template's ALT_HANDLEBAR_DIRECTIVE upstream */
        @VisibleForTesting
        val ALT_HANDLEBAR_DIRECTIVE = "{{=<% %>=}}"

        @VisibleForTesting
        fun new_to_legacy(template_part: String): String {
            return template_part.replace("{{", "<%").replace("}}", "%>")
        }

        /**
         * @param template The part of the template that must still be lexed
         * @param legacy whether <% is accepted as a handlebar
         * @return The longest prefix without handlebar, or null if it's empty.
         */
        @VisibleForTesting
        internal fun text_token(template: String, legacy: Boolean): IResult? {
            val first_legacy_handlebar = if (legacy) template.indexOf("<%") else -1
            val first_new_handlebar = template.indexOf("{{")
            val text_size: Int
            text_size = if (first_new_handlebar == -1) {
                if (first_legacy_handlebar == -1) {
                    template.length
                } else {
                    first_legacy_handlebar
                }
            } else {
                if (first_legacy_handlebar == -1 || first_new_handlebar < first_legacy_handlebar) {
                    first_new_handlebar
                } else {
                    first_legacy_handlebar
                }
            }
            return if (text_size == 0) {
                null
            } else {
                IResult(
                    Token(
                        TokenKind.TEXT,
                        template.substring(0, text_size)
                    ),
                    template.substring(text_size)
                )
            }
        }

        /**
         * classify handle based on leading character
         * @param handle The content between {{ and }}
         */
        internal fun classify_handle(handle: String): Token {
            var start_pos = 0
            while (start_pos < handle.length && handle[start_pos] == '{') {
                start_pos++
            }
            val start = handle.substring(start_pos).trim { it <= ' ' }
            return if (start.length < 2) {
                Token(
                    TokenKind.REPLACEMENT,
                    start
                )
            } else {
                when (start[0]) {
                    '#' -> Token(
                        TokenKind.OPEN_CONDITIONAL,
                        start.substring(1).trim { it <= ' ' }
                    )
                    '/' -> Token(
                        TokenKind.CLOSE_CONDITIONAL,
                        start.substring(1).trim { it <= ' ' }
                    )
                    '^' -> Token(
                        TokenKind.OPEN_NEGATED,
                        start.substring(1).trim { it <= ' ' }
                    )
                    else -> Token(
                        TokenKind.REPLACEMENT,
                        start
                    )
                }
            }
        }

        /**
         * @param template a part of a template to lex
         * @param legacy   Whether to also consider handlebar starting with <%
         * @return The content of handlebar at start of template
         */
        @VisibleForTesting
        internal fun handlebar_token(template: String, legacy: Boolean): IResult? {
            val new_handlebar_token = new_handlebar_token(template)
            if (new_handlebar_token != null) {
                return new_handlebar_token
            }
            return if (legacy) {
                legacy_handlebar_token(template)
            } else {
                null
            }
        }

        /**
         * @param template a part of a template to lex
         * @return The content of handlebar at start of template
         */
        @VisibleForTesting
        internal fun new_handlebar_token(template: String): IResult? {
            return handlebar_token(template, "{{", "}}")
        }

        private fun handlebar_token(template: String, prefix: String, suffix: String): IResult? {
            if (!template.startsWith(prefix)) {
                return null
            }
            val end = template.indexOf(suffix)
            if (end == -1) {
                return null
            }
            val content = template.substring(prefix.length, end)
            val handlebar = classify_handle(content)
            return IResult(handlebar, template.substring(end + suffix.length))
        }

        /**
         * @param template a part of a template to lex
         * @return The content of handlebar at start of template
         */
        @VisibleForTesting
        internal fun legacy_handlebar_token(template: String): IResult? {
            return handlebar_token(template, "<%", "%>")
        }

        /**
         * @param template The remaining of template to lex
         * @param legacy   Whether to accept <% as handlebar
         * @return The next token, or null at end of string
         */
        internal fun next_token(template: String, legacy: Boolean): IResult? {
            val t = handlebar_token(template, legacy)
            return t ?: text_token(template, legacy)
        }
    }

    /**
     * @param template A question or answer template.
     */
    init {
        @Suppress("NAME_SHADOWING")
        var template = template
        val trimmed = template.trim { it <= ' ' }
        mLegacy = trimmed.startsWith(ALT_HANDLEBAR_DIRECTIVE)
        if (mLegacy) {
            template = trimmed.substring(ALT_HANDLEBAR_DIRECTIVE.length)
        }
        mTemplate = template
    }
}
