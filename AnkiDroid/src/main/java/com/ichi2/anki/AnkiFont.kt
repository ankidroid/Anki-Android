//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.content.Context
import android.graphics.Typeface
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.libanki.Utils
import timber.log.Timber
import java.io.File
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.util.*

class AnkiFont private constructor(val name: String, private val family: String, private val attributes: List<String>, val path: String) {
    private var mIsDefault = false
    private var mIsOverride = false
    val declaration: String
        get() = "@font-face {" + getCSS(false) + " src: url(\"file://" + path + "\");}"

    fun getCSS(override: Boolean): String {
        val sb = StringBuilder("font-family: \"").append(family)
        if (override) {
            sb.append("\" !important;")
        } else {
            sb.append("\";")
        }
        for (attr in attributes) {
            sb.append(" ").append(attr)
            if (override) {
                if (sb[sb.length - 1] == ';') {
                    sb.deleteCharAt(sb.length - 1)
                    sb.append(" !important;")
                } else {
                    Timber.d("AnkiFont.getCSS() - unable to set a font attribute important while override is set.")
                }
            }
        }
        return sb.toString()
    }

    private fun setAsDefault() {
        mIsDefault = true
        mIsOverride = false
    }

    private fun setAsOverride() {
        mIsOverride = true
        mIsDefault = false
    }

    companion object {
        private const val fAssetPathPrefix = "/android_asset/fonts/"
        private val corruptFonts: MutableSet<String> = HashSet()

        /**
         * Factory for AnkiFont creation. Creates a typeface wrapper from a font file representing.
         *
         * @param ctx Activity context, needed to access assets
         * @param filePath Path to typeface file, needed when this is a custom font.
         * @param fromAssets True if the font is to be found in assets of application
         * @return A new AnkiFont object or null if the file can't be interpreted as typeface.
         */
        fun createAnkiFont(ctx: Context, filePath: String, fromAssets: Boolean): AnkiFont? {
            val fontFile = File(filePath)
            val path = if (fromAssets) {
                fAssetPathPrefix + fontFile.name
            } else {
                filePath
            }
            val name = Utils.splitFilename(fontFile.name)[0]
            var family = name
            val attributes: MutableList<String> = ArrayList(2)
            val tf = getTypeface(ctx, path) // unable to create typeface
                ?: return null
            if (tf.isBold || name.lowercase().contains("bold")) {
                attributes.add("font-weight: bolder;")
                family = family.replaceFirst("(?i)-?Bold".toRegex(), "")
            } else if (name.lowercase().contains("light")) {
                attributes.add("font-weight: lighter;")
                family = family.replaceFirst("(?i)-?Light".toRegex(), "")
            } else {
                attributes.add("font-weight: normal;")
            }
            if (tf.isItalic || name.lowercase().contains("italic")) {
                attributes.add("font-style: italic;")
                family = family.replaceFirst("(?i)-?Italic".toRegex(), "")
            } else if (name.lowercase().contains("oblique")) {
                attributes.add("font-style: oblique;")
                family = family.replaceFirst("(?i)-?Oblique".toRegex(), "")
            } else {
                attributes.add("font-style: normal;")
            }
            if (name.lowercase().contains("condensed") || name.lowercase().contains("narrow")) {
                attributes.add("font-stretch: condensed;")
                family = family.replaceFirst("(?i)-?Condensed".toRegex(), "")
                family = family.replaceFirst("(?i)-?Narrow(er)?".toRegex(), "")
            } else if (name.lowercase().contains("expanded") || name.lowercase().contains("wide")) {
                attributes.add("font-stretch: expanded;")
                family = family.replaceFirst("(?i)-?Expanded".toRegex(), "")
                family = family.replaceFirst("(?i)-?Wide(r)?".toRegex(), "")
            }
            val createdFont = AnkiFont(name, family, attributes, path)

            // determine if override font or default font
            val preferences = ctx.sharedPrefs()
            val defaultFont = preferences.getString("defaultFont", "")
            val overrideFont = "1" == preferences.getString("overrideFontBehavior", "0")
            if (defaultFont.equals(name, ignoreCase = true)) {
                if (overrideFont) {
                    createdFont.setAsOverride()
                } else {
                    createdFont.setAsDefault()
                }
            }
            return createdFont
        }

        fun getTypeface(ctx: Context, path: String): Typeface? {
            return try {
                if (path.startsWith(fAssetPathPrefix)) {
                    Typeface.createFromAsset(ctx.assets, path.replaceFirst("/android_asset/".toRegex(), ""))
                } else {
                    Typeface.createFromFile(path)
                }
            } catch (e: RuntimeException) {
                Timber.w(e, "Runtime error in getTypeface for File: %s", path)
                if (!corruptFonts.contains(path)) {
                    // Show warning toast
                    val name = File(path).name
                    val res = AnkiDroidApp.appResources
                    UIUtils.showThemedToast(ctx, res.getString(R.string.corrupt_font, name), false)
                    // Don't warn again in this session
                    corruptFonts.add(path)
                }
                null
            }
        }
    }
}
