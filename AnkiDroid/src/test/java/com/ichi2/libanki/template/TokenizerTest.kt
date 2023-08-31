/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.libanki.template

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.template.TemplateError.NoClosingBrackets
import com.ichi2.libanki.template.Tokenizer.Companion.ALT_HANDLEBAR_DIRECTIVE
import com.ichi2.libanki.template.Tokenizer.Companion.classify_handle
import com.ichi2.libanki.template.Tokenizer.Companion.handlebar_token
import com.ichi2.libanki.template.Tokenizer.Companion.legacy_handlebar_token
import com.ichi2.libanki.template.Tokenizer.Companion.new_handlebar_token
import com.ichi2.libanki.template.Tokenizer.Companion.new_to_legacy
import com.ichi2.libanki.template.Tokenizer.Companion.next_token
import com.ichi2.libanki.template.Tokenizer.Companion.text_token
import com.ichi2.libanki.template.Tokenizer.TokenKind.CLOSE_CONDITIONAL
import com.ichi2.libanki.template.Tokenizer.TokenKind.OPEN_CONDITIONAL
import com.ichi2.libanki.template.Tokenizer.TokenKind.OPEN_NEGATED
import com.ichi2.libanki.template.Tokenizer.TokenKind.REPLACEMENT
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class TokenizerTest : RobolectricTest() {
    private fun test_text_token_is_null(template: String) {
        assertThat(
            text_token(template, false),
            nullValue()
        )
        assertThat(
            text_token(template, true),
            nullValue()
        )
        val legacy_template = new_to_legacy(template)
        assertThat(
            text_token(legacy_template, true),
            nullValue()
        )
        // No test for legacy_template without legacy interpretation.
    }

    private fun test_text_token(template: String, expected: Tokenizer.IResult) {
        assertThat(text_token(template, false), equalTo(expected))
        assertThat(text_token(template, true), equalTo(expected))
        val legacy_template = new_to_legacy(template)
        val legacy_expected = expected.new_to_legacy()
        assertThat(
            text_token(legacy_template, true),
            equalTo(legacy_expected)
        )
        // No test for legacy_template without legacy interpretation.
    }

    @Test
    fun test_text_token() {
        test_text_token_is_null("{{neasiet}}")
        test_text_token_is_null("")
        test_text_token(
            "foo{{bar}}plop",
            Tokenizer.IResult(
                Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo"),
                "{{bar}}plop"
            )
        )
        test_text_token(
            "foo{bar}plop",
            Tokenizer.IResult(
                Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo{bar}plop"),
                ""
            )
        )
    }

    @Test
    fun legacy_in_test_new_and_legacytext_token() {
        assertThat(
            text_token("foo<%bar%>{{plop}}", true),
            equalTo(
                Tokenizer.IResult(
                    Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo"),
                    "<%bar%>{{plop}}"
                )
            )
        )
        assertThat(
            text_token("foo{{bar}}<%plop%>", true),
            equalTo(
                Tokenizer.IResult(
                    Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo"),
                    "{{bar}}<%plop%>"
                )
            )
        )
        assertThat(
            text_token("foo<%bar%>{{plop}}", false),
            equalTo(
                Tokenizer.IResult(
                    Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo<%bar%>"),
                    "{{plop}}"
                )
            )
        )
        assertThat(
            text_token("foo{{bar}}<%plop%>", false),
            equalTo(
                Tokenizer.IResult(
                    Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo"),
                    "{{bar}}<%plop%>"
                )
            )
        )
    }

    private fun test_classify_handle(
        template: String,
        token: Tokenizer.TokenKind,
        remaining: String
    ) {
        assertThat(
            classify_handle(template),
            equalTo(Tokenizer.Token(token, remaining))
        )
    }

    @Test
    fun test_classify_handle() {
        test_classify_handle("#foo", OPEN_CONDITIONAL, "foo")
        test_classify_handle("/foo", CLOSE_CONDITIONAL, "foo")
        test_classify_handle("^foo", OPEN_NEGATED, "foo")
        test_classify_handle("!foo", REPLACEMENT, "!foo")
        test_classify_handle("{#foo}", OPEN_CONDITIONAL, "foo}")
        test_classify_handle("{  #foo}", OPEN_CONDITIONAL, "foo}")
        test_classify_handle("    #", REPLACEMENT, "#")
        test_classify_handle("    foo   ", REPLACEMENT, "foo")
    }

    private fun test_handlebar_token(
        template: String,
        token: Tokenizer.TokenKind,
        field_name: String,
        remaining: String
    ) {
        val expected = Tokenizer.IResult(
            Tokenizer.Token(token, field_name),
            remaining
        )
        assertThat(new_handlebar_token(template), equalTo(expected))
        assertThat(handlebar_token(template, true), equalTo(expected))
        assertThat(
            handlebar_token(template, false),
            equalTo(expected)
        )
        val legacy_template = new_to_legacy(template)
        val legacy_expected = expected.new_to_legacy()
        assertThat(
            legacy_handlebar_token(legacy_template),
            equalTo(legacy_expected)
        )
        assertThat(
            handlebar_token(legacy_template, true),
            equalTo(legacy_expected)
        )
        assertThat(
            handlebar_token(legacy_template, false),
            nullValue()
        )
    }

    private fun test_handlebar_token_is_null(template: String) {
        assertThat(new_handlebar_token(template), nullValue())
        val legacy_template = new_to_legacy(template)
        assertThat(
            legacy_handlebar_token(legacy_template),
            nullValue()
        )
    }

    @Test
    fun test_handlebar_token() {
        test_handlebar_token("{{#foo}} bar", OPEN_CONDITIONAL, "foo", " bar")
        test_handlebar_token("{{/foo}} bar", CLOSE_CONDITIONAL, "foo", " bar")
        test_handlebar_token("{{^foo}} bar", OPEN_NEGATED, "foo", " bar")
        test_handlebar_token("{{!foo}} bar", REPLACEMENT, "!foo", " bar")
        test_handlebar_token("{{{#foo}}} bar", OPEN_CONDITIONAL, "foo", "} bar")
        test_handlebar_token(
            "{{{  #foo}}} bar",
            OPEN_CONDITIONAL,
            "foo",
            "} bar"
        )
        test_handlebar_token("{{    #}} bar", REPLACEMENT, "#", " bar")
        test_handlebar_token("{{    foo   }} bar", REPLACEMENT, "foo", " bar")
        test_handlebar_token(
            "{{filter:field}} bar",
            REPLACEMENT,
            "filter:field",
            " bar"
        )
        // The empty field name without filter is not valid in Anki,
        // However, it's not the lexer job to deal with it, and so it should be lexed correctly.
        test_handlebar_token("{{}} bar", REPLACEMENT, "", " bar")
        // Empty field name with filter is valid and has special meaning
        test_handlebar_token("{{filter:}} bar", REPLACEMENT, "filter:", " bar")
        test_handlebar_token_is_null("")
        test_handlebar_token_is_null("{")
        test_handlebar_token_is_null("{nisens")
        test_handlebar_token_is_null("inesa{{aieb }}")
    }

    @Test
    fun test_space_in_token() {
        test_next_token(
            "{{ # foo bar }} baz",
            OPEN_CONDITIONAL,
            "foo bar",
            " baz"
        )
        test_handlebar_token(
            "{{ / foo bar }} baz",
            CLOSE_CONDITIONAL,
            "foo bar",
            " baz"
        )
        test_handlebar_token(
            "{{ ^ foo bar }} baz",
            OPEN_NEGATED,
            "foo bar",
            " baz"
        )
        // REPLACEMENT types will have leading and trailing spaces trimmed, but otherwise no changes
        test_handlebar_token("{{ ! foo}} bar", REPLACEMENT, "! foo", " bar")
        // REPLACEMENT types will have leading and trailing spaces trimmed, but otherwise no changes
        test_handlebar_token(
            "{{ ! foo with spaces before during and after }} bar",
            REPLACEMENT,
            "! foo with spaces before during and after",
            " bar"
        )
    }

    private fun test_next_token(
        template: String,
        token: Tokenizer.TokenKind,
        field_name: String,
        remaining: String
    ) {
        val expected = Tokenizer.IResult(
            Tokenizer.Token(
                token,
                field_name
            ),
            remaining
        )
        assertThat(
            next_token(template, true),
            equalTo(expected)
        )
        assertThat(
            next_token(template, false),
            equalTo(expected)
        )
        val legacy_expected = expected.new_to_legacy()
        val legacy_template = new_to_legacy(template)
        assertThat(
            next_token(legacy_template, true),
            equalTo(legacy_expected)
        )
    }

    @Suppress("SameParameterValue")
    private fun test_next_token_is_null(template: String) {
        assertThat(next_token(template, false), nullValue())
        assertThat(next_token(template, true), nullValue())
        val legacy_template = new_to_legacy(template)
        assertThat(next_token(legacy_template, true), nullValue())
    }

    @Test
    fun test_next_token() {
        test_next_token("{{#foo}} bar", OPEN_CONDITIONAL, "foo", " bar")
        test_next_token("{{/foo}} bar", CLOSE_CONDITIONAL, "foo", " bar")
        test_next_token("{{^foo}} bar", OPEN_NEGATED, "foo", " bar")
        test_next_token("{{!foo}} bar", REPLACEMENT, "!foo", " bar")
        test_next_token("{{{#foo}}} bar", OPEN_CONDITIONAL, "foo", "} bar")
        test_next_token("{{{  #foo}}} bar", OPEN_CONDITIONAL, "foo", "} bar")
        test_next_token("{{    #}} bar", REPLACEMENT, "#", " bar")
        test_next_token("{{    foo   }} bar", REPLACEMENT, "foo", " bar")

        test_next_token_is_null("")
        test_next_token("foo{{bar}}plop", Tokenizer.TokenKind.TEXT, "foo", "{{bar}}plop")
        test_next_token("foo{bar}plop", Tokenizer.TokenKind.TEXT, "foo{bar}plop", "")
    }

    @Test
    fun test_tokens() {
        val template = "Foo {{Test}} {{{  #Bar}} {{/Plop }}iee {{!ien nnr"
        val legacy_template = new_to_legacy_template(template)
        val tokenizer = Tokenizer(template)
        val legacy_tokenizer = Tokenizer(legacy_template)

        assertThat(
            tokenizer.next(),
            equalTo(Tokenizer.Token(Tokenizer.TokenKind.TEXT, "Foo "))
        )
        assertThat(
            legacy_tokenizer.next(),
            equalTo(Tokenizer.Token(Tokenizer.TokenKind.TEXT, "Foo "))
        )
        assertThat(
            tokenizer.next(),
            equalTo(Tokenizer.Token(REPLACEMENT, "Test"))
        )
        assertThat(
            legacy_tokenizer.next(),
            equalTo(Tokenizer.Token(REPLACEMENT, "Test"))
        )
        assertThat(
            tokenizer.next(),
            equalTo(Tokenizer.Token(Tokenizer.TokenKind.TEXT, " "))
        )
        assertThat(
            legacy_tokenizer.next(),
            equalTo(Tokenizer.Token(Tokenizer.TokenKind.TEXT, " "))
        )
        assertThat(
            tokenizer.next(),
            equalTo(Tokenizer.Token(OPEN_CONDITIONAL, "Bar"))
        )
        assertThat(
            legacy_tokenizer.next(),
            equalTo(Tokenizer.Token(OPEN_CONDITIONAL, "Bar"))
        )
        assertThat(
            tokenizer.next(),
            equalTo(Tokenizer.Token(Tokenizer.TokenKind.TEXT, " "))
        )
        assertThat(
            legacy_tokenizer.next(),
            equalTo(Tokenizer.Token(Tokenizer.TokenKind.TEXT, " "))
        )
        assertThat(
            tokenizer.next(),
            equalTo(Tokenizer.Token(CLOSE_CONDITIONAL, "Plop"))
        )
        assertThat(
            legacy_tokenizer.next(),
            equalTo(Tokenizer.Token(CLOSE_CONDITIONAL, "Plop"))
        )
        assertThat(
            tokenizer.next(),
            equalTo(Tokenizer.Token(Tokenizer.TokenKind.TEXT, "iee "))
        )
        assertThat(
            legacy_tokenizer.next(),
            equalTo(Tokenizer.Token(Tokenizer.TokenKind.TEXT, "iee "))
        )
        assertFailsWith<NoClosingBrackets> {
            tokenizer.next()
        }.let { exc ->
            assertThat(exc.remaining, equalTo("{{!ien nnr"))
        }
        assertFailsWith<NoClosingBrackets> {
            legacy_tokenizer.next()
        }.let { exc ->
            assertThat(exc.remaining, equalTo("<%!ien nnr"))
        }
        assertThat(tokenizer.hasNext(), equalTo(false))
    }

    companion object {
        fun new_to_legacy_template(template: String): String {
            return "   " + ALT_HANDLEBAR_DIRECTIVE + new_to_legacy(template)
        }
    }
}
