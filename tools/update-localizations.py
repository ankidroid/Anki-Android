#!/usr/bin/python
# -*- coding: utf-8 -*-

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
# Add a language if 01-core.xml is translated
# Do not remove languages.
# When you add a language, please also add it to mAppLanguages in Preferences.java

languages = ['ar', 'bg', 'ca', 'cs', 'de', 'el', 'es-AR', 'es-ES', 'et', 'fa', 'fi', 'fr', 'gl', 'he', 'hi', 'hu', 'id', 'it', 'ja', 'ko', 'lt', 'lv', 'nl', 'no', 'pl', 'pt-PT', 'pt-BR', 'ro', 'ru', 'sk', 'sl', 'sr', 'sv-SE', 'th', 'tr', 'uk', 'vi', 'zh-CN', 'zh-TW', 'got', 'eo'];
# languages which are localized for more than one region
localizedRegions = ['es', 'pt', 'zh']

fileNames = ['01-core', '02-strings', '03-dialogs', '04-network', '05-feedback', '06-statistics', '07-cardbrowser', '08-widget', '09-backup', '10-preferences', '11-arrays', '14-marketdescription', '15-markettitle', '16-multimedia-editor', '17-model-manager']
anyError = False
titleFile = 'docs/marketing/localized_description/ankidroid-titles.txt'
titleString = 'AnkiDroid Flashcards'

import os
import shutil
import zipfile
import urllib
import string
import re
import difflib
import subprocess
from os.path import expanduser

def replacechars(filename, fileExt, isCrowdin):
    s = open(filename,"r+")
    newfilename = filename + ".tmp"
    fin = open(newfilename,"w")
    errorOccured = False
    if fileExt != '.csv':
        for line in s.readlines():
            if line.startswith("<?xml"):
                line = "<?xml version=\"1.0\" encoding=\"utf-8\"?> \n <!-- \n ~ Copyright (c) 2009 Andrew <andrewdubya@gmail> \n ~ Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com> \n ~ Copyright (c) 2009 Daniel Svaerd <daniel.svard@gmail.com> \n ~ Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com> \n ~ Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com> \n ~ This program is free software; you can redistribute it and/or modify it under \n ~ the terms of the GNU General Public License as published by the Free Software \n ~ Foundation; either version 3 of the License, or (at your option) any later \n ~ version. \n ~ \n ~ This program is distributed in the hope that it will be useful, but WITHOUT ANY \n ~ WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A \n ~ PARTICULAR PURPOSE. See the GNU General Public License for more details. \n ~ \n ~ You should have received a copy of the GNU General Public License along with \n ~ this program.  If not, see <http://www.gnu.org/licenses/>. \n --> \n \n"
            else:
                # some people outwitted crowdin's "0"-bug by filling in "0 ", this changes it back:
                if line.startswith("    <item>0 </item>"): 
                    line = "    <item>0</item>\n"
                line = string.replace(line, '\'', '\\\'')
                line = string.replace(line, '\\\\\'', '\\\'')
                line = string.replace(line, '\n\s', '\\n')
                line = string.replace(line, 'amp;', '')
                if re.search('%[0-9]\\s\\$|%[0-9]\\$\\s', line) != None:
                    errorOccured = True
#           print line      
            fin.write(line)
    else:
        fin.write("<?xml version=\"1.0\" encoding=\"utf-8\"?> \n <!-- \n ~ Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com> \n ~ This program is free software; you can redistribute it and/or modify it under \n ~ the terms of the GNU General Public License as published by the Free Software \n ~ Foundation; either version 3 of the License, or (at your option) any later \n ~ version. \n ~ \n ~ This program is distributed in the hope that it will be useful, but WITHOUT ANY \n ~ WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A \n ~ PARTICULAR PURPOSE. See the GNU General Public License for more details. \n ~ \n ~ You should have received a copy of the GNU General Public License along with \n ~ this program.  If not, see <http://www.gnu.org/licenses/>. \n --> \n \n \n<resources> \n <string-array name=\"tutorial_questions\"> \n")
        content = re.sub('([^\"])\n', "\\1", s.read()).split("\n")
        length = len(content)
        line = []
        for i in range(length - 1):
            if isCrowdin:
                start = content[i].rfind('\",\"') + 3
            else:
                start=content[i].find('\"') + 1
            contentLine = content[i][start:len(content[i])-1]
            sepPos = contentLine.find('<separator>')
            if sepPos == -1:
                if len(contentLine) > 2:
                    errorOccured = True
                    print contentLine
                continue
            line.append(["<![CDATA[" + contentLine[:sepPos] + "]]>", "<![CDATA[" + contentLine[sepPos+11:] + "]]>"])
        for fi in line:
            fi[0] = re.sub('\"+', '\\\"', fi[0])
            fi[0] = re.sub('\'+', '\\\'', fi[0])
            fi[0] = re.sub('\\\\{2,}', '\\\\', fi[0])
            fin.write("    <item>" + fi[0] + "</item> \n");
        fin.write(" </string-array>\n <string-array name=\"tutorial_answers\">\n");
        for fi in line:
            fi[1] = re.sub('\"+', '\\\"', fi[1])
            fi[1] = re.sub('\'+', '\\\'', fi[1])
            fi[1] = re.sub('\\\\{2,}', '\\\\', fi[1])
            fin.write("    <item>" + fi[1] + "</item> \n");
        fin.write(" </string-array>\n</resources>");
    s.close()
    fin.close()
    shutil.move(newfilename,filename)
    if errorOccured:
        #os.remove(filename)
        print 'Error in file ' + filename
        return False
    else:
        # print 'File ' + filename + ' successfully copied' # Disabled, makes output too large.
        return True

def fileExtFor(f):
    if f == '14-marketdescription':
        return '.txt'
    elif f == '15-markettitle':
        return '.txt'
    else:
        return '.xml'

def createIfNotExisting(directory):
    if not os.path.isdir(directory):
        os.mkdir(directory)

def update(valuesDirectory, f, source, fileExt, isCrowdin, language=''):
    if f == '14-marketdescription':
        newfile = 'docs/marketing/localized_description/marketdescription' + '-' + language + fileExt
        file(newfile, 'w').write(source)
        # translations must be compared to the old version of marketdescription (bug of crowdin)
        oldContent = open('docs/marketing/localized_description/oldVersionJustToCompareWith.txt').readlines()
        newContent = open(newfile).readlines()
        for i in range(0, len(oldContent)):
            if oldContent[i] != newContent[i]:
                print 'File ' + newfile + ' successfully copied'
                return True         
        os.remove(newfile)
        print 'File marketdescription is not translated into language ' + language
        return True
    elif f == '15-markettitle':
#       newfile = 'docs/marketing/localized_description/marketdescription' + '-' + language + fileExt
#       file(newfile, 'w').write(source)
        translatedTitle = source.replace("\n", "")
        if titleString != translatedTitle:
            s = open(titleFile, 'a')
            s.write("\n" + language + ': ' + translatedTitle)
            s.close()
            print 'Added translated title'
        else:
            print 'Title not translated'
        return True
    else:
        newfile = valuesDirectory + f + '.xml'
        file(newfile, 'w').write(source)
        return replacechars(newfile, fileExt, isCrowdin)

def build():
    try:
        try:
            p = os.path.join(expanduser("~"), "src", "crowdin_key.txt")
            print(p)
            c = open(p,"r+")
        except IOError as e0:
            c = open("tools/crowdin_key.txt","r+")
        CROWDIN_KEY = c.readline();
        c.close()
        print "Building ZIP on server..."
        urllib.urlopen('https://api.crowdin.com/api/project/ankidroid/export?key=' + CROWDIN_KEY)
        print "Built."
    except IOError as e:
        print "No crowdin_key.txt file, skipping build."

build()

zipname = 'ankidroid.zip'

print "Downloading Crowdin file"
urllib.urlretrieve('https://crowdin.com/backend/download/project/ankidroid.zip',zipname)

zip = zipfile.ZipFile(zipname, "r")

#create title file
t = open(titleFile, 'w')
t.write(titleString)
t.close()

for language in languages:
    if language[:2] in localizedRegions:
        androidLanguage = string.replace(language, '-', '-r')
    else:
        androidLanguage = language[:2] # Example: es-ES becomes es

    print "\nCopying language files for: " + androidLanguage
    valuesDirectory = "AnkiDroid/src/main/res/values-" + androidLanguage + "/"
    createIfNotExisting(valuesDirectory)

    # Copy localization files, mask chars and append gnu/gpl licence
    for f in fileNames:
        fileExt = fileExtFor(f)
        anyError = not(update(valuesDirectory, f, zip.read(language + "/" + f + fileExt), fileExt, True, language)) or anyError

    if anyError:
        print "At least one file of the last handled language contains an error."
        anyError = False

print "\nRemoving Crowdin file\n"
zip.close()
os.remove(zipname)

print "Committing updates. Please add any fixes as another commit."
subprocess.call("git add docs/marketing/localized_description AnkiDroid/src/main/res/values*", shell=True)
subprocess.call("git commit -m 'Updated strings from Crowdin'", shell=True)

print "Checking with Lint."
subprocess.call("lint . --config lint.xml --nowarn --exitcode", shell=True)
