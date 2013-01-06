/***************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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

package com.ichi2.libanki;

import android.text.Html;

import com.ichi2.libanki.hooks.Hook;
import com.ichi2.libanki.hooks.Hooks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class used to display and handle correctly LaTeX.
 */
public class LaTeX {
    public class LaTeXFilter extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            return LaTeX.mungeQA((String) arg, (Collection) args[4]);
        }
    }


    public void installHook(Hooks h) {
        h.addHook("mungeQA", new LaTeXFilter());
    }

    /**
     * Patterns used to identify LaTeX tags
     */
    public static Pattern sStandardPattern = Pattern.compile("\\[latex\\](.+?)\\[/latex\\]");
    public static Pattern sExpressionPattern = Pattern.compile("\\[\\$\\](.+?)\\[/\\$\\]");
    public static Pattern sMathPattern = Pattern.compile("\\[\\$\\$\\](.+?)\\[/\\$\\$\\]");
    public static Pattern sEntityPattern = Pattern.compile("(&[a-z]+;)");


    /**
     * Convert TEXT with embedded latex tags to image links.
     * 
     * @param html The content to search for embedded latex tags.
     * @param col The related collection.
     * @return The content with the tags converted to links.
     */
    public static String mungeQA(String html, Collection col) {
        StringBuffer sb = new StringBuffer();

        Matcher matcher = sStandardPattern.matcher(html);
        while (matcher.find()) {
            matcher.appendReplacement(sb, _imgLink(col, matcher.group(1)));
        }
        matcher.appendTail(sb);

        matcher = sExpressionPattern.matcher(sb.toString());
        sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, _imgLink(col, "$" + matcher.group(1) + "$"));
        }
        matcher.appendTail(sb);

        matcher = sMathPattern.matcher(sb.toString());
        sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb,
                    _imgLink(col, "\\begin{displaymath}" + matcher.group(1) + "\\end{displaymath}"));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }


    /**
     * Return an img link for LATEX, creating it if necessary.
     * 
     * @param col The associated Collection object.
     * @param latex The LATEX expression to be replaced
     * @return A string with the link to the image that is the representation of the LATEX expression.
     */
    private static String _imgLink(Collection col, String latex) {
        String txt = _latexFromHtml(col, latex);
        String fname = "latex-" + Utils.checksum(txt) + ".png";
        String link = "<img src=\"" + fname + "\">";
        return link;
    }


    /**
     * Convert entities and fix newlines.
     * 
     * @param col The associated Collection where the LATEX is found
     * @param latex The
     * @return
     */
    private static String _latexFromHtml(Collection col, String latex) {
        // entitydefs defines nbsp as \xa0 instead of a standard space, so we
        // replace it first
        latex = latex.replace("&nbsp;", " ");
        latex = latex.replaceAll("<br( /)?>|<div>", "\n");
        // replace <div> etc with spaces
        latex = latex.replaceAll("<.+?>", " ");
        latex = Utils.stripHTML(latex);
        return latex;
    }

    // ///Use this to replace <br> tags
    // private static Pattern sBRPattern = Pattern
    // .compile("<br( /)?>");
    //
    // /* Prevent class from being instantiated */
    // private LaTeX() { }
    //
    // /**
    // * Parses the content (belonging to deck), replacing LaTeX with img tags
    // *
    // * @TODO should check to see if the image exists or not? Or can we assume that
    // * the user is bright enough to compile stuff if they are using LaTeX?
    // * @param deck Deck whose content is being parsed
    // * @param content HTML content of a card
    // * @return content Content with the onload events for the img tags
    // */
    // public static String parseLaTeX(Models model, String content) {
    //
    // StringBuilder stringBuilder = new StringBuilder();
    // String contentLeft = content;
    // String latex;
    //
    // //First pass, grab everything that the standard pattern gets
    // Log.i(AnkiDroidApp.TAG, "parseLaTeX");
    // Matcher matcher = sStandardPattern.matcher(contentLeft);
    // while (matcher.find()) {
    // latex = matcher.group(1);
    // String img = mungeLatex(model, latex);
    // img = "latex-" + Utils.checksum(img) + ".png";
    //
    // String imgTag = matcher.group();
    // int markerStart = contentLeft.indexOf(imgTag);
    // stringBuilder.append(contentLeft.substring(0, markerStart));
    // stringBuilder.append("<img src=\"" + img + "\" alt=\"" + latex + "\">");
    //
    // contentLeft = contentLeft.substring(markerStart + imgTag.length());
    // }
    // stringBuilder.append(contentLeft);
    //
    // //Second pass, grab everything that the expression pattern gets
    // contentLeft = stringBuilder.toString();
    // stringBuilder = new StringBuilder();
    // matcher = sExpressionPattern.matcher(contentLeft);
    // while (matcher.find()) {
    // latex = matcher.group(1);
    // String img = "$" + mungeLatex(model, latex) + "$";
    // img = "latex-" + Utils.checksum(img) + ".png";
    //
    // String imgTag = matcher.group();
    // int markerStart = contentLeft.indexOf(imgTag);
    // stringBuilder.append(contentLeft.substring(0, markerStart));
    // stringBuilder.append("<img src=\"" + img + "\" alt=\"" + latex + "\">");
    //
    // contentLeft = contentLeft.substring(markerStart + imgTag.length());
    // }
    // stringBuilder.append(contentLeft);
    //
    // //Thid pass, grab everything that the math pattern gets
    // contentLeft = stringBuilder.toString();
    // stringBuilder = new StringBuilder();
    // matcher = sMathPattern.matcher(contentLeft);
    // while (matcher.find()) {
    // latex = matcher.group(1);
    // String img = "\\begin{displaymath}" + mungeLatex(model, latex) + "\\end{displaymath}";
    // img = "latex-" + Utils.checksum(img) + ".png";
    //
    // String imgTag = matcher.group();
    // int markerStart = contentLeft.indexOf(imgTag);
    // stringBuilder.append(contentLeft.substring(0, markerStart));
    // stringBuilder.append("<img src=\"" + img + "\" alt=\"" + latex + "\">");
    //
    // contentLeft = contentLeft.substring(markerStart + imgTag.length());
    // }
    // stringBuilder.append(contentLeft);
    //
    // return stringBuilder.toString();
    // }
    //
    // private static final Pattern htmlNamedEntityPattern = Pattern.compile("(?i)&[a-z]+;");
    //
    // /**
    // * Convert entities, fix newlines, convert to utf8, and wrap pre/postamble.
    // *
    // * @param deck The deck, needed to get the latex pre/post-ambles
    // * @param latex The latex trsing to be munged
    // * @return the latex string transformed as described above
    // */
    // private static String mungeLatex(Models model, String latex) {
    // // Deal with HTML named entities
    // StringBuilder sb = new StringBuilder(latex);
    // Matcher namedEntity = htmlNamedEntityPattern.matcher(sb);
    // while (namedEntity.find()) {
    // sb.replace(namedEntity.start(), namedEntity.end(), Html.fromHtml(namedEntity.group()).toString());
    // namedEntity = htmlNamedEntityPattern.matcher(sb);
    // }
    // latex = sb.toString();
    //
    // // Fix endlines
    // latex = sBRPattern.matcher(latex).replaceAll("\n");
    //
    // // Add pre/post-ambles
    // try {
    // latex = model.getConf().getString("latexPre") + "\n" + latex + "\n" + model.getConf().getString("latexPost");
    // } catch (JSONException e) {
    // throw new RuntimeException(e);
    // }
    //
    // // TODO: Do we need to convert to UTF-8?
    //
    // return latex;
    // }
}
