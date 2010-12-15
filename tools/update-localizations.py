#!/usr/bin/python

import os
import zipfile
import urllib

# This script extract localization from ankidroid.zip into the right folders.
# http://crowdin.net/download/project/ankidroid.zip
# Unfortunately, the arrays.xml files' 0,1,2,3,4,5 values must be fixed manually (Linux command: meld git) --> seems to work now

# TODO
# Add GNU-GPL header to files

# Below is the list of official AnkiDroid localizations.
# Add a language if it is more than 50% translated.
languages = ['pt-PT', 'fr', 'ru', 'ca', 'es-ES', 'el', 'it', 'pl', 'de', 'ro', 'sv-SE', 'zh-CN', 'zh-TW'];
zipname = 'ankidroid.zip'

print "downloading crowdin-file"
req = urllib.urlopen('http://crowdin.net/api/project/ankidroidv0-5/download/all.zip?key=0e54d112854fd69514d6e25856234621')
file(zipname, 'w').write(req.read())
req.close()

zip = zipfile.ZipFile(zipname, "r")

for language in languages:
	if language == 'zh-TW':
		androidLanguage = 'zh-rTW'
	elif language == 'zh-CN':
		androidLanguage = 'zh-rCN'
	else:
		androidLanguage = language[:2] # Example: pt-PT becomes pt

	print "updating language files for: " + androidLanguage
	valuesDirectory = "../res/values-" + androidLanguage + "/"

	# Create directory if it does not exist yet.
	if not os.path.isdir(valuesDirectory):
		os.mkdir(valuesDirectory)

	# Copy localization files
	file(valuesDirectory + 'arrays.xml', 'w').write(zip.read(language + "/arrays.xml"))
	file(valuesDirectory + 'strings.xml', 'w').write(zip.read(language + "/strings.xml"))

print "removing crowdin-file"
os.remove(zipname)

