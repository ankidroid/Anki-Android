package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.model.DeckNotification
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NotificationDatastoreTest {
    private lateinit var notificationDatastore: NotificationDatastore

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        notificationDatastore = NotificationDatastore.getInstance(context)
    }

    @Test
    fun deckSchedDataReadWriteTest() {
        val deckNotification = DeckNotification()

        runBlocking {
            val isSuccess = notificationDatastore.setDeckSchedData(
                deckNotification.did,
                deckNotification
            )
            assertTrue(
                isSuccess,
                "Unable to save the deck data in notification preference datastore."
            )

            val dataStored = notificationDatastore.getDeckSchedData(deckNotification.did)
            assertNotNull(
                dataStored,
                "Unable to read the stored deck data from notification preference datastore."
            )
        }
    }

    @Test
    fun unStoredDeckSchedDataTest() {
        runBlocking {
            val deckIdUnStored = Random.nextLong()
            val dataUnStored = notificationDatastore.getDeckSchedData(deckIdUnStored)
            assertNull(dataUnStored, "Expected null, But found data for deckId $deckIdUnStored")
        }
    }

    @Test(expected = Exception::class)
    fun deckSchedDataDeSerializationTest() {
        val deckId = 2L

        @Language("JSON")
        val invalidDeckSchedJson = """
            {
               "deckPreference":{
                  "number":10,
                  "valueType":"CARDS"
               },
               "did":1,
               "enabled":false,
               "includeSubdecks":true,
               "notificationTime": 0
            }
        """.trimIndent()

        runBlocking {
            notificationDatastore.putStringAsync(deckId.toString(), invalidDeckSchedJson)
            notificationDatastore.getDeckSchedData(deckId)
        }
    }

    @After
    fun cleanUp() {
        runBlocking {
            notificationDatastore.clearDatastore()
        }
    }
}
