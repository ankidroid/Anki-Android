//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import com.ichi2.libanki.template.TemplateFilters
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.ArrayList

/**
 * Parse card sides, extracting text snippets that should be read using a text-to-speech engine.
 */
object TtsParser {
    /**
     * Returns the list of text snippets contained in the given HTML fragment that should be read
     * using the Android text-to-speech engine, together with the languages they are in.
     *
     *
     * Each returned LocalisedText object contains the text extracted from a &lt;tts&gt; element
     * whose 'service' attribute is set to 'android', and the localeCode taken from the 'voice'
     * attribute of that element. This holds unless the HTML fragment contains no such &lt;tts&gt;
     * elements; in that case the function returns a single LocalisedText object containing the
     * text extracted from the whole HTML fragment, with the localeCode set to an empty string.
     */
    @JvmStatic
    fun getTextsToRead(html: String, clozeReplacement: String?): List<LocalisedText> {
        val textsToRead: MutableList<LocalisedText> = ArrayList()
        val elem = Jsoup.parseBodyFragment(html).body()
        parseTtsElements(elem, textsToRead)
        if (textsToRead.isEmpty()) {
            // No <tts service="android"> elements found: return the text of the whole HTML fragment
            textsToRead.add(LocalisedText(elem.text().replace(TemplateFilters.CLOZE_DELETION_REPLACEMENT, clozeReplacement!!)))
        }
        return textsToRead
    }

    private fun parseTtsElements(element: Element, textsToRead: MutableList<LocalisedText>) {
        if ("tts".equals(element.tagName(), ignoreCase = true) &&
            "android".equals(element.attr("service"), ignoreCase = true)
        ) {
            textsToRead.add(LocalisedText(element.text(), element.attr("voice")))
            return // ignore any children
        }
        for (child in element.children()) {
            parseTtsElements(child, textsToRead)
        }
    }
    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------
    /**
     * Snippet of text accompanied by its locale code (if known).
     */
    class LocalisedText {
        val text: String
        val localeCode: String

        /**
         * Construct an object representing a snippet of text in an unknown locale.
         */
        constructor(text: String) {
            this.text = text
            localeCode = ""
        }

        /**
         * Construct an object representing a snippet of text in a particular locale.
         *
         * @param localeCode A string representation of a locale in the format returned by
         * Locale.toString().
         */
        constructor(text: String, localeCode: String) {
            this.text = text
            this.localeCode = localeCode
        }
    }
}
