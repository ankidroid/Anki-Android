#!/usr/bin/python

# Copyright (c) 2010 norbert.nagold@gmail.com
#
# This program is free software; you can redistribute it and/or modify it under
# the terms of the GNU General Public License as published by the Free Software
# Foundation; either version 3 of the License, or (at your option) any later
# version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
# PARTICULAR PURPOSE. See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along with
# this program.  If not, see <http://www.gnu.org/licenses/>.
#
# This script extract localization from ankidroid.zip into the right folders.
# http://crowdin.net/download/project/ankidroid.zip

# Below is the list of official AnkiDroid localizations.
# Add a language if it is more than 40% translated.
# Do not remove languages.
languages = ['ca', 'cs', 'de', 'el', 'es-ES', 'fi', 'fr', 'it', 'ja', 'ko', 'pl', 'pt-PT', 'ro', 'ru', 'sr', 'sv-SE', 'zh-CN', 'zh-TW'];




import os
import zipfile
import urllib
import string

def replacechars(filename):
	s = open(filename,"r+")
	newfilename = filename + ".tmp"
	fin = open(newfilename,"w")
	for line in s.readlines():
		if line.startswith("<?xml"):
			line = "<?xml version=\"1.0\" encoding=\"utf-8\"?> \n <!-- \n ~ Copyright (c) 2009 Andrew <andrewdubya@gmail> \n ~ Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com> \n ~ Copyright (c) 2009 Daniel Svaerd <daniel.svard@gmail.com> \n ~ Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com> \n ~ Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com> \n ~ This program is free software; you can redistribute it and/or modify it under \n ~ the terms of the GNU General Public License as published by the Free Software \n ~ Foundation; either version 3 of the License, or (at your option) any later \n ~ version. \n ~ \n ~ This program is distributed in the hope that it will be useful, but WITHOUT ANY \n ~ WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A \n ~ PARTICULAR PURPOSE. See the GNU General Public License for more details. \n ~ \n ~ You should have received a copy of the GNU General Public License along with \n ~ this program.  If not, see <http://www.gnu.org/licenses/>. \n --> \n \n"
		else:
			# some people outwitted crowdin's "0"-bug by filling in "0 ", this changes it back:
			if line.startswith("    <item>0 </item>"): 
				line = "    <item>0</item>\n"
			line = string.replace(line, '\'', '\\\'')
			line = string.replace(line, '\\\\\'', '\\\'')
		print line		
		fin.write(line)
	s.close()
	fin.close()
	os.rename(newfilename, filename)
	
zipname = 'ankidroid.zip'

print "downloading crowdin-file"
req = urllib.urlopen('http://crowdin.net/download/project/ankidroidv0-5.zip')
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

	print "copying language files for: " + androidLanguage
	valuesDirectory = "../res/values-" + androidLanguage + "/"

	# Create directory if it does not exist yet.
	if not os.path.isdir(valuesDirectory):
		os.mkdir(valuesDirectory)

	# Copy localization files, mask chars and append gnu/gpl licence
	newfile = valuesDirectory + 'arrays.xml'
	file(newfile, 'w').write(zip.read(language + "/arrays.xml"))
	replacechars(newfile)

	newfile = valuesDirectory + 'strings.xml'
	file(newfile, 'w').write(zip.read(language + "/strings.xml"))
	replacechars(newfile)

print "removing crowdin-file"
os.remove(zipname)


