package com.ichi2.anki.tests;

//Import the uiautomator libraries

import com.android.uiautomator.core.UiObject;

import com.android.uiautomator.testrunner.UiAutomatorTestCase;

import com.android.uiautomator.core.UiSelector;

import com.android.uiautomator.core.UiObjectNotFoundException;

import com.android.uiautomator.core.UiScrollable;

public class AnkiUiScreenshots extends UiAutomatorTestCase {

	public void testingCalculator() throws UiObjectNotFoundException {   

		// First we testing the press of the HOME button.

		getUiDevice().pressHome();

		// using the uiautomatorviewer tool we found that the button for the "applications" has

		//the value “Apps” (screen9)

		// so we use this property to create a UiSelector to find the button.

		UiObject Applications = new UiObject(new UiSelector().description("Apps"));

		// testing the click to bring up the All Apps screen.

		Applications.clickAndWaitForNewWindow();

		// In the All Apps screen, the "Calculator" application is located in

		// the Apps tab. So, we create a UiSelector to find a tab with the text

		// label “Apps”.

		UiObject apps = new UiObject(new UiSelector().text("Apps"));

		// and then testing the click to this tab in order to enter the Apps tab.

		apps.click();

		// All the applications are in a scrollable list

		// so first we need to get a reference to that list

		UiScrollable ListOfapplications = new UiScrollable(new UiSelector().scrollable(true));

		// and then trying to find the application

		// with the name AnkiDroid

		UiObject ankidroidApp = ListOfapplications.getChildByText(new UiSelector().className(android.widget.TextView.class.getName()),"AnkiDroid");

		ankidroidApp.clickAndWaitForNewWindow();

		// now the AnkiDroid app is open

		// so we can test the press of button "7" using the ID "com.android.calculator2:id/digit7"

		//we found by using uiautomatorviewer



        // Expect test to break here, now:
		UiObject seven = new UiObject(new UiSelector().resourceId("com.android.calculator2:id/digit7"));

		seven.click();

		// now we test the press of button "+"

		UiObject plus = new UiObject(new UiSelector().resourceId("com.android.calculator2:id/plus"));

		plus.click();

		// and then the press of button "1"

		UiObject one = new UiObject(new UiSelector().resourceId("com.android.calculator2:id/digit1"));

		one.click();

		// we test the press of button "="

		UiObject result = new UiObject(new UiSelector().resourceId("com.android.calculator2:id/equal"));

		result.click();

		//and finally we test the press of "Back" button

		getUiDevice().pressBack();

	}

}


/***

 Other test file for reference:

 package com.ichi2.anki;

 // Import the uiautomator libraries
 import com.android.uiautomator.core.UiObject;
 import com.android.uiautomator.core.UiObjectNotFoundException;
 import com.android.uiautomator.core.UiScrollable;
 import com.android.uiautomator.core.UiSelector;
 import com.android.uiautomator.testrunner.UiAutomatorTestCase;

 public class LaunchSettings extends UiAutomatorTestCase {

 public void testDemo() throws UiObjectNotFoundException {

 // Simulate a short press on the HOME button.
 getUiDevice().pressHome();

 // We’re now in the home screen. Next, we want to simulate
 // a user bringing up the All Apps screen.
 // If you use the uiautomatorviewer tool to capture a snapshot
 // of the Home screen, notice that the All Apps button’s
 // content-description property has the value “Apps”.  We can
 // use this property to create a UiSelector to find the button.
 UiObject allAppsButton = new UiObject(new UiSelector()
 .description("Apps"));

 // Simulate a click to bring up the All Apps screen.
 allAppsButton.clickAndWaitForNewWindow();

 // In the All Apps screen, the Settings app is located in
 // the Apps tab. To simulate the user bringing up the Apps tab,
 // we create a UiSelector to find a tab with the text
 // label “Apps”.
 UiObject appsTab = new UiObject(new UiSelector()
 .text("Apps"));

 // Simulate a click to enter the Apps tab.
 appsTab.click();

 // Next, in the apps tabs, we can simulate a user swiping until
 // they come to the Settings app icon.  Since the container view
 // is scrollable, we can use a UiScrollable object.
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
 }
 }

 */