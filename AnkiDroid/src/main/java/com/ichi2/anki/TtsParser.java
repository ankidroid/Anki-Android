package com.ichi2.anki;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Parse card sides, extracting text snippets that should be read using a text-to-speech engine.
 */
public final class TtsParser {
    /**
     * Returns the list of text snippets contained in the given HTML fragment that should be read
     * using the Android text-to-speech engine, together with the languages they are in.
     * <p>
     * Each returned LocalisedText object contains the text extracted from a &lt;tts&gt; element
     * whose 'service' attribute is set to 'android', and the localeCode taken from the 'voice'
     * attribute of that element. This holds unless the HTML fragment contains no such &lt;tts&gt;
     * elements; in that case the function returns a single LocalisedText object containing the
     * text extracted from the whole HTML fragment, with the localeCode set to an empty string.
     */
    public static List<LocalisedText> getTextsToRead(String html) {
        List<LocalisedText> textsToRead = new ArrayList<>();

        Element elem = Jsoup.parseBodyFragment(html).body();
        parseTtsElements(elem, textsToRead);
        if (textsToRead.size() == 0) {
            // No <tts service="android"> elements found: return the text of the whole HTML fragment
            textsToRead.add(new LocalisedText(elem.text()));
        }

        return textsToRead;
    }

    private static void parseTtsElements(Element element, List<LocalisedText> textsToRead) {
        if (element.tagName().equalsIgnoreCase("tts") &&
                element.attr("service").equalsIgnoreCase("android")) {
            textsToRead.add(new LocalisedText(element.text(), element.attr("voice")));
            return; // ignore any children
        }

        for (Element child : element.children()) {
            parseTtsElements(child, textsToRead);
        }
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    /**
     * Snippet of text accompanied by its locale code (if known).
     */
    public static final class LocalisedText {
        private String mText;
        private String mLocaleCode;

        /**
         * Construct an object representing a snippet of text in an unknown locale.
         */
        public LocalisedText(String text) {
            mText = text;
            mLocaleCode = "";
        }

        /**
         * Construct an object representing a snippet of text in a particular locale.
         *
         * @param localeCode A string representation of a locale in the format returned by
         *                   Locale.toString().
         */
        public LocalisedText(String text, String localeCode) {
            mText = text;
            mLocaleCode = localeCode;
        }

        public String getText() {
            return mText;
        }

        public String getLocaleCode() {
            return mLocaleCode;
        }
    }
}
