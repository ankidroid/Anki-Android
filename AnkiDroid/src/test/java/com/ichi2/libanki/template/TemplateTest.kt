/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki.template

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class TemplateTest : RobolectricTest() {
    private fun render(template: String, fields: Map<String, String>): String {
        return ParsedNode.parse_inner(template).render(fields, true, targetContext)
    }

    @Test
    fun fieldStartingWithExclamation() {
        // Ankidroid used not to display fields whose name start with !
        val context = HashMap<String, String>()
        context["!Front"] = "Test"
        assertThat(render("{{!Front}}", context), equalTo("Test"))
    }

    @Test
    @Config(qualifiers = "en")
    fun missingExclamation() {
        // Ankidroid used not to display fields whose name start with !
        val context = HashMap<String, String>()
        val rendered = render("{{!Front}}", context)

        assertThat(rendered, notNullValue())
        assertThat(
            rendered,
            containsString("there is no field called '!Front'")
        )
    }

    @Test
    fun typeInFieldRenders() {
        val context = HashMap<String, String>()
        context["Front"] = "AA{{type:Back}}"

        assertThat(render("{{Front}}", context), equalTo("AA{{type:Back}}"))
    }

    @Test
    fun testNotFoundWillRender() {
        val maybeBad = "{{#NotFound}}{{NotFound}}{{/NotFound}}"

        val context = HashMap<String, String>()

        assertThat(render(maybeBad, context), emptyString())
    }

    @Test
    @Config(qualifiers = "en")
    fun nestedTemplatesRenderWell() {
        // #6123
        val problematicTemplate = """{{#One}}
    {{#One}}
        {{One}}<br>
    {{/One}}
    {{#Two}}
        {{Two}}
    {{/Two}}
{{/One}}"""
        val context = HashMap<String, String>()
        context["One"] = "Card1 - One"
        context["Two"] = "Card1 - Two"
        val result = render(problematicTemplate, context)

        // most important - that it does render
        assertThat(result, not("{{Invalid template}}"))
        // Actual value (may be subject to change).
        assertThat(result, equalTo("\n    \n        Card1 - One<br>\n    \n    \n        Card1 - Two\n    \n"))
    }

    @Test
    @Ignore("GitHub: 6284")
    fun fieldNamesHaveTrailingSpacesIgnored() {
        // #6284
        val templateWithSpaces =
            "{{#IllustrationExample }}Illustration Example: {{IllustrationExample }}{{/IllustrationExample}}"

        val context = HashMap<String, String>()
        context["IllustrationExample"] = "ilex"
        test_render(templateWithSpaces, context, "Illustration Example: ilex")
    }

    private fun test_render(template: String, m: Map<String, String>, expected: String) {
        assertThat(render(template, m), equalTo(expected))
        val legacyTemplate = TokenizerTest.new_to_legacy_template(template)
        assertThat(render(legacyTemplate, m), equalTo(expected))
    }

    private fun test_render_contains(template: String, m: Map<String, String>, contained: String) {
        assertThat(render(template, m), containsString(contained))
        val legacyTemplate = TokenizerTest.new_to_legacy_template(template)
        assertThat(render(legacyTemplate, m), containsString(contained))
    }

    @Test
    @Config(qualifiers = "en")
    fun test_render() {
        val m: MutableMap<String, String> = HashMap()
        m["Test"] = "Test"
        m["Foo"] = "Foo"
        test_render("", m, "")
        test_render("Test", m, "Test")
        test_render("{{Test}}", m, "Test")
        test_render("{{Filter2:Filter1:Test}}", m, "Test")
        test_render("{{type:Test}}", m, "[[type:Test]]")
        test_render("{{Filter2:type:Test}}", m, "[[Filter2:type:Test]]")
        test_render("Foo{{Test}}", m, "FooTest")
        test_render_contains("Foo{{!Test}}", m, "there is no field called '!Test'")
        test_render("{{#Foo}}{{Test}}{{/Foo}}", m, "Test")
        test_render("{{^Foo}}{{Test}}{{/Foo}}", m, "")
        m["Foo"] = ""
        test_render("{{#Foo}}{{Test}}{{/Foo}}", m, "")
        test_render("{{^Foo}}{{Test}}{{/Foo}}", m, "Test")
        m["Foo"] = "   \t"
        test_render("{{#Foo}}{{Test}}{{/Foo}}", m, "")
        test_render("{{^Foo}}{{Test}}{{/Foo}}", m, "Test")
    }

    @Test
    @Config(qualifiers = "en")
    fun empty_field_name() {
        val m: MutableMap<String, String> = HashMap()
        // Empty field is not usually a valid field name and should be corrected.
        // However, if we have an empty field name in the collection, this test ensure
        // that it works as expected.
        // This is especially relevant because filter applied to no field is valid
        m["Test"] = "Test"
        m["Foo"] = "Foo"
        test_render_contains("{{}}", m, "there is no field called ''")
        test_render_contains("{{  }}", m, "there is no field called ''")
        test_render("{{filterName:}}", m, "")
        test_render("{{filterName:    }}", m, "")

        m[""] = "Test"
        test_render("{{}}", m, "Test")
        m.clear()
    }
}
