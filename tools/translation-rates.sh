#!/bin/sh
#
# Shows the completion rate of the translation for each language.
#
wget -O tmp-translations-page.html http://crowdin.net/project/ankidroid
cat tmp-translations-page.html |
 grep "Completed on" |
 grep -v "Completed on  0%" |
 sed -e "s/.*<br\/>//" |
 sed -e "s/<span>Completed on //" |
 sed -e "s/<\/span>//" > tmp-list.txt

echo "By country:"
cat tmp-list.txt |  sort

echo "\nBy rate:"
cat tmp-list.txt | sed -e "s/\(.*\) \([0-9]*\)%/\2% \1/g" | sort -nr

rm -f tmp-translations-page.html tmp-list.txt
