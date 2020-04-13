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
    public void testMp3() {
        String html = "<td><a href=\"/speak-de/0/7/52qA5FttGIU.mp3\">Mit Ihrem";

        String mp3 = BeolingusParser.getMp3AddressFromPronounciation(html);
        assertEquals("https://dict.tu-chemnitz.de/speak-de/0/7/52qA5FttGIU.mp3", mp3);
    }
}
