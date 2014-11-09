/****************************************************************************************
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.utils;

import android.test.AndroidTestCase;

/**
 * Unit tests for {@link HtmlUtil}.
 */
public class HtmlUtilTest extends AndroidTestCase {

    public void testUnescape_EntityName() {
        assertEquals("\"", HtmlUtil.unescape("&quot;"));
        assertEquals("&", HtmlUtil.unescape("&amp;"));
        assertEquals("<", HtmlUtil.unescape("&lt;"));
        assertEquals(">", HtmlUtil.unescape("&gt;"));
        assertEquals("ë", HtmlUtil.unescape("&euml;"));
        assertEquals("\u039B", HtmlUtil.unescape("&Lambda;"));
    }


    public void testUnescape_Code() {
        assertEquals("&", HtmlUtil.unescape("&#38;"));
        assertEquals("<", HtmlUtil.unescape("&#60;"));
        assertEquals(">", HtmlUtil.unescape("&#62;"));
        assertEquals("A", HtmlUtil.unescape("&#65;"));
        assertEquals("ë", HtmlUtil.unescape("&#235;"));
        assertEquals("\u039B", HtmlUtil.unescape("&#923;"));
    }


    public void testUnescape_XCode() {
        assertEquals("&", HtmlUtil.unescape("&#x26;"));
        assertEquals("<", HtmlUtil.unescape("&#x3c;"));  // Lower case.
        assertEquals(">", HtmlUtil.unescape("&#x3E;"));  // Upper case.
        assertEquals("A", HtmlUtil.unescape("&#x41;"));
        assertEquals("ë", HtmlUtil.unescape("&#xeB;"));  // Mixed case.
        assertEquals("\u039B", HtmlUtil.unescape("&#x039B;"));
    }


    public void testUnescape_MultipleSequences() {
        assertEquals("Запомните все! Mémoriser quoi que ce soit!背诵的东西！",
                HtmlUtil.unescape("&#x0417;&#x0430;&#x043f;&#x043e;&#x043c;&#x043d;&#x0438;&#x0442;&#x0435; "
                        + "&#x0432;&#x0441;&#x0435;! M&eacute;moriser quoi que ce soit!&#x80cc;&#x8bf5;&#x7684;"
                        + "&#x4e1c;&#x897f;&#xff01;"));
    }


    public void testUnescape_WithTags() {
        assertEquals("ë", HtmlUtil.unescape("<b>&#235;</b>"));
        assertEquals("ë", HtmlUtil.unescape("<a href='http://www.example.com/'>&#235;</a>"));
    }

}
