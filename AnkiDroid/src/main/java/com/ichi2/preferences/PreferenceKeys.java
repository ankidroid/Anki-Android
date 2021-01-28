/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.preferences;

/**
 * Keys to ensure consistency between XML and code defaults
 * TODO: No tests have been run on this yet
 * */
public class PreferenceKeys {
    public static PreferenceKey<Boolean> UseInputTag = new PreferenceKey<>("useInputTag", false);
    public static PreferenceKey<Boolean> NoCodeFormatting = new PreferenceKey<>("noCodeFormatting", false);
    public static PreferenceKey<String> Dictionary = new PreferenceKey<>("dictionary", "0");
    public static PreferenceKey<String> FullscreenMode = new PreferenceKey<>("fullscreenMode", "0");
    public static PreferenceKey<Integer> AnswerButtonSide = new PreferenceKey<>("answerButtonSize", 100);
    public static PreferenceKey<Boolean> Tts = new PreferenceKey<>("tts", false);
    public static PreferenceKey<Boolean> TimeoutAnswer = new PreferenceKey<>("timeoutAnswer", false);
    public static PreferenceKey<Integer> TimeoutAnswerSeconds = new PreferenceKey<>("timeoutAnswerSeconds", 20);
    public static PreferenceKey<Integer> TimeoutQuestionSeconds = new PreferenceKey<>("timeoutQuestionSeconds", 60);
    public static PreferenceKey<Boolean> ScrollingButtons = new PreferenceKey<>("scrolling_buttons", false);
    public static PreferenceKey<Boolean> DoubleScrolling = new PreferenceKey<>("double_scrolling", false);
    public static PreferenceKey<Boolean> ShowTopBar = new PreferenceKey<>("showTopbar", true);
    public static PreferenceKey<Boolean> LinkOverridesTouchGesture = new PreferenceKey<>("linkOverridesTouchGesture", false);

    public static PreferenceKey<String> GestureSwipeUp = new PreferenceKey<>("gestureSwipeUp", "9");
    public static PreferenceKey<String> GestureSwipeDown = new PreferenceKey<>("gestureSwipeDown", "0");
    public static PreferenceKey<String> GestureSwipeLeft = new PreferenceKey<>("gestureSwipeLeft", "8");
    public static PreferenceKey<String> GestureSwipeRight = new PreferenceKey<>("gestureSwipeRight", "17");
    public static PreferenceKey<String> GestureDoubleTap = new PreferenceKey<>("gestureDoubleTap", "7");
    public static PreferenceKey<String> GestureLongClick = new PreferenceKey<>("gestureLongclick", "11");
    public static PreferenceKey<String> GestureVolumeUp = new PreferenceKey<>("gestureVolumeUp", "0");
    public static PreferenceKey<String> GestureVolumeDown = new PreferenceKey<>("gestureVolumeDown", "0");

    public static PreferenceKey<Boolean> KeepScreenOn = new PreferenceKey<>("keepScreenOn", false);
    public static PreferenceKey<Boolean> HtmlJavascriptDebugging = new PreferenceKey<>("html_javascript_debugging", false);
    public static PreferenceKey<String> AnswerButtonPosition = new PreferenceKey<>("answerButtonPosition", "bottom"); // This exists in constants.xml - remove?

    // card appearance
    public static PreferenceKey<Integer> CardZoom = new PreferenceKey<>("cardZoom", 100);
    public static PreferenceKey<Integer> ImageZoom = new PreferenceKey<>("imageZoom", 100);
    public static PreferenceKey<Boolean> CenterVertically = new PreferenceKey<>("centerVertically", false);
    public static PreferenceKey<Boolean> InvertedColors = new PreferenceKey<>("invertedColors", false);


    public static class PreferenceKey<T> {
        public String key;
        public T defaultValue;

        public PreferenceKey(String key, T value) {
            this.key = key;
            this.defaultValue = value;
        }
    }
}
