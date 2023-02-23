/*
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.themes

import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

@KotlinCleanup("for better possibly-null handling")
object HtmlColors {
    private val fHtmlColorPattern = Pattern.compile(
        "((?:color|background)\\s*[=:]\\s*\"?)((?:[a-z]+|#[0-9a-f]+|rgb\\([0-9]+,\\s*[0-9],+\\s*[0-9]+\\)))([\";\\s])",
        Pattern.CASE_INSENSITIVE
    )
    private val fShortHexColorPattern = Pattern.compile("^#([0-9a-f])([0-9a-f])([0-9a-f])$", Pattern.CASE_INSENSITIVE)
    private val fLongHexColorPattern = Pattern.compile("^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$", Pattern.CASE_INSENSITIVE)
    private val fRgbColorPattern = Pattern.compile("^rgb\\(([0-9]+)\\s*,\\s*([0-9]+)\\s*,\\s*([0-9]+)\\)$", Pattern.CASE_INSENSITIVE)

    @Suppress("RegExpRedundantEscape") // In Android, } should be escaped
    private val fClozeStylePattern = Pattern.compile("(.cloze\\s*\\{[^}]*color:\\s*#)[0-9a-f]{6}(;[^}]*\\})", Pattern.CASE_INSENSITIVE)
    private fun nameToHex(name: String): String? {
        if (sColorsMap == null) {
            sColorsMap = HashMapInit(fColorsRawList.size)
            var i = 0
            while (i < fColorsRawList.size) {
                (sColorsMap as HashMap<String, String>)[fColorsRawList[i].lowercase(Locale.US)] = fColorsRawList[i + 1].lowercase(Locale.US)
                i += 2
            }
        }
        val normalisedName = name.lowercase(Locale.US)
        return if (sColorsMap!!.containsKey(normalisedName)) {
            sColorsMap!![normalisedName]
        } else {
            name
        }
    }

    /**
     * Returns a string where all colors have been inverted. It applies to anything that is in a tag and looks like
     * #FFFFFF Example: Here only #000000 will be replaced (#777777 is content) <span style="color: #000000;">Code
     * #777777 is the grey color</span> This is done with a state machine with 2 states: - 0: within content - 1: within
     * a tag
     */
    fun invertColors(text: String?): String {
        val sb = StringBuffer()
        val m1 = fHtmlColorPattern.matcher(text!!)
        while (m1.find()) {
            // Convert names to hex
            var color = nameToHex(m1.group(2)!!)
            var m2: Matcher
            try {
                if (color!!.length == 4 && color[0] == '#') {
                    m2 = fShortHexColorPattern.matcher(color)
                    if (m2.find()) {
                        color = String.format(
                            Locale.US,
                            "#%x%x%x",
                            0xf - m2.group(1)!!.toInt(16),
                            0xf - m2.group(2)!!.toInt(16),
                            0xf - m2.group(3)!!.toInt(16)
                        )
                    }
                } else if (color.length == 7 && color[0] == '#') {
                    m2 = fLongHexColorPattern.matcher(color)
                    if (m2.find()) {
                        color = String.format(
                            Locale.US,
                            "#%02x%02x%02x",
                            0xff - m2.group(1)!!.toInt(16),
                            0xff - m2.group(2)!!.toInt(16),
                            0xff - m2.group(3)!!.toInt(16)
                        )
                    }
                } else if (color.length > 9 && color.lowercase(Locale.US).startsWith("rgb")) {
                    m2 = fRgbColorPattern.matcher(color)
                    if (m2.find()) {
                        color = String.format(
                            Locale.US,
                            "rgb(%d, %d, %d)",
                            0xff - m2.group(1)!!.toInt(),
                            0xff - m2.group(2)!!.toInt(),
                            0xff - m2.group(3)!!.toInt()
                        )
                    }
                }
            } catch (e: NumberFormatException) {
                Timber.w(e)
                // shouldn't happen but ignore anyway
            }
            m1.appendReplacement(sb, m1.group(1)!! + color + m1.group(3))
        }
        m1.appendTail(sb)
        var invertedText = sb.toString()
        // fix style for cloze to light blue instead of inverted blue which ends up as yellow
        val mc = fClozeStylePattern.matcher(invertedText)
        invertedText = mc.replaceAll("$10088ff$2")
        return invertedText
    }

    private var sColorsMap: MutableMap<String, String>? = null
    private val fColorsRawList = arrayOf(
        "AliceBlue", "#F0F8FF",
        "AntiqueWhite", "#FAEBD7",
        "Aqua", "#00FFFF",
        "Aquamarine", "#7FFFD4",
        "Azure", "#F0FFFF",
        "Beige", "#F5F5DC",
        "Bisque", "#FFE4C4",
        "Black", "#000000",
        "BlanchedAlmond", "#FFEBCD",
        "Blue", "#0000FF",
        "BlueViolet", "#8A2BE2",
        "Brown", "#A52A2A",
        "BurlyWood", "#DEB887",
        "CadetBlue", "#5F9EA0",
        "Chartreuse", "#7FFF00",
        "Chocolate", "#D2691E",
        "Coral", "#FF7F50",
        "CornflowerBlue", "#6495ED",
        "Cornsilk", "#FFF8DC",
        "Crimson", "#DC143C",
        "Cyan", "#00FFFF",
        "DarkBlue", "#00008B",
        "DarkCyan", "#008B8B",
        "DarkGoldenRod", "#B8860B",
        "DarkGray", "#A9A9A9",
        "DarkGrey", "#A9A9A9",
        "DarkGreen", "#006400",
        "DarkKhaki", "#BDB76B",
        "DarkMagenta", "#8B008B",
        "DarkOliveGreen", "#556B2F",
        "Darkorange", "#FF8C00",
        "DarkOrchid", "#9932CC",
        "DarkRed", "#8B0000",
        "DarkSalmon", "#E9967A",
        "DarkSeaGreen", "#8FBC8F",
        "DarkSlateBlue", "#483D8B",
        "DarkSlateGray", "#2F4F4F",
        "DarkSlateGrey", "#2F4F4F",
        "DarkTurquoise", "#00CED1",
        "DarkViolet", "#9400D3",
        "DeepPink", "#FF1493",
        "DeepSkyBlue", "#00BFFF",
        "DimGray", "#696969",
        "DimGrey", "#696969",
        "DodgerBlue", "#1E90FF",
        "FireBrick", "#B22222",
        "FloralWhite", "#FFFAF0",
        "ForestGreen", "#228B22",
        "Fuchsia", "#FF00FF",
        "Gainsboro", "#DCDCDC",
        "GhostWhite", "#F8F8FF",
        "Gold", "#FFD700",
        "GoldenRod", "#DAA520",
        "Gray", "#808080",
        "Grey", "#808080",
        "Green", "#008000",
        "GreenYellow", "#ADFF2F",
        "HoneyDew", "#F0FFF0",
        "HotPink", "#FF69B4",
        "IndianRed", "#CD5C5C",
        "Indigo", "#4B0082",
        "Ivory", "#FFFFF0",
        "Khaki", "#F0E68C",
        "Lavender", "#E6E6FA",
        "LavenderBlush", "#FFF0F5",
        "LawnGreen", "#7CFC00",
        "LemonChiffon", "#FFFACD",
        "LightBlue", "#ADD8E6",
        "LightCoral", "#F08080",
        "LightCyan", "#E0FFFF",
        "LightGoldenRodYellow", "#FAFAD2",
        "LightGray", "#D3D3D3",
        "LightGrey", "#D3D3D3",
        "LightGreen", "#90EE90",
        "LightPink", "#FFB6C1",
        "LightSalmon", "#FFA07A",
        "LightSeaGreen", "#20B2AA",
        "LightSkyBlue", "#87CEFA",
        "LightSlateGray", "#778899",
        "LightSlateGrey", "#778899",
        "LightSteelBlue", "#B0C4DE",
        "LightYellow", "#FFFFE0",
        "Lime", "#00FF00",
        "LimeGreen", "#32CD32",
        "Linen", "#FAF0E6",
        "Magenta", "#FF00FF",
        "Maroon", "#800000",
        "MediumAquaMarine", "#66CDAA",
        "MediumBlue", "#0000CD",
        "MediumOrchid", "#BA55D3",
        "MediumPurple", "#9370D8",
        "MediumSeaGreen", "#3CB371",
        "MediumSlateBlue", "#7B68EE",
        "MediumSpringGreen", "#00FA9A",
        "MediumTurquoise", "#48D1CC",
        "MediumVioletRed", "#C71585",
        "MidnightBlue", "#191970",
        "MintCream", "#F5FFFA",
        "MistyRose", "#FFE4E1",
        "Moccasin", "#FFE4B5",
        "NavajoWhite", "#FFDEAD",
        "Navy", "#000080",
        "OldLace", "#FDF5E6",
        "Olive", "#808000",
        "OliveDrab", "#6B8E23",
        "Orange", "#FFA500",
        "OrangeRed", "#FF4500",
        "Orchid", "#DA70D6",
        "PaleGoldenRod", "#EEE8AA",
        "PaleGreen", "#98FB98",
        "PaleTurquoise", "#AFEEEE",
        "PaleVioletRed", "#D87093",
        "PapayaWhip", "#FFEFD5",
        "PeachPuff", "#FFDAB9",
        "Peru", "#CD853F",
        "Pink", "#FFC0CB",
        "Plum", "#DDA0DD",
        "PowderBlue", "#B0E0E6",
        "Purple", "#800080",
        "Red", "#FF0000",
        "RosyBrown", "#BC8F8F",
        "RoyalBlue", "#4169E1",
        "SaddleBrown", "#8B4513",
        "Salmon", "#FA8072",
        "SandyBrown", "#F4A460",
        "SeaGreen", "#2E8B57",
        "SeaShell", "#FFF5EE",
        "Sienna", "#A0522D",
        "Silver", "#C0C0C0",
        "SkyBlue", "#87CEEB",
        "SlateBlue", "#6A5ACD",
        "SlateGray", "#708090",
        "SlateGrey", "#708090",
        "Snow", "#FFFAFA",
        "SpringGreen", "#00FF7F",
        "SteelBlue", "#4682B4",
        "Tan", "#D2B48C",
        "Teal", "#008080",
        "Thistle", "#D8BFD8",
        "Tomato", "#FF6347",
        "Turquoise", "#40E0D0",
        "Violet", "#EE82EE",
        "Wheat", "#F5DEB3",
        "White", "#FFFFFF",
        "WhiteSmoke", "#F5F5F5",
        "Yellow", "#FFFF00",
        "YellowGreen", "#9ACD32"
    )
}
