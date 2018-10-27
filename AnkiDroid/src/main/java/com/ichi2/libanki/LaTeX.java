/***************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki;

import com.ichi2.libanki.hooks.Hook;
import com.ichi2.libanki.hooks.Hooks;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is used to detect LaTeX tags in HTML and convert them to their corresponding image
 * file names.
 *
 * Anki provides shortcut forms for certain expressions. These three forms are considered valid
 * LaTeX tags in Anki:
 * 1 - [latex]...[/latex]
 * 2 - [$]...[$]
 * 3 - [$$]...[$$]
 *
 * Unlike the original python implementation of this class, the AnkiDroid version does not support
 * the generation of LaTeX images.
 */
@SuppressWarnings({"PMD.MethodNamingConventions","PMD.AvoidReassigningParameters"})
public class LaTeX {

    /**
     * Patterns used to identify LaTeX tags
     */
    public static Pattern sStandardPattern = Pattern.compile("\\[latex\\](.+?)\\[/latex\\]",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    public static Pattern sExpressionPattern = Pattern.compile("\\[\\$\\](.+?)\\[/\\$\\]",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    public static Pattern sMathPattern = Pattern.compile("\\[\\$\\$\\](.+?)\\[/\\$\\$\\]",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);


    /**
     * Convert HTML with embedded latex tags to image links.
     * NOTE: _imgLink produces an alphanumeric filename so there is no need to escape the replacement string.
     */
    public static String mungeQA(String html, Collection col, JSONObject model) {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = sStandardPattern.matcher(html);
        while (matcher.find()) {
            matcher.appendReplacement(sb, _imgLink(matcher.group(1), model));
        }
        matcher.appendTail(sb);

        matcher = sExpressionPattern.matcher(sb.toString());
        sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, _imgLink("$" + matcher.group(1) + "$", model));
        }
        matcher.appendTail(sb);

        matcher = sMathPattern.matcher(sb.toString());
        sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb,
                    _imgLink("\\begin{displaymath}" + matcher.group(1) + "\\end{displaymath}", model));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }


    /**
     * Return an img link for LATEX.
     */
    private static String _imgLink(String latex, JSONObject model) {
        String txt = _latexFromHtml(latex);

        String ext = "png";
        if (model.optBoolean("latexsvg", false)) {
            ext = "svg";
        }

        String fname = "latex-" + Utils.checksum(txt) + "." + ext;
        return "<img class=latex src=\"" + fname + "\">";
    }


    /**
     * Convert entities and fix newlines.
     */
    private static String _latexFromHtml(String latex) {
        latex = latex.replaceAll("<br( /)?>|<div>", "\n");
        latex = Utils.stripHTML(latex);
        return latex;
    }

    public class LaTeXFilter extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            return LaTeX.mungeQA((String) arg, (Collection) args[4], (JSONObject) args[2]);
        }
    }


    public void installHook(Hooks h) {
        h.addHook("mungeQA", new LaTeXFilter());
    }
}
