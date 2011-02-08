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

# This script updates the master file(s) for crowdin.net
# There seems to a bug in the upload of translation files; it's therefore deactivated

import pycurl
import StringIO
import sys

CROWDIN_KEY = 'insert your Crowdin API key here'
PROJECT_IDENTIFIER = 'ankidroidv0-6'

path = '../res/values/'

sel = raw_input("update (m)aster file, update (t)ranslation or (r)efresh builds? ")

if sel == 'm':
	# Update Master Files:
	selu = raw_input("update (s)rings.xml or (a)rrays.xml? ")
	if selu == 's':
		filename = 'strings.xml'
	elif selu == 'a':
		filename = 'arrays.xml'
	else:
		filename = ''		
		print "nothing to do"

	if filename:	
		print 'Update of Master File ' + filename
		c = pycurl.Curl()
		fields = [('files['+filename+']', (c.FORM_FILE, path + filename))]
		c.setopt(pycurl.URL, 'http://crowdin.net/api/project/' + PROJECT_IDENTIFIER + '/update-file?key=' + CROWDIN_KEY)
		c.setopt(pycurl.HTTPPOST, fields)
		b = StringIO.StringIO()
		c.setopt(pycurl.WRITEFUNCTION, b.write) 
		c.perform()
		c.close()
		print b.getvalue()

elif sel == 't':
	# Update Translations:
	print 'still problems with crowding here'
	language = raw_input("enter language code: ")
	selu = raw_input("update (s)rings.xml or (a)rrays.xml? ")
	path = '../res/values-'+language+'/'
	if selu == 's':
		filename = 'strings.xml'
	elif selu == 'a':
		filename = 'arrays.xml'
	else:
		filename = ''		
		print "nothing to do"
	print 'Update of Translation '+language+' for '+filename
	if filename:
		if language:
			c = pycurl.Curl()
			fields = [('files['+filename+']', (c.FORM_FILE, path + filename)), ('language', language), ('auto_approve_imported','0')]
			c.setopt(pycurl.URL, 'http://crowdin.net/api/project/' + PROJECT_IDENTIFIER + '/upload-translation?key=' + CROWDIN_KEY)
			c.setopt(pycurl.HTTPPOST, fields)
			b = StringIO.StringIO()
			c.setopt(pycurl.WRITEFUNCTION, b.write) 
			c.perform()
			c.close()
			print b.getvalue()
		else:
			print 'no language code entered'

elif sel == 'r':
	# Update Translations:
	print "Force translation export"
	c = pycurl.Curl()
	c.setopt(pycurl.URL, 'http://crowdin.net/api/project/' + PROJECT_IDENTIFIER + '/export?&key=' + CROWDIN_KEY)
	b = StringIO.StringIO()
	c.setopt(pycurl.WRITEFUNCTION, b.write) 
	c.perform()
	c.close()
	print b.getvalue()
else:
	print "nothing to do"
