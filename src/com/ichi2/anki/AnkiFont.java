package com.ichi2.anki;

import android.content.Context;
import android.graphics.Typeface;

import com.ichi2.libanki.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AnkiFont {
    private String mName;
    private String mFamily;
    private List<String> mAttributes = new ArrayList<String>();
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
            mAttributes.add("font-weight: bolder;");
            mFamily = mFamily.replaceFirst("(?i)-?Bold", "");
        } else if (mName.toLowerCase().contains("light")) {
            mAttributes.add("font-weight: lighter;");
            mFamily = mFamily.replaceFirst("(?i)-?Light", "");
        } else {
            mAttributes.add("font-weight: normal;");
        }
        if (tf.isItalic() || mName.toLowerCase().contains("italic")) {
            mAttributes.add("font-style: italic;");
            mFamily = mFamily.replaceFirst("(?i)-?Italic", "");
        } else if (mName.toLowerCase().contains("oblique")) {
            mAttributes.add("font-style: oblique;");
            mFamily = mFamily.replaceFirst("(?i)-?Oblique", "");
        } else {
            mAttributes.add("font-style: normal;");
        }
        if (mName.toLowerCase().contains("condensed") || mName.toLowerCase().contains("narrow")) {
            mAttributes.add("font-stretch: condensed;");
            mFamily = mFamily.replaceFirst("(?i)-?Condensed", "");
            mFamily = mFamily.replaceFirst("(?i)-?Narrow(er)?", "");
        } else if (mName.toLowerCase().contains("expanded") || mName.toLowerCase().contains("wide")) {
            mAttributes.add("font-stretch: expanded;");
            mFamily = mFamily.replaceFirst("(?i)-?Expanded", "");
            mFamily = mFamily.replaceFirst("(?i)-?Wide(r)?", "");
        } else {
            mAttributes.add("font-stretch: normal;");
        }
        mFamily = mFamily.replaceFirst("(?i)-?Regular", "");
    }
    public String getDeclaration() {
        StringBuilder sb = new StringBuilder("@font-face {");
        sb.append(getCSS()).append(" src: url(\"file://").append(mPath).append("\");}");
        return sb.toString();
    }
    public String getCSS() {
        StringBuilder sb = new StringBuilder("font-family: \"").append(mFamily).append("\";");
        for (String attr : mAttributes) {
            sb.append(" ").append(attr);
        }
        return sb.toString();
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
