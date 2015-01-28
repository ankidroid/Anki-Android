#!/bin/sh
#
# Shows the completion rate of the translation for each language.
#
wget -O tmp-translations-page.html https://crowdin.net/project/ankidroid --no-check-certificate
cat tmp-translations-page.html |
 tr "\n" " " |
 sed -e "s/project-language-name\">/\n/g" |
 sed -e "s/.*project-language-name//g" |
 sed -e "s/<\/div>//g" |
 grep "translated:" |
 sed -e "s/<\/strong>.*translated://g" |
 sed -e "s/<\/ins>.*//g" |
 sed -e 's/[[:space:]]*$//g' |
 grep -v " 0%" > tmp-list.txt

echo "By country:"
cat tmp-list.txt |  sort

echo "\nBy rate:"
cat tmp-list.txt | sed -e "s/\(.*\) \([0-9]*\)%/\2% \1/g" | sort -nr

rm -f tmp-translations-page.html tmp-list.txt
