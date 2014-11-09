/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
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

package com.ichi2.anki.multimediacard.test;

/**
 * class for tests
 */
public class MockTranslationFetcher {
    public static String get() {
        return

        "<td class=\"r\"><a href=\"/deutsch-englisch/Welt.html\" onclick=\"return m('Welt',this,'l1');\" ondblclick=\"return d(this);\"><b>Welt</b></a> <span title=\"noun, feminine (die)\">{f}</span>   <a href=\"/dings.cgi?speak=de/4/0/yNuwm9F0njM;text=Welt\" onclick=\"return s(this)\" onmouseover=\"return u('Welt')\"><img src=\"/pics/s1.png\" width=\"16\" height=\"16\" alt=\"[listen]\" title=\"Welt\" border=\"0\" align=\"top\" /></a></td>"
                + "                <td class=\"r\"><a href=\"/english-german/world.html\" onclick=\"return m('world',this,'l2');\" ondblclick=\"return d(this);\">world</a>   <a href=\"/dings.cgi?speak=en/8/5/emUMgGzviSg;text=world\" onclick=\"return s(this)\" onmouseover=\"return u('world')\"><img src=\"/pics/s1.png\" width=\"16\" height=\"16\" alt=\"[listen]\" title=\"world\" border=\"0\" align=\"top\" /></a></td><td><a href=\"#\" onclick=\"return vi('Welt {f}','world','Welt','de','en',this);\"><img src=\"/pics/v.png\" /></a></td>"
                + ""
                + "                </tr>"
                + "                </tbody>"
                + "                <tbody id=\"b1\" class=\"n\">"
                + "                <tr class=\"s1 c\">"
                + "                <td align=\"right\"><br /></td>"
                + "                <td class=\"f\"> <a href=\"/deutsch-englisch/Welten.html\" onclick=\"return m('Welten',this,'l1');\" ondblclick=\"return d(this);\"><b>Welt</b>en</a> <span title=\"noun, plural (die)\">{pl}</span> </td>"
                + "                <td class=\"f\"> <a href=\"/english-german/worlds.html\" onclick=\"return m('worlds',this,'l2');\" ondblclick=\"return d(this);\">worlds</a> </td><td><a href=\"#\" onclick=\"return vi('Welten {pl}','worlds','Welt','de','en',this);\"><img src=\"/pics/v.png\" /></a></td>"
                + "" + "                </tr>";

    }
}
