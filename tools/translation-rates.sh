#!/bin/sh
#
# Shows the completion rate of the translation for each language.
#
wget -O tmp-translations-page.html http://crowdin.net/project/ankidroidv0-5
cat tmp-translations-page.html |
 grep "Completed on" |
 grep -v "Completed on  0%" |
 sed -e "s/.*<br\/>//" |
 sed -e "s/<span>Completed on //" |
 sed -e "s/<\/span>//" |
 sort
rm -f tmp-translations-page.html
