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

package com.ichi2.libanki.template;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.testutils.AnkiAssert;
import com.ichi2.utils.Assert;

import org.hamcrest.Matcher;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.template.TokenizerTest.new_to_legacy_template;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class ParserTest extends RobolectricTest {
    public void test_parsing(@NonNull String template, @NonNull ParsedNode node) {
        assertThat(ParsedNode.parse_inner(template), is(node));
        String legacy_template = new_to_legacy_template(template);
        assertThat(ParsedNode.parse_inner(legacy_template), is(node));
    }

    @Test
    public void test_parsing() {
        test_parsing("", new EmptyNode());
        test_parsing("Test", new Text("Test"));
        test_parsing("{{Test}}", new Replacement("Test"));
        test_parsing("{{filter:Test}}", new Replacement("Test", "filter"));
        test_parsing("{{filter:}}", new Replacement("", "filter"));
        test_parsing("{{}}", new Replacement(""));
        test_parsing("{{!Test}}", new Replacement("!Test"));
        test_parsing("{{Filter2:Filter1:Test}}", new Replacement("Test", "Filter1", "Filter2"));
        test_parsing("Foo{{Test}}",
                new ParsedNodes(new Text("Foo"),
                        new Replacement("Test")
                ));
        test_parsing("{{#Foo}}{{Test}}{{/Foo}}", new Conditional("Foo", new Replacement("Test")));
        test_parsing("{{^Foo}}{{Test}}{{/Foo}}", new NegatedConditional("Foo", new Replacement("Test")));
        try {
            ParsedNode.parse_inner("{{foo");
            fail();
        } catch (TemplateError.NoClosingBrackets ncb) {
        }
        try {
            ParsedNode.parse_inner("{{#foo}}");
            fail();
        } catch (TemplateError.ConditionalNotClosed ncb) {
        }
        try {
            ParsedNode.parse_inner("{{/foo}}");
            fail();
        } catch (TemplateError.ConditionalNotOpen ncb) {
        }
        try {
            ParsedNode.parse_inner("{{#bar}}{{/foo}}");
            fail();
        } catch (TemplateError.WrongConditionalClosed ncb) {
        }
    }

    public void test_parsing_is_empty(@NonNull String template, @NonNull String... nonempty_fields) {
        assertThat(ParsedNode.parse_inner(template).template_is_empty(nonempty_fields), is(true));
    }

    public void test_parsing_is_non_empty(@NonNull String template, @NonNull String... nonempty_fields) {
        assertThat(ParsedNode.parse_inner(template).template_is_empty(nonempty_fields), is(false));
    }

    @Test
    public void test_emptyness() {
        /* In the comment below, I assume Testi is the field FOOi in position i*/

        // No field. Req was `("none", [], [])`
        test_parsing_is_empty("");

        // Single field.  Req was `("all", [0])`
        test_parsing_is_empty("{{Field0}}");
        test_parsing_is_empty("{{!Field0}}");
        test_parsing_is_empty("{{Field0}}", "Field1");
        test_parsing_is_non_empty("{{Field0}}", "Field0");
        test_parsing_is_empty("{{type:Field0}}");
        test_parsing_is_empty("{{Filter2:Filter1:Field0}}");
        test_parsing_is_non_empty("{{Filter2:Filter1:Field0}}", "Field0");
        test_parsing_is_empty("{{Filter2:Filter1:Field0}}", "Field1");
        test_parsing_is_empty("Foo{{Field0}}");
        test_parsing_is_non_empty("Foo{{Field0}}", "Field0");
        test_parsing_is_empty("Foo{{Field0}}", "Field1");

        // Two fields. Req was `("any", [0, 1])`
        String twoFields = "{{Field0}}{{Field1}}";
        test_parsing_is_empty(twoFields);
        test_parsing_is_non_empty(twoFields, "Field0");
        test_parsing_is_non_empty(twoFields, "Field1");
        test_parsing_is_non_empty(twoFields, "Field0", "Field1");


        // Two fields required, one shown, req used to be `("all", [0, 1])`
        String mandatoryAndField = "{{#Mandatory1}}{{Field0}}{{/Mandatory1}}";
        test_parsing_is_empty(mandatoryAndField);
        test_parsing_is_empty(mandatoryAndField, "Field0");
        test_parsing_is_empty(mandatoryAndField, "Mandatory1");
        test_parsing_is_non_empty(mandatoryAndField, "Field0", "Mandatory1");

        // Three required fields , req used to be`("all", [0, 1, 2])`
        String twoMandatoriesOneField = "{{#Mandatory2}}{{#Mandatory1}}{{Field0}}{{/Mandatory1}}{{/Mandatory2}}";
        test_parsing_is_empty(twoMandatoriesOneField);
        test_parsing_is_empty(twoMandatoriesOneField, "Field0");
        test_parsing_is_empty(twoMandatoriesOneField, "Mandatory1");
        test_parsing_is_empty(twoMandatoriesOneField, "Field0", "Mandatory1");
        test_parsing_is_empty(twoMandatoriesOneField, "Mandatory2");
        test_parsing_is_empty(twoMandatoriesOneField, "Field0", "Mandatory2");
        test_parsing_is_empty(twoMandatoriesOneField, "Mandatory1", "Mandatory2");
        test_parsing_is_non_empty(twoMandatoriesOneField, "Field0", "Mandatory1", "Mandatory2");

        // A mandatory field and one of two to display , req used to be`("all", [2])`
        String mandatoryAndTwoField = "{{#Mandatory2}}{{Field1}}{{Field0}}{{/Mandatory2}}";
        test_parsing_is_empty(mandatoryAndTwoField);
        test_parsing_is_empty(mandatoryAndTwoField, "Field0");
        test_parsing_is_empty(mandatoryAndTwoField, "Field1");
        test_parsing_is_empty(mandatoryAndTwoField, "Field0", "Field1");
        test_parsing_is_empty(mandatoryAndTwoField, "Mandatory2"); // This one used to be false, because the only mandatory field was filled
        test_parsing_is_non_empty(mandatoryAndTwoField, "Field0", "Mandatory2");
        test_parsing_is_non_empty(mandatoryAndTwoField, "Field1", "Mandatory2");
        test_parsing_is_non_empty(mandatoryAndTwoField, "Field0", "Field1", "Mandatory2");

        // either first field, or two next one , req used to be`("any", [0])`
        String oneOrTwo = "{{#Condition2}}{{Field1}}{{/Condition2}}{{Field0}}";
        test_parsing_is_empty(oneOrTwo);
        test_parsing_is_non_empty(oneOrTwo, "Field0");
        test_parsing_is_empty(oneOrTwo, "Field1");
        test_parsing_is_non_empty(oneOrTwo, "Field0", "Field1");
        test_parsing_is_empty(oneOrTwo, "Condition2");
        test_parsing_is_non_empty(oneOrTwo, "Field0", "Condition2");
        test_parsing_is_non_empty(oneOrTwo, "Field1", "Condition2"); // This one was broken, because the field Field0 was not filled, and the two other fields are not sufficient for generating alone
        test_parsing_is_non_empty(oneOrTwo, "Field0", "Field1", "Condition2");

        // One forbidden field. This means no card used to be filled. Requirement used to be  `("none", [], [])`
        String oneForbidden = "{{^Forbidden1}}{{Field0}}{{/Forbidden1}}";
        test_parsing_is_empty(oneForbidden);
        test_parsing_is_empty(oneForbidden, "Forbidden1");
        test_parsing_is_non_empty(oneForbidden, "Field0");
        test_parsing_is_empty(oneForbidden, "Forbidden1", "Field0");

        // One field, a useless one. Req used to be `("all", [0])`
        // Realistically, that can be used to display differently conditionally on useless1
        String oneUselessOneField = "{{^Useless1}}{{Field0}}{{/Useless1}}{{#Useless1}}{{Field0}}{{/Useless1}}";
        test_parsing_is_empty(oneUselessOneField);
        test_parsing_is_empty(oneUselessOneField, "Useless1");
        test_parsing_is_non_empty(oneUselessOneField, "Field0");
        test_parsing_is_non_empty(oneUselessOneField, "Useless1", "Field0");

        // Switch from shown field. Req used to be `("all", [2])`
        String switchField = "{{^Useless1}}{{Field0}}{{/Useless1}}{{#Useless1}}{{Field2}}{{/Useless1}}";
        test_parsing_is_empty(switchField);
        test_parsing_is_empty(switchField, "Useless1");
        test_parsing_is_non_empty(switchField, "Field0"); // < 2.1.28 would return true by error
        test_parsing_is_empty(switchField, "Useless1", "Field0");
        test_parsing_is_empty(switchField, "Field2"); // < 2.1.28 would return false by error
        test_parsing_is_non_empty(switchField, "Useless1", "Field2");
        test_parsing_is_non_empty(switchField, "Field0", "Field2");
        test_parsing_is_non_empty(switchField, "Useless1", "Field0", "Field2");
    }
}
