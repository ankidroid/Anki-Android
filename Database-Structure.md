This document contains a brief description of the Anki2 database structure as of version 11 (see `ver` field of the `col` table). There are planned upgrades to Anki's db structure as explained in [the planned changes document.](Database-Structure-Planned-Changes.md)

Thanks to @sartak and @fasiha for [starting to make this](https://gist.github.com/sartak/3921255).
Additional thanks to @bibstha for [more documentation](https://github.com/bibstha/Anki-Android/wiki/Anki2-database-documentation)

# Anki database structure

Anki uses a single [SQLite](https://sqlite.org/index.html) database to store information on all of its decks, templates, fields and cards. This file can be found inside the Anki package file (the `.apkg` file) with the extension `.anki2`.

Extracting `example.apkg` we have the following structure.

	.
	├── example
	│   ├── example.anki2
	│   └── media
	└── example.apkg

In linux `sqliteman` or `sqlite3` can be used to read and modify the `.anki2` files.

# Terminology

Anki contains bascially the following types:

1. Cards
2. Decks
3. Notes
4. Templates
5. Collection

More information on what these represent are clearly explained in http://ankisrs.net/docs/manual.html#basics.

This document often refers to "epoch seconds/milliseconds". This is the amount of time that has passed since the [Unix Epoch](https://en.wikipedia.org/wiki/Epoch_(computing)) (1 January 1970 00:00:00 UT).

# Database schema
## Cards
```sql
-- Cards are what you review. 
-- There can be multiple cards for each note, as determined by the Template.
CREATE TABLE cards (
    id              integer primary key,
      -- the epoch milliseconds of when the card was created
    nid             integer not null,--    
      -- notes.id
    did             integer not null,
      -- deck id (available in col table)
    ord             integer not null,
      -- ordinal : identifies which of the card templates or cloze deletions it corresponds to 
      --   for card templates, valid values are from 0 to num templates - 1
      --   for cloze deletions, valid values are from 0 to max cloze index - 1 (they're 0 indexed despite the first being called `c1`)
    mod             integer not null,
      -- modificaton time as epoch seconds
    usn             integer not null,
      -- update sequence number : used to figure out diffs when syncing. 
      --   value of -1 indicates changes that need to be pushed to server. 
      --   usn < server usn indicates changes that need to be pulled from server.
    type            integer not null,
      -- 0=new, 1=learning, 2=review, 3=relearning
    queue           integer not null,
      -- -3=user buried(In scheduler 2),
      -- -2=sched buried (In scheduler 2), 
      -- -2=buried(In scheduler 1),
      -- -1=suspended,
      -- 0=new, 1=learning, 2=review (as for type)
      -- 3=in learning, next rev in at least a day after the previous review
      -- 4=preview
    due             integer not null,
     -- Due is used differently for different card types: 
     --   new: note id or random int
     --   due: integer day, relative to the collection's creation time
     --   learning: integer timestamp in second
    ivl             integer not null,
      -- interval (used in SRS algorithm). Negative = seconds, positive = days
    factor          integer not null,
      -- The ease factor of the card in permille (parts per thousand). If the ease factor is 2500, the card’s interval will be multiplied by 2.5 the next time you press Good.
    reps            integer not null,
      -- number of reviews
    lapses          integer not null,
      -- the number of times the card went from a "was answered correctly" 
      --   to "was answered incorrectly" state
    left            integer not null,
      -- of the form a*1000+b, with:
      -- a the number of reps left today
      -- b the number of reps left till graduation
      -- for example: '2004' means 2 reps left today and 4 reps till graduation
    odue            integer not null,
      -- original due: In filtered decks, it's the original due date that the card had before moving to filtered.
                    -- If the card lapsed in scheduler1, then it's the value before the lapse. (This is used when switching to scheduler 2. At this time, cards in learning becomes due again, with their previous due date)
                    -- In any other case it's 0.
    odid            integer not null,
      -- original did: only used when the card is currently in filtered deck
    flags           integer not null,
      -- an integer. This integer mod 8 represents a "flag", which can be see in browser and while reviewing a note. Red 1, Orange 2, Green 3, Blue 4, no flag: 0. This integer divided by 8 represents currently nothing
    data            text not null
      -- currently unused
);

```


```sql

-- col contains a single row that holds various information about the collection
CREATE TABLE col (
    id              integer primary key,
      -- arbitrary number since there is only one row
    crt             integer not null,
      -- timestamp of the creation date in second. It's correct up to the day. For V1 scheduler, the hour corresponds to starting a new day. By default, new day is 4.
    mod             integer not null,
      -- last modified in milliseconds
    scm             integer not null,
      -- schema mod time: time when "schema" was modified. 
      --   If server scm is different from the client scm a full-sync is required
    ver             integer not null,
      -- version
    dty             integer not null,
      -- dirty: unused, set to 0
    usn             integer not null,
      -- update sequence number: used for finding diffs when syncing. 
      --   See usn in cards table for more details.
    ls              integer not null,
      -- "last sync time"
    conf            text not null,
      -- json object containing configuration options that are synced. Described below in "configuration JSONObjects"
    models          text not null,
      -- json object of json object(s) representing the models (aka Note types) 
      -- keys of this object are strings containing integers: "creation time in epoch milliseconds" of the models
      -- values of this object are other json objects of the form described below in "Models JSONObjects"
    decks           text not null,
      -- json object of json object(s) representing the deck(s)
      -- keys of this object are strings containing integers: "deck creation time in epoch milliseconds" for most decks, "1" for the default deck
      -- values of this object are other json objects of the form described below in "Decks JSONObjects"
    dconf           text not null,
      -- json object of json object(s) representing the options group(s) for decks
      -- keys of this object are strings containing integers: "options group creation time in epoch milliseconds" for most groups, "1" for the default option group
      -- values of this object are other json objects of the form described below in "DConf JSONObjects"
    tags            text not null
      -- a cache of tags used in the collection (This list is displayed in the browser. Potentially at other place)
);

-- Contains deleted cards, notes, and decks that need to be synced. 
-- usn should be set to -1, 
-- oid is the original id.
-- type: 0 for a card, 1 for a note and 2 for a deck
CREATE TABLE graves (
    usn             integer not null,
    oid             integer not null,
    type            integer not null
);

-- Notes contain the raw information that is formatted into a number of cards
-- according to the models
CREATE TABLE notes (
    id              integer primary key,
      -- epoch miliseconds of when the note was created
    guid            text not null,
      -- globally unique id, almost certainly used for syncing
    mid             integer not null,
      -- model id
    mod             integer not null,
      -- modification timestamp, epoch seconds
    usn             integer not null,
      -- update sequence number: for finding diffs when syncing.
      --   See the description in the cards table for more info
    tags            text not null,
      -- space-separated string of tags. 
      --   includes space at the beginning and end, for LIKE "% tag %" queries
    flds            text not null,
      -- the values of the fields in this note. separated by 0x1f (31) character.
    sfld            integer not null,
      -- sort field: used for quick sorting and duplicate check. The sort field is an integer so that when users are sorting on a field that contains only numbers, they are sorted in numeric instead of lexical order. Text is stored in this integer field.
    csum            integer not null,
      -- field checksum used for duplicate check.
      --   integer representation of first 8 digits of sha1 hash of the first field
    flags           integer not null,
      -- unused
    data            text not null
      -- unused
);

-- revlog is a review history; it has a row for every review you've ever done!
CREATE TABLE revlog (
    id              integer primary key,
       -- epoch-milliseconds timestamp of when you did the review
    cid             integer not null,
       -- cards.id
    usn             integer not null,
        -- update sequence number: for finding diffs when syncing. 
        --   See the description in the cards table for more info
    ease            integer not null,
       -- which button you pushed to score your recall. 
       -- review:  1(wrong), 2(hard), 3(ok), 4(easy)
       -- learn/relearn:   1(wrong), 2(ok), 3(easy)
    ivl             integer not null,
       -- interval (i.e. as in the card table)
    lastIvl         integer not null,
       -- last interval (i.e. the last value of ivl. Note that this value is not necessarily equal to the actual interval between this review and the preceding review)
    factor          integer not null,
      -- factor
    time            integer not null,
       -- how many milliseconds your review took, up to 60000 (60s)
    type            integer not null
       --  0=learn, 1=review, 2=relearn, 3=cram
);


CREATE INDEX ix_cards_nid on cards (nid);
CREATE INDEX ix_cards_sched on cards (did, queue, due);
CREATE INDEX ix_cards_usn on cards (usn);
CREATE INDEX ix_notes_csum on notes (csum);
CREATE INDEX ix_notes_usn on notes (usn);
CREATE INDEX ix_revlog_cid on revlog (cid);
CREATE INDEX ix_revlog_usn on revlog (usn);
```

# Models JSONObjects
Here is an annotated description of the JSONObjects in the models field of the `col` table. Each object is the value of a key that's a model id (epoch time in milliseconds):
``` java
{
"model id (epoch time in milliseconds)" :
  {
    css : "CSS, shared for all templates",
    did :
        "Long specifying the id of the deck that cards are added to by default",
    flds : [
             "JSONArray containing object for each field in the model as follows:",
             {
               font : "display font",
               media : "array of media. appears to be unused",
               name : "field name",
               ord : "ordinal of the field - goes from 0 to num fields -1",
               rtl : "boolean, right-to-left script",
               size : "font size",
               sticky : "sticky fields retain the value that was last added 
                           when adding new notes"
             }
           ],
    id : "model ID, matches notes.mid",
    latexPost : "String added to end of LaTeX expressions (usually \\end{document})",
    latexPre : "preamble for LaTeX expressions",
    mod : "modification time in seconds",
    name : "model name",
    req : [
            "req is unused in modern clients. May exist for backwards compatibility. 
             https://forums.ankiweb.net/t/is-req-still-used-or-present/9977
             AnkiDroid 2.14 uses it, AnkiDroid 2.15 does not use it but still generates it.
             Array of arrays describing, for each template T, which fields are required to generate T.
             The array is of the form [T,string,list], where:
             -  T is the ordinal of the template. 
             - The string is 'none', 'all' or 'any'. 
             - The list contains ordinal of fields, in increasing order.
             The meaning is as follows:
             - if the string is 'none', then no cards are generated for this template. The list should be empty.
             - if the string is 'all' then the card is generated only if each field of the list are filled
             - if the string is 'any', then the card is generated if any of the field of the list is filled.

             The algorithm to decide how to compute req from the template is explained on: 
             https://github.com/Arthur-Milchior/anki/blob/commented/documentation//templates_generation_rules.md"
          ],
    sortf : "Integer specifying which field is used for sorting in the browser",
    tags : "Anki saves the tags of the last added note to the current model, use an empty array []",
    tmpls : [
              "JSONArray containing object of CardTemplate for each card in model",
              {
                afmt : "answer template string",
                bafmt : "browser answer format: 
                          used for displaying answer in browser",
                bqfmt : "browser question format: 
                          used for displaying question in browser",
                did : "deck override (null by default)",
                name : "template name",
                ord : "template number, see flds",
                qfmt : "question format string"
              }
            ],
    type : "Integer specifying what type of model. 0 for standard, 1 for cloze",
    usn : "usn: Update sequence number: used in same way as other usn vales in db",
    vers : "Legacy version number (unused), use an empty array []"
  }
}
```

# Decks JSONObjects
Here is an annotated description of the JSONObjects in the decks field of the `col` table:

```java
{
"deck id (creation time in epoch milliseconds for most decks, '1' for the default deck)"
  {
    name: "name of deck", 
    extendRev: "extended review card limit (for custom study)
                Potentially absent, in this case it's considered to be 10 by aqt.customstudy", 
    usn: "usn: Update sequence number: used in same way as other usn vales in db", 
    collapsed: "true when deck is collapsed", 
    browserCollapsed: "true when deck collapsed in browser", 
    newToday/revToday/lrnToday : two number array.
                                 First one is the number of days that have passed between the collection was created and the deck was last updated
                                 The second one is equal to the number of cards seen today in this deck minus the number of new cards in custom study today.
                                 BEWARE, it's changed in anki.sched(v2).Scheduler._updateStats and anki.sched(v2).Scheduler._updateCutoff.update  but can't be found by grepping 'newToday', because it's instead written as type+"Today" with type which may be new/rev/lrnToday    
    timeToday: "two number array used somehow for custom study. Currently unused in the code", 
    dyn: "1 if dynamic (AKA filtered) deck", 
    extendNew: "extended new card limit (for custom study). 
                Potentially absent, in this case it's considered to be 10 by aqt.customstudy", 
    conf: "id of option group from dconf in `col` table. Or absent if the deck is dynamic. 
          Its absent in filtered deck", 
    id: "deck ID (automatically generated long)", 
    mod: "last modification time", 
    desc: "deck description"
  }
}
```

# DConf JSONObjects
Here is an annotated description of the JSONObjects in the dconf field of the `col.decks` table:

```java
{
"deck config id (creation time in epoch milliseconds for most option groups, '1' for the default option group)" :
  {
        autoplay : "whether the audio associated to a question should be
played when the question is shown"
        dyn : "Whether this deck is dynamic. Not present by default in decks.py"
        id : "deck ID (automatically generated long). Not present by default in decks.py"
        lapse : {
            "The configuration for lapse cards."
            delays : "The list of successive delay between the learning steps of the new cards, as explained in the manual."
            leechAction : "What to do to leech cards. 0 for suspend, 1 for mark. Numbers according to the order in which the choices appear in aqt/dconf.ui"
            leechFails : "the number of lapses authorized before doing leechAction."
            minInt: "a lower limit to the new interval after a leech"
            mult : "percent by which to multiply the current interval when a card goes has lapsed"
        }
        maxTaken : "The number of seconds after which to stop the timer"
        mod : "Last modification time"
        name : "The name of the configuration"
        new : {
            "The configuration for new cards."
            bury : "Whether to bury cards related to new cards answered"
            delays : "The list of successive delay between the learning steps of the new cards, as explained in the manual."
            initialFactor : "The initial ease factor"
            ints : "The list of delays according to the button pressed while leaving the learning mode. Good, easy and unused. In the GUI, the first two elements corresponds to Graduating Interval and Easy interval"
            order : "In which order new cards must be shown. NEW_CARDS_RANDOM = 0 and NEW_CARDS_DUE = 1."
            perDay : "Maximal number of new cards shown per day."
            separate : "Seems to be unused in the code."
                
        }
        replayq : "whether the audio associated to a question should be played when the answer is shown"
        rev : {
            "The configuration for review cards."
            bury : "Whether to bury cards related to new cards answered"
            ease4 : "the number to add to the easyness when the easy button is pressed"
            fuzz : "The new interval is multiplied by a random number between -fuzz and fuzz"
            ivlFct : "multiplication factor applied to the intervals Anki generates"
            maxIvl : "the maximal interval for review"
            minSpace : "not currently used according to decks.py code's comment"
            perDay : "Numbers of cards to review per day"
        }
        timer : "whether timer should be shown (1) or not (0)"
        usn : "See usn in cards table for details."
  }
}
```

# configuration JSONObject
Here is an annotated description of the JSONObject in the conf field of the `col` table when the collection is started. More values may be added to it by any add-on. Unlike the `models`, `decks`, and `dconf` JSONObjects, there should be only one `conf` JSONObject per collection.
```java
{
    "curDeck": "The id (as int) of the last deck selected (during review, adding card, changing the deck of a card)",
    "activeDecks": "The list containing the current deck id and its descendent (as ints)",
    "newSpread": "In which order to view to review the cards. This can be selected in Preferences>Basic. Possible values are:
      0 -- NEW_CARDS_DISTRIBUTE (Mix new cards and reviews)
      1 -- NEW_CARDS_LAST (see new cards after review)
      2 -- NEW_CARDS_FIRST (See new card before review)",
    "collapseTime": "'Preferences>Basic>Learn ahead limit'*60. If there is no more card to review now but next card in learning is in less than collapseTime second, show it now.
    If there are no other card to review, then we can review cards in learning in advance if they are due in less than this number of seconds.",
    "timeLim": "'Preferences>Basic>Timebox time limit'*60. Each time this number of second elapse, anki tell you how many card you reviewed.",
    "estTimes": "'Preferences>Basic>Show next review time above answer buttons'. A Boolean."
    "dueCounts": "'Preferences>Basic>Show remaining card count during review'. A Boolean."
    "curModel": "Id (as string) of the last note type (a.k.a. model) used (i.e. either when creating a note, or changing the note type of a note).",
    "nextPos": "This is the highest value of a due value of a new card. It allows to decide the due number to give to the next note created. (This is useful to ensure that cards are seen in order in which they are added.",
    "sortType": "A string representing how the browser must be sorted. Its value should be one of the possible value of 'aqt.browsers.DataModel.activeCols' (or equivalently of 'activeCols'  but not any of ('question', 'answer', 'template', 'deck', 'note', 'noteTags')",
    "sortBackwards": "A Boolean stating whether the browser sorting must be in increasing or decreasing order",
    "addToCur": "A Boolean. True for 'When adding, default to current deck' in Preferences>Basic. False for 'Change deck depending on note type'.",
    "dayLearnFirst": "A Boolean. It corresponds to the option 'Show learning cards with larger steps before reviews'. But this option does not seems to appear in the preference box",
    "newBury": "A Boolean. Always set to true and not read anywhere in the code but at the place where it is set to True if it is not already true. Hence probably quite useful.",

    "lastUnburied":"The date of the last time the scheduler was initialized or reset. If it's not today, then buried notes must be unburied. This is not in the json until scheduler is used once.",
    "activeCols":"the list of name of columns to show in the browser. Possible values are listed in aqt.browser.Browser.setupColumns. They are:
    'question' -- the browser column'Question',
    'answer' -- the browser column'Answer',
    'template' -- the browser column'Card',
    'deck' -- the browser column'Deck',
    'noteFld' -- the browser column'Sort Field',
    'noteCrt' -- the browser column'Created',
    'noteMod' -- the browser column'Edited',
    'cardMod' -- the browser column'Changed',
    'cardDue' -- the browser column'Due',
    'cardIvl' -- the browser column'Interval',
    'cardEase' -- the browser column'Ease',
    'cardReps' -- the browser column'Reviews',
    'cardLapses' -- the browser column'Lapses',
    'noteTags' -- the browser column'Tags',
    'note' -- the browser column'Note',
    The default columns are: noteFld, template, cardDue and deck
    This is not in the json at creaton. It's added when the browser is open.
     "
}
```
