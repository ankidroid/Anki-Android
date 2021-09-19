//noinspection MissingCopyrightHeader - explicitly not GPL

/*
 * Copying and distribution of this file, with or without modification, are permitted in any
 * medium without royalty. This file is offered as-is, without any warranty.
 */
package com.ichi2.anki;
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
 * <a href="https://docs.ankiweb.net/getting-started.html#key-concepts">the basics section in the Anki manual.</a>
 * </p>
 * <p></p>
 * <p>
 * In short, you can think of cards as instances of notes, with the limitation that the number of
 * instances and their names are pre-defined.
 * </p>
 * <p>
 * The most important data of notes/cards are "fields". Fields contain the actual information of the
 * flashcard that is used for learning. Typical fields are "Japanese" and "English" (for a native
 * English speaker to learn Japanese), or just "front" and "back" (for a generic front side and back
 * side of a card, without saying anything about the purpose).
 * </p>
 * <p></p>
 * Note and card information is accessed in the following way:
 * <p></p>
 * <ul>
 * <li>
 * Each row from the {@link Note} provider represents a note that is stored in the AnkiDroid database.
 * This provider must be used in order to find flashcards. The notes
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
 * A row from the {@link Card} sub-provider gives access to notes cards. The
 * cards are accessed as described in the {@link Card} description.
 * </li>
 * <li>
 * The format of notes and cards is described in models. The models are accessed as described
 * in the {@link Model} description.
 * </li>
 * </ul>
 * <p></p>
 * The AnkiDroid Flashcard content provider supports the following operation on it's URIs:
 * <p></p>
 * <table class="memberSummary" border="">
 * <caption><span>URIs and operations supported by CardContentProvider</span><span class="tabEnd">&nbsp;</span></caption>
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
    public static final String READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE";

    /**
     * A content:// style uri to the authority for the flash card provider
     */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /* Don't create instances of this class. */
    private FlashCardsContract() {
    }


    /**
     * The Notes can be accessed by
     * the {@link #CONTENT_URI}. If the {@link #CONTENT_URI} is appended by the note's ID, this
     * note can be directly accessed. If no ID is appended the content provides functions return
     * all the notes that match the query as defined in {@code selection} argument in the
     * {@code query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)} call.
     * For queries, the {@code selectionArgs} parameter can contain an optional selection statement for the notes table
     * in the sql database. E.g. "mid = 12345678" could be used to limit to a particular model ID.
     * The {@code selection} parameter is an optional search string for the Anki browser. The syntax is described
     * <a href="https://docs.ankiweb.net/searching.html">in the search section of the Anki manual</a>.
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
     * <p>
     * Updating tags for a note can be done this way:
     * <pre>
     *     <code>
             Uri updateNoteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
             ContentValues values = new ContentValues();
             values.put(FlashCardsContract.Note.TAGS, tag1 + " " + tag2);
             int updateCount = cr.update(updateNoteUri, values, null, null);
     *     </code>
     * </pre>
     *
     * <p>
     * A note consists of the following columns:
     * <p>
     * <table class="jd-sumtable"><caption>Note column description</caption>
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
     * <td>see <a href="https://github.com/ankidroid/Anki-Android/wiki/Database-Structure">main documentation</a></td>
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
     * <td>see <a href="https://github.com/ankidroid/Anki-Android/wiki/Database-Structure">main documentation</a></td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #USN}</td>
     * <td>read-only</td>
     * <td>see <a href="https://github.com/ankidroid/Anki-Android/wiki/Database-Structure">main documentation</a></td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #TAGS}</td>
     * <td>read-write</td>
     * <td>NoteTag of this note. NoteTag are separated  by spaces.</td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #FLDS}</td>
     * <td>read-write</td>
     * <td>Fields of this note. Fields are separated by "\\x1f", a.k.a. Consts.FIELD_SEPARATOR</td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #SFLD}</td>
     * <td>read-only</td>
     * <td>see <a href="https://github.com/ankidroid/Anki-Android/wiki/Database-Structure">main documentation</a></td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #CSUM}</td>
     * <td>read-only</td>
     * <td>see <a href="https://github.com/ankidroid/Anki-Android/wiki/Database-Structure">main documentation</a></td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #FLAGS}</td>
     * <td>read-only</td>
     * <td>see <a href="https://github.com/ankidroid/Anki-Android/wiki/Database-Structure">main documentation</a></td>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #DATA}</td>
     * <td>read-only</td>
     * <td>see <a href="https://github.com/ankidroid/Anki-Android/wiki/Database-Structure">main documentation</a></td>
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
         * <p>
         *
         * <p>
         * If the URI is appended by the note ID and then the keyword "data", it is possible to
         * access the details of a note:
         * <p>
         * <pre>
         *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
         *         Uri dataUri = Uri.withAppendedPath(noteUri, "data");
         *     </pre>
         * <p>
         * <p>
         *
         * For examples on how to use the URI for queries see class description.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "notes");

        /**
         * The content:// style URI for notes, but with a direct SQL query to the notes table instead of accepting
         * a query in the libanki browser search syntax like the main URI #CONTENT_URI does.
         */
        public static final Uri CONTENT_URI_V2 = Uri.withAppendedPath(AUTHORITY_URI, "notes_v2");

        /**
         * This is the ID of the note. It is the same as the note ID in Anki. This ID can be
         * used for accessing the data of a note using the URI
         * "content://com.ichi2.anki.flashcards/notes/&lt;ID&gt;/data
         */
        public static final String _ID = "_id";
        public static final String GUID = "guid";
        public static final String MID = "mid";
        public static final String ALLOW_EMPTY = "allow_empty";
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

        /**
         * Used only by bulkInsert() to specify which deck the notes should be placed in
         */
        public static final String DECK_ID_QUERY_PARAM = "deckId";
    }



    /**
     * A model describes what cards look like.
     * <table class="jd-sumtable"><caption>Card model description</caption>
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
     * <td>String</td>
     * <td>{@link #NAME}</td>
     * <td>Name of the model.
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #CSS}</td>
     * <td>CSS styling code which is shared across all the templates</td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #FIELD_NAMES}</td>
     * <td>read-only</td>
     * <td>Names of all the fields, separate by the 0x1f character</td>
     * </tr>
     * <tr>
     * <td>Integer</td>
     * <td>{@link #NUM_CARDS}</td>
     * <td>read-only</td>
     * <td>Number of card templates, which corresponds to the number of rows in the templates table
     * </td>
     * </tr>
     * <tr>
     * <td>Long</td>
     * <td>{@link #DECK_ID}</td>
     * <td>read-only</td>
     * <td>The default deck that cards should be added to</td>
     * </tr>
     * <tr>
     * <td>Integer</td>
     * <td>{@link #SORT_FIELD_INDEX}</td>
     * <td>read-only</td>
     * <td>Which field is used as the main sort field</td>
     * </tr>
     * <tr>
     * <td>Integer</td>
     * <td>{@link #TYPE}</td>
     * <td>read-only</td>
     * <td>0 for normal model, 1 for cloze model</td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #LATEX_POST}</td>
     * <td>read-only</td>
     * <td>Code to go at the end of LaTeX renderings in Anki Desktop</td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #LATEX_PRE}</td>
     * <td>read-only</td>
     * <td>Code to go at the front of LaTeX renderings in Anki Desktop</td>
     * </tr>
     * </table>
     * <p>
     * It's possible to query all models at once like this
     * <p>
     * <pre>
     *     <code>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         final Cursor cursor = cr.query(FlashCardsContract.Model.CONTENT_URI,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *     </code>
     *     </pre>
     * <p>
     *
     * It's also possible to access a specific model like this:
     * <p>
     * <pre>
     *     <code>
     *         long modelId = ...// Use the correct model ID
     *         Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
     *         final Cursor cur = cr.query(modelUri,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *     </code>
     *     </pre>
     * <p>
     *
     * Instead of specifying the model ID, it's also possible to get the currently active model using the following URI:
     * <p>
     * <pre>
     *      <code>
     *          Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, FlashCardsContract.Model.CURRENT_MODEL_ID);
     *      </code>
     * </pre>
     * <p>
     */
    public static class Model {
        /**
         * The content:// style URI for model. If the it is appended by the model's ID, this
         * note can be directly accessed. See class description above for further details.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "models");
        public static final String CURRENT_MODEL_ID = "current";

        /**
         * This is the ID of the model. It is the same as the note ID in Anki. This ID can be
         * used for accessing the data of the model using the URI
         * "content://com.ichi2.anki.flashcards/models/&lt;ID&gt;
         */
        public static final String _ID = "_id";
        public static final String NAME = "name";
        public static final String FIELD_NAME = "field_name";
        public static final String FIELD_NAMES = "field_names";
        public static final String NUM_CARDS = "num_cards";
        public static final String CSS = "css";
        public static final String SORT_FIELD_INDEX = "sort_field_index";
        public static final String TYPE = "type";
        public static final String LATEX_POST = "latex_post";
        public static final String LATEX_PRE = "latex_pre";
        public static final String NOTE_COUNT = "note_count";

        /**
         * The deck ID that is selected by default when adding new notes with this model.
         * This is only used when the "Deck for new cards" preference is set to "Decide by note type"
         */
        public static final String DECK_ID = "deck_id";

        public static final String[] DEFAULT_PROJECTION = {
                _ID,
                NAME,
                FIELD_NAMES,
                NUM_CARDS,
                CSS,
                DECK_ID,
                SORT_FIELD_INDEX,
                TYPE,
                LATEX_POST,
                LATEX_PRE};

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
     * Card template for a model. A template defines how to render the fields of a note into the actual HTML that
     * makes up a flashcard. A model can define multiple card templates, for example a Forward and Reverse Card could
     * be defined with the forward card allowing to review a word from Japanese-&gt;English (e.g. 犬 -&gt; dog), and the
     * reverse card allowing review in the "reverse" direction (e.g dog -&gt; 犬). When a Note is inserted, a Card will
     * be generated for each active CardTemplate which is defined.
     */
    public static class CardTemplate {

        /**
         * MIME type used for data.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.ichi2.anki.model.template";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.com.ichi2.anki.model.template";

        /**
         * Row ID. This is a virtual ID which actually does not exist in AnkiDroid's data base.
         * This column only exists so that this interface can be used with existing CursorAdapters
         * that require the existence of a "_id" column. This means, that it CAN NOT be used
         * reliably over subsequent queries. Especially if the number of cards or fields changes,
         * the _ID will change too.
         */
        public static final String _ID = "_id";

        /**
         * This is the ID of the model that this row belongs to (i.e. {@link Model#_ID}).
         */
        public static final String MODEL_ID = "model_id";


        /**
         * This is the ordinal / index of the card template (from 0 to number of cards - 1).
         */
        public static final String ORD = "ord";


        /**
         * The template name e.g. "Card 1".
         */
        public static final String NAME = "card_template_name";


        /**
         * The definition of the template for the question
         */
        public static final String QUESTION_FORMAT = "question_format";


        /**
         * The definition of the template for the answer
         */
        public static final String ANSWER_FORMAT = "answer_format";


        /**
         * Optional alternative definition of the template for the question when rendered with the browser
         */
        public static final String BROWSER_QUESTION_FORMAT = "browser_question_format";


        /**
         * Optional alternative definition of the template for the answer when rendered with the browser
         */
        public static final String BROWSER_ANSWER_FORMAT = "browser_answer_format";

        public static final String CARD_COUNT = "card_count";


        /**
         * Default columns that are returned when querying the ...models/#/templates URI.
         */
        public static final String[] DEFAULT_PROJECTION = {
                _ID,
                MODEL_ID,
                ORD,
                NAME,
                QUESTION_FORMAT,
                ANSWER_FORMAT};
    }

    /**
     * A card is an instance of a note.
     * <p>
     * If the URI of a note is appended by the keyword "cards", it is possible to
     * access all the cards that are associated with this note:
     * <p>
     *     <pre>
     *     <code>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
     *         final Cursor cur = cr.query(cardsUri,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *     </code>
     *     </pre>
     * <p>
     * If it is furthermore appended by the cards ordinal (see {@link #CARD_ORD}) it's possible to
     * directly access a specific card.
     * <p>
     *     <pre>
     *     <code>
     *         Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *         Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
     *         Uri specificCardUri = Uri.withAppendedPath(noteUri, Integer.toString(cardOrd));
     *         final Cursor cur = cr.query(specificCardUri,
     *                                     null,  // projection
     *                                     null,  // selection is ignored for this URI
     *                                     null,  // selectionArgs is ignored for this URI
     *                                     null   // sortOrder is ignored for this URI
     *                                     );
     *     </code>
     *     </pre>
     * <p>
     *
     * A card consists of the following columns:
     * <table class="jd-sumtable"><caption>Card column description</caption>
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
     * <td>{@link #DECK_ID}</td>
     * <td>read-write</td>
     * <td>The id of the deck that this card is part of.
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
     * <tr>
     * <td>String</td>
     * <td>{@link #QUESTION_SIMPLE}</td>
     * <td>read-only</td>
     * <td>The question for this card in the simplified form, without card styling information (CSS).
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #ANSWER_SIMPLE}</td>
     * <td>read-only</td>
     * <td>The answer for this card in the simplified form, without card styling information (CSS).
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #ANSWER_PURE}</td>
     * <td>read-only</td>
     * <td>
     * Purified version of the answer. In case the {@link #ANSWER} contains any additional elements
     * (like a duplicate of the question) this is removed for {@link #ANSWER_PURE}.
     * Like {@link #ANSWER_SIMPLE} it does not contain styling information (CSS).
     * </td>
     * </tr>
     * </table>
     *
     * The only writable column is the {@link #DECK_ID}. Moving a card to another deck, can be
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
     *             long newDid = 12345678L;
     *             long oldDid = cur.getLong(cur.getColumnIndex(FlashCardsContract.Card.DECK_ID));
     *             if(newDid != oldDid){
     *                 // Move to new deck
     *                 ContentValues values = new ContentValues();
     *                 values.put(FlashCardsContract.Card.DECK_ID, newDid);
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
        public static final String DECK_ID = "deck_id";

        /**
         * The question for this card.
         */
        public static final String QUESTION = "question";

        /**
         * The answer for this card.
         */
        public static final String ANSWER = "answer";

        /**
         * Simplified version of the question, without card styling (CSS).
         */
        public static final String QUESTION_SIMPLE = "question_simple";

        /**
         * Simplified version of the answer, without card styling (CSS).
         */
        public static final String ANSWER_SIMPLE = "answer_simple";

        /**
         * Purified version of the answer. In case the ANSWER contains any additional elements
         * (like a duplicate of the question) this is removed for ANSWER_PURE
         */
        public static final String ANSWER_PURE = "answer_pure";

        public static final String[] DEFAULT_PROJECTION = {
                NOTE_ID,
                CARD_ORD,
                CARD_NAME,
                DECK_ID,
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


    /**
     * A ReviewInfo contains information about a card that is scheduled for review.
     * <p></p>
     * To access the next scheduled card(s), a simple query with the {@link #CONTENT_URI} can be used.<br>
     * Arguments:
     * <table class="jd-sumtable"><caption>ReviewInfo information table</caption>
     * <tr>
     * <th>Type</th><th>Name</th><th>default value</th><th>description</th>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>deckID</td>
     * <td>The deck, that was last selected for reviewing by the user in the Deckchooser dialog of the App</td>
     * <td>The deckID of the deck from which the scheduled cards should be pulled.
     * </td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>limit</td>
     * <td>1</td>
     * <td>The maximum number of cards (rows) that will be returned.
     *     In case the deck has fewer scheduled cards, the returned number of cards will be lower than the limit.
     * </td>
     * </tr>
     * </table>
     *
     *
     * <p></p>
     * <pre>
     *
     * <code>Uri scheduled_cards_uri = FlashCardsContract.ReviewInfo.CONTENT_URI;
     * String deckArguments[] = new String[]{"5", "123456789"};
     * String deckSelector = "limit=?, deckID=?";
     * final Cursor cur = cr.query(scheduled_cards_uri,
     *                              null,  // projection
     *                              deckSelector,  // if null, default values will be used
     *                              deckArguments,  // if null, the deckSelector must not contain any placeholders ("?")
     *                              null   // sortOrder is ignored for this URI
     *                              );
     * </code>
     * </pre>
     * <p></p>
     *
     * A ReviewInfo consists of the following columns:
     * <table class="jd-sumtable"><caption>ReviewInfo column information</caption>
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
     * <td>int</td>
     * <td>{@link #BUTTON_COUNT}</td>
     * <td>read-only</td>
     * <td>The number of buttons/ease identifiers that can be used to answer the card.
     * </td>
     * </tr>
     * <tr>
     * <td>JSONArray</td>
     * <td>{@link #NEXT_REVIEW_TIMES}</td>
     * <td>read-only</td>
     * <td>A JSONArray containing when the card will be scheduled for review for all ease identifiers available.<br>
     * The number of entries in this array must equal the number of buttons in {@link #BUTTON_COUNT}.
     * </td>
     * </tr>
     * <tr>
     * <td>JSONArray</td>
     * <td>{@link #MEDIA_FILES}</td>
     * <td>read-only</td>
     * <td>The media files, like images and sound files, contained in the cards.
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #EASE}</td>
     * <td>write-only</td>
     * <td>The ease of the card. Used when answering the card. One of: <br>
     *          com.ichi2.anki.AbstractFlashcardViewer.EASE_1<br>
     *          com.ichi2.anki.AbstractFlashcardViewer.EASE_2<br>
     *          com.ichi2.anki.AbstractFlashcardViewer.EASE_3<br>
     *          com.ichi2.anki.AbstractFlashcardViewer.EASE_4
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #TIME_TAKEN}</td>
     * <td>write-only</td>
     * <td>The it took to answer the card (in milliseconds). Used when answering the card.
     * </td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>{@link #BURY}</td>
     * <td>write-only</td>
     * <td>Set to 1 to bury the card. Mutually exclusive with setting EASE/TIME_TAKEN/SUSPEND
     * </td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>{@link #SUSPEND}</td>
     * <td>write-only</td>
     * <td>Set to 1 to suspend the card. Mutually exclusive with setting EASE/TIME_TAKEN/BURY
     * </td>
     * </tr>
     * </table>
     *
     * <p>
     * Answering a card can be done as shown in this example. Don't set BURY/SUSPEND when answering a card.
     * </p>
     * <pre>
     *    <code>ContentResolver cr = getContentResolver();
     *    Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
     *    ContentValues values = new ContentValues();
     *    long noteId = 123456789; //&lt;-- insert real note id here
     *    int cardOrd = 0;   //&lt;-- insert real card ord here
     *    int ease = AbstractFlashcardViewer.EASE_3; //&lt;-- insert real ease here
     *    long timeTaken = System.currentTimeMillis() - cardStartTime; //&lt;-- insert real time taken here
     *
     *    values.put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId);
     *    values.put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd);
     *    values.put(FlashCardsContract.ReviewInfo.EASE, ease);
     *    values.put(FlashCardsContract.ReviewInfo.TIME_TAKEN, timeTaken);
     *    cr.update(reviewInfoUri, values, null, null);
     *    </code>
     * </pre>
     *
     *
     * <p>
     * Burying or suspending a card can be done this way. Don't set EASE/TIME_TAKEN when burying/suspending a card
     * </p>
     * <pre>
     *    <code>
     *    ContentResolver cr = getContentResolver();
     *    Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
     *    ContentValues values = new ContentValues();
     *    long noteId = card.note().getId();
     *    int cardOrd = card.getOrd();
     *
     *    values.put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId);
     *    values.put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd);
     *
     *    // use this to bury a card
     *    values.put(FlashCardsContract.ReviewInfo.BURY, 1);
     *
     *    // if you want to suspend, use this instead
     *    // values.put(FlashCardsContract.ReviewInfo.SUSPEND, 1);
     *
     *    int updateCount = cr.update(reviewInfoUri, values, null, null);
     *    </code>
     * </pre>
     */
    public static class ReviewInfo {

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "schedule");

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
         * This is the number of ease modes. It can take a value between 2 and 4.
         */
        public static final String BUTTON_COUNT = "button_count";


        /**
         * This is a JSONArray containing the next review times for all buttons.
         */
        public static final String NEXT_REVIEW_TIMES = "next_review_times";


        /**
         * The names of the media files in the question and answer
         */
        public static final String MEDIA_FILES = "media_files";

        /*
         * Ease of an answer. Is not set when requesting the scheduled cards.
         * Can take values of AbstractFlashcardViewer e.g. EASE_1
         */

        public static final String EASE = "answer_ease";

        /*
         * Time it took to answer the card (in ms)
         */

        public static final String TIME_TAKEN = "time_taken";

        /**
         * Write-only field, allows burying of a card when set to 1
         */
        public static final String BURY = "buried";

        /**
         * Write-only field, allows suspending of a card when set to 1
         */
        public static final String SUSPEND = "suspended";

        public static final String[] DEFAULT_PROJECTION = {
                NOTE_ID,
                CARD_ORD,
                BUTTON_COUNT,
                NEXT_REVIEW_TIMES,
                MEDIA_FILES
        };


        /**
         * MIME type used for ReviewInfo.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.ichi2.anki.review_info";

    }

    /**
     * A Deck contains information about a deck contained in the users deck list.
     * <p></p>
     * To request a list of all decks the URI {@link #CONTENT_ALL_URI} can be used.
     * To request the currently selected deck the URI {@link #CONTENT_SELECTED_URI} can be used.<br>
     *
     * A Deck consists of the following columns:
     * <table class="jd-sumtable"><caption>Columns available in a Deck</caption>
     * <tr>
     * <th>Type</th><th>Name</th><th>access</th><th>description</th>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #DECK_ID}</td>
     * <td>read-only</td>
     * <td>This is the unique ID of the Deck.
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #DECK_NAME}</td>
     * <td>This is the name of the Deck as the user usually sees it.
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #DECK_DESC}</td>
     * <td>The deck description shown on the overview page</td>
     * </tr>
     * <tr>
     * <td>JSONArray</td>
     * <td>{@link #DECK_COUNTS}</td>
     * <td>read-only</td>
     * <td>These are the deck counts of the Deck. [learn, review, new]
     * </tr>
     * <tr>
     * <td>JSONObject</td>
     * <td>{@link #OPTIONS}</td>
     * <td>read-only</td>
     * <td>These are the options of the deck.
     * </tr>
     * <tr>
     * <td>Boolean</td>
     * <td>{@link #DECK_DYN}</td>
     * <td>read-only</td>
     * <td>Whether or not the deck is a filtered deck</td>
     * </tr>
     * </table>
     *
     * Requesting a list of all decks can be done as shown in this example
     * <pre>
     *     <code>
     *     Cursor decksCursor = getContentResolver().query(FlashCardsContract.Deck.CONTENT_ALL_URI, null, null, null, null);
     *     if (decksCursor.moveToFirst()) {
     *      HashMap&lt;Long,String&gt; decks = new HashMap&lt;Long,String&gt;();
     *      do {
     *          long deckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
     *          String deckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));
     *          try {
     *              JSONObject deckOptions = new JSONObject(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.OPTIONS)));
     *              JSONArray deckCounts = new JSONArray(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_COUNTS)));
     *          } catch (JSONException e) {
     *              e.printStackTrace();
     *          }
     *          decks.put(deckID, deckName);
     *     } while (decksCursor.moveToNext());
     *     </code>
     * </pre>
     *
     *
     * Requesting a single deck can be done the following way:
     *
     * <pre>
     *     <code>
     *     long deckId = 123456 //&lt;-- insert real deck ID here
     *     Uri deckUri = Uri.withAppendedPath(FlashCardsContract.Deck.CONTENT_ALL_URI, Long.toString(deckId));
     *              Cursor decksCursor = getContentResolver().query(deckUri, null, null, null, null);
     *
     *      if (decksCursor == null || !decksCursor.moveToFirst()) {
     *          Log.d(TAG, "query for deck returned no result");
     *          if (decksCursor != null) {
     *              decksCursor.close();
     *          }
     *      } else {
     *          JSONObject decks = new JSONObject();
     *          long deckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
     *          String deckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));
     *
     *          try {
     *              JSONObject deckOptions = new JSONObject(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.OPTIONS)));
     *              JSONArray deckCounts = new JSONArray(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_COUNTS)));
     *              Log.d(TAG, "deckCounts " + deckCounts);
     *              Log.d(TAG, "deck Options " + deckOptions);
     *              decks.put(deckName, deckID);
     *          } catch (JSONException e) {
     *              e.printStackTrace();
     *          }
     *          decksCursor.close();
     *     }
     *    </code>
     * </pre>
     *
     *
     * Updating the selected deck can be done as shown in this example
     * <pre>
     *     <code>
     *       long deckId = 123456; //&gt;-- insert real deck id here
     *
     *       ContentResolver cr = getContentResolver();
     *       Uri selectDeckUri = FlashCardsContract.Deck.CONTENT_SELECTED_URI;
     *       ContentValues values = new ContentValues();
     *       values.put(FlashCardsContract.Deck.DECK_ID, deckId);
     *       cr.update(selectDeckUri, values, null, null);
     *     </code>
     * </pre>
     *
     */

    public static class Deck {

        public static final Uri CONTENT_ALL_URI = Uri.withAppendedPath(AUTHORITY_URI, "decks");
        public static final Uri CONTENT_SELECTED_URI = Uri.withAppendedPath(AUTHORITY_URI, "selected_deck");
        /**
         * The name of the Deck
         */
        public static final String DECK_NAME = "deck_name";

        /**
         * The unique identifier of the Deck
         */
        public static final String DECK_ID = "deck_id";

        /**
         * The number of cards in the Deck
         */
        public static final String DECK_COUNTS = "deck_count";

        /**
         * The options of the Deck
         */
        public static final String OPTIONS = "options";

        /**
         * 1 if dynamic (AKA filtered) deck
         */
        public static final String DECK_DYN = "deck_dyn";

        /**
         * Deck description
         */
        public static final String DECK_DESC = "deck_desc";

        public static final String[] DEFAULT_PROJECTION = {
                DECK_NAME,
                DECK_ID,
                DECK_COUNTS,
                OPTIONS,
                DECK_DYN,
                DECK_DESC
        };


        /**
         * MIME type used for Deck.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.ichi2.anki.deck";

    }



    /**
     * For interacting with Anki's media collection.
     * <p></p>
     * To insert a file into the media collection use:
     * <pre>
     *     <code>
     *     Uri fileUri = ...; //&lt;-- Use real Uri for media file here
     *     String preferredName = "my_media_file"; //&lt;-- Use preferred name for inserted media file here
     *     ContentResolver cr = getContentResolver();
     *     ContentValues cv = new ContentValues();
     *     cv.put(AnkiMedia.FILE_URI, fileUri.toString());
     *     cv.put(AnkiMedia.PREFERRED_NAME, "file_name");
     *     Uri insertedFile = cr.insert(AnkiMedia.CONTENT_URI, cv);
     *     </code>
     * </pre>
     */
    public static class AnkiMedia {
        /**
         * Content Uri for the MEDIA row of the CardContentProvider
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "media");

        /**
         * Uri.toString() which points to the media file that is to be inserted.
         */
        public static final String FILE_URI = "file_uri";

        /**
         * The preferred name for the file that will be inserted/copied into collection.media
         */
        public static final String PREFERRED_NAME = "preferred_name";
    }
}