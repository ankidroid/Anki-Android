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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class ParserTest extends RobolectricTest {
    @Test
    public void parsing() {
        assertThat(ParsedNode.parse_inner(""), is(new EmptyNode()));
        assertThat(ParsedNode.parse_inner("Test"), is(new Text("Test")));
        assertThat(ParsedNode.parse_inner("{{Test}}"), is(new Replacement("Test")));
        assertThat(ParsedNode.parse_inner("{{filter:Test}}"), is(new Replacement("Test", "filter")));
        assertThat(ParsedNode.parse_inner("{{filter:}}"), is(new Replacement("", "filter")));
        assertThat(ParsedNode.parse_inner("{{}}"), is(new Replacement("")));
        assertThat(ParsedNode.parse_inner("{{!Test}}"), is(new Replacement("!Test")));
        assertThat(ParsedNode.parse_inner("{{Filter2:Filter1:Test}}"), is(new Replacement("Test", "Filter1", "Filter2")));
        assertThat(ParsedNode.parse_inner("Foo{{Test}}"), is(
                new ParsedNodes(new Text("Foo"),
                        new Replacement("Test")
                )));
        assertThat(ParsedNode.parse_inner("{{#Foo}}{{Test}}{{/Foo}}"), is(new Conditional("Foo", new Replacement("Test"))));
        assertThat(ParsedNode.parse_inner("{{^Foo}}{{Test}}{{/Foo}}"), is(new NegatedConditional("Foo", new Replacement("Test"))));
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

    @Test
    public void emptyness() {
        /* In the comment below, I assume Testi is the field FOOi in position i*/

        // No field. Req was `("none", [], [])`
        assertThat(ParsedNode.parse_inner("").template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner("Test0").template_is_empty(),
                is(true));

        // Single field.  Req was `("all", [0])`
        assertThat(ParsedNode.parse_inner("{{Field0}}").template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner("{{!Field0}}").template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner("{{Field0}}").template_is_empty("Field1"),
                is(true));
        assertThat(ParsedNode.parse_inner("{{Field0}}").template_is_empty("Field0"),
                is(false));
        assertThat(ParsedNode.parse_inner("{{type:Field0}}").template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner("{{Filter2:Filter1:Field0}}").template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner("{{Filter2:Filter1:Field0}}").template_is_empty("Field0"),
                is(false));
        assertThat(ParsedNode.parse_inner("{{Filter2:Filter1:Field0}}").template_is_empty("Field1"),
                is(true));
        assertThat(ParsedNode.parse_inner("Foo{{Field0}}").template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner("Foo{{Field0}}").template_is_empty("Field0"),
                is(false));
        assertThat(ParsedNode.parse_inner("Foo{{Field0}}").template_is_empty("Field1"),
                is(true));

        // Two fields. Req was `("any", [0, 1])`
        String twoFields = "{{Field0}}{{Field1}}";
        assertThat(ParsedNode.parse_inner(twoFields).template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner(twoFields).template_is_empty("Field0"),
                is(false));
        assertThat(ParsedNode.parse_inner(twoFields).template_is_empty("Field1"),
                is(false));
        assertThat(ParsedNode.parse_inner(twoFields).template_is_empty("Field0", "Field1"),
                is(false));


        // Two fields required, one shown, req used to be `("all", [0, 1])`
        String mandatoryAndField = "{{#Mandatory1}}{{Field0}}{{/Mandatory1}}";
        assertThat(ParsedNode.parse_inner(mandatoryAndField).template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner(mandatoryAndField).template_is_empty("Field0"),
                is(true));
        assertThat(ParsedNode.parse_inner(mandatoryAndField).template_is_empty("Mandatory1"),
                is(true));
        assertThat(ParsedNode.parse_inner(mandatoryAndField).template_is_empty("Field0", "Mandatory1"),
                is(false));

        // Three required fields , req used to be`("all", [0, 1, 2])`
        String twoMandatoriesOneField = "{{#Mandatory2}}{{#Mandatory1}}{{Field0}}{{/Mandatory1}}{{/Mandatory2}}";
        assertThat(ParsedNode.parse_inner(twoMandatoriesOneField).template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner(twoMandatoriesOneField).template_is_empty("Field0"),
                is(true));
        assertThat(ParsedNode.parse_inner(twoMandatoriesOneField).template_is_empty("Mandatory1"),
                is(true));
        assertThat(ParsedNode.parse_inner(twoMandatoriesOneField).template_is_empty("Field0", "Mandatory1"),
                is(true));
        assertThat(ParsedNode.parse_inner(twoMandatoriesOneField).template_is_empty("Mandatory2"),
                is(true));
        assertThat(ParsedNode.parse_inner(twoMandatoriesOneField).template_is_empty("Field0", "Mandatory2"),
                is(true));
        assertThat(ParsedNode.parse_inner(twoMandatoriesOneField).template_is_empty("Mandatory1", "Mandatory2"),
                is(true));
        assertThat(ParsedNode.parse_inner(twoMandatoriesOneField).template_is_empty("Field0", "Mandatory1", "Mandatory2"),
                is(false));

        // A mandatory field and one of two to display , req used to be`("all", [2])`
        String mandatoryAndTwoField = "{{#Mandatory2}}{{Field1}}{{Field0}}{{/Mandatory2}}";
        assertThat(ParsedNode.parse_inner(mandatoryAndTwoField).template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner(mandatoryAndTwoField).template_is_empty("Field0"),
                is(true));
        assertThat(ParsedNode.parse_inner(mandatoryAndTwoField).template_is_empty("Field1"),
                is(true));
        assertThat(ParsedNode.parse_inner(mandatoryAndTwoField).template_is_empty("Field0", "Field1"),
                is(true));
        assertThat(ParsedNode.parse_inner(mandatoryAndTwoField).template_is_empty("Mandatory2"),
                is(true)); // This one used to be false, because the only mandatory field was filled
        assertThat(ParsedNode.parse_inner(mandatoryAndTwoField).template_is_empty("Field0", "Mandatory2"),
                is(false));
        assertThat(ParsedNode.parse_inner(mandatoryAndTwoField).template_is_empty("Field1", "Mandatory2"),
                is(false));
        assertThat(ParsedNode.parse_inner(mandatoryAndTwoField).template_is_empty("Field0", "Field1", "Mandatory2"),
                is(false));

        // either first field, or two next one , req used to be`("any", [0])`
        String oneOrTwo = "{{#Condition2}}{{Field1}}{{/Condition2}}{{Field0}}";
        assertThat(ParsedNode.parse_inner(oneOrTwo).template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner(oneOrTwo).template_is_empty("Field0"),
                is(false));
        assertThat(ParsedNode.parse_inner(oneOrTwo).template_is_empty("Field1"),
                is(true));
        assertThat(ParsedNode.parse_inner(oneOrTwo).template_is_empty("Field0", "Field1"),
                is(false));
        assertThat(ParsedNode.parse_inner(oneOrTwo).template_is_empty("Condition2"),
                is(true));
        assertThat(ParsedNode.parse_inner(oneOrTwo).template_is_empty("Field0", "Condition2"),
                is(false));
        assertThat(ParsedNode.parse_inner(oneOrTwo).template_is_empty("Field1", "Condition2"),
                is(false)); // This one was broken, because the field Field0 was not filled, and the two other fields are not sufficient for generating alone
        assertThat(ParsedNode.parse_inner(oneOrTwo).template_is_empty("Field0", "Field1", "Condition2"),
                is(false));

        // One forbidden field. This means no card used to be filled. Requirement used to be  `("none", [], [])`
        String oneForbidden = "{{^Forbidden1}}{{Field0}}{{/Forbidden1}}";
        assertThat(ParsedNode.parse_inner(oneForbidden).template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner(oneForbidden).template_is_empty("Forbidden1"),
                is(true));
        assertThat(ParsedNode.parse_inner(oneForbidden).template_is_empty("Field0"),
                is(false));
        assertThat(ParsedNode.parse_inner(oneForbidden).template_is_empty("Forbidden1", "Field0"),
                is(true));

        // One field, a useless one. Req used to be `("all", [0])`
        // Realistically, that can be used to display differently conditionally on useless1
        String oneUselessOneField = "{{^Useless1}}{{Field0}}{{/Useless1}}{{#Useless1}}{{Field0}}{{/Useless1}}";
        assertThat(ParsedNode.parse_inner(oneUselessOneField).template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner(oneUselessOneField).template_is_empty("Useless1"),
                is(true));
        assertThat(ParsedNode.parse_inner(oneUselessOneField).template_is_empty("Field0"),
                is(false));
        assertThat(ParsedNode.parse_inner(oneUselessOneField).template_is_empty("Useless1", "Field0"),
                is(false));

        // Switch from shown field. Req used to be `("all", [2])`
        String switchField = "{{^Useless1}}{{Field0}}{{/Useless1}}{{#Useless1}}{{Field2}}{{/Useless1}}";
        assertThat(ParsedNode.parse_inner(switchField).template_is_empty(),
                is(true));
        assertThat(ParsedNode.parse_inner(switchField).template_is_empty("Useless1"),
                is(true));
        assertThat(ParsedNode.parse_inner(switchField).template_is_empty("Field0"),
                is(false)); // < 2.1.28 would return true by error
        assertThat(ParsedNode.parse_inner(switchField).template_is_empty("Useless1", "Field0"),
                is(true));
        assertThat(ParsedNode.parse_inner(switchField).template_is_empty("Field2"),
                is(true)); // < 2.1.28 would return false by error
        assertThat(ParsedNode.parse_inner(switchField).template_is_empty("Useless1", "Field2"),
                is(false));
        assertThat(ParsedNode.parse_inner(switchField).template_is_empty("Field0", "Field2"),
                is(false));
        assertThat(ParsedNode.parse_inner(switchField).template_is_empty("Useless1", "Field0", "Field2"),
                is(false));
    }
}
