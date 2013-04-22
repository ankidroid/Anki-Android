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

package com.ichi2.anki.htmlutils;

import android.annotation.SuppressLint;

import java.util.HashMap;
import java.util.Map;

// DO NOT SUBMIT
// From google gdata 
// Please, make sure the license allows to use this code, otherwise use and distribute it on your own risk!!
// None of the committers is responsible for this distribution.
// As of time of the commit the code below was in the public domain, publicly available on the web,
// Apache license 2.0

/**
 * This utility is needed to convert from HTML escape sequences into normal text.
 */
@SuppressLint("UseValueOf")
public class Unescaper {
    static Map<String, Character> escapeStrings;

    static {
        // HTML character entity references as defined in HTML 4
        // see http://www.w3.org/TR/REC-html40/sgml/entities.html
        escapeStrings = new HashMap<String, Character>(252);

        escapeStrings.put("&nbsp;", new Character('\u00A0'));
        escapeStrings.put("&iexcl;", new Character('\u00A1'));
        escapeStrings.put("&cent;", new Character('\u00A2'));
        escapeStrings.put("&pound;", new Character('\u00A3'));
        escapeStrings.put("&curren;", new Character('\u00A4'));
        escapeStrings.put("&yen;", new Character('\u00A5'));
        escapeStrings.put("&brvbar;", new Character('\u00A6'));
        escapeStrings.put("&sect;", new Character('\u00A7'));
        escapeStrings.put("&uml;", new Character('\u00A8'));
        escapeStrings.put("&copy;", new Character('\u00A9'));
        escapeStrings.put("&ordf;", new Character('\u00AA'));
        escapeStrings.put("&laquo;", new Character('\u00AB'));
        escapeStrings.put("&not;", new Character('\u00AC'));
        escapeStrings.put("&shy;", new Character('\u00AD'));
        escapeStrings.put("&reg;", new Character('\u00AE'));
        escapeStrings.put("&macr;", new Character('\u00AF'));
        escapeStrings.put("&deg;", new Character('\u00B0'));
        escapeStrings.put("&plusmn;", new Character('\u00B1'));
        escapeStrings.put("&sup2;", new Character('\u00B2'));
        escapeStrings.put("&sup3;", new Character('\u00B3'));
        escapeStrings.put("&acute;", new Character('\u00B4'));
        escapeStrings.put("&micro;", new Character('\u00B5'));
        escapeStrings.put("&para;", new Character('\u00B6'));
        escapeStrings.put("&middot;", new Character('\u00B7'));
        escapeStrings.put("&cedil;", new Character('\u00B8'));
        escapeStrings.put("&sup1;", new Character('\u00B9'));
        escapeStrings.put("&ordm;", new Character('\u00BA'));
        escapeStrings.put("&raquo;", new Character('\u00BB'));
        escapeStrings.put("&frac14;", new Character('\u00BC'));
        escapeStrings.put("&frac12;", new Character('\u00BD'));
        escapeStrings.put("&frac34;", new Character('\u00BE'));
        escapeStrings.put("&iquest;", new Character('\u00BF'));
        escapeStrings.put("&Agrave;", new Character('\u00C0'));
        escapeStrings.put("&Aacute;", new Character('\u00C1'));
        escapeStrings.put("&Acirc;", new Character('\u00C2'));
        escapeStrings.put("&Atilde;", new Character('\u00C3'));
        escapeStrings.put("&Auml;", new Character('\u00C4'));
        escapeStrings.put("&Aring;", new Character('\u00C5'));
        escapeStrings.put("&AElig;", new Character('\u00C6'));
        escapeStrings.put("&Ccedil;", new Character('\u00C7'));
        escapeStrings.put("&Egrave;", new Character('\u00C8'));
        escapeStrings.put("&Eacute;", new Character('\u00C9'));
        escapeStrings.put("&Ecirc;", new Character('\u00CA'));
        escapeStrings.put("&Euml;", new Character('\u00CB'));
        escapeStrings.put("&Igrave;", new Character('\u00CC'));
        escapeStrings.put("&Iacute;", new Character('\u00CD'));
        escapeStrings.put("&Icirc;", new Character('\u00CE'));
        escapeStrings.put("&Iuml;", new Character('\u00CF'));
        escapeStrings.put("&ETH;", new Character('\u00D0'));
        escapeStrings.put("&Ntilde;", new Character('\u00D1'));
        escapeStrings.put("&Ograve;", new Character('\u00D2'));
        escapeStrings.put("&Oacute;", new Character('\u00D3'));
        escapeStrings.put("&Ocirc;", new Character('\u00D4'));
        escapeStrings.put("&Otilde;", new Character('\u00D5'));
        escapeStrings.put("&Ouml;", new Character('\u00D6'));
        escapeStrings.put("&times;", new Character('\u00D7'));
        escapeStrings.put("&Oslash;", new Character('\u00D8'));
        escapeStrings.put("&Ugrave;", new Character('\u00D9'));
        escapeStrings.put("&Uacute;", new Character('\u00DA'));
        escapeStrings.put("&Ucirc;", new Character('\u00DB'));
        escapeStrings.put("&Uuml;", new Character('\u00DC'));
        escapeStrings.put("&Yacute;", new Character('\u00DD'));
        escapeStrings.put("&THORN;", new Character('\u00DE'));
        escapeStrings.put("&szlig;", new Character('\u00DF'));
        escapeStrings.put("&agrave;", new Character('\u00E0'));
        escapeStrings.put("&aacute;", new Character('\u00E1'));
        escapeStrings.put("&acirc;", new Character('\u00E2'));
        escapeStrings.put("&atilde;", new Character('\u00E3'));
        escapeStrings.put("&auml;", new Character('\u00E4'));
        escapeStrings.put("&aring;", new Character('\u00E5'));
        escapeStrings.put("&aelig;", new Character('\u00E6'));
        escapeStrings.put("&ccedil;", new Character('\u00E7'));
        escapeStrings.put("&egrave;", new Character('\u00E8'));
        escapeStrings.put("&eacute;", new Character('\u00E9'));
        escapeStrings.put("&ecirc;", new Character('\u00EA'));
        escapeStrings.put("&euml;", new Character('\u00EB'));
        escapeStrings.put("&igrave;", new Character('\u00EC'));
        escapeStrings.put("&iacute;", new Character('\u00ED'));
        escapeStrings.put("&icirc;", new Character('\u00EE'));
        escapeStrings.put("&iuml;", new Character('\u00EF'));
        escapeStrings.put("&eth;", new Character('\u00F0'));
        escapeStrings.put("&ntilde;", new Character('\u00F1'));
        escapeStrings.put("&ograve;", new Character('\u00F2'));
        escapeStrings.put("&oacute;", new Character('\u00F3'));
        escapeStrings.put("&ocirc;", new Character('\u00F4'));
        escapeStrings.put("&otilde;", new Character('\u00F5'));
        escapeStrings.put("&ouml;", new Character('\u00F6'));
        escapeStrings.put("&divide;", new Character('\u00F7'));
        escapeStrings.put("&oslash;", new Character('\u00F8'));
        escapeStrings.put("&ugrave;", new Character('\u00F9'));
        escapeStrings.put("&uacute;", new Character('\u00FA'));
        escapeStrings.put("&ucirc;", new Character('\u00FB'));
        escapeStrings.put("&uuml;", new Character('\u00FC'));
        escapeStrings.put("&yacute;", new Character('\u00FD'));
        escapeStrings.put("&thorn;", new Character('\u00FE'));
        escapeStrings.put("&yuml;", new Character('\u00FF'));
        escapeStrings.put("&fnof;", new Character('\u0192'));
        escapeStrings.put("&Alpha;", new Character('\u0391'));
        escapeStrings.put("&Beta;", new Character('\u0392'));
        escapeStrings.put("&Gamma;", new Character('\u0393'));
        escapeStrings.put("&Delta;", new Character('\u0394'));
        escapeStrings.put("&Epsilon;", new Character('\u0395'));
        escapeStrings.put("&Zeta;", new Character('\u0396'));
        escapeStrings.put("&Eta;", new Character('\u0397'));
        escapeStrings.put("&Theta;", new Character('\u0398'));
        escapeStrings.put("&Iota;", new Character('\u0399'));
        escapeStrings.put("&Kappa;", new Character('\u039A'));
        escapeStrings.put("&Lambda;", new Character('\u039B'));
        escapeStrings.put("&Mu;", new Character('\u039C'));
        escapeStrings.put("&Nu;", new Character('\u039D'));
        escapeStrings.put("&Xi;", new Character('\u039E'));
        escapeStrings.put("&Omicron;", new Character('\u039F'));
        escapeStrings.put("&Pi;", new Character('\u03A0'));
        escapeStrings.put("&Rho;", new Character('\u03A1'));
        escapeStrings.put("&Sigma;", new Character('\u03A3'));
        escapeStrings.put("&Tau;", new Character('\u03A4'));
        escapeStrings.put("&Upsilon;", new Character('\u03A5'));
        escapeStrings.put("&Phi;", new Character('\u03A6'));
        escapeStrings.put("&Chi;", new Character('\u03A7'));
        escapeStrings.put("&Psi;", new Character('\u03A8'));
        escapeStrings.put("&Omega;", new Character('\u03A9'));
        escapeStrings.put("&alpha;", new Character('\u03B1'));
        escapeStrings.put("&beta;", new Character('\u03B2'));
        escapeStrings.put("&gamma;", new Character('\u03B3'));
        escapeStrings.put("&delta;", new Character('\u03B4'));
        escapeStrings.put("&epsilon;", new Character('\u03B5'));
        escapeStrings.put("&zeta;", new Character('\u03B6'));
        escapeStrings.put("&eta;", new Character('\u03B7'));
        escapeStrings.put("&theta;", new Character('\u03B8'));
        escapeStrings.put("&iota;", new Character('\u03B9'));
        escapeStrings.put("&kappa;", new Character('\u03BA'));
        escapeStrings.put("&lambda;", new Character('\u03BB'));
        escapeStrings.put("&mu;", new Character('\u03BC'));
        escapeStrings.put("&nu;", new Character('\u03BD'));
        escapeStrings.put("&xi;", new Character('\u03BE'));
        escapeStrings.put("&omicron;", new Character('\u03BF'));
        escapeStrings.put("&pi;", new Character('\u03C0'));
        escapeStrings.put("&rho;", new Character('\u03C1'));
        escapeStrings.put("&sigmaf;", new Character('\u03C2'));
        escapeStrings.put("&sigma;", new Character('\u03C3'));
        escapeStrings.put("&tau;", new Character('\u03C4'));
        escapeStrings.put("&upsilon;", new Character('\u03C5'));
        escapeStrings.put("&phi;", new Character('\u03C6'));
        escapeStrings.put("&chi;", new Character('\u03C7'));
        escapeStrings.put("&psi;", new Character('\u03C8'));
        escapeStrings.put("&omega;", new Character('\u03C9'));
        escapeStrings.put("&thetasym;", new Character('\u03D1'));
        escapeStrings.put("&upsih;", new Character('\u03D2'));
        escapeStrings.put("&piv;", new Character('\u03D6'));
        escapeStrings.put("&bull;", new Character('\u2022'));
        escapeStrings.put("&hellip;", new Character('\u2026'));
        escapeStrings.put("&prime;", new Character('\u2032'));
        escapeStrings.put("&Prime;", new Character('\u2033'));
        escapeStrings.put("&oline;", new Character('\u203E'));
        escapeStrings.put("&frasl;", new Character('\u2044'));
        escapeStrings.put("&weierp;", new Character('\u2118'));
        escapeStrings.put("&image;", new Character('\u2111'));
        escapeStrings.put("&real;", new Character('\u211C'));
        escapeStrings.put("&trade;", new Character('\u2122'));
        escapeStrings.put("&alefsym;", new Character('\u2135'));
        escapeStrings.put("&larr;", new Character('\u2190'));
        escapeStrings.put("&uarr;", new Character('\u2191'));
        escapeStrings.put("&rarr;", new Character('\u2192'));
        escapeStrings.put("&darr;", new Character('\u2193'));
        escapeStrings.put("&harr;", new Character('\u2194'));
        escapeStrings.put("&crarr;", new Character('\u21B5'));
        escapeStrings.put("&lArr;", new Character('\u21D0'));
        escapeStrings.put("&uArr;", new Character('\u21D1'));
        escapeStrings.put("&rArr;", new Character('\u21D2'));
        escapeStrings.put("&dArr;", new Character('\u21D3'));
        escapeStrings.put("&hArr;", new Character('\u21D4'));
        escapeStrings.put("&forall;", new Character('\u2200'));
        escapeStrings.put("&part;", new Character('\u2202'));
        escapeStrings.put("&exist;", new Character('\u2203'));
        escapeStrings.put("&empty;", new Character('\u2205'));
        escapeStrings.put("&nabla;", new Character('\u2207'));
        escapeStrings.put("&isin;", new Character('\u2208'));
        escapeStrings.put("&notin;", new Character('\u2209'));
        escapeStrings.put("&ni;", new Character('\u220B'));
        escapeStrings.put("&prod;", new Character('\u220F'));
        escapeStrings.put("&sum;", new Character('\u2211'));
        escapeStrings.put("&minus;", new Character('\u2212'));
        escapeStrings.put("&lowast;", new Character('\u2217'));
        escapeStrings.put("&radic;", new Character('\u221A'));
        escapeStrings.put("&prop;", new Character('\u221D'));
        escapeStrings.put("&infin;", new Character('\u221E'));
        escapeStrings.put("&ang;", new Character('\u2220'));
        escapeStrings.put("&and;", new Character('\u2227'));
        escapeStrings.put("&or;", new Character('\u2228'));
        escapeStrings.put("&cap;", new Character('\u2229'));
        escapeStrings.put("&cup;", new Character('\u222A'));
        escapeStrings.put("&int;", new Character('\u222B'));
        escapeStrings.put("&there4;", new Character('\u2234'));
        escapeStrings.put("&sim;", new Character('\u223C'));
        escapeStrings.put("&cong;", new Character('\u2245'));
        escapeStrings.put("&asymp;", new Character('\u2248'));
        escapeStrings.put("&ne;", new Character('\u2260'));
        escapeStrings.put("&equiv;", new Character('\u2261'));
        escapeStrings.put("&le;", new Character('\u2264'));
        escapeStrings.put("&ge;", new Character('\u2265'));
        escapeStrings.put("&sub;", new Character('\u2282'));
        escapeStrings.put("&sup;", new Character('\u2283'));
        escapeStrings.put("&nsub;", new Character('\u2284'));
        escapeStrings.put("&sube;", new Character('\u2286'));
        escapeStrings.put("&supe;", new Character('\u2287'));
        escapeStrings.put("&oplus;", new Character('\u2295'));
        escapeStrings.put("&otimes;", new Character('\u2297'));
        escapeStrings.put("&perp;", new Character('\u22A5'));
        escapeStrings.put("&sdot;", new Character('\u22C5'));
        escapeStrings.put("&lceil;", new Character('\u2308'));
        escapeStrings.put("&rceil;", new Character('\u2309'));
        escapeStrings.put("&lfloor;", new Character('\u230A'));
        escapeStrings.put("&rfloor;", new Character('\u230B'));
        escapeStrings.put("&lang;", new Character('\u2329'));
        escapeStrings.put("&rang;", new Character('\u232A'));
        escapeStrings.put("&loz;", new Character('\u25CA'));
        escapeStrings.put("&spades;", new Character('\u2660'));
        escapeStrings.put("&clubs;", new Character('\u2663'));
        escapeStrings.put("&hearts;", new Character('\u2665'));
        escapeStrings.put("&diams;", new Character('\u2666'));
        escapeStrings.put("&quot;", new Character('\u0022'));
        escapeStrings.put("&amp;", new Character('\u0026'));
        escapeStrings.put("&lt;", new Character('\u003C'));
        escapeStrings.put("&gt;", new Character('\u003E'));
        escapeStrings.put("&OElig;", new Character('\u0152'));
        escapeStrings.put("&oelig;", new Character('\u0153'));
        escapeStrings.put("&Scaron;", new Character('\u0160'));
        escapeStrings.put("&scaron;", new Character('\u0161'));
        escapeStrings.put("&Yuml;", new Character('\u0178'));
        escapeStrings.put("&circ;", new Character('\u02C6'));
        escapeStrings.put("&tilde;", new Character('\u02DC'));
        escapeStrings.put("&ensp;", new Character('\u2002'));
        escapeStrings.put("&emsp;", new Character('\u2003'));
        escapeStrings.put("&thinsp;", new Character('\u2009'));
        escapeStrings.put("&zwnj;", new Character('\u200C'));
        escapeStrings.put("&zwj;", new Character('\u200D'));
        escapeStrings.put("&lrm;", new Character('\u200E'));
        escapeStrings.put("&rlm;", new Character('\u200F'));
        escapeStrings.put("&ndash;", new Character('\u2013'));
        escapeStrings.put("&mdash;", new Character('\u2014'));
        escapeStrings.put("&lsquo;", new Character('\u2018'));
        escapeStrings.put("&rsquo;", new Character('\u2019'));
        escapeStrings.put("&sbquo;", new Character('\u201A'));
        escapeStrings.put("&ldquo;", new Character('\u201C'));
        escapeStrings.put("&rdquo;", new Character('\u201D'));
        escapeStrings.put("&bdquo;", new Character('\u201E'));
        escapeStrings.put("&dagger;", new Character('\u2020'));
        escapeStrings.put("&Dagger;", new Character('\u2021'));
        escapeStrings.put("&permil;", new Character('\u2030'));
        escapeStrings.put("&lsaquo;", new Character('\u2039'));
        escapeStrings.put("&rsaquo;", new Character('\u203A'));
        escapeStrings.put("&euro;", new Character('\u20AC'));
    }


    /**
     * Replace all the occurences of HTML escape strings with the respective characters.
     * 
     * @param s a <code>String</code> value
     * @return a <code>String</code> value
     */
    public static final String unescapeHTML(String s) {
        char[] chars = s.toCharArray();
        char[] escaped = new char[chars.length];

        // Note: escaped[pos] = end of the escaped char array.
        int pos = 0;

        for (int i = 0; i < chars.length;) {
            if (chars[i] != '&') {
                escaped[pos++] = chars[i++];
                continue;
            }

            // Allow e.g. {
            int j = i + 1;
            if (j < chars.length && chars[j] == '#')
                j++;

            // Scan until we find a char that is not letter or digit.
            for (; j < chars.length; j++) {
                if (!Character.isLetterOrDigit(chars[j]))
                    break;
            }

            boolean replaced = false;
            if (j < chars.length && chars[j] == ';') {
                if (s.charAt(i + 1) == '#') {
                    try {
                        long charcode = 0;
                        char ch = s.charAt(i + 2);
                        if (ch == 'x' || ch == 'X') {
                            charcode = Long.parseLong(new String(chars, i + 3, j - i - 3), 16);
                        } else if (Character.isDigit(ch)) {
                            charcode = Long.parseLong(new String(chars, i + 2, j - i - 2));
                        }
                        if (charcode > 0 && charcode < 65536) {
                            escaped[pos++] = (char) charcode;
                            replaced = true;
                        }
                    } catch (NumberFormatException ex) {
                        // Failed, not replaced.
                    }

                } else {
                    String key = new String(chars, i, j - i + 1);
                    Character repl = escapeStrings.get(key);
                    if (repl != null) {
                        escaped[pos++] = repl.charValue();
                        replaced = true;
                    }
                }
                j++; // Skip over ';'
            }

            if (!replaced) {
                // Not a recognized escape sequence, leave as-is
                System.arraycopy(chars, i, escaped, pos, j - i);
                pos += j - i;
            }
            i = j;
        }
        return new String(escaped, 0, pos);
    }
}
