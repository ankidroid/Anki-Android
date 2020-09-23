/****************************************************************************************
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
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

import java.util.Random;

public class NoteFieldDecorator {

    private static final Random random = new Random();

    private static final String[] huevoDecorations = {
            "\uD83D\uDC8C",
            "\uD83D\uDE3B",
            "\uD83D\uDC96",
            "\uD83D\uDC97",
            "\uD83D\uDC93",
            "\uD83D\uDC9E",
            "\uD83D\uDC95",
            "\uD83D\uDC9F",
            "\uD83D\uDCAF",
            "\uD83D\uDE03",
            "\uD83D\uDE0D"
    };

    private static final String[] huevoOpciones = {
            "qnr",
            "gvzenr",
            "aboantb",
            "avpbynf-enbhy",
            "Neguhe-Zvypuvbe",
            "zvxruneql",
            "qnivq-nyyvfba-1",
            "vavwh",
            "uffz",
            "syreqn",
            "rqh-mnzben",
            "ntehraroret",
            "bfcnyu",
            "znaqer",
            "qnavry-fineq",
            "vasvalgr7",
            "Oynvfbeoynqr",
            "genfuphggre",
            "qzvgel-gvzbsrri",
            "inabfgra",
            "unacvatpuvarfr",
            "jro5atnl"
    };

    public static String aplicaHuevo(String fieldText) {
        String revuelto = huevoRevuelto(fieldText);
        for (String huevo : huevoOpciones) {
            if (huevo.equalsIgnoreCase(revuelto)) {
                String decoration = huevoDecorations[getRandomIndex(huevoDecorations.length)];
                return String.format("%s%s %s %s%s", decoration, decoration, fieldText, decoration, decoration);
            }
        }
        return fieldText;
    }

    private static int getRandomIndex(int max) {
        return random.nextInt(max);
    }

    private static String huevoRevuelto(String huevo) {
        if (huevo == null || huevo.length() == 0) {
            return huevo;
        }
        StringBuilder revuelto = new StringBuilder();
        for (int i = 0; i < huevo.length(); i++) {
            char c = huevo.charAt(i);
            if       (c >= 'a' && c <= 'm') c += 13;
            else if  (c >= 'A' && c <= 'M') c += 13;
            else if  (c >= 'n' && c <= 'z') c -= 13;
            else if  (c >= 'N' && c <= 'Z') c -= 13;
            revuelto.append(c);
        }
        return revuelto.toString();
    }
}
