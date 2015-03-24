/*
 * Copying and distribution of this file, with or without modification, are permitted in any
 * medium without royalty. This file is offered as-is, without any warranty.
 */

package com.ichi2.anki.provider;

import android.net.Uri;

/**
 * <p>
 * The contract between AnkiDroid and applications. Contains definitions for the supported URIs and
 * columns.
 * </p>
 * <h3>Overview</h3>
 * <p>
 * FlashCardsContract defines the access to flash card related information. Flash cards consist of
 * notes and cards. To find out more about notes and cards, see
 * <a href="http://ankisrs.net/docs/manual.html#the-basics">the basics section in the Anki manual.</a>
 * </p>
 * <p/>
 * <p>
 * In short, you can think of cards as instances of notes, with the limitation that the number of
 * instances and their names are pre-defined.
 * </p>
 * <p>
 * The most important data of notes/cards are "fields". Fields contain the actual information of the
 * flashcard that is used for learning. Typical fields are "Japanese" and "English" (for a native
 * English speaker to learn Japanese), or just "front" and "back" (for a generic front side and back
 * side of a card, without saying anything about the purpose). Fields can be accessed through the
 * {@link FlashCardsContract.Data} content provider using the special
 * {@link FlashCardsContract.Data.Field#MIMETYPE} for fields.
 * </p>
 * <p/>
 * Note and card information is accessed in the following way:
 * </p>
 * <ul>
 * <li>
 * Each row from the {@link Note} provider represents a note that is stored in AnkiDroid.
 * This provider must be used in order to find flashcards. Some of the data that is returned by
 * this provider can also be obtained through the {@link Data} in a more compact way. The notes
 * can be accessed by the {@link Note#CONTENT_URI}, like this to search for note:
 * <pre>
 *     <code>
 *         // Query all available notes
 *         final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, null, null, null);
 *     </code>
 * </pre>
 * or this if you know the note's ID:
 * <pre>
 *     <code>
 *         String noteId = ... // Use the known note ID
 *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, noteId);
 *         final Cursor cur = cr.query(noteUri, null, null, null, null);
 *     </code>
 * </pre>
 * </li>
 * <li>
 * A row from the {@link Data} provider gives access to notes data, such as fields and tags. The
 * data is accessed as described in the {@link FlashCardsContract.DataColumns} description.
 * </li>
 * <li>
 * A row from the {@link Card} provider gives access to notes cards. The
 * cards are accessed as described in the {@link FlashCardsContract.Card} description.
 * </li>
 * <li>
 * The format of notes and cards is described in models. The models are accessed as described
 * in the {@link FlashCardsContract.Model} description.
 * </li>
 * </ul>
 * <p/>
 * The AnkiDroid Flashcard content provider supports the following operation on it's URIs:
 * <p/>
 * <table class="memberSummary" border="0" cellpadding="3" cellspacing="0" summary="URIs and Operations supported by CardContentProvider">
 * <caption><span>URIs and operations</span><span class="tabEnd">&nbsp;</span></caption>
 * <tr>
 * <th class="colFirst" scope="col">URI</th>
 * <th class="colLast" scope="col">Description</th>
 * </tr>
 * <tr class="altColor">
 * <td class="colFirst"><code>notes</code></td>
 * <td class="colLast">Note with id <code>note_id</code> as raw data
 * <div class="block">Supports insert(mid), query(). For code examples see class description of {@link Note}.</div>
 * </td>
 * </tr>
 * <tr class="rowColor">
 * <td class="colFirst"><code>notes/&lt;note_id&gt;</code></td>
 * <td class="colLast">Note with id <code>note_id</code> as raw data
 * <div class="block">Supports query(). For code examples see class description of {@link Note}.</div>
 * </td>
 * </tr>
 * <tr class="altColor">
 * <td class="colFirst"><code>notes/&lt;note_id&gt;/data</code></td>
 * <td class="colLast">Note with id <code>note_id</code> as high level data (i.e. split fields, tags).
 * <div class="block">Supports update(), query(). For code examples see class description of {@link DataColumns}.</div>
 * </td>
 * </tr>
 * <tr class="rowColor">
 * <td class="colFirst"><code>notes/&lt;note_id&gt;/cards</code></td>
 * <td class="colLast">All cards belonging to note <code>note_id</code> as high level data (Deck name, question, answer).
 * <div class="block">Supports query(). For code examples see class description of {@link Card}.</div>
 * </td>
 * </tr>
 * <tr class="altColor">
 * <td class="colFirst"><code>notes/&lt;note_id&gt;/cards/&lt;ord&gt;</code></td>
 * <td class="colLast">NoteCard <code>ord</code> (with ord = 0... num_cards-1) belonging to note <code>note_id</code> as high level data (Deck name, question, answer).
 * <div class="block">Supports update(), query(). For code examples see class description of {@link Card}.</div>
 * </td>
 * </tr>
 * <tr class="rowColor">
 * <td class="colFirst"><code>models</code></td>
 * <td class="colLast">All models as JSONObjects.
 * <div class="block">Supports query(). For code examples see class description of {@link Model}.</div>
 * </td>
 * </tr>
 * <tr class="altColor">
 * <td class="colFirst"><code>model/&lt;model_id&gt;</code></td>
 * <td class="colLast">Direct access to model <code>model_id</code> as JSONObject.
 * <div class="block">Supports query(). For code examples see class description of {@link Model}.</div>
 * </td>
 * </tr>
 * </table>
 */
public class FlashCardsContract {
    public static final String AUTHORITY = "com.ichi2.anki.flashcards";

    /**
     * A content:// style uri to the authority for the flash card provider
     */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /* Don't create instances of this class. */
    private FlashCardsContract() {
    }

    ;

    /**
     * The Notes can be accessed by
     * the {@link #CONTENT_URI}. If the {@link #CONTENT_URI} is appended by the note's ID, this
     * note can be directly accessed. If no ID is appended the content provides functions return
     * all the notes that match the query as defined in {@code selection} argument in the
     * {@code query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)} call.
     * The {@code selectionArgs} parameter is always ignored. The query syntax that must go in the
     * {@code selection} argument is described
     * <a href="http://ankisrs.net/docs/manual.html#searching">in the search section of the Anki manual</a>.
     * <p/>
     * <p>
     * Example for querying notes with a certain tag:
     * <pre>
     *     <code>
     *         final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI,
     *                                        null,         // projection
     *                                        "tag:my_tag", // example query
     *                                        null,         // selectionArgs is ignored for this URI
     *                                        null          // sortOrder is ignored for this URI
     *                                        );
     *     </code>
     * </pre>
     * </p>
     * Example for querying notes with a certain note id with direct URI:
     * <pre>
     *     <code>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         final Cursor cursor = cr.query(noteUri,
     *                                        null,  // projection
     *                                        null,  // selection is ignored for this URI
     *                                        null,  // selectionArgs is ignored for this URI
     *                                        null   // sortOrder is ignored for this URI
     *                                        );
     *     </code>
     * </pre>
     * <p/>
     * In order to insert a new note (the cards for this note will be added to the default deck)
     * the {@link #CONTENT_URI} must be used together with a model (see {@link Model})
     * ID, e.g.
     * <pre>
     *     <code>
     *         Long mId = ... // Use the correct model ID
     *         ContentValues values = new ContentValues();
     *         values.put(FlashCardsContract.Note.MID, mId);
     *         Uri newNoteUri = cr.insert(FlashCardsContract.Note.CONTENT_URI, values);
     *     </code>
     * </pre>
     * <p/>
     * It's not possible to update notes through this interface. Instead the {@link DataColumns}
     * interface and it's implementations should be used.
     * <p/>
     * A note consists of the following columns:
     * <p/>
     * <table class="jd-sumtable">
     * <tr>
     * <th>Type</th><th>Name</th><th>access</th><th>description</th>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #_ID}</td>
     * <td>read-only</td>
     * <td>Row ID. This is the ID of the note. It is the same as the note ID in Anki. This
     * ID can be used for accessing the data of a note using the URI
     * "content://com.ichi2.anki.flashcards/notes/&lt;ID&gt;/data</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #GUID}</td>
     * <td>read-only</td>
     * <td>???</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #MID}</td>
     * <td>read-only</td>
     * <td>This is the ID of the model that is used for rendering the cards. This ID can be used for
     * accessing the data of the model using the URI
     * "content://com.ichi2.anki.flashcards/model/&lt;ID&gt;</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #MOD}</td>
     * <td>read-only</td>
     * <td>???</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #USN}</td>
     * <td>read-only</td>
     * <td>???</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #TAGS}</td>
     * <td>read-only</td>
     * <td>Tags of this note. Tags are separated  by spaces.</td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #FLDS}</td>
     * <td>read-only</td>
     * <td>Fields of this note. Fields are separated by "\\x1f"</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #SFLD}</td>
     * <td>read-only</td>
     * <td>???</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #CSUM}</td>
     * <td>read-only</td>
     * <td>???</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #FLAGS}</td>
     * <td>read-only</td>
     * <td>???</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #DATA}</td>
     * <td>read-only</td>
     * <td>???</td>
     * </tr>
     * </table>
     */
    public static class Note {
        /**
         * The content:// style URI for notes. If the it is appended by the note's ID, this
         * note can be directly accessed, e.g.
         * <p>
         * <pre>
         *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
         *     </pre>
         * </p>
         *
         * <p>
         * If the URI is appended by the note ID and then the keyword "data", it is possible to
         * access the details of a note:
         * <p>
         * <pre>
         *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
         *         Uri dataUri = Uri.withAppendedPath(noteUri, "data");
         *     </pre>
         * </p>
         * </p>
         *
         * For examples on how to use the URI for queries see class description.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "notes");

        /**
         * This is the ID of the note. It is the same as the note ID in Anki. This ID can be
         * used for accessing the data of a note using the URI
         * "content://com.ichi2.anki.flashcards/notes/&lt;ID&gt;/data
         */
        public static final String _ID = "_id";
        public static final String GUID = "guid";
        public static final String MID = "mid";
        public static final String MOD = "mod";
        public static final String USN = "usn";
        public static final String TAGS = "tags";
        public static final String FLDS = "flds";
        public static final String SFLD = "sfld";
        public static final String CSUM = "csum";
        public static final String FLAGS = "flags";
        public static final String DATA = "data";

        public static final String[] DEFAULT_PROJECTION = {
                Note._ID,
                Note.GUID,
                Note.MID,
                Note.MOD,
                Note.USN,
                Note.TAGS,
                Note.FLDS,
                Note.SFLD,
                Note.CSUM,
                Note.FLAGS,
                Note.DATA};

        /**
         * MIME type used for a note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.com.ichi2.anki.note";

        /**
         * MIME type used for notes.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.ichi2.anki.note";
    }

    /**
     * This is the generic interface class that describes the detailed content of notes.
     * <p/>
     * The note details consist of four columns:
     * <p/>
     * <table class="jd-sumtable">
     * <tr>
     * <th>Type</th><th>Name</th><th>access</th><th>description</th>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #_ID}</td>
     * <td>read-only</td>
     * <td>Row ID. This is a virtual ID which actually does not exist in AnkiDroid's data base.</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #NOTE_ID}</td>
     * <td>read-only</td>
     * <td>This is the ID of the note that this row belongs to (i.e. {@link Note#_ID}).
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #MIMETYPE}</td>
     * <td>read-only</td>
     * <td>This describes the MIME type of the row, which describes how to interpret the columns
     * {@link #DATA1} and {@link #DATA2}.
     * </td>
     * </tr>
     * <tr>
     * <td>MIMETYPE dependent.</td>
     * <td>{@link #DATA1}</td>
     * <td>MIMETYPE dependent.</td>
     * <td>This is the first of two data columns. The column must be interpreted according to the
     * {@link #MIMETYPE} column.
     * </td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #DATA2}</td>
     * <td>MIMETYPE dependent.</td>
     * <td>This is the second of two data columns. The column must be interpreted according to the
     * {@link #MIMETYPE} column.
     * </td>
     * </tr>
     * </table>
     * <p/>
     * Example for querying data and using the aliases from {@link Data.Field} and
     * {@link Data.Tags}:
     * <pre>
     *     <code>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         Uri dataUri = Uri.withAppendedPath(noteUri, "data");
     *         final Cursor cur = cr.query(dataUri,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *         do {
     *             if (cur.getString(cur.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE)) {
     *                 String fieldContent = cur.getString(cur.getColumnIndex(FlashCardsContract.Data.Field.FIELD_CONTENT));
     *                 String fieldName = cur.getString(cur.getColumnIndex(FlashCardsContract.Data.Field.FIELD_NAME));
     *                 // Do something
     *             } else if (cur.getString(cur.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Tags.CONTENT_ITEM_TYPE)) {
     *                 String tags = cur.getString(cur.getColumnIndex(FlashCardsContract.Data.Tags.TAG_CONTENT));
     *                 // Do something
     *             } else {
     *                 // Unknown MIME type
     *             }
     *         } while (cur.moveToNext());
     *     </code>
     * </pre>
     * <p/>
     * Example for updating fields using the aliases from {@link Data.Field}:
     * <pre>
     *     <code>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         Uri dataUri = Uri.withAppendedPath(noteUri, "data");
     *         Cursor cur = cr.query(dataUri, null, null, null, null);
     *         assertNotNull("Check that there is a valid cursor for detail data", cur);
     *         assertEquals("Move to beginning of cursor after querying for detail data", true, cur.moveToFirst());
     *         do {
     *             if (cur.getString(cur.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE)) {
     *                 // Update field
     *                 ContentValues values = new ContentValues();
     *                 values.put(FlashCardsContract.DataColumns.MIMETYPE, FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE);
     *                 values.put(FlashCardsContract.Data.Field.FIELD_NAME, cur.getString(cur.getColumnIndex(FlashCardsContract.Data.Field.FIELD_NAME)));
     *                 values.put(FlashCardsContract.Data.Field.FIELD_CONTENT, TEST_FIELD_VALUE);
     *                 cr.update(dataUri, values, null, null);
     *             } else {
     *                 // ignore other data
     *             }
     *         } while (cur.moveToNext());
     *     </code>
     * </pre>
     */
    public interface DataColumns {
        /**
         * Row ID. This is a virtual ID which actually does not exist in AnkiDroid's data base.
         * This column only exists so that this interface can be used with existing CursorAdapters
         * that require the existence of a "_id" column. This means, that it CAN NOT be used
         * reliably over subsequent queries. Especially if the number of cards or fields changes,
         * the _ID will change too.
         */
        public static final String _ID = "_id";

        /**
         * This is the ID of the note that this row belongs to (i.e. {@link Note#_ID}).
         */
        public static final String NOTE_ID = "note_id";

        /**
         * This describes the MIME type of the row, which describes how to interpret the columns
         * {@link #DATA1} and {@link #DATA2}. Allowed values are:
         * <ul>
         * <li>{@link FlashCardsContract.Data.Field#CONTENT_ITEM_TYPE}:
         * You can use the aliases described in
         * {@link FlashCardsContract.Data.Field} to access the
         * columns instead of the generic "DATA1" or "DATA2".
         * </li>
         * <li>{@link FlashCardsContract.Data.Tags#CONTENT_ITEM_TYPE}:
         * You can use the aliases described in
         * {@link FlashCardsContract.Data.Tags} to access the
         * columns instead of the generic "DATA1" or "DATA2".
         * </li>
         * </ul>
         */
        public static final String MIMETYPE = "mimetype";

        /**
         * This is the first of two data columns. The column must be interpreted according to the
         * {@link #MIMETYPE} column.
         */
        public static final String DATA1 = "data1";

        /**
         * This is the second of two data columns. The column must be interpreted according to the
         * {@link #MIMETYPE} column.
         */
        public static final String DATA2 = "data2";

        /**
         * Default columns that are returned when querying the ...notes/#/data URI.
         */
        public static final String[] DEFAULT_PROJECTION = {
                _ID,
                NOTE_ID,
                MIMETYPE,
                DATA1,
                DATA2};
    }

    /**
     * Container for definitions of common data types returned by the data content provider.
     */
    public class Data {

        /**
         * MIME type used for data.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.ichi2.anki.note_data";

        /**
         * A data kind representing a field in a note.
         * <p/>
         * You can use the columns defined for
         * {@link FlashCardsContract.DataColumns} as well as the following
         * aliases.
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th><th>Alias</th><th>Data column</th><th>access</th><th></th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #FIELD_NAME}</td>
         * <td>{@link FlashCardsContract.DataColumns#DATA1}</td>
         * <td>read-only</td>
         * <td>Field name</td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #FIELD_CONTENT}</td>
         * <td>{@link FlashCardsContract.DataColumns#DATA2}</td>
         * <td>read-write</td>
         * <td>Field content</td>
         * </tr>
         * </table>
         * <p/>
         * Since the fields are defined by the model type, it is not possible to insert or delete
         * fields. To update a field see the class description of {@link FlashCardsContract.DataColumns}.
         */
        public class Field implements DataColumns {
            /**
             * MIME type used for fields.
             */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.com.ichi2.anki.note_field";

            /**
             * The field name as defined by the model, e.g. "front" or "back".
             */
            public static final String FIELD_NAME = DATA1;

            /**
             * The content of the field, e.g. "dog" or "犬".
             */
            public static final String FIELD_CONTENT = DATA2;
        }

        /**
         * A data kind representing tags in a note.
         * <p/>
         * You can use the columns defined for
         * {@link FlashCardsContract.DataColumns} as well as the following
         * aliases.
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th><th>Alias</th><th>Data column</th><th>access</th><th></th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #TAG_CONTENT}</td>
         * <td>{@link FlashCardsContract.DataColumns#DATA1}</td>
         * <td>read-write</td>
         * <td>Tags, seperated by spaces</td>
         * </tr>
         * </table>
         */
        public class Tags implements DataColumns {
            /**
             * MIME type used for tags.
             */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.com.ichi2.anki.note_tags";

            /**
             * The tags of this note, e.g. "fruits".
             */
            public static final String TAG_CONTENT = DATA1;
        }
    }

    /**
     * A model describes what cards look like. This information is described in the
     * column {@link #JSONOBJECT}.
     * <table class="jd-sumtable">
     * <tr>
     * <th>Type</th><th>Name</th><th>access</th><th>description</th>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #_ID}</td>
     * <td>read-only</td>
     * <td>Model ID.</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #NAME}</td>
     * <td>read-only</td>
     * <td>Name of the model.
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #JSONOBJECT}</td>
     * <td>read-only</td>
     * <td>Describes what the cards for this model will look like.
     * </td>
     * </tr>
     * </table>
     * <p/>
     * It's possible to query all models at once like this
     * <p>
     * <pre>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         final Cursor cursor = cr.query(FlashCardsContract.Model.CONTENT_URI,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *     </pre>
     * </p>
     *
     * It's also possible to access a specific model like this:
     * <p>
     * <pre>
     *         long modelId = ...// Use the correct model ID
     *         Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
     *         final Cursor cur = cr.query(modelUri,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *     </pre>
     * </p>
     */
    public static class Model {
        /**
         * The content:// style URI for model. If the it is appended by the model's ID, this
         * note can be directly accessed. See class description above for further details.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "models");

        /**
         * This is the ID of the model. It is the same as the note ID in Anki. This ID can be
         * used for accessing the data of the model using the URI
         * "content://com.ichi2.anki.flashcards/models/&lt;ID&gt;
         */
        public static final String _ID = "_id";
        public static final String NAME = "name";

        // TODO: The fields need description.
        /**
         * The JSONOBJECT can be converted into a org.json.JSONObject and describes the model
         * in detail. A model consists of
         * <ul>
         * <li>sortf: What is this</li>
         * <li>did: What is this</li>
         * <li>latexPre: What is this</li>
         * <li>latexPost: What is this</li>
         * <li>mod: What is this</li>
         * <li>usn: What is this</li>
         * <li>vers: What is this</li>
         * <li>type: What is this</li>
         * <li>css: What is this</li>
         * <li>name: The name of the model (same as in column {@link #NAME})</li>
         * <li>flds: What is this</li>
         * <li>tmpls: This is a JSONArray describing the template. Each entry in this array
         * describes which cards exist for each note that uses this model.</li>
         * <li>tags: What is this</li>
         * <li>id: The ID of the model (same as in column {@link #_ID})</li>
         * <li>req (optional): This seems to describe which fields(?) or cards(?) are required. But required for what?</li>
         * </ul>
         */
        public static final String JSONOBJECT = "jsonobject";

        public static final String[] DEFAULT_PROJECTION = {
                _ID,
                NAME,
                JSONOBJECT};

        /**
         * MIME type used for a model.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.com.ichi2.anki.model";

        /**
         * MIME type used for model.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.ichi2.anki.model";
    }

    /**
     * A card is an instance of a note.
     * <p/>
     * If the URI of a note is appended by the keyword "cards", it is possible to
     * access all the cards that are associated with this note:
     * <p>
     * <pre>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
     *         final Cursor cur = cr.query(cardsUri,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *     </pre>
     * </p>
     * If it is furthermore appended by the cards ordinal (see {@link #CARD_ORD}) it's possible to
     * directly access a specific card.
     * <p>
     * <pre>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
     *         Uri specificCardUri = Uri.withAppendedPath(noteUri, Integer.toString(cardOrd));
     *         final Cursor cur = cr.query(specificCardUri,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *     </pre>
     * </p>
     *
     * A card consists of the following columns:
     * <table class="jd-sumtable">
     * <tr>
     * <th>Type</th><th>Name</th><th>access</th><th>description</th>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #NOTE_ID}</td>
     * <td>read-only</td>
     * <td>This is the ID of the note that this row belongs to (i.e. {@link Note#_ID}).
     * </td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>{@link #CARD_ORD}</td>
     * <td>read-only</td>
     * <td>This is the ordinal of the card. A note has 1..n cards. The ordinal can also be used
     * to directly access a card as describe in the class description.
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #CARD_NAME}</td>
     * <td>read-only</td>
     * <td>The card's name.
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #DECK_NAME}</td>
     * <td>read-write</td>
     * <td>The name of the deck that this card is part of.
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #QUESTION}</td>
     * <td>read-only</td>
     * <td>The question for this card.
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #ANSWER}</td>
     * <td>read-only</td>
     * <td>The answer for this card.
     * </td>
     * </tr>
     * </table>
     *
     * The only writable column is the {@link #DECK_NAME}. Moving a card to another deck, can be
     * done as shown in this example
     * <pre>
     *     <code>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
     *         final Cursor cur = cr.query(cardsUri,
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *         do {
     *             String deckName = cur.getString(cur.getColumnIndex(FlashCardsContract.Card.DECK_NAME));
     *             if(!deckName.equals("MyDeck")){
     *                 // Move to "MyDeck"
     *                 ContentValues values = new ContentValues();
     *                 values.put(FlashCardsContract.Card.DECK_NAME, "MyDeck");
     *                 Uri cardUri = Uri.withAppendedPath(cardsUri, cur.getString(cur.getColumnIndex(FlashCardsContract.Card.CARD_ORD)));
     *                 cr.update(cardUri, values, null, null);
     *             }
     *         } while (cur.moveToNext());
     *     </code>
     * </pre>
     */
    public static class Card {
        /**
         * This is the ID of the note that this card belongs to (i.e. {@link Note#_ID}).
         */
        public static final String NOTE_ID = "note_id";

        /**
         * This is the ordinal of the card. A note has 1..n cards. The ordinal can also be used
         * to directly access a card as describe in the class description.
         */
        public static final String CARD_ORD = "ord";

        /**
         * The card's name.
         */
        public static final String CARD_NAME = "card_name";

        /**
         * The name of the deck that this card is part of.
         */
        public static final String DECK_NAME = "deck_name";

        /**
         * The question for this card.
         */
        public static final String QUESTION = "question";

        /**
         * The answer for this card.
         */
        public static final String ANSWER = "answer";

        public static final String[] DEFAULT_PROJECTION = {
                NOTE_ID,
                CARD_ORD,
                CARD_NAME,
                DECK_NAME,
                QUESTION,
                ANSWER};

        /**
         * MIME type used for a card.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.com.ichi2.anki.card";

        /**
         * MIME type used for cards.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.ichi2.anki.card";
    }
}