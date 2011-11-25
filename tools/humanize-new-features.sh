#!/bin/bash
# Shows human-readable (not XML) changelog for English and for languages where it is not identical to English.

LANGS=`ls res | grep "values-" | sed -e "s/values-//" | grep -v "v11"`

for LANG in $LANGS
do
	DIFFERENT=`diff -b res/values-$LANG/13-newfeatures.xml res/values/13-newfeatures.xml | grep "<item>" | wc -l`
	if [ $DIFFERENT -ne "0" ]
	then
		echo "$LANG:"
		grep "<item>" res/values-$LANG/13-newfeatures.xml | sed -e "s/.*<item>/• /" -e "s/<.*//" -e "s/\\\\//"
	else
		echo "($LANG identical to English)"
	fi
done




