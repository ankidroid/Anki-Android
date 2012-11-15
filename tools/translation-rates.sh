#!/bin/sh
#
# Shows the completion rate of the translation for each language.
#
wget -O tmp-translations-page.html http://crowdin.net/project/ankidroid
cat tmp-translations-page.html |
 grep " completed" |
 grep -v ">0% completed" |
 sed -e "s/.*<br\/>//" |
 sed -e "s/<span>/ /" |
 sed -e "s/<\/span>//" |
 sed -e "s/ completed//" > tmp-list.txt

echo "By country:"
cat tmp-list.txt |  sort

echo "\nBy rate:"
cat tmp-list.txt | sed -e "s/\(.*\) \([0-9]*\)%/\2% \1/g" | sort -nr

rm -f tmp-translations-page.html tmp-list.txt
