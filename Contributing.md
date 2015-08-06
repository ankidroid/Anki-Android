

# Become a beta tester
Please see the AnkiDroid manual for [how to become a beta tester](http://ankidroid.org/manual.html#betaTesting).

# Donate
Please see the [manual](http://ankidroid.org/manual.html#contributing) for information on donating.

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

# Other Non-developer tasks
  * Send us your ManualTesting results about the alphas/betas.
  * **Blog** about AnkiDroid and spread the word :-)
  * Follow us on [Twitter](https://twitter.com/#!/AnkiDroid) and [Facebook](http://www.facebook.com/AnkiDroid).
  * Here is a **[list of tasks](http://code.google.com/p/ankidroid/issues/list?q=label:Nondeveloper)** that can be done by people who can't code.


# Source code

First, register at [Github.com](http://github.com), open [AnkiDroid github repository](https://github.com/ankidroid/Anki-Android) and follow the forking instructions. A very basic outline of the structure of the source code is given in [Development Documentation Wiki](DevelopmentDocumentation.md). Everyone is encouraged to contribute to the Wiki to help new developers.

If you want to be notified about each new improvement/bugfix, please subscribe to the [commits feed for the development branch](https://github.com/ankidroid/Anki-Android/commits/develop.atom).

The project has been configured to work "out of the box" with Android Studio, though you first need to set up the Android SDK as follows.

## Setting up the SDK
Before you can do anything with AnkiDroid, you should install [Android Studio and the Android SDK](https://developer.android.com/sdk/index.html), and make sure you have the following items checked in the Android SDK manager:

  * Android SDK Build-tools and Android SDK platform (version must match "buildToolsVersion" and "compileSdkVersion" in the project's [build.gradle file](https://github.com/ankidroid/Anki-Android/blob/develop/AnkiDroid/build.gradle))
  * Android Support Repository (latest version)
  * Android Support Library (latest version)

## Opening in Android Studio
After forking and cloning the Anki-Android git repository, open Android Studio and choose "Open Project", then select the folder which you cloned the github repository to (we will refer to this folder as `%AnkiDroidRoot%`). The project should start without error, and build automatically.

## Compiling from the command line
If you have the Android SDK installed, you should be able to compile from the command line even without installing Android Studio.

**Windows:** Open a command prompt in `%AnkiDroidRoot%` and type:
```
gradlew.bat assembleDebug
```

**Linux & Mac:** Open a terminal in `%AnkiDroidRoot%` and type:
```
./gradlew assembleDebug
```

An apk file signed with a standard "debug" key will be generated named `"AnkiDroid-debug.apk"` in:
`%AnkiDroidRoot%/AnkiDroid/build/outputs/apk/`

## Running unit tests
Several unit tests are defined in the `AnkiDroid/androidTest` folder. You can run the tests from within Android Studio by simply right clicking on the test and running it (be sure to choose the icon with the Android symbol if there are multiple options shown), or from the command line using
```
./gradlew connectedCheck
```

## Using Eclipse
Eclipse is no longer officially supported, however it may be possible to get it working using the gradle plugin for Eclipse.

## Branching Model
We use the [gitflow branching model](http://nvie.com/posts/a-successful-git-branching-model/), so "develop" (the default branch) contains the latest development code, whereas "master" contains the code for the latest stable release. When we move into the "beta" phase of the development cycle, we implement a feature freeze, and a temporary branch "release-N.n" (N.n being the version code) is created which is only for important bug fixes. During this period, changes to "release-N.n" are regularly merged back into the "develop" branch so that it doesn't fall behind. If an urgent bug is discovered shortly after a major release, a special "hotfix-N.n" branch will be created from master.

In most cases you should base your code on and send pull requests to the default "develop" branch. However, if you are working on a critical bug fix during the feature freeze period or for a hot-fix, you should use the "release-N.n" or "hotfix-N.n" branch. If you are unsure which branch to use, please ask on the forum.


# Other development tools

A tool like "SQLite Database Browser" is very useful to understand how your "collection.anki2" file is made, and to test SQL queries. To install it on Ubuntu:
```
sudo apt-get install sqlitebrowser
```

Binaries for Windows and Mac can be found [here](https://github.com/sqlitebrowser/sqlitebrowser/releases).


# Submit improvements

Once you have improved the code, commit it and send a pull request to [AnkiDroid Github Repository](https://github.com/ankidroid/Anki-Android). It will then be accepted after the code has been reviewed, and the enhanced application will be available on the Android Market on the next release. See [the branching model section](#Branching_Model.md) if you are unsure which branch to push to.

If you have trouble with Git, you can send your modifications as an attachement to a bug in the [issue tracker](http://code.google.com/p/ankidroid/issues), or just paste the changed files as text to the [forum](http://groups.google.com/group/anki-android).

<img src='http://i.imgur.com/2QpVr.png' align='right'>


<h1>Checking database modifications</h1>

On Ubuntu Linux:<br>
<br>
<ul><li>Install sqlite3 and meld: sudo apt-get install sqlite3 meld<br>
</li><li>Make sure my desktop and android have about the same clock time.<br>
</li><li>Copy country-capitals.anki to both<br>
</li><li>Perform the same review sequence on both at the same time.<br>
</li><li>Copy the modified decks for comparizon.<br>
</li><li>Run:<br>
<pre><code>echo .dump | sqlite3 desktop_collection.anki2 &gt; desktop.dump
<br>
echo .dump | sqlite3 android_collection.anki2 &gt; android.dump
<br>
diff desktop.dump android.dump
<br>
</code></pre>
</li><li>Check that times are not too different, and check for any other difference.</li></ul>

<h1>To do from time to time</h1>
In addition to <a href='http://code.google.com/p/ankidroid/issues'>bugs and enhancements</a>, here are a few things that someone or another should perform once in a while, maybe every month or so:<br>
<h2>Licenses</h2>
<ul><li>Check that all files mention the GNU-GPL license.<br>
</li><li>Add it to those who don't.<br>
<h2>Download localized strings</h2>
</li><li>From AnkiDroid's top directory, run tools/update-localizations.py<br>
</li><li>To build a new package on Crowdin, the script needs the Crowdin API key. Ask on the mailing list and we will send it to you<br>
</li><li>Commit and push<br>
<h2>Alternative markets</h2>
</li><li>Check whether the versions are AnkiDroid's latest release. If not, contact the person responsible for this Market.<br>
</li><li>Look for new alternative markets (especially in non-English languages) and upload there (please update the Wiki then).</li></ul>

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