source: https://github.com/ankitects/anki/tree/2.1.44/rslib/src/storage/upgrades

Changes are being made to the Anki database structure. According to the `mod.rs` file in the above link, the minimum supported version (and the version that new collections are created with) is version 11. This number is found in the `ver` field of the `col` table. AnkiDroid uses version 11 of the database at the time of this writing. Desktop Anki
seems to perform upgrades to the database when opening it to improve performance, but then downgrades to allow compatibility with Anki clients still on version 11.

The `schemaXX_upgrade.sql` files indicate what each upgrade does. Here are explantions for each of those files:

# schema17_upgrade.sql
additional fields (db term, not Anki's fields) for the `tags` table.
# schema15_upgrade.sql
new tables `FIELDS`, `templates`, `notetypes`, `decks`
# schema14_upgrade.sql
new tables `deck_config`, `config`, and `tags` to replace the corresponding fields in the `col` table.

# Theories
I almost exclusively review on AnkiDroid and so I'm on db version 11 and can't confirm these:
* new tables have a field of `mtime_secs integer NOT NULL` is this modified time in epoch seconds? Aka what the older tables have as `mod`?
* similarly, there's a new field of `ntid integer NOT NULL,` in some of the tables. Is this referring to the id of a note or the id of a note type? 
