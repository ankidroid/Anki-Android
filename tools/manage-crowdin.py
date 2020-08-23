#!/usr/bin/env python3

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

# This script updates the master file(s) for crowdin.net

import pycurl
import io
import sys
import string
import os
from os.path import expanduser

CROWDIN_KEY = ''
PROJECT_IDENTIFIER = 'ankidroid'

path = './AnkiDroid/src/main/res/values/'

files = ['01-core', '02-strings', '03-dialogs', '04-network', '05-feedback', '06-statistics', '07-cardbrowser', '08-widget', '09-backup', '10-preferences', '11-arrays', '14-marketdescription', '16-multimedia-editor', '17-model-manager', '18-standard-models']
alllang = ['ar', 'bg', 'ca', 'cs', 'de', 'el', 'eo', 'es-AR', 'es-ES', 'et', 'fa', 'fi', 'fr', 'gl', 'got', 'he', 'hi', 'hu', 'id', 'it', 'ja', 'ko', 'lt', 'lv', 'nl', 'nn-NO', 'no', 'pl', 'pt-PT', 'pt-BR', 'ro', 'ru', 'sk', 'sl', 'sr', 'sv-SE', 'th', 'tr', 'tt-RU', 'uk', 'vi', 'zh-CN', 'zh-TW']


def updateMasterFile(fn):
    if fn == '14-marketdescription':
        targetName = '14-marketdescription.txt'
        sourceName = './docs/marketing/localized_description/marketdescription.txt'
    else:
        targetName = fn + '.xml'
        sourceName = path + targetName
    if targetName:  
        print('Update of Master File ' + targetName)
        c = pycurl.Curl()
        fields = [('files['+targetName+']', (c.FORM_FILE, sourceName))]
        c.setopt(pycurl.URL, 'https://api.crowdin.com/api/project/' + PROJECT_IDENTIFIER + '/update-file?key=' + CROWDIN_KEY)
        c.setopt(pycurl.HTTPPOST, fields)
        b = io.BytesIO()
        c.setopt(pycurl.WRITEFUNCTION, b.write) 
        c.perform()
        c.close()
        print(b.getvalue().decode('utf-8'))

try:
    try:
        p = os.path.join(expanduser("~"), "src", "crowdin_key.txt")
        print(p)
        c = open(p,"r+")
    except IOError as e0:
        c = open("tools/crowdin_key.txt","r+")
    CROWDIN_KEY = c.readline();
    c.close()
except IOError as e:
    CROWDIN_KEY = input("please enter your crowdin key or create \'crowdin_key.txt\': ")

for f in files:
    updateMasterFile(f)
