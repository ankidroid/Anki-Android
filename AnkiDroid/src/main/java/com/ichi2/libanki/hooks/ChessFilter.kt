/***************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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

package com.ichi2.libanki.hooks;



import android.content.Context;

import com.ichi2.anki.AnkiDroidApp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

public class ChessFilter {
    private static final Pattern fFenPattern = Pattern.compile("\\[fen ?([^]]*)]([^\\[]+)\\[/fen]");
    private static final Pattern fFenOrientationPattern = Pattern.compile("orientation *= *\"?(black|white)\"?");
    private static final String fRenderFen =
    		"(function (fentxt, showBlack) {" +
    		"    fentxt=fentxt.replace(/ .*/g,'');" +
    		"    if (showBlack) {" +
    		"        fentxt = fentxt.split(\"\").reverse().join(\"\");" +
    		"    }" +
    		"    fentxt=fentxt.replace(/r/g,'x');" +
    		"    fentxt=fentxt.replace(/\\\\//g,'</tr><tr>');" +
    		"    fentxt=fentxt.replace(/1/g,'<td></td>');" +
    		"    fentxt=fentxt.replace(/2/g,'<td></td><td></td>');" +
    		"    fentxt=fentxt.replace(/3/g,'<td></td><td></td><td></td>');" +
    		"    fentxt=fentxt.replace(/4/g,'<td></td><td></td><td></td><td></td>');" +
    		"    fentxt=fentxt.replace(/5/g,'<td></td><td></td><td></td><td></td><td></td>');" +
    		"    fentxt=fentxt.replace(/6/g,'<td></td><td></td><td></td><td></td><td></td><td></td>');" +
    		"    fentxt=fentxt.replace(/7/g,'<td></td><td></td><td></td><td></td><td></td><td></td><td></td>');" +
    		"    fentxt=fentxt.replace(/8/g,'<td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>');" +
    		"    fentxt=fentxt.replace(/K/g,'<td>&#9812;</td>');" +
    		"    fentxt=fentxt.replace(/Q/g,'<td>&#9813;</td>');" +
    		"    fentxt=fentxt.replace(/R/g,'<td>&#9814;</td>');" +
    		"    fentxt=fentxt.replace(/B/g,'<td>&#9815;</td>');" +
    		"    fentxt=fentxt.replace(/N/g,'<td>&#9816;</td>');" +
    		"    fentxt=fentxt.replace(/P/g,'<td>&#9817;</td>');" +
    		"    fentxt=fentxt.replace(/k/g,'<td>&#9818;</td>');" +
    		"    fentxt=fentxt.replace(/q/g,'<td>&#9819;</td>');" +
    		"    fentxt=fentxt.replace(/x/g,'<td>&#9820;</td>');" +
    		"    fentxt=fentxt.replace(/b/g,'<td>&#9821;</td>');" +
    		"    fentxt=fentxt.replace(/n/g,'<td>&#9822;</td>');" +
    		"    fentxt=fentxt.replace(/p/g,'<td>&#9823;</td>');" +
    		"    return '<div align=\"center\" width=\"100%%\"><table class=\"chess_board\" cellspacing=\"0\" cellpadding=\"0\"><tr>'+fentxt+'</tr></table></div>';" +
    		"})('%s', %b)";

    public static String fenToChessboard(String text, Context context) {
        if (!AnkiDroidApp.getSharedPrefs(context).getBoolean("convertFenText", false)) {
            return text;
        }
        boolean showBlack = false;
        Matcher mf = fFenPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (mf.find()) {
            if (mf.group(1) != null) {
                Matcher mo = fFenOrientationPattern.matcher(mf.group(1));
                if (mo.find() && mo.group(1) != null && "black".equalsIgnoreCase(mo.group(1))) {
                    showBlack = true;
                }
            }

            try {
                mf.appendReplacement(sb, "<script type=\"text/javascript\">document.write(" +
                        String.format(Locale.US, fRenderFen, mf.group(2), showBlack) + ");</script>");
            } catch (Exception e) {
                Timber.e(e, "ChessFilter exception: ");
            }
        }
        mf.appendTail(sb);
        return sb.toString();
    }
}
