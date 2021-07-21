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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;

import com.ichi2.anki.MediaRegistration;
import java.util.Locale;

import androidx.annotation.NonNull;
import timber.log.Timber;

public class SummerNoteVisualEditor extends VisualEditorWebView {

    private final MediaRegistration mMediaRegistration;
    public SummerNoteVisualEditor(Context context) {
        super(context);
        mMediaRegistration = new MediaRegistration(context);
    }


    public SummerNoteVisualEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMediaRegistration = new MediaRegistration(context);
    }


    public SummerNoteVisualEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mMediaRegistration = new MediaRegistration(context);
    }


    public SummerNoteVisualEditor(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mMediaRegistration = new MediaRegistration(context);
    }

    @Override
    public String getJsFunctionName(VisualEditorFunctionality functionality) {
        switch (functionality) {
            case BOLD: return "setBold";
            case ITALIC: return "setItalic";
            case UNDERLINE: return "setUnderline";
            case CLEAR_FORMATTING: return "removeFormat";
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
    public void setSelectedTextColor(int color) {
        execUnsafe("setTextForeColor('" + colorToHex(color) + "');");
    }

    @Override
    public void setSelectedBackgroundColor(int color) {
        execUnsafe("setTextBackColor('" + colorToHex(color) +"');");
    }

    @Override
    protected void onPostInit(String utf8Content, String baseUrl) {
        addJavascriptInterface(this, "VisualEditor");
        loadDataWithBaseURL(baseUrl + "__visual_editor__.html\"", utf8Content, "text/html; charset=utf-8", "UTF-8", null);
    }


    @Override
    public void deleteImage(@NonNull String guid) {
        //noinspection ConstantConditions
        if (guid == null) {
            Timber.w("Failed to delete image - no guid");
            return;
        }
        ExecEscaped safeString =  ExecEscaped.fromString(guid);
        String safeCommand = String.format("deleteImage('%s')", safeString.getEscapedValue());
        execUnsafe(safeCommand);
    }

    @JavascriptInterface
    public void pasteImage() {
        post(() -> {
            ClipboardManager clipboardManager = (ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData pData = clipboardManager.getPrimaryClip();
            ClipData.Item item = pData.getItemAt(0);
            if (item.getUri() != null) {
                String image;
                try {
                    image = mMediaRegistration.onImagePaste(item.getUri());
                    pasteHtml(image);
                } catch (NullPointerException e) {
                    Timber.w(e);
                }
            }
        });
    }


    // used ` instead of "/' double or single quotes. They can't be used to declare multiline string.
    @Override
    public void pasteHtml(String html) {
        Timber.v("pasting: %s", html);
        ExecEscaped safeString = ExecEscaped.fromString(html);
        execUnsafe("pasteHTML(`" + safeString.getEscapedValue() + "`);");
    }

    @Override
    public void insertCloze(int clozeId) {
        ExecEscaped e = ExecEscaped.fromString(String.format(Locale.US, "cloze(%d)", clozeId));
        exec(e);
    }

    @Override
    public void insertCustomTag(String customPrefix, String customSuffix) {
        execUnsafe("insertCustomTag('" + customPrefix + "','" + customSuffix + "');");
    }

    @Override
    public void setHtml(@NonNull String html) {
        ExecEscaped s = ExecEscaped.fromString(html);
        execUnsafe("setHtml(`" + s.getEscapedValue() + "`);");
    }
}
