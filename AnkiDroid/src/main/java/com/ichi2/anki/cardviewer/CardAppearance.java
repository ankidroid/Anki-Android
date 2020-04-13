package com.ichi2.anki.cardviewer;

import android.content.SharedPreferences;
import android.os.Build;

import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.themes.Themes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.CheckResult;

/** Responsible for calculating CSS and element styles and modifying content on a flashcard */
public class CardAppearance {

    /** Max size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MAX_SIZE = 14;

    /** Min size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MIN_SIZE = 3;
    private static final int DYNAMIC_FONT_FACTOR = 5;

    /** Constant for class attribute signaling answer */
    public static final String ANSWER_CLASS = "\"answer\"";

    /** Constant for class attribute signaling question */
    public static final String QUESTION_CLASS = "\"question\"";

    private final int mCardZoom;
    private final int mImageZoom;
    private final boolean mNightMode;
    private final boolean mCenterVertically;
    private final ReviewerCustomFonts mCustomFonts;

    public CardAppearance(ReviewerCustomFonts customFonts, int cardZoom, int imageZoom, boolean nightMode, boolean centerVertically) {
        this.mCustomFonts = customFonts;
        this.mCardZoom = cardZoom;
        this.mImageZoom = imageZoom;
        this.mNightMode = nightMode;
        this.mCenterVertically = centerVertically;
    }

    public boolean isNightMode() {
        return mNightMode;
    }

    public static CardAppearance create(ReviewerCustomFonts customFonts, SharedPreferences preferences) {
        int cardZoom = preferences.getInt("cardZoom", 100);
        int imageZoom = preferences.getInt("imageZoom", 100);
        boolean nightMode = preferences.getBoolean("invertedColors", false);
        boolean centerVertically = preferences.getBoolean("centerVertically", false);
        return new CardAppearance(customFonts, cardZoom, imageZoom, nightMode, centerVertically);
    }


    public static String fixBoldStyle(String content) {
        // In order to display the bold style correctly, we have to change
        // font-weight to 700
        return content.replace("font-weight:600;", "font-weight:700;");
    }

    /**
     * Converts characters in Unicode Supplementary Multilingual Plane (SMP) to their equivalent Html Entities. This is
     * done because webview has difficulty displaying these characters.
     *
     * @param text the text co convert to HTML Entities
     * @return The resultant text with SMP characters converted to entities
     */
    public static String convertSmpToHtmlEntity(String text) {
        //This is believed to be unused on current WebViews. See discussion:
        //SMP fails on my Anki Desktop, and displays fine to me without this patch.
        //https://github.com/ankidroid/Anki-Android/pull/5988#issuecomment-612683552
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return text;
        }
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("([^\u0000-\uFFFF])").matcher(text);
        while (m.find()) {
            String a = "&#x" + Integer.toHexString(m.group(1).codePointAt(0)) + ";";
            m.appendReplacement(sb, Matcher.quoteReplacement(a));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Below could be in a better abstraction. **/
    public void appendCssStyle(StringBuilder style) {
        // Zoom cards
        if (mCardZoom != 100) {
            style.append(String.format("body { zoom: %s }\n", mCardZoom / 100.0));
        }

        // Zoom images
        if (mImageZoom != 100) {
            style.append(String.format("img { zoom: %s }\n", mImageZoom / 100.0));
        }
    }

    @CheckResult
    public String getCssClasses(int currentTheme) {
        StringBuilder cardClass = new StringBuilder();

        if (mCenterVertically) {
            cardClass.append(" vertically_centered");
        }

        if (mNightMode) {
            // Enable the night-mode class
            cardClass.append(" night_mode");

            // Emit the dark_mode selector to allow dark theme overrides
            if (currentTheme == Themes.THEME_NIGHT_DARK) {
                cardClass.append(" ankidroid_dark_mode");
            }
        } else {
            // Emit the plain_mode selector to allow plain theme overrides
            if (currentTheme == Themes.THEME_DAY_PLAIN) {
                cardClass.append(" ankidroid_plain_mode");
            }
        }
        return cardClass.toString();
    }

    /**
     * Calculates a dynamic font size depending on the length of the contents taking into account that the input string
     * contains html-tags, which will not be displayed and therefore should not be taken into account.
     *
     * @param htmlContent The content to measure font size for
     * @return font size respecting MIN_DYNAMIC_FONT_SIZE and MAX_DYNAMIC_FONT_SIZE
     */
    public static int calculateDynamicFontSize(String htmlContent) {
        // NB: Comment seems incorrect
        // Replace each <br> with 15 spaces, each <hr> with 30 spaces, then
        // remove all html tags and spaces
        String realContent = htmlContent.replaceAll("\\<br.*?\\>", " ");
        realContent = realContent.replaceAll("\\<hr.*?\\>", " ");
        realContent = realContent.replaceAll("\\<.*?\\>", "");
        realContent = realContent.replaceAll("&nbsp;", " ");
        return Math.max(DYNAMIC_FONT_MIN_SIZE, DYNAMIC_FONT_MAX_SIZE - realContent.length() / DYNAMIC_FONT_FACTOR);
    }


    public String getStyle() {
        StringBuilder style = new StringBuilder();

        mCustomFonts.updateCssStyle(style);

        this.appendCssStyle(style);

        return style.toString();
    }


    public String getCardClass(int oneBasedCardOrdinal, int currentTheme) {
        String cardClass = "card card" + oneBasedCardOrdinal;

        cardClass += getCssClasses(currentTheme);

        return cardClass;
    }

    /**
     * Adds a div html tag around the contents to have an indication, where answer/question is displayed
     *
     * @param content The content to surround with tags.
     * @param isAnswer if true then the class attribute is set to "answer", "question" otherwise.
     * @return The enriched content
     */
    public static String enrichWithQADiv(String content, boolean isAnswer) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=");
        if (isAnswer) {
            sb.append(ANSWER_CLASS);
        } else {
            sb.append(QUESTION_CLASS);
        }
        sb.append(" id=\"qa\">");
        sb.append(content);
        sb.append("</div>");
        return sb.toString();
    }
}
