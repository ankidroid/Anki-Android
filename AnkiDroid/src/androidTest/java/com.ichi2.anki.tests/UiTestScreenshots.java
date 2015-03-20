/****************************************************************************************
 * Copyright (c) 2015 John Shevek <johnshevek@gmail.com>                                *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.tests;

//Import the uiautomator libraries
import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiObjectNotFoundException;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

import java.io.File;

// The purpose of this class is to traverse the majority of the screens of interest
// in the anki app taking screenshots.   To improve readability, it uses an array of
// string commands to do most of this, with occasional handling of edge cases through
// normal methods. For example:
//   {TEXT, "Identifier", SCREENSHOT, BACK}, means: "Click the UI element that has the
//     text 'Identifier', take a screenshot, then press the back button.)
// Please be careful with auto-formatting, as it may make some of the code less readable.

// To run this test, the jar file must be installed on the device and launched via adb shell.
// At the moment, the anki app must be already open when this is launched. Other OS events
// (calls, alarms, etc) may break the test.  To create the jar I do the following:
// $ cd AnkiDroid/build/intermediates/classes/test/debug/
// $ /home/js/bin/android-studio/sdk/build-tools/19.0.0/dx --dex --output=UiTestScreenshots.jar com/ichi2/anki/tests/UiTestScreenshots.class
// Then push to the device
// $ adb push UiTestScreenshots.jar /data/local/tmp
// Open anki to the deckpicker screen on the device, then launch:
// $ adb shell "uiautomator runtest /data/local/tmp/UiTestScreenshots.jar" >> uiautomator.output

// Note that 'failing to find and click a particular element' will throw an exception, but is
//  not necessarily a concerning failure for this process.  This often just means that app was
//  not exactly in the assumed state, and in some cases the test code recovers and continues on
//  correctly.

public class UiTestScreenshots extends UiAutomatorTestCase {
//    private final static String PRE = "com.ichi2.anki_themes:id/";  // Keep this to switch between versions of the app
        private final static String PRE = "com.ichi2.anki:id/";   // Keep this to switch between versions of the app
    private final static String SCREENSHOT_PATH = "/extSdCard/anki-screenshots/";
    private final static String OVERFLOW_MENU = "More options";

    // This test is launched via the adb shell, and this System.out.println method seems the only way to collect tracing / logging output
    private static void log(String msgParam) {
        String msg = "<< ANKI >> - " + msgParam;
        System.out.println(msg);
    }

    // Click the first view that contains the text given
    private void clickText(String s) {
        log("clickText: " + s);
        mLastCommand = s;
        try {
            new UiObject(new UiSelector().textContains(s)).click();
        } catch (UiObjectNotFoundException e) {
            log(" Error - Text not found: " + s);
            e.printStackTrace();
        }
    }

    // Click the view that exactly matches the given text
    private void clickExactTextWait(String s) {
        log("clickExactText: " + s);
        mLastCommand = s;
        try {
            new UiObject(new UiSelector().text(s)).clickAndWaitForNewWindow();
        } catch (UiObjectNotFoundException e) {
            log(" Error - Text not found: " + s);
            e.printStackTrace();
        }
    }


    // Click the view that exactly matches the given text
    private void clickExactText(String s) {
        log("clickExactText: " + s);
        mLastCommand = s;
        try {
            new UiObject(new UiSelector().text(s)).click();
        } catch (UiObjectNotFoundException e) {
            log(" Error - Text not found: " + s);
            e.printStackTrace();
        }
    }

    // Like clickText, except wait for the window to appear
    private void clickTextWait(String s) {
        log("clickTextWait: " + s);
        mLastCommand = s;
        UiObject button = new UiObject(new UiSelector().textContains(s));
        try {
            button.clickAndWaitForNewWindow();
        } catch (UiObjectNotFoundException e) {
            log(" Error - Text not found: " + s);
            e.printStackTrace();
        }
    }

    // Especially for dismissing dialogs
    private void cancel() {
        clickExactText("Cancel");
    }


    // Given a string that matches the resource Id of a view on the screen, click that view.
    private void clickId(String s) {
        log("clickId: " + s);
        mLastCommand = s;
        UiObject button = new UiObject(new UiSelector().resourceId(s));
        try {
            button.click();
        } catch (UiObjectNotFoundException e) {
            log(" Error - ID not found: " + s);
            e.printStackTrace();
        }
    }

    // Given a string that matches the resource Id of a view on the screen, click that view and wait for new window
    private void clickIdWait(String s) {
        log("clickIdWait: " + s);
        mLastCommand = s;
        try {
            new UiObject(new UiSelector().resourceId(s)).clickAndWaitForNewWindow();
        } catch (UiObjectNotFoundException e) {
            log(" Error - ID not found: " + s);
            e.printStackTrace();
        }
    }

    private void enterTextClassName(String uiClassName, String test) {
        log("enterTextClassName: " + uiClassName + ",   " + test);
        mLastCommand = uiClassName;
        try {
            new UiObject(new UiSelector().className(uiClassName)).setText(test);
        } catch (UiObjectNotFoundException e) {
            log(" Error - ID not found: " + uiClassName);
            e.printStackTrace();
        }
    }

    private void clickDesc(String s) {
        log("clickText: " + s);
        mLastCommand = s;
        try {
            new UiObject(new UiSelector().descriptionContains(s)).click();
        } catch (UiObjectNotFoundException e) {
            log(" Error - Description not found: " + s);
            e.printStackTrace();
        }
    }


    // Tally of screenshots,  used in the filename; unique to each successful screenshot made during this run
    // Your device must have the correct file path available, and write permission
    // Notice that, currently, the content of 's' is used in the file name, unchecked.
    int mScreenshotCount = 0;

    private void screenshot(String s) {
        log("Screenshot: "+s);
        mScreenshotCount++;
        // If the last command is of the form "com.ichi2.anki:id/multimedia_edit_field_to_text"
        //  then extract only the part after the "/"
        if (s.contains("/")) {
            s = s.split("/")[1];
        }



        getUiDevice().takeScreenshot(new File(SCREENSHOT_PATH + "Screenshot_" + mScreenshotCount + "_" + s.replace(" ", "_") + ".png"));
    }



    private void screenshot() {
        screenshot(mLastCommand);
    }

    private void back() {
        log("back");
        getUiDevice().pressBack();
    }

    String mLastCommand = "First command";

    private void screenshotDeckPickerAndAddNote() {

        executeCommands( new String[]{
                //  ----- DECK PICKER -----
                ID, PRE + "action_add_decks", SCREENSHOT,
                TEXT_WAIT, "Get shared decks", SCREENSHOT, BACK,
                ID, PRE + "action_add_decks",
                TEXT_WAIT, "Create deck", SCREENSHOT, BACK,
                ID, PRE + "action_add_decks",
                TEXT_WAIT, "Add note", SCREENSHOT,
                //  ----- ADD NOTE SCREEN -----
                ID_WAIT, PRE + "note_type_spinner", SCREENSHOT, BACK,
//                ID, PRE + "simple_spinner_dropdown_item", // dismiss spinner
                ID_WAIT, PRE + "note_deck_spinner", SCREENSHOT, BACK,
//                ID, PRE + "simple_spinner_dropdown_item", // dismiss spinner
                ID, PRE + "id_media_button",
                TEXT_WAIT, "Add image", SCREENSHOT,
                TEXT, "Gallery", SCREENSHOT, BACK,
                TEXT, "Camera", SCREENSHOT, BACK,
                ID_WAIT, PRE + "multimedia_edit_field_to_text", SCREENSHOT // Pushes the 'T' button
        });
        enterTextClassName("android.widget.EditText", "test");  // enter text
        executeCommands(new String[] {
                TEXT, "Translation", SCREENSHOT, BACK,
                TEXT, "Pronunciation", SCREENSHOT, BACK,
                TEXT_WAIT, "Image", SCREENSHOT, BACK,
                BACK,  // To 'add note' screen
                DESC, OVERFLOW_MENU, SCREENSHOT,
                BACK // to deck picker
        });

        clickText("Translation");        screenshot();        back();
        clickText("Pronunciation");        screenshot();        back();
        clickTextWait(          "Image"            ); screenshot(); back(); // TODO need to make this wait longer before the screenshot
        back();  // To 'add note' screen
        // TODO JS actionbar menu
        back(); // to deck picker
        // Back in the deck picker
        // Skipping the 'refresh' dialog

        // TODO action bar dropdown - definitely
    }

    private void screenshotCustomStudySessionAndEditCard() {
        gotoDeckPicker();
        clickTextWait("Custom study session");
        screenshot(); // Assumes that this deck exists!
        //  ----- Study Options / Deck Options -----
        // Within the study options screen, go straight to deck options


        clickTextWait("Deck options");
        screenshot();
        clickTextWait("Search");
        screenshot();
        cancel();
        clickTextWait("Limit to");
        screenshot();
        cancel();
        clickTextWait("cards selected by");
        screenshot();
        cancel();
//        clickTextWait(      "Custom steps - skip");      screenshot(); cancel(); // Skip custom steps
        // don't touch the checkboxes
        back(); // to study options
        // Back in study options
        // Ignoring rebuild and empty - need to test a non-custom deck
        // TODO - how to get the menu dropdown?
        // TODO - also do the menu dropdown on all screens
        clickTextWait("Empty");
        screenshot();
        clickTextWait("Rebuild");
        screenshot();

        //  ----- Study -----
        clickIdWait(PRE + "studyoptions_start");
        screenshot();
        clickText("Show answer");
        screenshot();
        // Since this is for screenshots, rather than testing _each_ ui element, we skip the answer buttons
        // TODO Menu dropdown
        back();
        clickId(PRE + "action_night_mode");
        screenshot();
        clickIdWait(PRE + "studyoptions_start");
        screenshot();
        clickText("Show answer");
        screenshot();
        clickDesc(OVERFLOW_MENU);
        screenshot(); // Selects the menu dropdown
        clickText("Hide");
        screenshot();
        back();
        clickDesc(OVERFLOW_MENU); // Menu:
        clickText("whiteboard");
        screenshot();// enable whiteboard
        clickDesc(OVERFLOW_MENU); // Menu:
        clickText("whiteboard"); //  disable whiteboard
        clickDesc(OVERFLOW_MENU); // Menu:
        clickText("Edit");  // 'Edit note' or 'Edit card'
        screenshot();

        //  ----- Edit card (under study             ) -----
//        clickDesc("Preview");  screenshot(); back();  // Not always present
        clickDesc(OVERFLOW_MENU);
        screenshot(); // Menu:
        clickText("Add note");
        screenshot();
        back();
        clickDesc(OVERFLOW_MENU);
        clickText("Copy card");
        screenshot();
        back();
        screenshot();  // reverse back and screenshot, as we are capturing the dialog
        clickText("OK");
        clickDesc(OVERFLOW_MENU);
        clickText("Reset progress");
        screenshot();
        back();
        clickDesc(OVERFLOW_MENU);
        clickText("Reschedule");
        screenshot();
        back();
        clickId(PRE + "note_type_spinner");
        screenshot(); back();
//        clickId(PRE + "note_type_spinner"); // Remove spinner by clicking a second time (instead of back)
        clickId(PRE + "note_deck_spinner");
        screenshot(); back();
//        clickId(PRE + "note_deck_spinner");
        clickId(PRE + "id_media_button");
        screenshot();
        back();  // Notice that this media screen is not explored in depth here. Is done elsewhere
        back(); // To the study screen
        back(); // To the study options screen
        clickId(PRE + "action_night_mode"); // turn off night mode
        clickTextWait("Custom study");
        screenshot(); // Bring up the navigation panel
        clickTextWait("Card Browser");
        screenshot();  // Switch to card browser activity
    }

    private void screenshotCardBrowser() {
        gotoCardBrowser();
        //  ----- Card browser -----
        clickIdWait("android:id/action_bar_spinner");
        screenshot();
        back(); // View the spinner in the action bar
        clickTextWait("Sort field");
        screenshot(); // Why does this even have a spinner? TODO JS ASK
        clickTextWait("Sort field");    // Make it go away
//        clickTextWait(       "Answer");   screenshot(); // Assumes! that 'answer' is the currently selected sort field in the right column
        clickIdWait(PRE + "browser_column2_spinner");
        screenshot(); // Assumes! that 'answer' is the currently selected sort field in the right column
        clickTextWait("Answer");   // Make it go away

        // Wait(  "Create deck"       );  screenshot(); back();

    }


    private void gotoCardBrowser() {
        clickIdWait("android:id/home");
        clickExactTextWait("Card Browser");
    }

    private void gotoDeckPicker() {
        clickIdWait("android:id/home");
        clickExactTextWait("Decks");
    }


    // This method performs the walk-through with screenshots of the app.
    // Comments on incomplete coverage should be included where appropriate
    public void testDemo() throws UiObjectNotFoundException {
        log("Starting UI test/screenshots");
        screenshotDeckPickerAndAddNote();
        gotoDeckPicker();
        screenshotCustomStudySessionAndEditCard();
        screenshotCardBrowser();


/*
        String[] s = new String[]{
                // Back in the deck picker
                // Skipping the 'refresh' dialog
                // TODO action bar dropdown - definitely
                TEXT_WAIT, "Custom study session", SCREENSHOT, BACK,  // Assumes that this deck exists!

                // Within the study options screen
                TEXT_WAIT, "Deck options", SCREENSHOT,
                TEXT_WAIT, "Search", SCREENSHOT, BACK, BACK, // Two backs - one to kill the keyboard.  (Or, use 'cancel')
                TEXT_WAIT, "Limit to", SCREENSHOT, BACK, BACK, // Two backs - one to kill the keyboard.  (Or, use 'cancel')
                // TODO - do the other fields here
                BACK, // to study options
                // Ignoring rebuild and empty - need to test a non-custom deck
                TEXT, "Study", SCREENSHOT,
                TEXT, "Show answer", SCREENSHOT,
                // do the menu drop downs
        };
*/
    }

    // Commands used to traverse the app:
    private final static String ID = "ANKI_COMMAND_ID";  // Find this element (by ID) on the screen and click it
    private final static String ID_WAIT = "ANKI_COMMAND_ID_WAIT";  // As above, but wait for the new window
    private final static String TEXT = "ANKI_COMMAND_TEXT";  // Find this element (by text) on the screen and click it
    private final static String TEXT_WAIT = "ANKI_COMMAND_TEXT_WAIT";
    private final static String SCREENSHOT = "ANKI_COMMAND_SCREENSHOT";  // Take a screenshot
    private final static String BACK = "ANKI_COMMAND_BACK";  // Hit the back button
    private final static String DESC = "ANKI_COMMAND_DESCRIPTION";  //  Find this element (by Content Description) and click it
    private final static String CANCEL = "ANKI_COMMAND_CANCEL";  // Hit the cancel button, usually on a dialog


    // To make the tests easier to read and edit, I'm using an array of string commands.
    // This method iterates over that array and executes those commands.
    private void executeCommands(String[] commands) {
        // lastButton allows the name of the last view that was clicked to be included in the screenshot filename
        String lastButton = "Starting button";
        for (int index = 0; index < commands.length; index++) {
            log("Executing: "+commands[index]);
            switch (commands[index]) {
                case SCREENSHOT:
                    screenshot(lastButton);
                    break;
                case BACK:
                    getUiDevice().pressBack();
                    break;
                case TEXT:
                    index++;
                    lastButton = commands[index];
                    clickText(lastButton);
                    break;
                case ID:
                    index++;
                    lastButton = commands[index];
                    clickId(lastButton);
                    break;
                case TEXT_WAIT:
                    index++;
                    lastButton = commands[index];
                    clickTextWait(lastButton);
                    break;
                case ID_WAIT:
                    index++;
                    lastButton = commands[index];
                    clickIdWait(lastButton);
                    break;
                case DESC:
                    index++;
                    lastButton = commands[index];
                    clickDesc(lastButton);
                    break;
                case CANCEL:
                    cancel();
                    break;
                default:
                    log("Error - no command at index " + index + ".  \"" + commands[index] + "\" found instead.");
                    break;
            }
        }
    }
}


/* Reference:

 UiScrollable appViews = new UiScrollable(new UiSelector()
 .scrollable(true));

 // Set the swiping mode to horizontal (the default is vertical)
 appViews.setAsHorizontalList();

 // Create a UiSelector to find the Settings app and simulate
 // a user click to launch the app.
 UiObject settingsApp = appViews.getChildByText(new UiSelector()
 .className(android.widget.TextView.class.getName()),
 "Settings");
 settingsApp.clickAndWaitForNewWindow();

 // Validate that the package name is the expected one
 UiObject settingsValidation = new UiObject(new UiSelector()
 .packageName("com.android.settings"));
 assertTrue("Unable to detect Settings",
 settingsValidation.exists());

 */

// TODO JS: Is it possible to launch the app from anywhere, using code like this:
//    private static final String TEST_APP_PKG = "com.ichi2.anki";
//    private static final Intent START_MAIN_ACTIVITY = new Intent(Intent.ACTION_MAIN)
//            .setComponent(new ComponentName(TEST_APP_PKG, TEST_APP_PKG + ".MainActivity"))
//            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
