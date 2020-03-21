
package com.ichi2.anki;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;

import android.widget.Toast;

import com.ichi2.libanki.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import timber.log.Timber;

public class AnkiFont {
    private String mName;
    private String mFamily;
    private List<String> mAttributes;
    private String mPath;
    private Boolean mIsDefault;
    private Boolean mIsOverride;
    private static final String fAssetPathPrefix = "/android_asset/fonts/";
    private static Set<String> corruptFonts = new HashSet<>();


    private AnkiFont(String name, String family, List<String> attributes, String path) {
        mName = name;
        mFamily = family;
        mAttributes = attributes;
        mPath = path;
        mIsDefault = false;
        mIsOverride = false;
    }


    /**
     * Factory for AnkiFont creation. Creates a typeface wrapper from a font file representing.
     *
     * @param ctx Activity context, needed to access assets
     * @param path Path to typeface file, needed when this is a custom font.
     * @param fromAssets True if the font is to be found in assets of application
     * @return A new AnkiFont object or null if the file can't be interpreted as typeface.
     */
    public static AnkiFont createAnkiFont(Context ctx, String path, boolean fromAssets) {
        File fontfile = new File(path);
        String name = Utils.splitFilename(fontfile.getName())[0];
        String family = name;
        List<String> attributes = new ArrayList<>();

        if (fromAssets) {
            path = fAssetPathPrefix.concat(fontfile.getName());
        }
        Typeface tf = getTypeface(ctx, path);
        if (tf == null) {
            // unable to create typeface
            return null;
        }

        if (tf.isBold() || name.toLowerCase(Locale.US).contains("bold")) {
            attributes.add("font-weight: bolder;");
            family = family.replaceFirst("(?i)-?Bold", "");
        } else if (name.toLowerCase(Locale.US).contains("light")) {
            attributes.add("font-weight: lighter;");
            family = family.replaceFirst("(?i)-?Light", "");
        } else {
            attributes.add("font-weight: normal;");
        }
        if (tf.isItalic() || name.toLowerCase(Locale.US).contains("italic")) {
            attributes.add("font-style: italic;");
            family = family.replaceFirst("(?i)-?Italic", "");
        } else if (name.toLowerCase(Locale.US).contains("oblique")) {
            attributes.add("font-style: oblique;");
            family = family.replaceFirst("(?i)-?Oblique", "");
        } else {
            attributes.add("font-style: normal;");
        }
        if (name.toLowerCase(Locale.US).contains("condensed") || name.toLowerCase(Locale.US).contains("narrow")) {
            attributes.add("font-stretch: condensed;");
            family = family.replaceFirst("(?i)-?Condensed", "");
            family = family.replaceFirst("(?i)-?Narrow(er)?", "");
        } else if (name.toLowerCase(Locale.US).contains("expanded") || name.toLowerCase(Locale.US).contains("wide")) {
            attributes.add("font-stretch: expanded;");
            family = family.replaceFirst("(?i)-?Expanded", "");
            family = family.replaceFirst("(?i)-?Wide(r)?", "");
        }

        AnkiFont createdFont = new AnkiFont(name, family, attributes, path);

        // determine if override font or default font
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(ctx);
        String defaultFont = preferences.getString("defaultFont", "");
        boolean overrideFont = "1".equals(preferences.getString("overrideFontBehavior", "0"));
        if (defaultFont.equalsIgnoreCase(name)) {
            if (overrideFont) {
                createdFont.setAsOverride();
            } else{
                createdFont.setAsDefault();
            }
        }
        return createdFont;
    }


    public String getDeclaration() {
        return "@font-face {" + getCSS(false) + " src: url(\"file://" + mPath + "\");}";
    }


    public String getCSS(boolean override) {
        StringBuilder sb = new StringBuilder("font-family: \"").append(mFamily);
        if (override) {
            sb.append("\" !important;");
        } else {
            sb.append("\";");
        }
        for (String attr : mAttributes) {
            sb.append(" ").append(attr);
            if (override) {
                if (sb.charAt(sb.length() - 1) == ';') {
                    sb.deleteCharAt(sb.length() - 1);
                    sb.append(" !important;");
                } else {
                    Timber.d("AnkiFont.getCSS() - unable to set a font attribute important while override is set.");
                }
            }
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
        try {
            if (path.startsWith(fAssetPathPrefix)) {
                return Typeface.createFromAsset(ctx.getAssets(), path.replaceFirst("/android_asset/", ""));
            } else {
                return Typeface.createFromFile(path);
            }
        } catch (RuntimeException e) {
            Timber.w(e, "Runtime error in getTypeface for File: %s", path);
            if (!corruptFonts.contains(path)) {
                // Show warning toast
                String name = new File(path).getName();
                Resources res = AnkiDroidApp.getAppResources();
                Toast toast = Toast.makeText(ctx, res.getString(R.string.corrupt_font, name), Toast.LENGTH_LONG);
                toast.show();
                // Don't warn again in this session
                corruptFonts.add(path);
            }
            return null;
        }
    }


    private void setAsDefault() {
        mIsDefault = true;
        mIsOverride = false;
    }


    private void setAsOverride() {
        mIsOverride = true;
        mIsDefault = false;
    }
}
