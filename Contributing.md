# Links
Here are some links to other pages related to contributing to AnkiDroid:
* [[Development guide|Development-Guide]]
* [Alpha/beta testing](http://ankidroid.org/manual.html#betaTesting)
* [Donating](http://ankidroid.org/manual.html#contributing)

# Translate AnkiDroid into your language
Even if you prefer to use AnkiDroid in English, other people in your country might prefer to use it in their own language. Translating AnkiDroid to your native language means your country's AnkiDroid will grow much faster, leading to better shared decks in your language.

Translating is easy and fun:
  * Go to http://crowdin.net/project/ankidroid
  * Register, if you don't have an account yet
  * Click on the flag of your language
  * Click on a file (they are sorted by descending priority)
  * Click on "Sort"
  * Click on "Missing translation"
  * Grey bullet=missing, Green bullet=done
  * Terms like "%s", "%1$d" are placeholders for strings or numbers which will be filled later by AnkiDroid. They must not be changed, e.g. reversed ("1%d2") or filled with spaces ("% s").

For each grey bullet, translate the English text to your language.

**Tip:**

Many terms such as "note" or "leech" and some other messages should be consistent with Anki Desktop. You can copy the translations from Anki Desktop via it's [Launchpad page](http://bazaar.launchpad.net/~resolve/anki/master/files/head:/anki/). Click on a _language code_.po file (e.g. "ja.po" for Japanese) to view a single language, or click "view revision"->"download tarball" to download all the languages at once.

## Logic of the separation in different files

  * android\_market.xml : Text seen by Android users who see AnkiDroid in the Market, and are pondering whether to install it or not.
  * 01-core.xml : Most important strings (reviewer, studyoptions, deckpicker)
  * 02-strings.xml: Strings which are nice to have but are not immediately visible when learning
  * 03-dialogs.xml: Texts for dialogs (warnings, information etc.)
  * 04-network.xml: Strings for syncing and downloading decks
  * 05-feedback.xml: Strings for feedback system
  * 06-statistics.xml: Strings for all statistics
  * 07-cardbrowser.xml: Strings for card browser
  * 08-widget.xml: Strings for widget
  * 09-backup.xml: Strings for backup system
  * 10-preferences.xml: Strings which are used in the preferences screens
  * 11-arrays.xml: Array-Strings which are used in the preferences screens

## Switching the language of the AnkiDroid UI
  * Go to the decks list
  * Menu > Preferences (1st item) > Language (6th item) > Language (1st item)
  * Select the language you want

## Translating the AnkiDroid manual
The source for the AnkiDroid manual can be found on the [ankidroiddocs github page](https://github.com/ankidroid/ankidroiddocs). The manual is written in a plain text markup language called [asciidoctor](http://asciidoctor.org/docs/asciidoc-syntax-quick-reference/) which is very easy to use. The asciidoctor file is called "manual.txt" and it can be compiled to html as follows:

First install Asciidoctor:
  * [Install Ruby](https://www.ruby-lang.org/en/installation/)
  * Open command prompt with Ruby
  * Enter the command `gem install asciidoctor`

Then compile the manual as follows:
  * Open command prompt with Ruby
  * Enter the command `asciidoctor FULL_PATH_TO_SOURCE_FILE`
  * A file will be generated with the same name as the source file, but with .html extension.

The preferred method of contributing to the documentation is to fork the `ankidroiddocs` project on github, and send a pull request with your additions in the usual way. However, if you don't know how to use github, you can simply download the "manual.txt" file and send it to a project member or the Google Group.

To create a translation of the manual, please make a copy of "manual.txt" and add "-LANUGAGE\_CODE" ([list of ISO\_639-1 language codes](http://en.wikipedia.org/wiki/List_of_ISO_639-1_codes)). For example for Italian submit a translated file called "manual-it.txt" based on the original source file.

Translations should be periodically updated to reflect any changes in the original manual. Details of all changes can be found in the [list of commits on github](https://github.com/ankidroid/ankidroiddocs/commits/master/).

## Take screenshots
For each language we need:
  * A few screenshots on a normal phone (review with image, review with sound, card edition, deck list)
  * At least one screenshot on a ~7inch screen
  * At least one screenshot on a ~10inch screen

Here are all screenshots we have, so you can see which ones are missing:
https://github.com/ankidroid/Anki-Android/tree/v2.0.2-dev/docs/marketing/screenshots

Send the images to the [forum](http://groups.google.com/group/anki-android) so that we can use them to improve the Play Store page, the Wiki, etc.

You can download various shared decks to show nice content. To take a screenshot, press the "Power" and "Volume down" buttons simultaneously.
You can use the emulator if you don't have any 7inch or 10inch device.

To take screenshots for several languages, you can switch the AnkiDroid UI language in Preferences as explained above.

# Answering to reviews
For use on Android Play, other markets, outside forums, and potentially any place where users talk about AnkiDroid.

Don't hesitate to talk in the name of the AnkiDroid team if you feel part of it. Feel free to adapt, but please stay diplomatic and welcoming, even in face of harsh criticism or foul language.

| **Review** | **Answer** |
|:-----------|:-----------|
| That rocks! | (No reply) |
| That sucks! | (No reply) |
| I give up! | (No reply) |
| It continuously crashes! | Hello NAME, sorry for the inconvenience! You can try uninstalling and re-installing as this often fixes such bugs. If not, please report the problem at http://code.google.com/p/ankidroid/issues/entry , including as many details as possible (Android version, device, steps that lead to the problem) and we'll look into it. Thanks for using AnkiDroid! |
| How can I do X? (already in the manual) | Hello NAME, thanks for your feedback! Please read the corresponding section of the manual at ankidroid.org/manual.html#ENTRY and see if that answers your question. Thanks for using AnkiDroid! |
| There is a problem with X (already in tracker) | Hello NAME, thanks for your feedback! This problem is being investigated at TRACKERURL , please check whether it matches what you mean, and "star" that page to receive notifications. Thanks for using AnkiDroid!|
| There is a problem with X (not in tracker) | Hello NAME, thanks for your feedback! Please report the problem at http://code.google.com/p/ankidroid/issues/entry , including as many details as possible (Android version, device, steps that lead to the problem). Thanks for using AnkiDroid! |
| You should add feature X (already in tracker) | Hello NAME, thanks for your idea! This idea is debated at TRACKERURL , please check whether it matches what you mean, and "star" that page to receive notifications. Thanks for participating in AnkiDroid! |
| You should add feature X (not in tracker) | Hello NAME, thanks for your idea! Please suggest it at http://code.google.com/p/ankidroid/issues/entry  so that volunteers can see it and keep track of it. Thanks for participating in AnkiDroid!|
| I lost my decks! | Hello NAME. Sorry about the inconvenience. Fortunately, AnkiDroid makes regular backups, so chances are you can recover your decks. Please have a look at http://ankidroid.org/docs/manual.html#backups and if you still have problems send a message to the mailing list at public-forum@ankidroid.org. Thanks for your patience! |
| I lost my decks (Japanese) | NAME様、ご連絡いただきありがとうございます。バックアップから元に戻す可能性がございますので、なるべく早くメーリングリストへご連絡していただけないでしょうか。アドレスはankidroid-nihon@googlegroups.comです。よろしくお願い致します。|
| You can't change any options | AnkiDroid is designed to show you the cards just before you forget them, in order to minimize wasted study time. If the default settings are not working well for you, you can adjust the intervals under "deck options". If you simply want to study more than necessary, it's better to use the "custom study" feature. Please see the manual for more info. |
| Some characters don't show | Hello, It is likely that your Android device does not support those specific characters in its default font. If that's the case you can use a custom font instead: https://ankidroid.org/docs/manual.html#customFonts|

# Other Non-developer tasks
  * Send us your ManualTesting results about the alphas/betas.
  * **Blog** about AnkiDroid and spread the word :-)
  * Follow us on [Twitter](https://twitter.com/#!/AnkiDroid) and [Facebook](http://www.facebook.com/AnkiDroid).
  * Here is a **[list of tasks](http://code.google.com/p/ankidroid/issues/list?q=label:Nondeveloper)** that can be done by people who can't code.

<h1>How to sponsor development</h1>

In case you are willing to pay money for a feature or fix to be implemented, here is how to do:<br>
<br>
<ol><li>Find the issue in the <a href='http://code.google.com/p/ankidroid/issues'>issue tracker</a>, or create a new issue if it does not exist yet.<br>
</li><li>Add as many details as you can, describe how it could work, draw a prototype on paper, etc.<br>
</li><li>Post a comment asking "If I provide a good quality patch for this feature, will it be merged?" and wait for our answer.<br>
</li><li>Choose someone to do the job. Use any crowdsourcing platform you trust, or a developer friend, or a software company. Make sure they know Android and Git.</li></ol>

Reach an agreement with the developer:<br>
<ol><li>Agree with the developer on the conditions, defining exactly what they must implement.<br>
</li><li>Insist that produced source code must be released as Open Source (not doing so would be a breach of the GNU-GPLv3 license). A common error would be to ask only for the binary APK (which will quickly get out-of-date and incompatible)<br>
</li><li>If possible, make it a condition that produced source code must be merged by us before total payment. Or make it half/half. If the issue is Enhancement-Critical or Defect-High or Defect-Critical, then the developer can be assured that we will merge it fast, if the code is good enough.<br>
</li><li>Propose to become a tester if they need. Giving your feedback early could help. Be sure to test on different devices and in different scenarios.</li></ol>

Summary of the workflow:<br>
<br>
<a href='http://i.stack.imgur.com/EdSXK.png'><img src='http://i.stack.imgur.com/EdSXK.png' /></a>


<h1>Crash reporting system</h1>
Prior to AnkiDroid version 2.4, crash reports were collected by an app we called "Triage", and reports were publicly viewable at <a href='http://ankidroid-triage.appspot.com'>http://ankidroid-triage.appspot.com</a>. However this tool had a number of shortcomings, and from version 2.4 we now use the <a href='https://github.com/ACRA/acra'>Acra</a> library to send the crash reports to our private server which is running Acralyzer.<br>
<br>
Crash reports can be viewed <a href='https://ankidroid.org/couchdb/acralyzer/_design/acralyzer/index.html#/reports-browser/ankidroid'>here</a>, however a password is required in order to protect the privacy of our users, since the crash reports can contain logcat and user comments where private information could potentially be revealed.<br>
<br>
Non-core developers wishing to get a stacktrace or logcat for a specific issue should request this in the appropriate thread on the issue tracker, and someone from the AnkiDroid team will post the relevant information. Developers wishing to browse / search through the crash report database can request a password by emailing one of the core developers.<br>
<br>
<h1>Markets</h1>

<table><thead><th> <b>Market</b> </th><th> <b>Maintainer</b> </th><th> <b>AnkiDroid Version</b> </th><th> <b>Status</b> </th><th> <b>Downloads</b> </th></thead><tbody>
<tr><td> <a href='https://play.google.com/store/apps/details?id=com.ichi2.anki'>Google Play</a> </td><td> Nicolas Raoul and Tim </td><td> 2.4                      </td><td> Published     </td><td> 1,379,647        </td></tr>
<tr><td> Amazon AppStore </td><td> Tim               </td><td> 2.4                      </td><td> Published     </td><td> 10,014           </td></tr>
<tr><td> <a href='http://www.appslib.com/'>AppsLib</a> </td><td> Mike Morrison     </td><td> 2.3.2                    </td><td> Published     </td><td> 1,157 (all versions) </td></tr>
<tr><td> <a href='http://ankidroid.store.aptoide.com/app/market/com.ichi2.anki/20400107/7588240/AnkiDroid%20Flashcards'>Aptoide</a> </td><td> Nicolas Raoul     </td><td> 2.4alpha7                </td><td> Published     </td><td> 0                </td></tr>
<tr><td> <a href='http://fastapp.com/~usergen_90ea90105b978ab58d51d0076ecfcb3c'>FastApp</a> </td><td> Mike Morrison     </td><td> Referrer to Google's market </td><td> 0.6 info published; 0.7 info submitted; FastApp appears to be down as of Nov. 2014 </td><td> Unknown          </td></tr>
<tr><td> <a href='http://www.getjar.mobi/mobile/68332/AnkiDroid-flashcards'>GetJar</a> </td><td> Mike Morrison     </td><td> 0.7                      </td><td> 0.7 published; 2.3.2 "Corrupt file! Android manifest not found." Help request has been sent. </td><td> 3,684 (all versions) </td></tr>
<tr><td> Pdassi <a href='http://android.pdassi.com/126717/AnkiDroid_flashcards.html'>.com</a> <a href='http://android.pdassi.de/126717/AnkiDroid_flashcards.html'>.de</a> </td><td> Mike Morrison     </td><td> 0.7                      </td><td> 0.7 published; 2.3.2 "413 Request Entity Too Large". Help request has been sent. (note: non-English description text is copyright Pdassi)</td><td> 2,150 (all versions) </td></tr>
<tr><td> <a href='http://slideme.org/application/ankidroid'>SlideME</a> </td><td> Mike Morrison     </td><td> 2.3.2                    </td><td> Published     </td><td> 2,536 (all versions) </td></tr>
<tr><td> <a href='http://www.smartappfinder.com/pp/com.ichi2.anki.html'>SmartAppFinder</a> </td><td> Mike Morrison     </td><td> 0.7                      </td><td> Published, but now the app link and login link are harder to find. Help request has been sent. </td><td> Unknown          </td></tr>
<tr><td> <a href='http://mall.soc.io/apps/AnkiDroid+flashcards'>Soc.io Mall</a> </td><td> Mike Morrison     </td><td> 0.7                      </td><td> 0.7 published; 2.3.2 to be reviewed </td><td> 86 (all versions) </td></tr>
<tr><td> <a href='http://www.lenovomm.com/appstore/viewProduct.do?appId=12997'>LenovoMM Appstore</a> </td><td> Nicolas Raoul     </td><td> 0.7beta10lenovo          </td><td> Published     </td><td> 2                </td></tr>
<tr><td> Docomo Market </td><td> Nicolas Raoul     </td><td> Facade to Google's market </td><td> Published     </td><td> Can't be known   </td></tr>
<tr><td> Ndoo (aka nduao, N多市场) </td><td> Nicolas Raoul     </td><td> <a href='http://www.nduoa.com/apk/detail/234425'>1.1beta21</a> </td><td> Published     </td><td> 13,131           </td></tr>
<tr><td> <a href='http://www.telefon.de/apps_detail.asp?app_id=86373'>telefon.de</a> </td><td> Nicolas Raoul     </td><td> Facade to Google's market </td><td> Published     </td><td> Can't be known   </td></tr>
<tr><td> <a href='http://f-droid.org/repository/browse/?fdfilter=ankidroid&fdid=com.ichi2.anki'>F-Droid</a> </td><td> Anyone            </td><td> 2.3.2                    </td><td> Published     </td><td> Can't be known   </td></tr>
<tr><td> <a href='https://github.com/ankidroid/Anki-Android/releases'>Direct APK download</a> </td><td> Nicolas Raoul and Tim </td><td> all versions             </td><td> Published     </td><td> Thousands        </td></tr></tbody></table>

<h1>Administration</h1>

Read ReleaseProcedure for some insight on how AnkiDroid is released.<br>
<br>
LocalizationAdministration describes how Crowdin is managed under the hood.<br>
<br>
<h1>Other open source Android flashcards apps</h1>

We are on very friendly terms with other app creators, and some have already re-used AnkiDroid's code. Feel free to compare apps and transfer one's strong points to the other(s) when applicable:<br>
<ul><li><a href='http://anymemo.org/'>AnyMemo</a>
</li><li><a href='http://secretsockssoftware.com/androidflashcards/'>Android Flashcards</a>
</li><li><a href='http://code.google.com/p/kanji-flashcards-android/'>Kanji Flashcards Android</a></li></ul>

<h1>Contributors</h1>

Many thanks to all of the people and companies who contributed to AnkiDroid! :<br>
<ul><li>A hundred people are <a href='https://github.com/ankidroid/Anki-Android/graphs/contributors'>working on the code</a>, some sending just one patch, some becoming very involved.<br>
</li><li>Simplified Chinese translation by / 简体中文版 安智网汉化 <a href='http://goapk.com'>http://goapk.com</a>
</li><li>Tens of anonymous translation contributors<br>
</li><li>Hundreds of people are <a href='https://groups.google.com/group/anki-android/members'>participating in the forum</a>
</li><li>And thanks to the hundreds of thousands of AnkiDroid users!</li></ul>

We are very welcoming and open, please join us!