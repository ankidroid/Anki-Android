// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.utils.AnKingTags
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.observability.ensureOpsExecuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Integration tests for [ManageTagsViewModel] with a real collection */
@RunWith(AndroidJUnit4::class)
class ManageTagsViewModelTest : RobolectricTest() {
    @Test
    fun `refreshTags loads empty collection`() =
        runTest {
            withViewModel {
                assertIs<ManageTagsState.Content>(state.value)
                assertThat(loadedState.visibleNodes, hasSize(0))
            }
        }

    @Test
    fun `refreshTags loads tags from notes`() =
        runTest {
            addTags("science")
            withViewModel {
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
            }
        }

    @Test
    fun `refreshTags loads hierarchical tags`() =
        runTest {
            addTags("science::biology", "science::chemistry")
            withViewModel {
                // "science" parent is collapsed by default, so only it should be visible
                assertThat(loadedState.visibleNodes, hasSize(1))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[0].hasChildren, equalTo(true))
            }
        }

    @Test
    fun `refreshTags loads multiple tags from multiple notes`() =
        runTest {
            addTags("math")
            addTags("history")
            withViewModel {
                assertThat(loadedState.visibleNodes, hasSize(2))
                assertThat(
                    loadedState.visibleTagNames,
                    containsInAnyOrder("history", "math"),
                )
            }
        }

    @Test
    fun `refreshTags preserves search query`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                filter("sci")
                refreshTags()
                assertThat(searchQuery.value, equalTo("sci"))
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
            }
        }

    @Test
    fun `removeTag keeps existing content visible until completion`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                val previousTags = loadedState.visibleNodes
                withQueuedCollectionAccess {
                    val deletion = removeTag("science")
                    assertThat(loadedState.isWorking, equalTo(true))
                    assertThat(loadedState.visibleNodes, equalTo(previousTags))

                    deletion.join()

                    assertThat(loadedState.isWorking, equalTo(false))
                    assertThat(loadedState.error, nullValue())
                    assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
                }
            }
        }

    @Test
    fun `operations requested while busy do not mutate tags`() =
        runTest {
            addTags("science::biology", "history")
            addUnusedTag("unused")
            withViewModel {
                withQueuedCollectionAccess {
                    val refresh = refreshTags()
                    assertThat(refresh.isCompleted, equalTo(false))

                    assertThat(removeTag("science"), sameInstance(refresh))
                    assertThat(renameTag("history", "past"), sameInstance(refresh))
                    assertThat(clearUnusedTags(), sameInstance(refresh))
                    assertThat(toggleCollapsed("science"), sameInstance(refresh))
                    assertThat(refreshTags(), sameInstance(refresh))

                    refresh.join()
                }
                assertThat(loadedState.visibleTagNames, containsInAnyOrder("science", "history", "unused"))
                assertThat(loadedState.visibleNodes.single { it.fullTagName == "science" }.collapsed, equalTo(true))

                removeTag("science").join()
                assertThat(loadedState.visibleTagNames, containsInAnyOrder("history", "unused"))
            }
        }

    @Test
    fun `search updates visible content while refresh is running`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                withQueuedCollectionAccess {
                    val refresh = refreshTags()
                    filter("hist")

                    assertThat(loadedState.isWorking, equalTo(true))
                    assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))

                    refresh.join()
                    assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
                }
            }
        }

    @Test
    fun `operation failure preserves content and retry clears error`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                val previousTags = loadedState.visibleNodes
                withLockedCollection {
                    removeTag("science").join()
                }

                assertThat(loadedState.visibleNodes, equalTo(previousTags))
                assertThat(loadedState.isWorking, equalTo(false))
                assertThat(loadedState.error, instanceOf(CollectionLockedException::class.java))

                withQueuedCollectionAccess {
                    val retry = removeTag("science")
                    assertThat(loadedState.isWorking, equalTo(true))
                    assertThat(loadedState.error, nullValue())

                    retry.join()
                    assertThat(loadedState.isWorking, equalTo(false))
                    assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
                }
            }
        }

    @Test
    fun `initial load failure allows search input and retry`() =
        runTest {
            addTags("science", "history")
            val viewModel = withLockedCollection { ManageTagsViewModel() }
            val failure = viewModel.state.value
            assertThat(failure, instanceOf(ManageTagsState.Error::class.java))
            assertThat((failure as ManageTagsState.Error).error, instanceOf(CollectionLockedException::class.java))

            viewModel.filter("hist")
            assertThat(viewModel.searchQuery.value, equalTo("hist"))

            withQueuedCollectionAccess {
                val retry = viewModel.refreshTags()
                assertThat(viewModel.state.value, equalTo(ManageTagsState.Loading))
                retry.join()
            }
            assertThat(viewModel.loadedState.visibleTagNames, equalTo(listOf("history")))
        }

    @Test
    fun `cancelled operation clears progress and permits another operation`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                val previous = loadedState
                withQueuedCollectionAccess {
                    val deletion = removeTag("science")
                    assertThat(loadedState.isWorking, equalTo(true))

                    deletion.cancelAndJoin()

                    assertThat(loadedState, equalTo(previous))
                    val refresh = refreshTags()
                    assertThat(loadedState.isWorking, equalTo(true))
                    refresh.join()
                    assertThat(loadedState, equalTo(previous))
                }
            }
        }

    @Test
    fun `initial state is Loaded after construction`() =
        runTest {
            withViewModel {
                assertIs<ManageTagsState.Content>(state.value)
            }
        }

    @Test
    fun `filter narrows visible tags`() =
        runTest {
            addTags("science", "history", "math")
            withViewModel {
                filter("sci")
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
                assertThat(searchQuery.value, equalTo("sci"))
            }
        }

    @Test
    fun `filter updates query in an empty collection`() =
        runTest {
            withViewModel {
                filter("sci")
                assertThat(searchQuery.value, equalTo("sci"))
                assertThat(loadedState.visibleNodes, hasSize(0))

                addTags("science", "history")
                refreshTags()
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
            }
        }

    @Test
    fun `filter during initial loading applies latest query when tags arrive`() =
        runTest {
            addTags("science", "history")
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            withViewModel {
                assertThat(state.value, equalTo(ManageTagsState.Loading))
                filter("sci")
                filter("hist")
                assertThat(searchQuery.value, equalTo("hist"))

                runCurrent()

                assertThat(searchQuery.value, equalTo("hist"))
                assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
            }
        }

    @Test
    fun `filter during refresh is not overwritten when refresh completes`() =
        runTest {
            addTags("science", "history")
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            withViewModel {
                runCurrent()
                filter("sci")
                val refresh = refreshTags()
                filter("hist")
                assertThat(searchQuery.value, equalTo("hist"))

                refresh.join()

                assertThat(searchQuery.value, equalTo("hist"))
                assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
            }
        }

    @Test
    fun `filter can be cleared after deleting the last tag`() =
        runTest {
            addTags("science")
            withViewModel {
                filter("sci")
                removeTag("science")
                filter("")

                assertThat(searchQuery.value, equalTo(""))
                assertThat(loadedState.visibleNodes, hasSize(0))
            }
        }

    @Test
    fun `filter with empty query shows all tags`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                filter("sci")
                assertThat(loadedState.visibleNodes, hasSize(1))

                filter("")
                assertThat(loadedState.visibleNodes, hasSize(2))
            }
        }

    @Test
    fun `filter shows ancestors of matching nested tags`() =
        runTest {
            addTags("science::biology", "history")
            withViewModel {
                filter("bio")
                // "science" (ancestor) + "science::biology" (match)
                assertThat(loadedState.visibleNodes, hasSize(2))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[1].fullTagName, equalTo("science::biology"))
            }
        }

    @Test
    fun `filter with no matches returns empty`() =
        runTest {
            addTags("science")
            withViewModel {
                filter("zzz")
                assertThat(loadedState.visibleNodes, hasSize(0))
            }
        }

    @Test
    fun `toggleCollapsed expands a collapsed tag`() =
        runTest {
            addTags("science::biology", "science::chemistry")
            withViewModel {
                assertThat(loadedState.visibleNodes, hasSize(1))

                toggleCollapsed("science")

                assertThat(loadedState.visibleNodes, hasSize(3))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[0].collapsed, equalTo(false))
                assertThat(loadedState.visibleNodes[1].fullTagName, equalTo("science::biology"))
                assertThat(loadedState.visibleNodes[2].fullTagName, equalTo("science::chemistry"))
            }
        }

    @Test
    fun `toggleCollapsed collapses an expanded tag`() =
        runTest {
            addTags("science::biology")
            withViewModel {
                toggleCollapsed("science")
                assertThat(loadedState.visibleNodes, hasSize(2))

                toggleCollapsed("science")
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
                assertThat(loadedState.visibleNodes[0].collapsed, equalTo(true))
            }
        }

    @Test
    fun `toggleCollapsed on unknown tag queues UnexpectedError`() =
        runTest {
            addTags("science")
            withViewModel {
                toggleCollapsed("nonexistent")
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.UnexpectedError))
            }
        }

    @Test
    fun `removeTag removes a single tag`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                removeTag("science")
            }
            checkCollectionTags { tags ->
                assertThat(tags, not(hasItem("science")))
                assertThat(tags, hasItem("history"))
            }
        }

    @Test
    fun `removeTag removes tag and children`() =
        runTest {
            addTags("science::biology", "science::chemistry", "history")
            withViewModel {
                removeTag("science")
            }
            checkCollectionTags { tags ->
                assertThat(tags, not(hasItem("science::biology")))
                assertThat(tags, not(hasItem("science::chemistry")))
                assertThat(tags, hasItem("history"))
            }
        }

    @Test
    fun `renameTag updates tag name`() =
        runTest {
            addTags("science")
            withViewModel {
                renameTag("science", "physics")
            }
            checkCollectionTags { tags ->
                assertThat(tags, hasItem("physics"))
                assertThat(tags, not(hasItem("science")))
            }
        }

    @Test
    fun `renameTag updates hierarchical tags`() =
        runTest {
            addTags("science::biology")
            withViewModel {
                renameTag("science", "studies")
            }
            checkCollectionTags { tags ->
                assertThat(tags, hasItem("studies::biology"))
                assertThat(tags, not(hasItem("science::biology")))
            }
        }

    @Test
    fun `clearUnusedTags removes tags not on any note`() =
        runTest {
            addTags("used")
            addUnusedTag("unused")
            withViewModel {
                assertThat(loadedState.visibleTagNames, hasItem("unused"))

                clearUnusedTags()

                assertThat(loadedState.visibleTagNames, equalTo(listOf("used")))
            }
        }

    @Test
    fun `clearUnusedTags queues message with count`() =
        runTest {
            addTags("used")
            addUnusedTag("unused")
            withViewModel {
                clearUnusedTags()
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.ClearedUnusedTags(1)))
            }
        }

    @Test
    fun `removeTag queues message with affected note count`() =
        runTest {
            // add 'science to 2 notes - ensure that the return value is the notes affected
            addTags("science")
            addTags("science")
            withViewModel {
                removeTag("science")
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.TagRemoved(2)))
            }
        }

    @Test
    fun `renameTag queues message with affected note count`() =
        runTest {
            // add 'science to 2 notes - ensure that the return value is the notes affected
            addTags("science")
            addTags("science")
            withViewModel {
                renameTag("science", "physics")
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.TagRenamed(2)))
            }
        }

    @Test
    fun `messageShown removes the displayed message`() =
        runTest {
            addTags("science")
            withViewModel {
                removeTag("science").join()

                messageShown(pendingMessages.value.single().id)

                assertThat(pendingMessages.value, hasSize(0))
            }
        }

    @Test
    fun `pending messages survive refresh failures and retries`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                removeTag("science").join()
                val pending = pendingMessages.value
                withLockedCollection {
                    refreshTags().join()
                }
                assertThat(loadedState.error, instanceOf(CollectionLockedException::class.java))
                assertThat(pendingMessages.value, equalTo(pending))

                refreshTags().join()
                filter("hist")
                assertThat(loadedState.error, nullValue())
                assertThat(pendingMessages.value, equalTo(pending))
            }
        }

    @Test
    fun `removeTag fires opChanges`() =
        runTest {
            addTags("science")
            ensureOpsExecuted(1) {
                withViewModel { removeTag("science") }
            }
        }

    @Test
    fun `renameTag fires opChanges`() =
        runTest {
            addTags("science")
            ensureOpsExecuted(1) {
                withViewModel { renameTag("science", "physics") }
            }
        }

    @Test
    fun `clearUnusedTags fires opChanges`() =
        runTest {
            addTags("used")
            addUnusedTag("unused")
            ensureOpsExecuted(1) {
                withViewModel { clearUnusedTags() }
            }
        }

    @Test
    @MediumTest
    fun `toggleCollapsed performance with AnKing tags`() =
        runTest {
            val expected = 1.seconds

            fun ManageTagsViewModel.toggleOnOff(tag: String) {
                toggleCollapsed(tag)
                toggleCollapsed(tag)
            }

            val tags = setupAnKing()

            withViewModel {
                val hugeTag = "#AK_Step1_v11" // 10k+ child tags

                // warm up
                toggleOnOff(hugeTag)

                val iterations = 1
                val elapsed =
                    measureTimeMillis {
                        repeat(iterations) {
                            toggleOnOff(hugeTag)
                        }
                    }

                // ~55ms on my M1
                // ~342ms on a Ubuntu CI runner
                val avgMs = (elapsed.toDouble() / (iterations * 2)).milliseconds
                println("toggleCollapsed: ${tags.size} tags, avg ${avgMs}ms over ${iterations * 2} toggles")
                assertTrue(avgMs < expected, "toggleCollapsed took ${avgMs}ms on average, expected < $expected")
            }
        }

    private suspend fun <T> withLockedCollection(block: suspend () -> T): T {
        CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
        try {
            return block()
        } finally {
            CollectionManager.emulatedOpenFailure = null
        }
    }

    /** Suspend backend access so assertions can observe an operation in progress. */
    private suspend fun TestScope.withQueuedCollectionAccess(block: suspend () -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private suspend fun withViewModel(block: suspend ManageTagsViewModel.() -> Unit) = ManageTagsViewModel().block()

    /** Helper abstracting [com.ichi2.anki.libanki.Tags.all] */
    private fun checkCollectionTags(block: (List<String>) -> Unit) = block(col.tags.all())

    /** Adds 17k tags to a note */
    private fun setupAnKing(): List<String> =
        AnKingTags.value.also { tags ->
            addBasicNote().update { tags.forEach { addTag(it) } }
        }

    private fun addTags(vararg tags: String) = addBasicNote().update { tags.forEach { addTag(it) } }

    /** Adds a tag to the collection cache without it being on any note */
    private fun addUnusedTag(
        @Suppress("SameParameterValue") tag: String,
    ) {
        addTags(tag).update { removeTag(tag) }
    }
}

private val ManageTagsViewModel.loadedState: ManageTagsState.Content
    get() = state.value as ManageTagsState.Content
