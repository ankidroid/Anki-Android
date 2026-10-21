// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.databinding.IncludeFloatingAddButtonBinding
import com.ichi2.anki.deckpicker.DeckPickerFabMenu
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.compose.theme.AnkiDroidTheme
import timber.log.Timber

class DeckPickerFloatingActionMenu(
    private val context: Context,
    homescreenBinding: ActivityHomescreenBinding,
    private val deckPicker: DeckPicker,
    private val toggleListener: FloatingActionBarToggleListener,
    initiallyOpen: Boolean,
) {
    private val binding: IncludeFloatingAddButtonBinding = homescreenBinding.deckPickerPane.floatingActionButton
    private val linearLayout: LinearLayout = homescreenBinding.deckPickerPane.deckpickerView
    private val studyOptionsFrame: View? = homescreenBinding.studyoptionsFrame

    var isFABOpen by mutableStateOf(initiallyOpen)
        private set

    var isShown by mutableStateOf(true)
        private set

    private var pendingMenuReset = false

    val isFragmented: Boolean
        get() = studyOptionsFrame != null

    init {
        deckPicker.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = resetMenuAfterAdding()

                override fun onResume(owner: LifecycleOwner) = resetMenuAfterAdding()
            },
        )

        binding.composeFabMenu.setContent {
            AnkiDroidTheme {
                if (isShown) {
                    DeckPickerFabMenu(
                        expanded = isFABOpen,
                        onExpandedChange = { open ->
                            if (open) showFloatingActionMenu() else closeFloatingActionMenu(style = CloseAnimation.SHORT)
                        },
                        onAddNote = { addNote() },
                        onCreateDeck = {
                            closeFloatingActionMenu(style = CloseAnimation.LONG)
                            deckPicker.showCreateDeckDialog()
                        },
                        onAddFilteredDeck = {
                            closeFloatingActionMenu(style = CloseAnimation.LONG)
                            deckPicker.showCreateFilteredDeckDialog()
                        },
                        onAddSharedDeck = {
                            closeFloatingActionMenu(style = CloseAnimation.LONG)
                            Timber.d("DeckPickerFabMenu::onAddSharedDeck - Adding Shared Deck")
                            deckPicker.openAnkiWebSharedDecks()
                        },
                        createDeckLabel = with(context) { TR.sentenceCase.createDeck },
                    )
                }
            }
        }
        if (initiallyOpen) {
            showFloatingActionMenu()
        }
    }

    @VisibleForTesting
    fun showFloatingActionMenu() {
        if (pendingMenuReset) return
        toggleListener.onBeginToggle(isOpening = true)
        deckPicker.activeSnackBar?.dismiss()
        linearLayout.alpha = 0.5f
        studyOptionsFrame?.let { it.alpha = 0.5f }
        isFABOpen = true
    }

    /**
     * Dismisses the menu, or resets it off-screen after opening the editor.
     * [style] selects the closing animation for Back/outside taps or menu actions.
     * [animate] is false for the off-screen reset.
     */
    fun closeFloatingActionMenu(
        @Suppress("UNUSED_PARAMETER") style: CloseAnimation = CloseAnimation.SHORT,
        @Suppress("UNUSED_PARAMETER") animate: Boolean = true,
    ) {
        toggleListener.onBeginToggle(isOpening = false)
        linearLayout.alpha = 1f
        studyOptionsFrame?.let { it.alpha = 1f }
        isFABOpen = false
    }

    fun closeFloatingActionMenu(applyRiseAndShrinkAnimation: Boolean) {
        closeFloatingActionMenu()
    }

    fun showFloatingActionButton() {
        if (!isShown) {
            Timber.i("DeckPicker:: showFloatingActionButton()")
            isShown = true
            binding.fabMain.isVisible = true
            binding.root.isVisible = true
        }
    }

    fun hideFloatingActionButton() {
        if (isShown) {
            closeFloatingActionMenu(style = CloseAnimation.LONG)
            Timber.i("DeckPicker:: hideFloatingActionButton()")
            isShown = false
            binding.fabMain.isVisible = false
            binding.root.isVisible = false
        }
    }

    /**
     * Opens the [NoteEditorFragment], preserving the menu during the outgoing transition.
     * @see DeckPicker.addNote
     */
    private fun addNote() {
        if (pendingMenuReset) return
        pendingMenuReset = true
        isFABOpen = false
        deckPicker.addNote()
    }

    private fun resetMenuAfterAdding() {
        if (!pendingMenuReset) return
        pendingMenuReset = false
        closeFloatingActionMenu(style = CloseAnimation.LONG, animate = false)
    }

    /** Animation styles for closing the FAB menu, with offsets in pixels. */
    enum class CloseAnimation(
        val deckOffset: Float,
        val otherOffset: Float,
    ) {
        /** Back or an outside tap moves the menu buttons a shorter distance. */
        SHORT(deckOffset = 300f, otherOffset = 400f),

        /** Menu actions, the navigation drawer, or hiding the FAB move the buttons farther. */
        LONG(deckOffset = 400f, otherOffset = 600f),
    }

    fun interface FloatingActionBarToggleListener {
        /** Triggered when the drawer is starting to open/close */
        fun onBeginToggle(isOpening: Boolean)
    }
}
