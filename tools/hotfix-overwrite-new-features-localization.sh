#!/bin/bash
#
# Run this script when you need to release a hotfix, and there is no time to go through Crowdin.
# Hotfixes usually have very short change logs (like "Fixed TTS"), so English is OK.

LANGUAGES=$(ls res | grep values- | grep -v values-v11)
for LANGUAGE in $LANGUAGES
do
    echo $LANGUAGE
    cp res/values/13-newfeatures.xml res/$LANGUAGE/
done
