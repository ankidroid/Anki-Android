#!/bin/bash
#
# If you install the codacy-analysis-cli tool (which depends on Docker) you
# can run codacy locally and get reports
#
# This runs those reports and puts them in a common directory
#
# Typical usage scenario is to checkout master, run it with
# `codacy-ankidroid.sh master`
#
# ...then check your branch out and run:
# `codacy-ankidroid.sh <branchname>
#
# Finally, run a diff between the two reports and clean things up before a push
#

SOURCE_BASE=/home/$USER/work/AnkiDroid
SOURCE=$SOURCE_BASE/Anki-Android
TIMESTAMP=`date +"%Y%m%d-%H%M%S"`
TOKEN=<YOU NEED TO PASTE THE CODACY API TOKEN HERE! Tim has it>
if [ "$1" == "" ]; then
  OUTPUT=$SOURCE_BASE/reports/codacy/codacy-report-$TIMESTAMP.txt
  echo "no suffix specified - output to $OUTPUT"
else
  OUTPUT=$SOURCE_BASE/reports/codacy/codacy-report-$1.txt
  echo "suffix specifed - output to $OUTPUT"
fi

codacy-analysis-cli analyse --directory $SOURCE --project-token $TOKEN -p 6 -f text -o $SOURCE/build/reports/codacy-report.txt

mkdir -p $SOURCE_BASE/reports/codacy/
cat $SOURCE/build/reports/codacy-report.txt | sort > $OUTPUT
