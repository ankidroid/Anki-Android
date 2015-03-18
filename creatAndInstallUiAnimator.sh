#!/bin/bash
# TODO JS: private utility script, remove or generalize
cd AnkiDroid/build/intermediates/classes/test/debug/
/home/js/bin/android-studio/sdk/build-tools/19.0.0/dx --dex --output=UiTestScreenshots.jar com/ichi2/anki/tests/UiTestScreenshots.class
mv UiTestScreenshots.jar /home/js/projects/Anki-Android/
cd /home/js/projects/Anki-Android/
adb push UiTestScreenshots.jar /data/local/tmp
echo -e "\n----------\nStart uiautomator test:\n" >> uiautomator.output

# Now run:
#  adb shell "uiautomator runtest /data/local/tmp/UiTestScreenshots.jar" >> uiautomator.output
#  adb shell "uiautomator runtest /data/local/tmp/UiTestScreenshots.jar" 
