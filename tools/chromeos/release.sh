#!/usr/bin/env bash

# This script requires jq: http://stedolan.github.io/jq/

# Check for apk file
if [ $# -lt 2 ]; then
    echo "missing conversion script or apk file"
    exit 1
fi

SCRIPT_PATH=`dirname $0`
SOURCE_PATH=${SCRIPT_PATH%/*/*}

# Make sure cws directory exists
mkdir -p cws

# Use --for-webstore instead of --unpacked to remove potential debug stuff
python $1 --metadata $SCRIPT_PATH/release.crx.json --for-webstore --output cws/ankidroid.zip --destructive $2

# Unzip CWS package
rm -rf cws/unpacked
unzip -q cws/ankidroid.zip -d cws/unpacked

# Optimize image for CWS
cp $SOURCE_PATH/docs/marketing/chrome-web-store/iconID.png cws/unpacked/

# Add short name and description placeholder to manifest
echo "`jq '.short_name = "AnkiDroid" | .description = "__MSG_extDesc__"' cws/unpacked/manifest.json`" > cws/unpacked/manifest.json

# Extract name and description from ankidroid-titles.txt and marketdescription-XY.txt and
# inject them into extName and extDesc of messages.json in _locales/XY
LANGS=`ls cws/unpacked/_locales`
DEFAULT_NAME='AnkiDroid Flashcards'
DEFAULT_DESC='Memorize anything with AnkiDroid!'
for LANG in $LANGS
do
    if [ -f $SOURCE_PATH/docs/marketing/localized_description/marketdescription-$LANG.txt ]
    then
        NAME=`grep "^$LANG:" $SOURCE_PATH/docs/marketing/localized_description/ankidroid-titles.txt | sed 's/.*\: //'`
        DESC=`head -n 1 $SOURCE_PATH/docs/marketing/localized_description/marketdescription-$LANG.txt`
        echo "`jq '.extName.message = $extName | .extDesc.message = $extDesc' --arg extName "${NAME:-DEFAULT_NAME}" --arg extDesc "${DESC-DEFAULT_DESC}" cws/unpacked/_locales/$LANG/messages.json`" > cws/unpacked/_locales/$LANG/messages.json
    else
        echo "`jq '.extName.message = $extName | .extDesc.message = $extDesc' --arg extName "$DEFAULT_NAME" --arg extDesc "$DEFAULT_DESC" cws/unpacked/_locales/$LANG/messages.json`" > cws/unpacked/_locales/$LANG/messages.json
    fi
done

# Work around intent filter restriction to support APKG file handler
echo "`jq 'del(.file_handlers.any.types) | .file_handlers.any.extensions = ["apkg"]' cws/unpacked/manifest.json`" > cws/unpacked/manifest.json

# Prepare release package
cd cws/unpacked
zip -q -r -o ../../cws/release.zip *
