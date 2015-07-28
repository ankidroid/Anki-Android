# Introduction

This page contains a list of the manual tests to be performed ahead of a release.

A record of the manual test performed on each release can be found at ManualTestingRecord.

You can email new test scenarios to **flerda@gmail.com** with **"AnkiDroid Manual Testing"** in the subject.

To add new manual tests please add a subsection titled T followed by a number and a brief name describing the test scenario. In the section please list detailed steps for reproducing the test.

Please only update existing tests to reflect changes to the application, not to add new functionality to the test itself; add a new test for new functionality.

# Manual Tests

## T1: Downloading a shared deck
_Created on version: 1.1.2beta1_
  1. Install AnkiDroid.
  1. Open AnkiDroid.
  1. Say "No" to opening tutorial.
  1. Say "Ok" to "New version" dialog.
  1. Click on "Download deck" button.
  1. Select "Shared deck" option.
  1. Click on "Seach" field.
  1. Type "Geography".
  1. Click on "Geography Review" entry.
  1. Press "Back" until you get back to the device's home screen.
  1. Wait for notification of download completed.
  1. Pull down notification and click on "Geography Review" "Deck downloaded" notification.

## T2: Downloading a personal deck
_Created on version: 1.1.2beta1_
  1. Install AnkiDroid.
  1. Open AnkiDroid.
  1. Say "No" to opening tutorial.
  1. Say "Ok" to "New version" dialog.
  1. Click on "Download deck" button.
  1. Select "Personal deck" option.
  1. Choose "Log in"
  1. Use your own login and password
  1. Click on "Download deck" button.
  1. Select "Personal deck" option.
  1. Select one of your decks that is not already on the device.
  1. Press "Back" until you get back to the device's home screen.
  1. Wait for notification of download completed.
  1. Pull down notification and click on "Deck downloaded" notification.

## T3: Permissions
_Created on version: 1.1.3beta1_
  1. Download AnkiDroid's APK to the device.
  1. Open installation of AnkiDroid's APK.
  1. Before clicking the install button, check the permissions and compare them against:
> > https://play.google.com/store/apps/details?id=com.ichi2.anki
  1. Check no new permissions are added.
  1. Check no permissions are missing.
_Note: permission might sometimes change but please report any changes to the developers._

## T4: The AnkiWeb terms for downloading personal decks
_Created on version: 1.1.3beta1_
  1. Install AnkiDroid.
  1. Open AnkiDroid.
  1. Say "No" to opening tutorial.
  1. Say "Ok" to "New version" dialog.
  1. Click on "Download deck" button.
  1. Select "Personal deck" option.
  1. Choose "Log in".
  1. Use "resolver@gmail.com" as username and "abc" as password.
  1. Click on "Download deck" button.
  1. Select "Personal deck" option.
  1. Verify that you get a message saying "The terms for shared decks via ankiweb.net have changed and in order to use this service you need to agree to them. To do so, please visit http://ankiweb.net, agree to the terms and synching should work again."
  1. Press "Cancel".

## T5: The AnkiWeb terms for syncing
_Created on version: 1.1.3beta1_
  1. Install AnkiDroid.
  1. Open AnkiDroid.
  1. Say "No" to opening tutorial.
  1. Say "Ok" to "New version" dialog.
  1. Select "Load Other Deck"
  1. Make sure you have at least one deck locally on the device.
  1. Select "Sync all".
  1. Choose "Log in".
  1. Use "resolver@gmail.com" as username and "abc" as password.
  1. Verify that you get a message for each deck saying "The terms for shared decks via ankiweb.net have changed and in order to use this service you need to agree to them. To do so, please visit http://ankiweb.net, agree to the terms and synching should work again."
  1. Press "OK".

## T6: Download shared deck with images
_Created on version: 1.1.3beta3_
  1. Install AnkiDroid.
  1. Open AnkiDroid.
  1. Say "No" to opening tutorial.
  1. Say "Ok" to "New version" dialog.
  1. Click on "Download deck" button.
  1. Select "Shared deck" option.
  1. Click on "Seach" field.
  1. Type "Ultimate".
  1. Click on "Ultimate Geography Countries Capitals Fl" entry.
  1. Press "Back" until you get back to the device's home screen.
  1. Wait for notification of download completed.
  1. Pull down notification and click on "Geography Review" "Deck downloaded" notification.
  1. Select "All decks".
  1. Select "Ultimate Geography Countries Capitals Fl".
  1. Select "Start Reviewing".
  1. Verify that a map of Europe with the country of England in red is shown.