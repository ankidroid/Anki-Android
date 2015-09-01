This document contains a brief description of the Anki2 database structure. 

Thanks to @sartak and @fasiha for [starting to make this](https://gist.github.com/sartak/3921255).

# Database schema
```sql
-- Cards are what you review. There can be multiple cards for each note, determined by the Template.
CREATE TABLE cards (
    id              integer primary key,
      -- the epoch milliseconds of when the card was created
    nid             integer not null,
      -- notes.id
    did             integer not null,
      -- deck id (available in col table)
    ord             integer not null,
      -- ordinal : identifies which of the card templates it corresponds to (from 0 to num templates - 1)
    mod             integer not null,
      -- modificaton time as epoch seconds
    usn             integer not null,
      -- update sequence number : used to figure out diffs when syncing. 
      --        value of -1 indicates changes that need to be pushed to server. 
      --        usn < server usn indicates changes that need to be pulled from server.
    type            integer not null,
      -- 0=new, 1=learning, 2=due
    queue           integer not null,
      -- Same as type, but -1=suspended, -2=user buried, -3=sched buried
    due             integer not null,
     -- Due is used differently for different queues: 
     --     new queue: note id or random int
     --     rev queue: integer day
     --     lrn queue: integer timestamp
    ivl             integer not null,
      -- interval (used in SRS algorithm)
    factor          integer not null,
      -- factor (used in SRS algorithm)
    reps            integer not null,
      -- number of reviews
    lapses          integer not null,
      -- the number of times the card went from a "was answered correctly" to "was answered incorrectly" state
    left            integer not null,
      -- reps left till graduation
    odue            integer not null,
      -- original due: only used when the card is currently in a filtered deck to keep track of the original due value
    odid            integer not null,
      -- original did: only used when the card is currently in a filtered deck to keep track of the original did value
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
      -- schema mod time: time when "schema" was modified. If the server scm is different from the client scm a full-sync is required
    ver             integer not null,
      -- version
    dty             integer not null,
      -- dirty: unused, set to 0
    usn             integer not null,
      -- update sequence number: used for finding diffs when syncing. See usn in cards table for more details.
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
      -- a cache of tags used in the collection (probably for autocomplete etc)
);

-- Contains deleted cards that need to be synced. usn should be set to -1, and oid is the original card id
CREATE TABLE graves (
    usn             integer not null,
    oid             integer not null,
    type            integer not null
);

-- Notes contain the raw information that is formatted into a number of cards according to the models
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
      -- update sequence number: for finding diffs when syncing. See the description in the cards table for more info
    tags            text not null,
      -- space-separated string of tags. includes space at the beginning and end of the field, for LIKE "% tag %" queries
    flds            text not null,
      -- the values of the fields in this note. separated by 0x1f (31) separation character.
    sfld            integer not null,
      -- sort field: used for quick sorting and checking uniqueness
    csum            integer not null,
      -- field checksum
    flags           integer not null,
      -- unused
    data            text not null
      -- unused
);

-- revlog is a review history; it has a row for every single review you've ever done!
CREATE TABLE revlog (
    id              integer primary key,
       -- epoch-seconds timestamp of when you did the review
    cid             integer not null,
       -- cards.id
    usn             integer not null,
        -- update sequence number: for finding diffs when syncing. See the description in the cards table for more info
    ease            integer not null,
       -- which button you pushed to score your recall. 1(wrong), 2(hard), 3(ok), 4(easy)
    ivl             integer not null,
       -- interval
    lastIvl         integer not null,
       -- last interval
    factor          integer not null,
      -- factor
    time            integer not null,
       -- how many milliseconds your review took, up to 60000 (60s)
    type            integer not null
);


CREATE INDEX ix_cards_nid on cards (nid);
CREATE INDEX ix_cards_sched on cards (did, queue, due);
CREATE INDEX ix_cards_usn on cards (usn);
CREATE INDEX ix_notes_csum on notes (csum);
CREATE INDEX ix_notes_usn on notes (usn);
CREATE INDEX ix_revlog_cid on revlog (cid);
CREATE INDEX ix_revlog_usn on revlog (usn);
```

# Models JSONObject
Here is an annotated description of the JSONObjects in the models field of the `col` table:
``` java
{
    css : "CSS, shared for all templates",
    did :
        "Long specifying the id of the deck that cards are added to by default",
    flds : [
             "JSONArray containing a JSONObject for each field in the model as follows:",
             {
               font : "display font",
               media : "array of media. appears to be unused",
               name : "field name",
               ord : "ordinal of the field - goes from 0 to num fields -1",
               rtl : "boolean, right-to-left script",
               size : "font size",
               sticky : "sticky fields retain the value that was last added to them when adding new notes"
             }
           ],
    id : "model ID, matches cards.mid",
    latexPost : "String added to end of LaTeX expressions (usually \\end{document})",
    latexPre : "preample for LaTeX expressions",
    mod : "modification time in milliseconds",
    name : "model name",
    req : [
            "Array of arrays describing which fields are required for each card to be generated",
            [
              "array index, 0, 1, ...",
              '? string, "all"',
              "another array",
              ["appears to be the array index again"]
            ]
          ],
    sortf : "Integer specifying which field is used for sorting in the browser",
    tags : "Anki saves the tags of the last added note to the current model",
    tmpls : [
              "JSONArray containing a JSONObject of the CardTemplate for each card in the model",
              {
                afmt : "answer template string",
                bafmt : "browser answer format: used for displaying answer in browser",
                bqfmt : "browser question format: used for displaying question in browser",
                did : "null",
                name : "template name",
                ord : "template number, see flds",
                qfmt : "question format string"
              }
            ],
    type : "Integer specifying what type of model. 0 for standard, 1 for cloze",
    usn : "usn: Update sequence number: used for checking whether or not sync is required (see description in cards table for more info)",
    vers : "Legacy version number (unused)"
}
```