Updating the master strings from Git to Crowdin is a pretty delicate thing. Uploading an empty string.xml for instance would delete all translations. And uploading changed strings delete as well all translations. This is the desired behaviour in most cases, but when just some english typos are corrected this shouldn't destroy all translations.

In this case, it's necessary to:

  1. rebuild a download package at first (option "r")
  1. download all translations (update-tranlations.py)
  1. upload the changed strings
  1. reupload the translations (option "t" and language "all").