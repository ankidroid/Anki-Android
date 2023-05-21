/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2015 Frank Oltmanns <frank.oltmanns@gmail.com>                         *
 * Copyright (c) 2015 Timothy Rae <timothy.rae@gmail.com>                               *
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>                                  *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.tests

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.CursorWindow
import android.net.Uri
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.testutil.DatabaseUtils.cursorFillWindow
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.async.TaskManager.Companion.waitToFinish
import com.ichi2.libanki.*
import com.ichi2.utils.BlocksSchemaUpgrade
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.BackendFactory.defaultLegacySchema
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import timber.log.Timber
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.junit.JUnitAsserter.assertNotNull

/**
 * Test cases for [com.ichi2.anki.provider.CardContentProvider].
 *
 *
 * These tests should cover all supported operations for each URI.
 */
@KotlinCleanup("is -> equalTo")
@RunWith(Parameterized::class)
class ContentProviderTest : InstrumentedTest() {
    @JvmField // required for Parameter
    @Parameterized.Parameter
    @KotlinCleanup("lateinit")
    var schedVersion = 0

    @get:Rule
    var runtimePermissionRule = grantPermissions(storagePermission, FlashCardsContract.READ_WRITE_PERMISSION)

    // Whether tear down should be executed. I.e. if set up was not cancelled.
    private var mTearDown = false

    @KotlinCleanup("lateinit")
    private var mNumDecksBeforeTest = 0

    /* initialCapacity set to expected value when the test is written.
     * Should create no problem if we forget to change it when more tests are added.
     */
    private val mTestDeckIds: MutableList<Long> = ArrayList(TEST_DECKS.size + 1)
    private lateinit var mCreatedNotes: ArrayList<Uri>
    private var mModelId: Long = 0
    private var mDummyFields = arrayOfNulls<String>(1)

    /**
     * Initially create one note for each model.
     */
    @Before
    @BlocksSchemaUpgrade("some of these tests are failing; need to investigate why")
    @Throws(
        Exception::class
    )
    @KotlinCleanup("remove 'requireNoNulls' and fix mDummyFields")
    fun setUp() {
        assumeThat(defaultLegacySchema, `is`(true))
        Timber.i("setUp()")
        mCreatedNotes = ArrayList()

        // We have parameterized the "schedVersion" variable, if we are on an emulator
        // (so it is safe) we will try to run with multiple scheduler versions
        mTearDown = false
        if (isEmulator()) {
            col.changeSchedulerVer(schedVersion)
        } else {
            if (schedVersion == 1) {
                assumeThat(col.sched.name, equalTo("std"))
            } else {
                assumeThat(col.sched.name, equalTo("std2"))
            }
        }
        mTearDown = true
        // Do not teardown if setup was aborted

        // Add a new basic model that we use for testing purposes (existing models could potentially be corrupted)
        val model = StdModels.BASIC_MODEL.add(col, BASIC_MODEL_NAME)
        mModelId = model.getLong("id")
        val fields = model.fieldsNames
        // Use the names of the fields as test values for the notes which will be added
        mDummyFields = fields.toTypedArray()
        // create test decks and add one note for every deck
        mNumDecksBeforeTest = col.decks.count()
        for (fullName in TEST_DECKS) {
            val path = Decks.path(fullName)
            var partialName: String? = ""
            /* Looping over all parents of full name. Adding them to
             * mTestDeckIds ensures the deck parents decks get deleted
             * too at tear-down.
             */for (s in path) {
                partialName += s
                /* If parent already exists, don't add the deck, so
                 * that we are sure it won't get deleted at
                 * set-down, */
                if (col.decks.byName(partialName!!) != null) {
                    continue
                }
                val did = col.decks.id(partialName)
                mTestDeckIds.add(did)
                mCreatedNotes.add(setupNewNote(col, mModelId, did, mDummyFields.requireNoNulls(), TEST_TAG))
                partialName += "::"
            }
        }
        // Add a note to the default deck as well so that testQueryNextCard() works
        mCreatedNotes.add(setupNewNote(col, mModelId, 1, mDummyFields.requireNoNulls(), TEST_TAG))
    }

    /**
     * Remove the notes and decks created in setUp().
     */
    @After
    @Throws(Exception::class)
    fun tearDown() {
        Timber.i("tearDown()")
        if (!mTearDown) {
            return
        }

        // Delete all notes
        val remnantNotes = col.findNotes("tag:$TEST_TAG")
        if (remnantNotes.isNotEmpty()) {
            val noteIds = remnantNotes.toLongArray()
            col.remNotes(noteIds)
            col.save()
            assertEquals(
                "Check that remnant notes have been deleted",
                0,
                col.findNotes("tag:$TEST_TAG").size
            )
        }
        // delete test decks
        for (did in mTestDeckIds) {
            col.decks.rem(did, cardsToo = true, childrenToo = true)
        }
        col.decks.flush()
        assertEquals(
            "Check that all created decks have been deleted",
            mNumDecksBeforeTest,
            col.decks.count()
        )
        // Delete test model
        col.modSchemaNoCheck()
        removeAllModelsByName(col, BASIC_MODEL_NAME)
        removeAllModelsByName(col, TEST_MODEL_NAME)
    }

    @Throws(Exception::class)
    private fun removeAllModelsByName(col: com.ichi2.libanki.Collection, name: String) {
        var testModel = col.models.byName(name)
        while (testModel != null) {
            col.models.rem(testModel)
            testModel = col.models.byName(name)
        }
    }

    @Test
    fun testDatabaseUtilsInvocationWorks() {
        // called by android.database.CursorToBulkCursorAdapter
        // This is called by API clients implicitly, but isn't done by this test class
        val firstNote = getFirstCardFromScheduler(col)
        val noteProjection = arrayOf(
            FlashCardsContract.Note._ID,
            FlashCardsContract.Note.FLDS,
            FlashCardsContract.Note.TAGS
        )
        val resolver = contentResolver
        val cursor = resolver.query(
            FlashCardsContract.Note.CONTENT_URI_V2,
            noteProjection,
            "id=" + firstNote!!.nid,
            null,
            null
        )
        assertNotNull(cursor)
        val window = CursorWindow("test")

        // Note: We duplicated the code as it did not appear to be accessible via reflection
        val initialPosition = cursor.position
        cursorFillWindow(cursor, 0, window)
        assertThat("position should not change", cursor.position, `is`(initialPosition))
        assertThat("Count should be copied", window.numRows, `is`(cursor.count))
    }

    /**
     * Check that inserting and removing a note into default deck works as expected
     */
    @Test
    @KotlinCleanup("assertThrows")
    fun testInsertAndRemoveNote() {
        // Get required objects for test
        val cr = contentResolver
        // Add the note
        val values = ContentValues().apply {
            put(FlashCardsContract.Note.MID, mModelId)
            put(FlashCardsContract.Note.FLDS, Utils.joinFields(TEST_NOTE_FIELDS))
            put(FlashCardsContract.Note.TAGS, TEST_TAG)
        }
        val newNoteUri = cr.insert(FlashCardsContract.Note.CONTENT_URI, values)
        assertNotNull("Check that URI returned from addNewNote is not null", newNoteUri)
        val col = reopenCol() // test that the changes are physically saved to the DB
        // Check that it looks as expected
        assertNotNull("check note URI path", newNoteUri!!.lastPathSegment)
        val addedNote = Note(col, newNoteUri.lastPathSegment!!.toLong())
        addedNote.load()
        assertArrayEquals(
            "Check that fields were set correctly",
            addedNote.fields,
            TEST_NOTE_FIELDS
        )
        assertEquals("Check that tag was set correctly", TEST_TAG, addedNote.tags[0])
        val model: JSONObject? = col.models.get(mModelId)
        assertNotNull("Check model", model)
        val expectedNumCards = model!!.getJSONArray("tmpls").length()
        assertEquals("Check that correct number of cards generated", expectedNumCards, addedNote.numberOfCards())
        // Now delete the note
        cr.delete(newNoteUri, null, null)
        try {
            addedNote.load()
            fail("Expected RuntimeException to be thrown when deleting note")
        } catch (e: RuntimeException) {
            // Expect RuntimeException to be thrown when loading deleted note
        }
    }

    /**
     * Check that inserting and removing a note into default deck works as expected
     */
    @Test
    @Throws(Exception::class)
    fun testInsertTemplate() {
        // Get required objects for test
        val cr = contentResolver
        var col = col
        // Add a new basic model that we use for testing purposes (existing models could potentially be corrupted)
        var model: Model? = StdModels.BASIC_MODEL.add(col, BASIC_MODEL_NAME)
        val modelId = model!!.getLong("id")
        // Add the note
        val modelUri = ContentUris.withAppendedId(FlashCardsContract.Model.CONTENT_URI, modelId)
        val testIndex =
            TEST_MODEL_CARDS.size - 1 // choose the last one because not the same as the basic model template
        val expectedOrd = model.getJSONArray("tmpls").length()
        val cv = ContentValues().apply {
            put(FlashCardsContract.CardTemplate.NAME, TEST_MODEL_CARDS[testIndex])
            put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, TEST_MODEL_QFMT[testIndex])
            put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, TEST_MODEL_AFMT[testIndex])
            put(FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT, TEST_MODEL_QFMT[testIndex])
            put(FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT, TEST_MODEL_AFMT[testIndex])
        }
        val templatesUri = Uri.withAppendedPath(modelUri, "templates")
        val templateUri = cr.insert(templatesUri, cv)
        col = reopenCol() // test that the changes are physically saved to the DB
        assertNotNull("Check template uri", templateUri)
        assertEquals(
            "Check template uri ord",
            expectedOrd.toLong(),
            ContentUris.parseId(
                templateUri!!
            )
        )
        model = col.models.get(modelId)
        assertNotNull("Check model", model)
        val template = model!!.getJSONArray("tmpls").getJSONObject(expectedOrd)
        assertEquals(
            "Check template JSONObject ord",
            expectedOrd,
            template.getInt("ord")
        )
        assertEquals(
            "Check template name",
            TEST_MODEL_CARDS[testIndex],
            template.getString("name")
        )
        assertEquals("Check qfmt", TEST_MODEL_QFMT[testIndex], template.getString("qfmt"))
        assertEquals("Check afmt", TEST_MODEL_AFMT[testIndex], template.getString("afmt"))
        assertEquals("Check bqfmt", TEST_MODEL_QFMT[testIndex], template.getString("bqfmt"))
        assertEquals("Check bafmt", TEST_MODEL_AFMT[testIndex], template.getString("bafmt"))
        col.models.rem(model)
    }

    /**
     * Check that inserting and removing a note into default deck works as expected
     */
    @Test
    @Throws(Exception::class)
    fun testInsertField() {
        // Get required objects for test
        val cr = contentResolver
        var col = col
        var model: Model? = StdModels.BASIC_MODEL.add(col, BASIC_MODEL_NAME)
        val modelId = model!!.getLong("id")
        val initialFieldsArr = model.getJSONArray("flds")
        val initialFieldCount = initialFieldsArr.length()
        val noteTypeUri = ContentUris.withAppendedId(FlashCardsContract.Model.CONTENT_URI, modelId)
        val insertFieldValues = ContentValues()
        insertFieldValues.put(FlashCardsContract.Model.FIELD_NAME, TEST_FIELD_NAME)
        val fieldUri = cr.insert(Uri.withAppendedPath(noteTypeUri, "fields"), insertFieldValues)
        assertNotNull("Check field uri", fieldUri)
        // Ensure that the changes are physically saved to the DB
        col = reopenCol()
        model = col.models.get(modelId)
        // Test the field is as expected
        val fieldId = ContentUris.parseId(fieldUri!!)
        assertEquals("Check field id", initialFieldCount.toLong(), fieldId)
        assertNotNull("Check model", model)
        val fldsArr = model!!.getJSONArray("flds")
        assertEquals(
            "Check fields length",
            (initialFieldCount + 1),
            fldsArr.length()
        )
        assertEquals(
            "Check last field name",
            TEST_FIELD_NAME,
            fldsArr.getJSONObject(fldsArr.length() - 1).optString("name", "")
        )
        col.models.rem(model)
    }

    /**
     * Test queries to notes table using direct SQL URI
     */
    @Test
    fun testQueryDirectSqlQuery() {
        // search for correct mid
        val cr = contentResolver
        var cursor = cr.query(
            FlashCardsContract.Note.CONTENT_URI_V2,
            null,
            "mid=$mModelId",
            null,
            null
        )
        assertNotNull(cursor)
        try {
            assertEquals(
                "Check number of results",
                mCreatedNotes.size,
                cursor.count
            )
        } finally {
            cursor.close()
        }
        // search for bogus mid
        cursor = cr.query(FlashCardsContract.Note.CONTENT_URI_V2, null, "mid=0", null, null)
        assertNotNull(cursor)
        try {
            assertEquals("Check number of results", 0, cursor.count)
        } finally {
            cursor.close()
        }
        // check usage of selection args
        cursor = cr.query(FlashCardsContract.Note.CONTENT_URI_V2, null, "mid=?", arrayOf("0"), null)
        assertNotNull(cursor)
    }

    /**
     * Test that a query for all the notes added in setup() looks correct
     */
    @Test
    fun testQueryNoteIds() {
        val cr = contentResolver
        // Query all available notes
        val allNotesCursor =
            cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:$TEST_TAG", null, null)
        assertNotNull(allNotesCursor)
        allNotesCursor.use {
            assertEquals(
                "Check number of results",
                mCreatedNotes.size,
                it.count
            )
            while (it.moveToNext()) {
                // Check that it's possible to leave out columns from the projection
                for (i in FlashCardsContract.Note.DEFAULT_PROJECTION.indices) {
                    val projection =
                        removeFromProjection(FlashCardsContract.Note.DEFAULT_PROJECTION, i)
                    val noteId =
                        it.getString(it.getColumnIndex(FlashCardsContract.Note._ID))
                    val noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, noteId)
                    val singleNoteCursor = cr.query(noteUri, projection, null, null, null)
                    assertNotNull("Check that there is a valid cursor for detail data", singleNoteCursor)
                    try {
                        assertEquals(
                            "Check that there is exactly one result",
                            1,
                            singleNoteCursor!!.count
                        )
                        assertTrue(
                            "Move to beginning of cursor after querying for detail data",
                            singleNoteCursor.moveToFirst()
                        )
                        // Check columns
                        assertEquals(
                            "Check column count",
                            projection.size,
                            singleNoteCursor.columnCount
                        )
                        for (j in projection.indices) {
                            assertEquals(
                                "Check column name $j",
                                projection[j],
                                singleNoteCursor.getColumnName(j)
                            )
                        }
                    } finally {
                        singleNoteCursor!!.close()
                    }
                }
            }
        }
    }

    /**
     * Check that a valid Cursor is returned when querying notes table with non-default projections
     */
    @Test
    fun testQueryNotesProjection() {
        val cr = contentResolver
        // Query all available notes
        for (i in FlashCardsContract.Note.DEFAULT_PROJECTION.indices) {
            val projection = removeFromProjection(FlashCardsContract.Note.DEFAULT_PROJECTION, i)
            val allNotesCursor = cr.query(
                FlashCardsContract.Note.CONTENT_URI,
                projection,
                "tag:$TEST_TAG",
                null,
                null
            )
            assertNotNull("Check that there is a valid cursor", allNotesCursor)
            try {
                assertEquals(
                    "Check number of results",
                    mCreatedNotes.size,
                    allNotesCursor!!.count
                )
                // Check columns
                assertEquals(
                    "Check column count",
                    projection.size,
                    allNotesCursor.columnCount
                )
                for (j in projection.indices) {
                    assertEquals(
                        "Check column name $j",
                        projection[j],
                        allNotesCursor.getColumnName(j)
                    )
                }
            } finally {
                allNotesCursor!!.close()
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun removeFromProjection(inputProjection: Array<String>, idx: Int): Array<String?> {
        val outputProjection = arrayOfNulls<String>(inputProjection.size - 1)
        if (idx >= 0) {
            System.arraycopy(inputProjection, 0, outputProjection, 0, idx)
        }
        for (i in idx + 1 until inputProjection.size) {
            outputProjection[i - 1] = inputProjection[i]
        }
        return outputProjection
    }

    /**
     * Check that updating the flds column works as expected
     * FIXME hanging sometimes. API30? API29?
     */
    @Test
    fun testUpdateNoteFields() {
        val cr = contentResolver
        val cv = ContentValues()
        // Change the fields so that the first field is now "newTestValue"
        val dummyFields2 = mDummyFields.clone()
        dummyFields2[0] = TEST_FIELD_VALUE
        for (uri in mCreatedNotes) {
            // Update the flds
            @Suppress("UNCHECKED_CAST")
            cv.put(FlashCardsContract.Note.FLDS, Utils.joinFields(dummyFields2 as Array<String>))
            cr.update(uri, cv, null, null)
            cr.query(uri, FlashCardsContract.Note.DEFAULT_PROJECTION, null, null, null)
                .use { noteCursor ->
                    assertNotNull(
                        "Check that there is a valid cursor for detail data after update",
                        noteCursor
                    )
                    assertEquals(
                        "Check that there is one and only one entry after update",
                        1,
                        noteCursor!!.count
                    )
                    assertTrue("Move to first item in cursor", noteCursor.moveToFirst())
                    val newFields = Utils.splitFields(
                        noteCursor.getString(noteCursor.getColumnIndex(FlashCardsContract.Note.FLDS))
                    )
                    assertArrayEquals(
                        "Check that the flds have been updated correctly",
                        newFields,
                        dummyFields2
                    )
                }
        }
    }

    /**
     * Check that inserting a new model works as expected
     */
    @Test
    fun testInsertAndUpdateModel() {
        val cr = contentResolver
        var cv = ContentValues().apply {
            // Insert a new model
            put(FlashCardsContract.Model.NAME, TEST_MODEL_NAME)
            put(FlashCardsContract.Model.FIELD_NAMES, Utils.joinFields(TEST_MODEL_FIELDS))
            put(FlashCardsContract.Model.NUM_CARDS, TEST_MODEL_CARDS.size)
        }
        val modelUri = cr.insert(FlashCardsContract.Model.CONTENT_URI, cv)
        assertNotNull("Check inserted model isn't null", modelUri)
        assertNotNull("Check last path segment exists", modelUri!!.lastPathSegment)
        val mid = modelUri.lastPathSegment!!.toLong()
        var col = reopenCol()
        try {
            var model: JSONObject? = col.models.get(mid)
            assertNotNull("Check model", model)
            assertEquals("Check model name", TEST_MODEL_NAME, model!!.getString("name"))
            assertEquals(
                "Check templates length",
                TEST_MODEL_CARDS.size,
                model.getJSONArray("tmpls").length()
            )
            assertEquals(
                "Check field length",
                TEST_MODEL_FIELDS.size,
                model.getJSONArray("flds").length()
            )
            val fields = model.getJSONArray("flds")
            for (i in 0 until fields.length()) {
                assertEquals(
                    "Check name of fields",
                    TEST_MODEL_FIELDS[i],
                    fields.getJSONObject(i).getString("name")
                )
            }
            // Test updating the model CSS (to test updating MODELS_ID Uri)
            cv = ContentValues()
            cv.put(FlashCardsContract.Model.CSS, TEST_MODEL_CSS)
            assertThat(
                cr.update(modelUri, cv, null, null),
                `is`(greaterThan(0))
            )
            col = reopenCol()
            model = col.models.get(mid)
            assertNotNull("Check model", model)
            assertEquals("Check css", TEST_MODEL_CSS, model!!.getString("css"))
            // Update each of the templates in model (to test updating MODELS_ID_TEMPLATES_ID Uri)
            for (i in TEST_MODEL_CARDS.indices) {
                cv = ContentValues().apply {
                    put(FlashCardsContract.CardTemplate.NAME, TEST_MODEL_CARDS[i])
                    put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, TEST_MODEL_QFMT[i])
                    put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, TEST_MODEL_AFMT[i])
                    put(FlashCardsContract.CardTemplate.BROWSER_QUESTION_FORMAT, TEST_MODEL_QFMT[i])
                    put(FlashCardsContract.CardTemplate.BROWSER_ANSWER_FORMAT, TEST_MODEL_AFMT[i])
                }
                val tmplUri = Uri.withAppendedPath(
                    Uri.withAppendedPath(modelUri, "templates"),
                    i.toString()
                )
                assertThat(
                    "Update rows",
                    cr.update(tmplUri, cv, null, null),
                    `is`(
                        greaterThan(0)
                    )
                )
                col = reopenCol()
                model = col.models.get(mid)
                assertNotNull("Check model", model)
                val template = model!!.getJSONArray("tmpls").getJSONObject(i)
                assertEquals(
                    "Check template name",
                    TEST_MODEL_CARDS[i],
                    template.getString("name")
                )
                assertEquals("Check qfmt", TEST_MODEL_QFMT[i], template.getString("qfmt"))
                assertEquals("Check afmt", TEST_MODEL_AFMT[i], template.getString("afmt"))
                assertEquals("Check bqfmt", TEST_MODEL_QFMT[i], template.getString("bqfmt"))
                assertEquals("Check bafmt", TEST_MODEL_AFMT[i], template.getString("bafmt"))
            }
        } finally {
            // Delete the model (this will force a full-sync)
            col.modSchemaNoCheck()
            try {
                val model = col.models.get(mid)
                assertNotNull("Check model", model)
                col.models.rem(model!!)
            } catch (e: ConfirmModSchemaException) {
                // This will never happen
            }
        }
    }

    /**
     * Query .../models URI
     */
    @Test
    fun testQueryAllModels() {
        val cr = contentResolver
        // Query all available models
        val allModels = cr.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null)
        assertNotNull(allModels)
        allModels.use {
            assertThat(
                "Check that there is at least one result",
                allModels.count,
                `is`(greaterThan(0))
            )
            while (allModels.moveToNext()) {
                val modelId =
                    allModels.getLong(allModels.getColumnIndex(FlashCardsContract.Model._ID))
                val modelUri = Uri.withAppendedPath(
                    FlashCardsContract.Model.CONTENT_URI,
                    modelId.toString()
                )
                val singleModel = cr.query(modelUri, null, null, null, null)
                assertNotNull(singleModel)
                singleModel.use {
                    assertEquals(
                        "Check that there is exactly one result",
                        1,
                        it.count
                    )
                    assertTrue("Move to beginning of cursor", it.moveToFirst())
                    val nameFromModels =
                        allModels.getString(allModels.getColumnIndex(FlashCardsContract.Model.NAME))
                    val nameFromModel =
                        it.getString(allModels.getColumnIndex(FlashCardsContract.Model.NAME))
                    assertEquals(
                        "Check that model names are the same",
                        nameFromModel,
                        nameFromModels
                    )
                    val flds =
                        allModels.getString(allModels.getColumnIndex(FlashCardsContract.Model.FIELD_NAMES))
                    assertThat(
                        "Check that valid number of fields",
                        Utils.splitFields(flds).size,
                        `is`(
                            greaterThanOrEqualTo(1)
                        )
                    )
                    val numCards =
                        allModels.getInt(allModels.getColumnIndex(FlashCardsContract.Model.NUM_CARDS))
                    assertThat(
                        "Check that valid number of cards",
                        numCards,
                        `is`(
                            greaterThanOrEqualTo(1)
                        )
                    )
                }
            }
        }
    }

    /**
     * Move all the cards from their old decks to the first deck that was added in setup()
     */
    @Test
    fun testMoveCardsToOtherDeck() {
        val cr = contentResolver
        // Query all available notes
        val allNotesCursor =
            cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:$TEST_TAG", null, null)
        assertNotNull(allNotesCursor)
        allNotesCursor.use {
            assertEquals(
                "Check number of results",
                mCreatedNotes.size,
                it.count
            )
            while (it.moveToNext()) {
                // Now iterate over all cursors
                val cardsUri = Uri.withAppendedPath(
                    Uri.withAppendedPath(
                        FlashCardsContract.Note.CONTENT_URI,
                        it.getString(it.getColumnIndex(FlashCardsContract.Note._ID))
                    ),
                    "cards"
                )
                val cardsCursor = cr.query(cardsUri, null, null, null, null)
                assertNotNull(
                    "Check that there is a valid cursor after query for cards",
                    cardsCursor
                )
                try {
                    assertThat(
                        "Check that there is at least one result for cards",
                        cardsCursor!!.count,
                        `is`(
                            greaterThan(0)
                        )
                    )
                    while (cardsCursor.moveToNext()) {
                        val targetDid = mTestDeckIds[0]
                        // Move to test deck (to test NOTES_ID_CARDS_ORD Uri)
                        val values = ContentValues()
                        values.put(FlashCardsContract.Card.DECK_ID, targetDid)
                        val cardUri = Uri.withAppendedPath(
                            cardsUri,
                            cardsCursor.getString(cardsCursor.getColumnIndex(FlashCardsContract.Card.CARD_ORD))
                        )
                        cr.update(cardUri, values, null, null)
                        reopenCol()
                        val movedCardCur = cr.query(cardUri, null, null, null, null)
                        assertNotNull(
                            "Check that there is a valid cursor after moving card",
                            movedCardCur
                        )
                        assertTrue(
                            "Move to beginning of cursor after moving card",
                            movedCardCur!!.moveToFirst()
                        )
                        val did =
                            movedCardCur.getLong(movedCardCur.getColumnIndex(FlashCardsContract.Card.DECK_ID))
                        assertEquals("Make sure that card is in new deck", targetDid, did)
                    }
                } finally {
                    cardsCursor!!.close()
                }
            }
        }
    }

    /**
     * Check that querying the current model gives a valid result
     */
    @Test
    fun testQueryCurrentModel() {
        val cr = contentResolver
        val uri = Uri.withAppendedPath(
            FlashCardsContract.Model.CONTENT_URI,
            FlashCardsContract.Model.CURRENT_MODEL_ID
        )
        val modelCursor = cr.query(uri, null, null, null, null)
        assertNotNull(modelCursor)
        modelCursor.use {
            assertEquals(
                "Check that there is exactly one result",
                1,
                it.count
            )
            assertTrue("Move to beginning of cursor", it.moveToFirst())
            assertNotNull(
                "Check non-empty field names",
                it.getString(it.getColumnIndex(FlashCardsContract.Model.FIELD_NAMES))
            )
            assertTrue(
                "Check at least one template",
                it.getInt(it.getColumnIndex(FlashCardsContract.Model.NUM_CARDS)) > 0
            )
        }
    }

    /**
     * Check that an Exception is thrown when unsupported operations are performed
     */
    @KotlinCleanup("use assertThrows")
    @Test
    fun testUnsupportedOperations() {
        val cr = contentResolver
        val dummyValues = ContentValues()
        val updateUris = arrayOf( // Can't update most tables in bulk -- only via ID
            FlashCardsContract.Note.CONTENT_URI,
            FlashCardsContract.Model.CONTENT_URI,
            FlashCardsContract.Deck.CONTENT_ALL_URI,
            FlashCardsContract.Note.CONTENT_URI.buildUpon()
                .appendPath("1234")
                .appendPath("cards")
                .build()
        )
        for (uri in updateUris) {
            try {
                cr.update(uri, dummyValues, null, null)
                fail("Update on $uri was supposed to throw exception")
            } catch (e: UnsupportedOperationException) {
                // This was expected ...
            } catch (e: IllegalArgumentException) {
                // ... or this.
            }
        }
        val deleteUris = arrayOf(
            FlashCardsContract.Note.CONTENT_URI, // Only note/<id> is supported
            FlashCardsContract.Note.CONTENT_URI.buildUpon()
                .appendPath("1234")
                .appendPath("cards")
                .build(),
            FlashCardsContract.Note.CONTENT_URI.buildUpon()
                .appendPath("1234")
                .appendPath("cards")
                .appendPath("2345")
                .build(),
            FlashCardsContract.Model.CONTENT_URI,
            FlashCardsContract.Model.CONTENT_URI.buildUpon()
                .appendPath("1234")
                .build()
        )
        for (uri in deleteUris) {
            try {
                cr.delete(uri, null, null)
                fail("Delete on $uri was supposed to throw exception")
            } catch (e: UnsupportedOperationException) {
                // This was expected
            }
        }
        val insertUris = arrayOf( // Can't do an insert with specific ID on the following tables
            FlashCardsContract.Note.CONTENT_URI.buildUpon()
                .appendPath("1234")
                .build(),
            FlashCardsContract.Note.CONTENT_URI.buildUpon()
                .appendPath("1234")
                .appendPath("cards")
                .build(),
            FlashCardsContract.Note.CONTENT_URI.buildUpon()
                .appendPath("1234")
                .appendPath("cards")
                .appendPath("2345")
                .build(),
            FlashCardsContract.Model.CONTENT_URI.buildUpon()
                .appendPath("1234")
                .build()
        )
        for (uri in insertUris) {
            try {
                cr.insert(uri, dummyValues)
                fail("Insert on $uri was supposed to throw exception")
            } catch (e: UnsupportedOperationException) {
                // This was expected ...
            } catch (e: IllegalArgumentException) {
                // ... or this.
            }
        }
    }

    /**
     * Test query to decks table
     */
    @Test
    fun testQueryAllDecks() {
        val decks = col.decks
        val decksCursor = contentResolver
            .query(
                FlashCardsContract.Deck.CONTENT_ALL_URI,
                FlashCardsContract.Deck.DEFAULT_PROJECTION,
                null,
                null,
                null
            )
        assertNotNull(decksCursor)
        decksCursor.use {
            assertEquals(
                "Check number of results",
                decks.count(),
                it.count
            )
            while (it.moveToNext()) {
                val deckID =
                    it.getLong(it.getColumnIndex(FlashCardsContract.Deck.DECK_ID))
                val deckName =
                    it.getString(it.getColumnIndex(FlashCardsContract.Deck.DECK_NAME))
                val deck = decks.get(deckID)
                assertNotNull("Check that the deck we received actually exists", deck)
                assertEquals(
                    "Check that the received deck has the correct name",
                    deck.getString("name"),
                    deckName
                )
            }
        }
    }

    /**
     * Test query to specific deck ID
     */
    @Test
    fun testQueryCertainDeck() {
        val deckId = mTestDeckIds[0]
        val deckUri = Uri.withAppendedPath(
            FlashCardsContract.Deck.CONTENT_ALL_URI,
            deckId.toString()
        )
        contentResolver.query(deckUri, null, null, null, null).use { decksCursor ->
            if (decksCursor == null || !decksCursor.moveToFirst()) {
                fail("No deck received. Should have delivered deck with id $deckId")
            } else {
                val returnedDeckID =
                    decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID))
                val returnedDeckName =
                    decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME))
                val realDeck = col.decks.get(deckId)
                assertEquals(
                    "Check that received deck ID equals real deck ID",
                    deckId,
                    returnedDeckID
                )
                assertEquals(
                    "Check that received deck name equals real deck name",
                    realDeck.getString("name"),
                    returnedDeckName
                )
            }
        }
    }

    /**
     * Test that query for the next card in the schedule returns a valid result without any deck selector
     */
    @Test
    fun testQueryNextCard() {
        val sched = col.sched
        val reviewInfoCursor = contentResolver.query(
            FlashCardsContract.ReviewInfo.CONTENT_URI,
            null,
            null,
            null,
            null
        )
        assertNotNull(reviewInfoCursor)
        assertEquals("Check that we actually received one card", 1, reviewInfoCursor.count)
        reviewInfoCursor.moveToFirst()
        val cardOrd =
            reviewInfoCursor.getInt(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD))
        val noteID =
            reviewInfoCursor.getLong(reviewInfoCursor.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID))
        var nextCard: Card? = null
        for (i in 0..9) { // minimizing fails, when sched.reset() randomly chooses between multiple cards
            col.reset()
            nextCard = sched.card
            waitToFinish()
            if (nextCard != null && nextCard.note().id == noteID && nextCard.ord == cardOrd) break
            waitToFinish()
        }
        assertNotNull("Check that there actually is a next scheduled card", nextCard)
        assertEquals(
            "Check that received card and actual card have same note id",
            nextCard!!.note().id,
            noteID
        )
        assertEquals(
            "Check that received card and actual card have same card ord",
            nextCard.ord,
            cardOrd
        )
    }

    /**
     * Test that query for the next card in the schedule returns a valid result WITH a deck selector
     */
    @Test
    @Synchronized
    fun testQueryCardFromCertainDeck() {
        val deckToTest = mTestDeckIds[0]
        val deckSelector = "deckID=?"
        val deckArguments = arrayOf(deckToTest.toString())

        val sched = col.sched
        val selectedDeckBeforeTest = col.decks.selected()
        col.decks.select(1) // select Default deck
        val reviewInfoCursor = contentResolver.query(
            FlashCardsContract.ReviewInfo.CONTENT_URI,
            null,
            deckSelector,
            deckArguments,
            null
        )
        assertNotNull(reviewInfoCursor)
        assertEquals("Check that we actually received one card", 1, reviewInfoCursor.count)
        reviewInfoCursor.use {
            it.moveToFirst()
            val cardOrd =
                it.getInt(it.getColumnIndex(FlashCardsContract.ReviewInfo.CARD_ORD))
            val noteID =
                it.getLong(it.getColumnIndex(FlashCardsContract.ReviewInfo.NOTE_ID))
            assertEquals(
                "Check that the selected deck has not changed",
                1,
                col.decks.selected()
            )
            col.decks.select(deckToTest)
            var nextCard: Card? = null
            for (i in 0..9) { // minimizing fails, when sched.reset() randomly chooses between multiple cards
                col.reset()
                nextCard = sched.card
                if (nextCard != null && nextCard.note().id == noteID && nextCard.ord == cardOrd) break
                try {
                    Thread.sleep(500)
                } catch (e: Exception) {
                    Timber.e(e)
                } // Reset counts is executed in background.
            }
            assertNotNull("Check that there actually is a next scheduled card", nextCard)
            assertEquals(
                "Check that received card and actual card have same note id",
                nextCard!!.note().id,
                noteID
            )
            assertEquals(
                "Check that received card and actual card have same card ord",
                nextCard.ord,
                cardOrd
            )
        }
        col.decks.select(selectedDeckBeforeTest)
    }

    /**
     * Test changing the selected deck
     */
    @Test
    fun testSetSelectedDeck() {
        val deckId = mTestDeckIds[0]
        val cr = contentResolver
        val selectDeckUri = FlashCardsContract.Deck.CONTENT_SELECTED_URI
        val values = ContentValues()
        values.put(FlashCardsContract.Deck.DECK_ID, deckId)
        cr.update(selectDeckUri, values, null, null)
        val col = reopenCol()
        assertEquals(
            "Check that the selected deck has been correctly set",
            deckId,
            col.decks.selected()
        )
    }

    private fun getFirstCardFromScheduler(col: com.ichi2.libanki.Collection): Card? {
        val deckId = mTestDeckIds[0]
        col.decks.select(deckId)
        col.reset()
        return col.sched.card
    }

    /**
     * Test giving the answer for a reviewed card
     */
    @Test
    fun testAnswerCard() {
        val card = getFirstCardFromScheduler(col)
        val cardId = card!!.id

        // the card starts out being new
        assertEquals("card is initial new", Consts.CARD_TYPE_NEW, card.queue)
        val cr = contentResolver
        val reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI
        val noteId = card.note().id
        val cardOrd = card.ord
        val earlyGraduatingEase =
            if (schedVersion == 1) AbstractFlashcardViewer.EASE_3 else AbstractFlashcardViewer.EASE_4
        val values = ContentValues().apply {
            val timeTaken: Long = 5000 // 5 seconds
            put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId)
            put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd)
            put(FlashCardsContract.ReviewInfo.EASE, earlyGraduatingEase)
            put(FlashCardsContract.ReviewInfo.TIME_TAKEN, timeTaken)
        }
        val updateCount = cr.update(reviewInfoUri, values, null, null)
        assertEquals("Check if update returns 1", 1, updateCount)
        try {
            Thread.currentThread().join(500)
        } catch (e: Exception) { /* do nothing */
        }
        col.reset()
        val newCard = col.sched.card
        if (newCard != null) {
            if (newCard.note().id == card.note().id && newCard.ord == card.ord) {
                fail("Next scheduled card has not changed")
            }
        }

        // lookup the card after update, ensure it's not new anymore
        val cardAfterReview = col.getCard(cardId)
        assertEquals("card is now type rev", Card.TYPE_REV, cardAfterReview.queue)
    }

    /**
     * Test burying a card through the ReviewInfo endpoint
     */
    @Test
    fun testBuryCard() {
        // get the first card due
        // ----------------------

        val card = getFirstCardFromScheduler(col)

        // verify that the card is not already user-buried
        assertNotEquals(
            "Card is not user-buried before test",
            Consts.QUEUE_TYPE_SIBLING_BURIED,
            card!!.queue
        )

        // retain the card id, we will lookup the card after the update
        val cardId = card.id

        // bury it through the API
        // -----------------------
        val cr = contentResolver
        val reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI
        val noteId = card.note().id
        val cardOrd = card.ord
        val bury = 1
        val values = ContentValues().apply {
            put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId)
            put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd)
            put(FlashCardsContract.ReviewInfo.BURY, bury)
        }
        val updateCount = cr.update(reviewInfoUri, values, null, null)
        assertEquals("Check if update returns 1", 1, updateCount)

        // verify that it did get buried
        // -----------------------------
        val cardAfterUpdate = col.getCard(cardId)
        // QUEUE_TYPE_MANUALLY_BURIED was also used for SIBLING_BURIED in sched v1
        assertEquals(
            "Card is user-buried",
            if (schedVersion == 1) Consts.QUEUE_TYPE_SIBLING_BURIED else Consts.QUEUE_TYPE_MANUALLY_BURIED,
            cardAfterUpdate.queue
        )

        // cleanup, unbury cards
        // ---------------------
        col.sched.unburyCards()
    }

    /**
     * Test suspending a card through the ReviewInfo endpoint
     */
    @Test
    fun testSuspendCard() {
        // get the first card due
        // ----------------------

        val card = getFirstCardFromScheduler(col)

        // verify that the card is not already suspended
        assertNotEquals(
            "Card is not suspended before test",
            Consts.QUEUE_TYPE_SUSPENDED,
            card!!.queue
        )

        // retain the card id, we will lookup the card after the update
        val cardId = card.id

        // suspend it through the API
        // --------------------------
        val cr = contentResolver
        val reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI
        val noteId = card.note().id
        val cardOrd = card.ord

        @KotlinCleanup("rename, while valid suspend is a kotlin soft keyword")
        val values = ContentValues().apply {
            val suspend = 1
            put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId)
            put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd)
            put(FlashCardsContract.ReviewInfo.SUSPEND, suspend)
        }
        val updateCount = cr.update(reviewInfoUri, values, null, null)
        assertEquals("Check if update returns 1", 1, updateCount)

        // verify that it did get suspended
        // --------------------------------
        val cardAfterUpdate = col.getCard(cardId)
        assertEquals("Card is suspended", Consts.QUEUE_TYPE_SUSPENDED, cardAfterUpdate.queue)

        // cleanup, unsuspend card and reschedule
        // --------------------------------------
        col.sched.unsuspendCards(longArrayOf(cardId))
        col.reset()
    }

    /**
     * Update tags on a note
     */
    @Test
    fun testUpdateTags() {
        // get the first card due
        // ----------------------

        val card = getFirstCardFromScheduler(col)
        val note = card!!.note()
        val noteId = note.id

        // make sure the tag is what we expect initially
        // ---------------------------------------------
        val tagList: List<String> = note.tags
        assertEquals("only one tag", 1, tagList.size)
        assertEquals("check tag value", TEST_TAG, tagList[0])

        // update tags
        // -----------
        val tag2 = "mynewtag"
        val cr = contentResolver
        val updateNoteUri = Uri.withAppendedPath(
            FlashCardsContract.Note.CONTENT_URI,
            noteId.toString()
        )
        val values = ContentValues()
        values.put(FlashCardsContract.Note.TAGS, "$TEST_TAG $tag2")
        val updateCount = cr.update(updateNoteUri, values, null, null)
        assertEquals("updateCount is 1", 1, updateCount)

        // lookup the note now and verify tags
        // -----------------------------------
        val noteAfterUpdate = col.getNote(noteId)
        val newTagList: List<String> = noteAfterUpdate.tags
        assertEquals("two tags", 2, newTagList.size)
        assertEquals("check first tag", TEST_TAG, newTagList[0])
        assertEquals("check second tag", tag2, newTagList[1])
    }

    /** Test that a null did will not crash the provider (#6378)  */
    @Test
    fun testProviderProvidesDefaultForEmptyModelDeck() {
        assumeTrue(
            "This causes mild data corruption - should not be run on a collection you care about",
            isEmulator()
        )

        col.models.all()[0].put("did", JSONObject.NULL)
        col.save()
        val cr = contentResolver
        // Query all available models
        val allModels = cr.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null)
        assertNotNull(allModels)
    }

    private fun reopenCol(): com.ichi2.libanki.Collection {
        CollectionHelper.instance.closeCollection(false, "ContentProviderTest: reopenCol")
        return col
    }

    private val contentResolver: ContentResolver
        get() = testContext.contentResolver

    companion object {
        @Parameterized.Parameters
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> {
            // This does one run with schedVersion injected as 1, and one run as 2
            return listOf(arrayOf(1), arrayOf(2))
        }

        private const val BASIC_MODEL_NAME = "com.ichi2.anki.provider.test.basic.x94oa3F"
        private const val TEST_FIELD_NAME = "TestFieldName"
        private const val TEST_FIELD_VALUE = "test field value"
        private const val TEST_TAG = "aldskfhewjklhfczmxkjshf"

        // In case of change in TEST_DECKS, change mTestDeckIds for efficiency
        private val TEST_DECKS = arrayOf(
            "cmxieunwoogyxsctnjmv",
            "sstuljxgmfdyugiujyhq",
            "pdsqoelhmemmmbwjunnu",
            "scxipjiyozczaaczoawo",
            "cmxieunwoogyxsctnjmv::abcdefgh::ZYXW",
            "cmxieunwoogyxsctnjmv::INSBGDS"
        )
        private const val TEST_MODEL_NAME = "com.ichi2.anki.provider.test.a1x6h9l"
        private val TEST_MODEL_FIELDS = arrayOf("FRONTS", "BACK")
        private val TEST_MODEL_CARDS = arrayOf("cArD1", "caRD2")
        private val TEST_MODEL_QFMT = arrayOf("{{FRONTS}}", "{{BACK}}")
        private val TEST_MODEL_AFMT = arrayOf("{{BACK}}", "{{FRONTS}}")
        private val TEST_NOTE_FIELDS = arrayOf("dis is za Fr0nt", "Te\$t")
        private const val TEST_MODEL_CSS = "styleeeee"

        @Suppress("SameParameterValue")
        private fun setupNewNote(
            col: com.ichi2.libanki.Collection,
            mid: Long,
            did: Long,
            fields: Array<String>,
            tag: String
        ): Uri {
            val newNote = Note(col, col.models.get(mid)!!)
            for (idx in fields.indices) {
                newNote.setField(idx, fields[idx])
            }
            newNote.addTag(tag)
            assertThat(
                "At least one card added for note",
                col.addNote(newNote),
                `is`(
                    greaterThanOrEqualTo(1)
                )
            )
            for (c in newNote.cards()) {
                c.did = did
                c.flush()
            }
            return Uri.withAppendedPath(
                FlashCardsContract.Note.CONTENT_URI,
                newNote.id.toString()
            )
        }
    }
}
