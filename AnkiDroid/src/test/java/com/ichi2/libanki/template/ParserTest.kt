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
import com.ichi2.libanki.template.TokenizerTest.Companion.new_to_legacy_template
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
@KotlinCleanup("is -> equalTo")
@RunWith(AndroidJUnit4::class)
class ParserTest : RobolectricTest() {
    fun test_parsing(template: String, node: ParsedNode) {
        assertThat(ParsedNode.parse_inner(template), `is`(node))
        val legacy_template = new_to_legacy_template(template)
        assertThat(ParsedNode.parse_inner(legacy_template), `is`(node))
    }

    @Test
    fun test_parsing() {
        test_parsing("", EmptyNode())
        test_parsing("Test", Text("Test"))
        test_parsing("{{Test}}", Replacement("Test"))
        test_parsing("{{filter:Test}}", Replacement("Test", "filter"))
        test_parsing("{{filter:}}", Replacement("", "filter"))
        test_parsing("{{}}", Replacement(""))
        test_parsing("{{!Test}}", Replacement("!Test"))
        test_parsing("{{Filter2:Filter1:Test}}", Replacement("Test", "Filter1", "Filter2"))
        test_parsing(
            "Foo{{Test}}",
            ParsedNodes(
                Text("Foo"),
                Replacement("Test")
            )
        )
        test_parsing("{{#Foo}}{{Test}}{{/Foo}}", Conditional("Foo", Replacement("Test")))
        test_parsing("{{^Foo}}{{Test}}{{/Foo}}", NegatedConditional("Foo", Replacement("Test")))
        try {
            ParsedNode.parse_inner("{{foo")
            fail()
        } catch (ncb: TemplateError.NoClosingBrackets) {
        }
        try {
            ParsedNode.parse_inner("{{#foo}}")
            fail()
        } catch (ncb: TemplateError.ConditionalNotClosed) {
        }
        try {
            ParsedNode.parse_inner("{{/foo}}")
            fail()
        } catch (ncb: TemplateError.ConditionalNotOpen) {
        }
        try {
            ParsedNode.parse_inner("{{#bar}}{{/foo}}")
            fail()
        } catch (ncb: TemplateError.WrongConditionalClosed) {
        }
    }

    fun test_parsing_is_empty(template: String, vararg nonempty_fields: String) {
        assertThat(
            ParsedNode.parse_inner(template).template_is_empty(*nonempty_fields),
            `is`(true)
        )
    }

    fun test_parsing_is_non_empty(template: String, vararg nonempty_fields: String) {
        assertThat(
            ParsedNode.parse_inner(template).template_is_empty(*nonempty_fields),
            `is`(false)
        )
    }

    @Test
    fun test_emptiness() {
        /* In the comment below, I assume Testi is the field FOOi in position i*/

        // No field. Req was `("none", [], [])`
        test_parsing_is_empty("")

        // Single field.  Req was `("all", [0])`
        test_parsing_is_empty("{{Field0}}")
        test_parsing_is_empty("{{!Field0}}")
        test_parsing_is_empty("{{Field0}}", "Field1")
        test_parsing_is_non_empty("{{Field0}}", "Field0")
        test_parsing_is_empty("{{type:Field0}}")
        test_parsing_is_empty("{{Filter2:Filter1:Field0}}")
        test_parsing_is_non_empty("{{Filter2:Filter1:Field0}}", "Field0")
        test_parsing_is_empty("{{Filter2:Filter1:Field0}}", "Field1")
        test_parsing_is_empty("Foo{{Field0}}")
        test_parsing_is_non_empty("Foo{{Field0}}", "Field0")
        test_parsing_is_empty("Foo{{Field0}}", "Field1")

        // Two fields. Req was `("any", [0, 1])`
        val twoFields = "{{Field0}}{{Field1}}"
        test_parsing_is_empty(twoFields)
        test_parsing_is_non_empty(twoFields, "Field0")
        test_parsing_is_non_empty(twoFields, "Field1")
        test_parsing_is_non_empty(twoFields, "Field0", "Field1")

        // Two fields required, one shown, req used to be `("all", [0, 1])`
        val mandatoryAndField = "{{#Mandatory1}}{{Field0}}{{/Mandatory1}}"
        test_parsing_is_empty(mandatoryAndField)
        test_parsing_is_empty(mandatoryAndField, "Field0")
        test_parsing_is_empty(mandatoryAndField, "Mandatory1")
        test_parsing_is_non_empty(mandatoryAndField, "Field0", "Mandatory1")

        // Three required fields , req used to be`("all", [0, 1, 2])`
        val twoMandatoriesOneField =
            "{{#Mandatory2}}{{#Mandatory1}}{{Field0}}{{/Mandatory1}}{{/Mandatory2}}"
        test_parsing_is_empty(twoMandatoriesOneField)
        test_parsing_is_empty(twoMandatoriesOneField, "Field0")
        test_parsing_is_empty(twoMandatoriesOneField, "Mandatory1")
        test_parsing_is_empty(twoMandatoriesOneField, "Field0", "Mandatory1")
        test_parsing_is_empty(twoMandatoriesOneField, "Mandatory2")
        test_parsing_is_empty(twoMandatoriesOneField, "Field0", "Mandatory2")
        test_parsing_is_empty(twoMandatoriesOneField, "Mandatory1", "Mandatory2")
        test_parsing_is_non_empty(twoMandatoriesOneField, "Field0", "Mandatory1", "Mandatory2")

        // A mandatory field and one of two to display , req used to be`("all", [2])`
        val mandatoryAndTwoField = "{{#Mandatory2}}{{Field1}}{{Field0}}{{/Mandatory2}}"
        test_parsing_is_empty(mandatoryAndTwoField)
        test_parsing_is_empty(mandatoryAndTwoField, "Field0")
        test_parsing_is_empty(mandatoryAndTwoField, "Field1")
        test_parsing_is_empty(mandatoryAndTwoField, "Field0", "Field1")
        test_parsing_is_empty(
            mandatoryAndTwoField,
            "Mandatory2"
        ) // This one used to be false, because the only mandatory field was filled
        test_parsing_is_non_empty(mandatoryAndTwoField, "Field0", "Mandatory2")
        test_parsing_is_non_empty(mandatoryAndTwoField, "Field1", "Mandatory2")
        test_parsing_is_non_empty(mandatoryAndTwoField, "Field0", "Field1", "Mandatory2")

        // either first field, or two next one , req used to be`("any", [0])`
        val oneOrTwo = "{{#Condition2}}{{Field1}}{{/Condition2}}{{Field0}}"
        test_parsing_is_empty(oneOrTwo)
        test_parsing_is_non_empty(oneOrTwo, "Field0")
        test_parsing_is_empty(oneOrTwo, "Field1")
        test_parsing_is_non_empty(oneOrTwo, "Field0", "Field1")
        test_parsing_is_empty(oneOrTwo, "Condition2")
        test_parsing_is_non_empty(oneOrTwo, "Field0", "Condition2")
        test_parsing_is_non_empty(
            oneOrTwo,
            "Field1",
            "Condition2"
        ) // This one was broken, because the field Field0 was not filled, and the two other fields are not sufficient for generating alone
        test_parsing_is_non_empty(oneOrTwo, "Field0", "Field1", "Condition2")

        // One forbidden field. This means no card used to be filled. Requirement used to be  `("none", [], [])`
        val oneForbidden = "{{^Forbidden1}}{{Field0}}{{/Forbidden1}}"
        test_parsing_is_empty(oneForbidden)
        test_parsing_is_empty(oneForbidden, "Forbidden1")
        test_parsing_is_non_empty(oneForbidden, "Field0")
        test_parsing_is_empty(oneForbidden, "Forbidden1", "Field0")

        // One field, a useless one. Req used to be `("all", [0])`
        // Realistically, that can be used to display differently conditionally on useless1
        val oneUselessOneField =
            "{{^Useless1}}{{Field0}}{{/Useless1}}{{#Useless1}}{{Field0}}{{/Useless1}}"
        test_parsing_is_empty(oneUselessOneField)
        test_parsing_is_empty(oneUselessOneField, "Useless1")
        test_parsing_is_non_empty(oneUselessOneField, "Field0")
        test_parsing_is_non_empty(oneUselessOneField, "Useless1", "Field0")

        // Switch from shown field. Req used to be `("all", [2])`
        val switchField = "{{^Useless1}}{{Field0}}{{/Useless1}}{{#Useless1}}{{Field2}}{{/Useless1}}"
        test_parsing_is_empty(switchField)
        test_parsing_is_empty(switchField, "Useless1")
        test_parsing_is_non_empty(switchField, "Field0") // < 2.1.28 would return true by error
        test_parsing_is_empty(switchField, "Useless1", "Field0")
        test_parsing_is_empty(switchField, "Field2") // < 2.1.28 would return false by error
        test_parsing_is_non_empty(switchField, "Useless1", "Field2")
        test_parsing_is_non_empty(switchField, "Field0", "Field2")
        test_parsing_is_non_empty(switchField, "Useless1", "Field0", "Field2")
    }
}
