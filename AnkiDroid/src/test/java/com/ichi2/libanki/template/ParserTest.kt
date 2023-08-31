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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ParserTest : RobolectricTest() {
    private fun testParsing(template: String, node: ParsedNode) {
        assertThat(ParsedNode.parse_inner(template), equalTo(node))
        val legacyTemplate = new_to_legacy_template(template)
        assertThat(ParsedNode.parse_inner(legacyTemplate), equalTo(node))
    }

    @Test
    fun testParsing() {
        testParsing("", EmptyNode())
        testParsing("Test", Text("Test"))
        testParsing("{{Test}}", Replacement("Test"))
        testParsing("{{filter:Test}}", Replacement("Test", "filter"))
        testParsing("{{filter:}}", Replacement("", "filter"))
        testParsing("{{}}", Replacement(""))
        testParsing("{{!Test}}", Replacement("!Test"))
        testParsing("{{Filter2:Filter1:Test}}", Replacement("Test", "Filter1", "Filter2"))
        testParsing(
            "Foo{{Test}}",
            ParsedNodes(
                Text("Foo"),
                Replacement("Test")
            )
        )
        testParsing("{{#Foo}}{{Test}}{{/Foo}}", Conditional("Foo", Replacement("Test")))
        testParsing("{{^Foo}}{{Test}}{{/Foo}}", NegatedConditional("Foo", Replacement("Test")))
        assertFailsWith<TemplateError.NoClosingBrackets> {
            ParsedNode.parse_inner("{{foo")
        }
        assertFailsWith<TemplateError.ConditionalNotClosed> {
            ParsedNode.parse_inner("{{#foo}}")
        }
        assertFailsWith<TemplateError.ConditionalNotOpen> {
            ParsedNode.parse_inner("{{/foo}}")
        }
        assertFailsWith<TemplateError.WrongConditionalClosed> {
            ParsedNode.parse_inner("{{#bar}}{{/foo}}")
        }
    }

    private fun testParsingIsEmpty(template: String, vararg nonempty_fields: String) {
        assertThat(
            ParsedNode.parse_inner(template).template_is_empty(*nonempty_fields),
            equalTo(true)
        )
    }

    private fun testParsingIsNonEmpty(template: String, vararg nonempty_fields: String) {
        assertThat(
            ParsedNode.parse_inner(template).template_is_empty(*nonempty_fields),
            equalTo(false)
        )
    }

    @Test
    fun test_emptiness() {
        /* In the comment below, I assume Testi is the field FOOi in position i*/

        // No field. Req was `("none", [], [])`
        testParsingIsEmpty("")

        // Single field.  Req was `("all", [0])`
        testParsingIsEmpty("{{Field0}}")
        testParsingIsEmpty("{{!Field0}}")
        testParsingIsEmpty("{{Field0}}", "Field1")
        testParsingIsNonEmpty("{{Field0}}", "Field0")
        testParsingIsEmpty("{{type:Field0}}")
        testParsingIsEmpty("{{Filter2:Filter1:Field0}}")
        testParsingIsNonEmpty("{{Filter2:Filter1:Field0}}", "Field0")
        testParsingIsEmpty("{{Filter2:Filter1:Field0}}", "Field1")
        testParsingIsEmpty("Foo{{Field0}}")
        testParsingIsNonEmpty("Foo{{Field0}}", "Field0")
        testParsingIsEmpty("Foo{{Field0}}", "Field1")

        // Two fields. Req was `("any", [0, 1])`
        val twoFields = "{{Field0}}{{Field1}}"
        testParsingIsEmpty(twoFields)
        testParsingIsNonEmpty(twoFields, "Field0")
        testParsingIsNonEmpty(twoFields, "Field1")
        testParsingIsNonEmpty(twoFields, "Field0", "Field1")

        // Two fields required, one shown, req used to be `("all", [0, 1])`
        val mandatoryAndField = "{{#Mandatory1}}{{Field0}}{{/Mandatory1}}"
        testParsingIsEmpty(mandatoryAndField)
        testParsingIsEmpty(mandatoryAndField, "Field0")
        testParsingIsEmpty(mandatoryAndField, "Mandatory1")
        testParsingIsNonEmpty(mandatoryAndField, "Field0", "Mandatory1")

        // Three required fields , req used to be`("all", [0, 1, 2])`
        val twoMandatoriesOneField =
            "{{#Mandatory2}}{{#Mandatory1}}{{Field0}}{{/Mandatory1}}{{/Mandatory2}}"
        testParsingIsEmpty(twoMandatoriesOneField)
        testParsingIsEmpty(twoMandatoriesOneField, "Field0")
        testParsingIsEmpty(twoMandatoriesOneField, "Mandatory1")
        testParsingIsEmpty(twoMandatoriesOneField, "Field0", "Mandatory1")
        testParsingIsEmpty(twoMandatoriesOneField, "Mandatory2")
        testParsingIsEmpty(twoMandatoriesOneField, "Field0", "Mandatory2")
        testParsingIsEmpty(twoMandatoriesOneField, "Mandatory1", "Mandatory2")
        testParsingIsNonEmpty(twoMandatoriesOneField, "Field0", "Mandatory1", "Mandatory2")

        // A mandatory field and one of two to display , req used to be`("all", [2])`
        val mandatoryAndTwoField = "{{#Mandatory2}}{{Field1}}{{Field0}}{{/Mandatory2}}"
        testParsingIsEmpty(mandatoryAndTwoField)
        testParsingIsEmpty(mandatoryAndTwoField, "Field0")
        testParsingIsEmpty(mandatoryAndTwoField, "Field1")
        testParsingIsEmpty(mandatoryAndTwoField, "Field0", "Field1")
        testParsingIsEmpty(
            mandatoryAndTwoField,
            "Mandatory2"
        ) // This one used to be false, because the only mandatory field was filled
        testParsingIsNonEmpty(mandatoryAndTwoField, "Field0", "Mandatory2")
        testParsingIsNonEmpty(mandatoryAndTwoField, "Field1", "Mandatory2")
        testParsingIsNonEmpty(mandatoryAndTwoField, "Field0", "Field1", "Mandatory2")

        // either first field, or two next one , req used to be`("any", [0])`
        val oneOrTwo = "{{#Condition2}}{{Field1}}{{/Condition2}}{{Field0}}"
        testParsingIsEmpty(oneOrTwo)
        testParsingIsNonEmpty(oneOrTwo, "Field0")
        testParsingIsEmpty(oneOrTwo, "Field1")
        testParsingIsNonEmpty(oneOrTwo, "Field0", "Field1")
        testParsingIsEmpty(oneOrTwo, "Condition2")
        testParsingIsNonEmpty(oneOrTwo, "Field0", "Condition2")
        testParsingIsNonEmpty(
            oneOrTwo,
            "Field1",
            "Condition2"
        ) // This one was broken, because the field Field0 was not filled, and the two other fields are not sufficient for generating alone
        testParsingIsNonEmpty(oneOrTwo, "Field0", "Field1", "Condition2")

        // One forbidden field. This means no card used to be filled. Requirement used to be  `("none", [], [])`
        val oneForbidden = "{{^Forbidden1}}{{Field0}}{{/Forbidden1}}"
        testParsingIsEmpty(oneForbidden)
        testParsingIsEmpty(oneForbidden, "Forbidden1")
        testParsingIsNonEmpty(oneForbidden, "Field0")
        testParsingIsEmpty(oneForbidden, "Forbidden1", "Field0")

        // One field, a useless one. Req used to be `("all", [0])`
        // Realistically, that can be used to display differently conditionally on useless1
        val oneUselessOneField =
            "{{^Useless1}}{{Field0}}{{/Useless1}}{{#Useless1}}{{Field0}}{{/Useless1}}"
        testParsingIsEmpty(oneUselessOneField)
        testParsingIsEmpty(oneUselessOneField, "Useless1")
        testParsingIsNonEmpty(oneUselessOneField, "Field0")
        testParsingIsNonEmpty(oneUselessOneField, "Useless1", "Field0")

        // Switch from shown field. Req used to be `("all", [2])`
        val switchField = "{{^Useless1}}{{Field0}}{{/Useless1}}{{#Useless1}}{{Field2}}{{/Useless1}}"
        testParsingIsEmpty(switchField)
        testParsingIsEmpty(switchField, "Useless1")
        testParsingIsNonEmpty(switchField, "Field0") // < 2.1.28 would return true by error
        testParsingIsEmpty(switchField, "Useless1", "Field0")
        testParsingIsEmpty(switchField, "Field2") // < 2.1.28 would return false by error
        testParsingIsNonEmpty(switchField, "Useless1", "Field2")
        testParsingIsNonEmpty(switchField, "Field0", "Field2")
        testParsingIsNonEmpty(switchField, "Useless1", "Field0", "Field2")
    }
}
