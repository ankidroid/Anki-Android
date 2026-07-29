// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.startup.ensureStorageIsReady
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The blocker's gate: shows the real reviewer and only lets the user through after
 * [GateProgress.requiredCorrect] cards are rated Good or Easy.
 *
 * Every way this activity can end funnels through [finish], which decides between
 * three outcomes: unlocked (progress complete or failing open), switching queues
 * (the reviewer exhausted its cards before completion — swallowed, a practice deck
 * takes over), or abandoned (user backed out — they are routed away from the
 * blocked target).
 */
class GateActivity :
    AnkiActivity(R.layout.activity_gate),
    GateReviewHost.Callbacks {
    private enum class GateState {
        /** Deck selection/practice-deck setup running; no reviewer attached yet */
        PREPARING,

        /** Reviewer on screen, counting answers */
        RUNNING,

        /** Reviewer exhausted its queue; a replacement practice queue is being built */
        SWITCHING_QUEUE,

        /** Gate passed (or failed open): finishing lets the user through */
        UNLOCKED,

        /** Gate dismissed without passing: finishing routes the user away */
        ABANDONED,
    }

    private var state = GateState.PREPARING
    private var target: BlockTarget? = null
    private lateinit var progress: GateProgress
    private lateinit var reviewHost: GateReviewHost
    private var requiredCards = BlockerPrefs.DEFAULT_CARDS_REQUIRED
    private var sourceDeckId: DeckId? = null
    private var practiceDeckId: DeckId? = null
    private var previousDeckId: DeckId? = null
    private var cleanupScheduled = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        target = BlockTarget.fromKey(intent.getStringExtra(EXTRA_TARGET).orEmpty())
        if (target == null) {
            Timber.w("Blocker: gate launched without a valid target")
            state = GateState.ABANDONED
            finish()
            return
        }
        if (!ensureStorageIsReady()) {
            failOpen()
            return
        }
        BlockerController.isGateActive = true
        requiredCards = BlockerPrefs.cardsRequired
        progress = GateProgress(requiredCards)
        reviewHost = GateReviewHost(this, R.id.gate_fragment_container, this)
        reviewHost.install()
        findViewById<TextView>(R.id.gate_target_label)?.text =
            getString(R.string.blocker_gate_unlocking, target?.displayName(this).orEmpty())
        updateProgressChip()

        lifecycleScope.launch {
            try {
                previousDeckId = withCol { decks.selected() }
                val prepared = PracticeDeckManager.prepareQueue(BlockerPrefs.gateDeckId)
                if (prepared == null) {
                    failOpen()
                    return@launch
                }
                sourceDeckId = prepared.sourceDeckId
                practiceDeckId = prepared.practiceDeckId
                state = GateState.RUNNING
                reviewHost.attachFreshReviewer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Blocker: gate preparation failed")
                failOpen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleInstance: re-triggering while the gate is already open routes here
        BlockTarget.fromKey(intent.getStringExtra(EXTRA_TARGET).orEmpty())?.let { target = it }
    }

    override fun onAnswered(
        cardId: CardId,
        rating: Rating,
    ) {
        if (state != GateState.RUNNING) return
        when (progress.onAnswer(cardId, rating)) {
            GateProgress.State.COMPLETE -> onGateSuccess()
            GateProgress.State.IN_PROGRESS -> updateProgressChip()
        }
    }

    override fun onQueueExhausted() {
        if (state != GateState.RUNNING) return
        Timber.i("Blocker: queue exhausted before completion, switching to a practice deck")
        // Set synchronously so the fragment's own finish() (from the same emission)
        // is swallowed by the SWITCHING_QUEUE branch below.
        state = GateState.SWITCHING_QUEUE
        lifecycleScope.launch {
            try {
                val source = sourceDeckId
                val practice = source?.let { PracticeDeckManager.buildPracticeDeck(it) }
                if (practice == null) {
                    failOpen()
                    return@launch
                }
                practiceDeckId = practice
                state = GateState.RUNNING
                reviewHost.attachFreshReviewer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Blocker: switching to practice deck failed")
                failOpen()
            }
        }
    }

    override fun finish() {
        when (state) {
            GateState.UNLOCKED, GateState.ABANDONED -> {
                scheduleCleanup()
                super.finish()
            }
            GateState.SWITCHING_QUEUE -> {
                // The exhausted reviewer fragment finishing its host: ignored, a new
                // reviewer is about to replace it.
                Timber.i("Blocker: ignoring reviewer finish during queue switch")
            }
            GateState.PREPARING, GateState.RUNNING -> {
                // Either a genuine user exit (back press) or the reviewer's
                // queue-exhausted auto-finish racing our own collector. Defer one
                // main-loop tick: if no state transition claims it, it's an abandon.
                mainHandler.post {
                    if (state == GateState.PREPARING || state == GateState.RUNNING) {
                        Timber.i("Blocker: gate abandoned by user")
                        state = GateState.ABANDONED
                        target?.let { BlockerController.onGateAbandoned(this, it) }
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BlockerController.isGateActive = false
        BlockerController.noteGateClosed()
        if (isFinishing && !cleanupScheduled) {
            scheduleCleanup()
        }
    }

    private fun onGateSuccess() {
        if (state == GateState.UNLOCKED) return
        state = GateState.UNLOCKED
        target?.let { BlockerController.grantUnlock(it) }
        showThemedToast(this, getString(R.string.blocker_unlocked_toast, BlockerPrefs.unlockMinutes), true)
        finish()
    }

    /**
     * The gate cannot show cards (collection unusable, no cards at all): grant a
     * regular unlock rather than trapping the user in a gate loop.
     */
    private fun failOpen() {
        if (state == GateState.UNLOCKED) return
        Timber.w("Blocker: failing open, cards unavailable")
        state = GateState.UNLOCKED
        BlockerController.isGateActive = false
        target?.let { BlockerController.grantUnlock(it) }
        showThemedToast(this, getString(R.string.blocker_cards_unavailable), true)
        finish()
    }

    /** Removes the practice deck and restores the user's deck selection. Runs once. */
    private fun scheduleCleanup() {
        if (cleanupScheduled) return
        cleanupScheduled = true
        BlockerController.isGateActive = false
        val practice = practiceDeckId
        val previous = previousDeckId
        if (practice == null && previous == null) return
        applicationScope.launch {
            try {
                practice?.let { PracticeDeckManager.remove(it) }
                previous?.let { withCol { decks.select(it) } }
            } catch (e: Exception) {
                Timber.w(e, "Blocker: gate cleanup failed")
            }
        }
    }

    private fun updateProgressChip() {
        findViewById<TextView>(R.id.gate_progress_chip)?.text =
            getString(R.string.blocker_gate_progress, progress.correctCount, requiredCards)
    }

    companion object {
        private const val EXTRA_TARGET = "target"

        fun getIntent(
            context: Context,
            target: BlockTarget,
        ): Intent = Intent(context, GateActivity::class.java).putExtra(EXTRA_TARGET, target.key)
    }
}
