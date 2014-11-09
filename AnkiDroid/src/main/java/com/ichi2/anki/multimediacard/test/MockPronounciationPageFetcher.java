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
 * This is a class used for tests actually
 */
public class MockPronounciationPageFetcher {

    public static String get() {
        return "        <param name=\"BGCOLOR\" value=\"#FFFFFF\" />"
                + "        <embed src=\"wimpy_button.swf?theFile=/speak-de/4/0/yNuwm9F0njM.mp3&autoplay=yes&loopMe=no\""
                + "        width=\"30\" height=\"30\" quality=\"high\" bgcolor=\"#FFFFFF\""
                + "        pluginspage=\"http://www.macromedia.com/go/getflashplayer\""
                + "        type=\"application/x-shockwave-flash\""
                + "        /></object>"
                + "       <strong></strong></td>"
                + "                   <td style=\"vertical-align: middle;\"><strong>Welt</strong></td>"
                + "                 </tr>"
                + "                 <tr class=\"air\"><td>&nbsp;</td><td><div>&nbsp;</div></td>"
                + "                 </tr>"
                + "                 <tr>"
                + "                   <td>&nbsp;</td>"
                + "                   <td><a href=\"/speak-de/4/0/yNuwm9F0njM.mp3\">Listen with your<br />default MP3 player</a></td>"
                + "                 </tr>" + "               </tbody>" + "             </table>" + "             </td>"
                + "           </tr>" + "           <tr>" + "             <td>&nbsp;</td>"
                + "             <td style=\"text-align:center";
    }
}
