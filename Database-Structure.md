This document contains a brief description of the Anki2 database structure. 

Thanks to @sartak and @fasiha for [starting to make this](https://gist.github.com/sartak/3921255).
Additional thanks to @bibstha for [more documentation](https://github.com/bibstha/Anki-Android/wiki/Anki2-database-documentation)

# Anki database structure

Anki uses one single sqlite database to store information of multiple decks, templates, fields and cards. This file can be found inside the Anki package file (.apkg file) with the extension anki2.

Extracting example.apkg we have the following structure.

	.
	├── example
	│   ├── example.anki2
	│   └── media
	└── example.apkg

In linux *sqliteman* can be used to read and modify the .anki2 files.

# Terminology

Anki contains bascially the following types:

1. Cards
2. Decks
3. Notes
4. Templates
5. Collection

More information on what these represent are clearly explained in http://ankisrs.net/docs/manual.html#basics.

# Database schema
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
      -- ordinal : identifies which of the card templates it corresponds to 
      --   valid values are from 0 to num templates - 1
    mod             integer not null,
      -- modificaton time as epoch seconds
    usn             integer not null,
      -- update sequence number : used to figure out diffs when syncing. 
      --   value of -1 indicates changes that need to be pushed to server. 
      --   usn < server usn indicates changes that need to be pulled from server.
    type            integer not null,
      -- 0=new, 1=learning, 2=due, 3=filtered
    queue           integer not null,
      -- -3=sched buried, -2=user buried, -1=suspended,
      -- 0=new, 1=learning, 2=due (as for type)
      -- 3=in learning, next rev in at least a day after the previous review
    due             integer not null,
     -- Due is used differently for different card types: 
     --   new: note id or random int
     --   due: integer day, relative to the collection's creation time
     --   learning: integer timestamp
    ivl             integer not null,
      -- interval (used in SRS algorithm). Negative = seconds, positive = days
    factor          integer not null,
      -- factor (used in SRS algorithm)
    reps            integer not null,
      -- number of reviews
    lapses          integer not null,
      -- the number of times the card went from a "was answered correctly" 
      --   to "was answered incorrectly" state
    left            integer not null,
      -- of the form a*1000+b, with:
      -- b the number of reps left till graduation
      -- a the number of reps left today
    odue            integer not null,
      -- original due: only used when the card is currently in filtered deck
    odid            integer not null,
      -- original did: only used when the card is currently in filtered deck
    flags           integer not null,
      -- currently unused
    data            text not null
      -- currently unused
);

-- col contains a single row that holds various information about the collection
CREATE TABLE col (
    id              integer primary key,
      -- arbitrary number since there is only one row
    crt             integer not null,
      -- created timestamp
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
      -- json object containing configuration options that are synced
    models          text not null,
      -- json array of json objects containing the models (aka Note types)
    decks           text not null,
      -- json array of json objects containing the deck
    dconf           text not null,
      -- json array of json objects containing the deck options
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
      -- epoch seconds of when the note was created
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
    sfld            text not null,
      -- sort field: used for quick sorting and duplicate check
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
       -- interval
    lastIvl         integer not null,
       -- last interval
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
    mod : "modification time in milliseconds",
    name : "model name",
    req : [
            "Array of arrays describing, for each template T, which fields are required to generate T.
             The array is of the form [T,string,list], where:
             -  T is the ordinal of the template. 
             - The string is 'none', 'all' or 'any'. 
             - The list contains ordinal of fields, in increasing order.
             The meaning is as follows:
             - if the string is 'none', then no cards are generated for this template. The list should be empty.
             - if the string is 'all' then the card is generated only if each field of the list are filled
             - if the string is 'any', then the card is generated if any of the field of the list is filled.

             The algorithm to decide how to compute req from the template is explained on: 
             https://github.com/Arthur-Milchior/anki/blob/master/documentation/templates_generation_rules.md"
          ],    sortf : "Integer specifying which field is used for sorting in the browser",
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
    name: "name of deck", 
    extendRev: "extended review card limit (for custom study)", 
    usn: "usn: Update sequence number: used in same way as other usn vales in db", 
    collapsed: "true when deck is collapsed", 
    browserCollapsed: "true when deck collapsed in browser", 
    newToday: "two number. First one currently not used. Second is the negation (-)
               of the number of new cards added today by custom study", 
    timeToday: "two number array used somehow for custom study. Currently unused in the code", 
    dyn: "1 if dynamic (AKA filtered) deck", 
    extendNew: "extended new card limit (for custom study)", 
    conf: "id of option group from dconf in `col` table. Or absent if the deck is dynamic", 
    revToday: "two number. First one currently not used. Second is the negation (-)
               of the number of review cards added today by custom study", 
    lrnToday: "two number array used somehow for custom study. Currently unused in the code", 
    id: "deck ID (automatically generated long)", 
    mod: "last modification time", 
    desc: "deck description"
}
```

# DConf JSONObjects
Here is an annotated description of the JSONObjects in the dconf field of the `col` table:

```java
{
"model id (epoch time in milliseconds)" :
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
