package com.ichi2.anki.tests;

//Import the uiautomator libraries

import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiObjectNotFoundException;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

import java.io.File;

// The purpose of this class is to traverse the majority
//   of the screens of interest in the anki app, and take a screenshot.
// This uses an "array of string commands" to do this, which may seem odd, however: I've tried to
//   write out the method calls directly, and found it verbose and difficult to re-read and tweak. I've
//   also tried iterating over a variety of arrays of strings that match the structure of the app (arrays
//   describing each menu, screen, etc )and found it awkward, due to the lack of consistency of
//   follow-up actions.
// I think this is the easiest and most clear way to describe the actions to take to traverse the app and take screenshots:
//   One large array of strings describes the actions to be performed, which can be grouped by line into 'sentences' like:
//                    TEXT, "Image", SCREENSHOT, BACK,
//   Which means "Click the UI element that has the text 'image', take a screen shot, then press the back button.
//   Obviously, each TEXT, ID, etc command must be followed by a string describing the element to select, or this breaks.

public class UiTestScreenshots extends UiAutomatorTestCase {

    private static final String TEST_APP_PKG = "com.ichi2.anki";

    // TODO JS: Is it possible to launch the app from anywhere, using code like this:
//    private static final Intent START_MAIN_ACTIVITY = new Intent(Intent.ACTION_MAIN)
//            .setComponent(new ComponentName(TEST_APP_PKG, TEST_APP_PKG + ".MainActivity"))
//            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);


    // This code is launched via the adb shell, and this is the only way to collect tracing / logging output
    private static void log(String msgParam) {
        String msg = "<< ANKI >> - " + msgParam;
        System.out.println(msg);
    }

    // Given a string that matches text on the screen, click the corresponding view.
    private void clickText(String s) {
        log("clickText: " + s);
        UiObject button = new UiObject(new UiSelector().text(s));
        try {
            button.click();
        } catch (UiObjectNotFoundException e) {
            log(" Error - ID not found: "+s);
            e.printStackTrace();
        }
    }

    // Like clickText, except wait for the window to apear
    private void clickTextAndWait(String s) {
        log("clickText: " + s);
        UiObject button = new UiObject(new UiSelector().text(s));
        try {
            button.clickAndWaitForNewWindow();
        } catch (UiObjectNotFoundException e) {
            log(" Error - ID not found: "+s);
            e.printStackTrace();
        }
    }


    // Given a string that matches the resource Id of a view on the screen, click that view.
    private void clickId(String s) {
        log("clickId: " + s);
        UiObject button = new UiObject(new UiSelector().resourceId(s));
        try {
            button.click();
        } catch (UiObjectNotFoundException e) {
            log(" Error - ID not found: "+s);
            e.printStackTrace();
        }
    }



    // Tally of screenshots,  used in the filename; unique to each successful screenshot made during this run
    int screenshotCount = 0;
    private void screenshot(String s) {
        screenshotCount++;
        getUiDevice().takeScreenshot(new File("/extSdCard/anki-screenshots/Screenshot_"+screenshotCount+"_"+s+".png"));
    }

    // Commands used to traverse the app:
    private final static String ID = "ANKI_COMMAND_ID";  // Find this element (by ID) on the screen and click it
    private final static String ID_WAIT = "ANKI_COMMAND_ID_WAIT";  // As above, but wait for the new window
    private final static String TEXT = "ANKI_COMMAND_TEXT";  // Find this element (by text) on the screen and click it
    private final static String TEXT_WAIT = "ANKI_COMMAND_TEXT_WAIT";
    private final static String SCREENSHOT = "ANKI_COMMAND_SCREENSHOT";  // Take a screen shot
    private final static String BACK = "ANKI_COMMAND_BACK";  // Hit the back button


    public void testDemo() throws UiObjectNotFoundException {
        log("Starting UI test/screenshots");

        String[] allCommands = new String[]{
                // Starting on the 'deck_picker' screen
                // Click the 'add decks' button
                ID, "com.ichi2.anki_themes:id/action_add_decks",
                TEXT_WAIT, "Get shared decks", SCREENSHOT, BACK,
                ID, "com.ichi2.anki_themes:id/action_add_decks",
                TEXT_WAIT, "Create deck", SCREENSHOT, BACK,
                ID, "com.ichi2.anki_themes:id/action_add_decks",
                TEXT_WAIT, "Add note", SCREENSHOT,

                // Within the 'add note' screen:
//                ID_WAIT, "com.ichi2.anki_themes:id/simple_spinner_item", BACK,
                TEXT_WAIT, "Basic-ea156", BACK,  // The id isn't working, so try this...
                ID, "com.ichi2.anki_themes:id/id_media_button",
                TEXT_WAIT, "Add image", SCREENSHOT,
                TEXT, "Gallery", SCREENSHOT, BACK,
                TEXT, "Camera", SCREENSHOT, BACK,
                // The "T" button - manually pressed it
                // TODO Input text!
                TEXT, "Translation", SCREENSHOT, BACK,
                TEXT, "Pronunciation", SCREENSHOT, BACK,
                TEXT_WAIT, "Image", SCREENSHOT, BACK,
                BACK,  // To 'add not' screen
                // TODO JS actionbar menu - bother?
                BACK, // to deck picker

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

                // ... only scores, maybe hundreds, of more UI events to go ...
        };


        String lastButton= "Starting button";

        for (int index = 0; index < allCommands.length; index++) {
            log("Executing: "+allCommands[index]);
            if (allCommands[index].equals(SCREENSHOT)) {
                screenshot(lastButton);
            } else if (allCommands[index].equals(BACK)) {
                getUiDevice().pressBack();
            } else if (allCommands[index].equals(TEXT)) {
                index++;
                lastButton = allCommands[index];
                clickText(lastButton);
            } else if (allCommands[index].equals(ID)) {
                index++;
                lastButton = allCommands[index];
                clickId(lastButton);
            } else if (allCommands[index].equals(TEXT_WAIT)) {
                index++;
                lastButton = allCommands[index];
                clickTextAndWait(lastButton);
            } else if (allCommands[index].equals(ID_WAIT)) {
                index++;
                lastButton = allCommands[index];
                clickTextAndWait(lastButton);
            } else {
                log("Error - no command at index "+index);
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