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

import com.ichi2.anki.RobolectricTest;

import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
public class TemplateTest extends RobolectricTest {

    @Test
    public void typeInFieldRenders() {
        HashMap<String, String> context = new HashMap<>();
        context.put("Front", "AA{{type:Back}}");
        Template t = new Template("{{Front}}", context);

        String rendered = t.render();

        assertThat(rendered, is("AA[[type:Back]]"));
    }

    @Test
    public void testNotFoundWillRender() {
        String maybeBad = "{{#NotFound}}{{NotFound}}{{/NotFound}}";

        HashMap<String, String> context = new HashMap<>();

        Template template = new Template(maybeBad, context);
        String result = template.render();

        assertThat(result, Matchers.isEmptyString());
    }

    @Test
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
        Template template = new Template(problematicTemplate, context);

        String result = template.render();

        //most important - that it does render
        assertThat(result, not("{{invalid template}}"));
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
        Template template = new Template(templateWithSpaces, context);

        String result = template.render();

        assertThat(result, is("Illustration Example: ilex"));
    }
}
