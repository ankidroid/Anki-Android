//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.multimediacard.beolingus.parsing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BeolingusParserTest {

    @Test
    public void testPronunciation() {
        String html = ""
                + "<a href=\"/dings.cgi?speak=de/0/7/52qA5FttGIU;text=Wasser\" "
                + "onclick=\"return s(this)\" onmouseover=\"return u('Wasser')\">"
                + "<img src=\"/pics/s1.png\" width=\"16\" height=\"16\" "
                + "alt=\"[anhören]\" title=\"Wasser\" border=\"0\" align=\"top\" /></a>";

        String pronunciationUrl = BeolingusParser.getPronunciationAddressFromTranslation(html, "Wasser");
        assertEquals("https://dict.tu-chemnitz.de/dings.cgi?speak=de/0/7/52qA5FttGIU;text=Wasser", pronunciationUrl);
    }

    @Test
    public void testHaystackCaseInsensitivity() {
        //#5810 - a search for "hello" did not match "Hello".
        String html = ""
                + "<a href=\"/dings.cgi?speak=en/2/0/zQbP7qZh_u2;text=Hello\" "
                + "onclick=\"return s(this)\" onmouseover=\"return u('Hello')\">"
                + "<img src=\"/pics/s1.png\" width=\"16\" height=\"16\" "
                + "alt=\"[listen]\" title=\"Hello\" border=\"0\" align=\"top\" /></a>";

        String pronunciationUrl = BeolingusParser.getPronunciationAddressFromTranslation(html, "hello");
        assertEquals("https://dict.tu-chemnitz.de/dings.cgi?speak=en/2/0/zQbP7qZh_u2;text=Hello", pronunciationUrl);
    }

    @Test
    public void testNeedleCaseInsensitivity() {
        //#5810 - confirm "HELLO" matches "Hello"
        String html = ""
                + "<a href=\"/dings.cgi?speak=en/2/0/zQbP7qZh_u2;text=Hello\" "
                + "onclick=\"return s(this)\" onmouseover=\"return u('Hello')\">"
                + "<img src=\"/pics/s1.png\" width=\"16\" height=\"16\" "
                + "alt=\"[listen]\" title=\"Hello\" border=\"0\" align=\"top\" /></a>";

        String pronunciationUrl = BeolingusParser.getPronunciationAddressFromTranslation(html, "HELLO");
        assertEquals("https://dict.tu-chemnitz.de/dings.cgi?speak=en/2/0/zQbP7qZh_u2;text=Hello", pronunciationUrl);
    }


    @Test
    public void testEszettCasing() {
        // Some transformations lose the Eszett: "ß".toUpperCase() == "SS".
        // Ensure that we don't do this.
        String html = ""
                + "<a href=\"/dings.cgi?speak=de/8/9/5wbPa4jy41_;text=Straße\" "
                + "onclick=\"return s(this)\" onmouseover=\"return u('Straße')\">"
                + "<img src=\"/pics/s1.png\" width=\"16\" height=\"16\" "
                + "alt=\"[listen]\" title=\"Straße\" border=\"0\" align=\"top\" /></a>";

        String pronunciationUrl = BeolingusParser.getPronunciationAddressFromTranslation(html, "straße");
        assertEquals("https://dict.tu-chemnitz.de/dings.cgi?speak=de/8/9/5wbPa4jy41_;text=Straße", pronunciationUrl);
    }

    @Test
    public void testMp3() {
        String html = "<td><a href=\"/speak-de/0/7/52qA5FttGIU.mp3\">Mit Ihrem";

        String mp3 = BeolingusParser.getMp3AddressFromPronounciation(html);
        assertEquals("https://dict.tu-chemnitz.de/speak-de/0/7/52qA5FttGIU.mp3", mp3);
    }
}
