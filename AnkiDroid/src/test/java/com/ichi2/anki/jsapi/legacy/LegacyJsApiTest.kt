// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.jsapi.legacy

import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.PostRequestUri
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.testutils.getString
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LegacyJsApiTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `card metadata and counts use legacy response types`() =
        runTest {
            val card = addBasicNote().firstCard()
            withReviewer { reviewer ->
                assertEquals(true, reviewer.request("init").getBoolean("value"))
                assertEquals(card.id, reviewer.request("cardId").getLong("value"))
                assertEquals(card.nid, reviewer.request("cardNid").getLong("value"))
                assertEquals(card.did, reviewer.request("cardDid").getLong("value"))
                assertEquals(
                    reviewer.countsFlow.value.new
                        .toInt(),
                    reviewer.request("newCardCount").getInt("value"),
                )
                assertEquals(false, reviewer.request("isDisplayingAnswer").getBoolean("value"))
            }
        }

    @Test
    fun `flag and mark actions update the current card`() =
        runTest {
            val card = addBasicNote().firstCard()
            withReviewer { reviewer ->
                assertTrue(reviewer.request("toggleFlag", "red").getBoolean("value"))
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).userFlag())
                assertTrue(reviewer.request("toggleFlag", "red").getBoolean("value"))
                advanceUntilIdle()
                assertEquals(0, col.getCard(card.id).userFlag())
                assertTrue(reviewer.request("markCard").getBoolean("value"))
                advanceUntilIdle()
                assertTrue(col.getCard(card.id).note(col).hasTag(col, "marked"))
            }
        }

    @Test
    fun `invalid contracts and flags cannot mutate the card`() =
        runTest {
            val card = addBasicNote().firstCard()
            withReviewer { reviewer ->
                val invalidVersion = reviewer.request("toggleFlag", "red", "99.0.0")
                assertFalse(invalidVersion.getBoolean("success"))
                assertEquals(-1, invalidVersion.getInt("value"))
                assertFalse(reviewer.request("toggleFlag", "invalid").getBoolean("value"))
                val malformed = reviewer.handlePostRequest(PostRequestUri(AnkiServer.ANKIDROID_JS_PREFIX + "markCard"), "{".toByteArray())
                assertFalse(JSONObject(malformed.decodeToString()).getBoolean("success"))
                advanceUntilIdle()
                assertEquals(0, col.getCard(card.id).userFlag())
                assertFalse(col.getCard(card.id).note(col).hasTag(col, "marked"))
            }
        }

    @Test
    fun `dangerous operations keep the legacy permission checks`() =
        runTest {
            editPreferences { putBoolean(getString(R.string.pref_allow_dangerous_js_api), false) }
            addBasicNote()
            val otherNote = addBasicNote("other", "answer")
            withReviewer { reviewer ->
                val otherId = if (reviewer.currentCard.await().nid == otherNote.id) addBasicNote("third", "answer").id else otherNote.id
                val params = JSONObject().put("noteId", otherId).put("tag", "forbidden").toString()
                assertFalse(reviewer.request("addTagToNote", params).getBoolean("value"))
                assertFalse(col.getNote(otherId).hasTag(col, "forbidden"))
                assertFalse(reviewer.request("searchCardWithCallback", "deck:*").getBoolean("value"))
            }
        }

    @Test
    fun `answer buttons flip the question before grading`() =
        runTest {
            val card = addBasicNote().firstCard()
            addBasicNote("second", "answer")
            withReviewer { reviewer ->
                assertTrue(reviewer.request("answerEase3").getBoolean("value"))
                advanceUntilIdle()
                assertTrue(reviewer.showingAnswer.value)
                assertEquals(0, col.getCard(card.id).reps)
                assertTrue(reviewer.request("answerEase3").getBoolean("value"))
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }

    @Test
    fun `setting tags refreshes the mark icon and supports undo`() =
        runTest {
            val note = addBasicNote()
            withReviewer { reviewer ->
                val params = JSONObject().put("tags", org.json.JSONArray(listOf("marked", "custom"))).toString()
                assertTrue(reviewer.request("setNoteTags", params).getBoolean("value"))
                advanceUntilIdle()
                assertTrue(reviewer.isMarkedFlow.value)
                assertTrue(col.getNote(note.id).hasTag(col, "custom"))
                reviewer.executeAction(com.ichi2.anki.preferences.reviewer.ViewerAction.UNDO)
                advanceUntilIdle()
                assertFalse(reviewer.isMarkedFlow.value)
                assertFalse(col.getNote(note.id).hasTag(col, "custom"))
            }
        }

    @Test
    fun `rescheduling card updates due date and resetting progress restores new status`() =
        runTest {
            val card = addBasicNote().firstCard()
            withReviewer { reviewer ->
                assertFalse(reviewer.request("setCardDue", "-1").getBoolean("value"))
                assertFalse(reviewer.request("setCardDue", "invalid").getBoolean("value"))

                assertTrue(reviewer.request("setCardDue", "7").getBoolean("value"))
                advanceUntilIdle()
                val rescheduled = col.getCard(card.id)
                assertEquals(CardType.Rev, rescheduled.type)
                assertEquals(col.sched.today + 7, rescheduled.due)

                assertTrue(reviewer.request("resetProgress").getBoolean("value"))
                advanceUntilIdle()
                val resetCard = col.getCard(card.id)
                assertEquals(CardType.New, resetCard.type)
            }
        }

    @Test
    fun `unsupported methods return failure without modifying state`() =
        runTest {
            addBasicNote()
            withReviewerFragment { fragment ->
                val webViewLayout = fragment.requireActivity().findViewById<ViewGroup>(R.id.web_view_layout)
                val webView = webViewLayout.getChildAt(0) as WebView
                val horizontalScrollbarEnabled = webView.isHorizontalScrollBarEnabled
                val verticalScrollbarEnabled = webView.isVerticalScrollBarEnabled

                listOf(
                    "enableHorizontalScrollbar" to "true",
                    "enableVerticalScrollbar" to "true",
                    "showOptionsMenu" to "",
                    "showNavigationDrawer" to "",
                    "showToast" to JSONObject().put("text", "unsupported").toString(),
                    "isInFullscreen" to "",
                    "isTopbarShown" to "",
                    "isInNightMode" to "",
                    "searchCardWithCallback" to "deck:*",
                    "sttSetLanguage" to "en-US",
                    "sttStart" to "",
                    "sttStop" to "",
                ).forEach { (method, data) ->
                    val response = fragment.viewModel.request(method, data)
                    assertFalse(response.getBoolean("success"))
                    assertFalse(response.getBoolean("value"))
                }

                assertEquals(horizontalScrollbarEnabled, webView.isHorizontalScrollBarEnabled)
                assertEquals(verticalScrollbarEnabled, webView.isVerticalScrollBarEnabled)
            }
        }

    @Test
    fun `destroying the view detaches the compatibility host`() =
        runTest {
            addBasicNote()
            lateinit var model: ReviewerViewModel
            withReviewer { reviewer ->
                model = reviewer
                assertTrue(reviewer.request("init").getBoolean("success"))
            }
            assertFalse(model.request("init").getBoolean("success"))
        }

    @Test
    fun `manual detach releases the compatibility host`() =
        runTest {
            addBasicNote()
            withReviewer { reviewer ->
                assertTrue(reviewer.request("init").getBoolean("success"))
                reviewer.legacyJsApi.detach()
                assertFalse(reviewer.request("init").getBoolean("success"))
            }
        }

    private suspend fun ReviewerViewModel.request(
        method: String,
        data: String = "",
        version: String = AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION,
    ): JSONObject {
        val contract = JSONObject().put("version", version).put("developer", "test@example.com").put("data", data)
        val response = handlePostRequest(PostRequestUri(AnkiServer.ANKIDROID_JS_PREFIX + method), contract.toString().toByteArray())
        return JSONObject(response.decodeToString())
    }

    private suspend fun TestScope.withReviewer(block: suspend (ReviewerViewModel) -> Unit) {
        withReviewerFragment { block(it.viewModel) }
    }

    private suspend fun TestScope.withReviewerFragment(block: suspend (ReviewerFragment) -> Unit) {
        Robolectric.buildActivity(CardViewerActivity::class.java, ReviewerFragment.getIntent(targetContext)).use { controller ->
            controller.setup()
            advanceUntilIdle()
            val reviewer = controller.get().fragment as ReviewerFragment
            reviewer.viewModel.onPageFinished(false)
            advanceUntilIdle()
            block(reviewer)
        }
    }
}
