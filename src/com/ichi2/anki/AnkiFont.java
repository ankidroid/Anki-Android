package com.ichi2.anki;

import android.content.Context;
import android.graphics.Typeface;

import com.ichi2.libanki.Utils;

import java.io.File;

public class AnkiFont {
    private String mName;
    private String mFamily;
    private String mWeight;
    private String mStyle;
    private String mStretch;
    private String mPath;
    private static final String fAssetPathPrefix = "/android_asset/fonts/";

    public AnkiFont(Context ctx, String path, boolean fromAssets) {
        mPath = path;
        File fontfile = new File(mPath);
        mName = Utils.removeExtension(fontfile.getName());
        mFamily = mName;

        if (fromAssets) {
            mPath = fAssetPathPrefix.concat(fontfile.getName());
        }
        Typeface tf = getTypeface(ctx, mPath);
        if (tf.isBold() || mName.toLowerCase().contains("bold")) {
            mWeight = "font-weight: bolder;";
            mFamily = mFamily.replaceFirst("(?i)-?Bold", "");
        } else if (mName.toLowerCase().contains("light")) {
            mWeight = "font-weight: lighter;";
            mFamily = mFamily.replaceFirst("(?i)-?Light", "");
        } else {
            mWeight = "font-weight: normal;";
        }
        if (tf.isItalic() || mName.toLowerCase().contains("italic")) {
            mStyle = "font-style: italic;";
            mFamily = mFamily.replaceFirst("(?i)-?Italic", "");
        } else if (mName.toLowerCase().contains("oblique")) {
            mStyle = "font-style: oblique;";
            mFamily = mFamily.replaceFirst("(?i)-?Oblique", "");
        } else {
            mStyle = "font-style: normal;";
        }
        if (mName.toLowerCase().contains("condensed") || mName.toLowerCase().contains("narrow")) {
            mStretch = "font-stretch: condensed;";
            mFamily = mFamily.replaceFirst("(?i)-?Condensed", "");
            mFamily = mFamily.replaceFirst("(?i)-?Narrow(er)?", "");
        } else if (mName.toLowerCase().contains("expanded") || mName.toLowerCase().contains("wide")) {
            mStretch = "font-stretch: expanded;";
            mFamily = mFamily.replaceFirst("(?i)-?Expanded", "");
            mFamily = mFamily.replaceFirst("(?i)-?Wide(r)?", "");
        } else {
            mStretch = "font-stretch: normal;";
        }
        mFamily = mFamily.replaceFirst("(?i)-?Regular", "");
    }
    public String getStyle() {
        String style = String.format("@font-face {font-family: \"%s\"; src: url(\"file://%s\");}",
                mName, mPath);
        if (mFamily.equalsIgnoreCase(mName)) {
            return style;
        }
        return String.format("%s\n@font-face {font-family: \"%s\"; %s %s %s src: url(\"file://%s\");}",
                style, mFamily, mWeight, mStyle, mStretch, mPath);
    }
    public String getName() {
        return mName;
    }
    public String getPath() {
        return mPath;
    }
    public static Typeface getTypeface(Context ctx, String path) {
        if (path.startsWith(fAssetPathPrefix)) {
            return Typeface.createFromAsset(ctx.getAssets(), path.replaceFirst("/android_asset/", ""));
        } else {
            return Typeface.createFromFile(path);
        }
    }
}
