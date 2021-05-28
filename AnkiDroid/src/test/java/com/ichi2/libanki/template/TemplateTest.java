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

package com.ichi2.libanki.template;

import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricTest;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.template.Tokenizer.ALT_HANDLEBAR_DIRECTIVE;
import static com.ichi2.libanki.template.Tokenizer.new_to_legacy;
import static com.ichi2.libanki.template.TokenizerTest.new_to_legacy_template;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class TemplateTest extends RobolectricTest {
    private String render(String template, Map<String, String> fields) {
        return ParsedNode.parse_inner(template).render(fields, true, getTargetContext());
    }

    @Test
    public void fieldStartingWithExclamation() {
        // Ankidroid used not to display fields whose name start with !
        HashMap<String, String> context = new HashMap<>();
        context.put("!Front", "Test");
        assertThat(render("{{!Front}}", context), is("Test"));
    }
    @Test
    @Config(qualifiers = "en")
    public void missingExclamation() {
        // Ankidroid used not to display fields whose name start with !
        HashMap<String, String> context = new HashMap<>();
        String rendered = render("{{!Front}}", context);

        assertThat(rendered, is(notNullValue()));
        assertThat(rendered, containsString("there is no field called '!Front'"));
    }
    @Test
    public void typeInFieldRenders() {
        HashMap<String, String> context = new HashMap<>();
        context.put("Front", "AA{{type:Back}}");

        assertThat(render("{{Front}}", context), is("AA{{type:Back}}"));
    }

    @Test
    public void testNotFoundWillRender() {
        String maybeBad = "{{#NotFound}}{{NotFound}}{{/NotFound}}";

        HashMap<String, String> context = new HashMap<>();

        assertThat(render(maybeBad, context), Matchers.isEmptyString());
    }

    @Test
    @Config(qualifiers = "en")
    public void nestedTemplatesRenderWell() {
        //#6123
        String problematicTemplate = "{{#One}}\n" +
                "    {{#One}}\n" +
                "        {{One}}<br>\n" +
                "    {{/One}}\n" +
                "    {{#Two}}\n" +
                "        {{Two}}\n" +
                "    {{/Two}}\n" +
                "{{/One}}";

        HashMap<String, String> context = new HashMap<>();
        context.put("One", "Card1 - One");
        context.put("Two", "Card1 - Two");
        String result = render(problematicTemplate, context);

        //most important - that it does render
        assertThat(result, not("{{Invalid template}}"));
        //Actual value (may be subject to change).
        assertThat(result, is("\n    \n        Card1 - One<br>\n    \n    \n        Card1 - Two\n    \n"));
    }

    @Test
    @Ignore("GitHub: 6284")
    public void fieldNamesHaveTrailingSpacesIgnored() {
        //#6284
        String templateWithSpaces = "{{#IllustrationExample }}Illustration Example: {{IllustrationExample }}{{/IllustrationExample}}";

        HashMap<String, String> context = new HashMap<>();
        context.put("IllustrationExample", "ilex");
        test_render(templateWithSpaces, context, "Illustration Example: ilex");
    }



    private void test_render(@NonNull String template, @NonNull Map<String, String> m, @NonNull String expected) {
        assertThat(render(template, m), is(expected));
        String legacy_template = new_to_legacy_template(template);
        assertThat(render(legacy_template, m), is(expected));
    }

    private void test_render_contains(@NonNull String template, @NonNull Map<String, String> m, @NonNull String contained) {
        assertThat(render(template, m), containsString(contained));
        String legacy_template = new_to_legacy_template(template);
        assertThat(render(legacy_template, m), containsString(contained));
    }

    @Test
    @Config(qualifiers = "en")
    public void test_render() {
        Map<String, String> m = new HashMap<>();
        m.put("Test", "Test");
        m.put("Foo", "Foo");
        test_render("", m, "");
        test_render("Test", m, "Test");
        test_render("{{Test}}", m, "Test");
        test_render("{{Filter2:Filter1:Test}}", m, "Test");
        test_render("{{type:Test}}", m, "[[type:Test]]");
        test_render("{{Filter2:type:Test}}", m, "[[Filter2:type:Test]]");
        test_render("Foo{{Test}}", m, "FooTest");
        test_render_contains("Foo{{!Test}}", m, "there is no field called '!Test'");
        test_render("{{#Foo}}{{Test}}{{/Foo}}", m, "Test");
        test_render("{{^Foo}}{{Test}}{{/Foo}}", m, "");
        m.put("Foo", "");
        test_render("{{#Foo}}{{Test}}{{/Foo}}", m, "");
        test_render("{{^Foo}}{{Test}}{{/Foo}}", m, "Test");
        m.put("Foo", "   \t");
        test_render("{{#Foo}}{{Test}}{{/Foo}}", m, "");
        test_render("{{^Foo}}{{Test}}{{/Foo}}", m, "Test");
    }

    @Test
    @Config(qualifiers = "en")
    public void empty_field_name() {
        Map m = new HashMap();
        // Empty field is not usually a valid field name and should be corrected.
        // However, if we have an empty field name in the collection, this test ensure
        // that it works as expected.
        // This is especially relevant because filter applied to no field is valid
        m.put("Test", "Test");
        m.put("Foo", "Foo");
        test_render_contains("{{}}", m, "there is no field called ''");
        test_render_contains("{{  }}", m, "there is no field called ''");
        test_render("{{filterName:}}", m, "");
        test_render("{{filterName:    }}", m, "");

        m.put("", "Test");
        test_render("{{}}", m, "Test");
        m.clear();
    }
}
