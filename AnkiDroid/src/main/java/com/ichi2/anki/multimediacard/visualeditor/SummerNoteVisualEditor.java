/*
MIT License

Copyright (c) 2017 Jhon Kenneth Cariño

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

//Based off https://github.com/jkennethcarino/rtexteditorview/blob/7c0a50240de51ee576793afdfcf4f173e4c609fd/library/src/main/java/com/jkcarino/rtexteditorview/RTextEditorView.java


package com.ichi2.anki.multimediacard.visualeditor;

import android.content.Context;
import android.util.AttributeSet;

import java.util.Locale;

import androidx.annotation.NonNull;

public class SummerNoteVisualEditor extends VisualEditorWebView {
    public SummerNoteVisualEditor(Context context) {
        super(context);
    }


    public SummerNoteVisualEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    public SummerNoteVisualEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public SummerNoteVisualEditor(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public String getJsFunctionName(VisualEditorFunctionality functionality) {
        switch (functionality) {
            case BOLD: return "setBold";
            case ITALIC: return "setItalic";
            case UNDERLINE: return "setUnderline";
            case CLEAR_FORMATTING: return "removeFormat";
            case UNORDERED_LIST: return "insertUnorderedList";
            case ORDERED_LIST: return "insertOrderedList";
            case HORIZONTAL_RULE: return "insertHorizontalRule";
            case ALIGN_LEFT: return "setAlignLeft";
            case ALIGN_CENTER: return "setAlignCenter";
            case ALIGN_RIGHT: return "setAlignRight";
            case ALIGN_JUSTIFY: return "setAlignJustify";
            case EDIT_SOURCE: return "editHtml";
            default: return null;
        }
    }

    @Override
    protected void onPostInit(String utf8Content, String baseUrl) {
        addJavascriptInterface(this, "RTextEditorView");
        loadDataWithBaseURL(baseUrl + "__visual_editor__.html\"", utf8Content, "text/html; charset=utf-8", "UTF-8", null);
    }


    @Override
    public void insertCloze(int clozeId) {
        ExecEscaped e = ExecEscaped.fromString(String.format(Locale.US, "cloze(%d)", clozeId));
        exec(e);
    }


    @Override
    public void setHtml(@NonNull String html) {
        ExecEscaped s = ExecEscaped.fromString(html);
        execUnsafe("setHtml('" + s.getEscapedValue() + "');");
    }
}
