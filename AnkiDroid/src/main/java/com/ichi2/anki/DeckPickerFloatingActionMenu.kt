// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki

import android.animation.Animator
import android.content.Context
import android.content.res.ColorStateList
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.databinding.IncludeFloatingAddButtonBinding
import com.ichi2.anki.ui.DoubleTapListener
import com.ichi2.anki.ui.internationalization.sentenceCase
import timber.log.Timber

class DeckPickerFloatingActionMenu(
    private val context: Context,
    homescreenBinding: ActivityHomescreenBinding,
    private val deckPicker: DeckPicker,
) {
    private val binding: IncludeFloatingAddButtonBinding = homescreenBinding.deckPickerPane.floatingActionButton

    private val linearLayout: LinearLayout = homescreenBinding.deckPickerPane.deckpickerView
    private val studyOptionsFrame: View? = homescreenBinding.studyoptionsFrame

    private val fabNormalColor = MaterialColors.getColor(binding.fabMain, R.attr.fab_normal)
    private val fabPressedColor = MaterialColors.getColor(binding.fabMain, R.attr.fab_pressed)

    private val addNoteIcon: Int = R.drawable.ic_add_note
    private val addWhiteIcon: Int = R.drawable.ic_add

    var isFABOpen = false
    var toggleListener: FloatingActionBarToggleListener? = null

    @Suppress("unused")
    val isFragmented: Boolean
        get() = studyOptionsFrame != null

    @VisibleForTesting
    fun showFloatingActionMenu() {
        toggleListener?.onBeginToggle(isOpening = true)
        deckPicker.activeSnackBar?.dismiss()
        linearLayout.alpha = 0.5f
        studyOptionsFrame?.let { it.alpha = 0.5f }
        isFABOpen = true

        setCreateDeckButtonLabel()

        if (deckPicker.animationEnabled()) {
            binding.addSharedButton.visibility = View.VISIBLE
            binding.addDeckButton.visibility = View.VISIBLE
            binding.addFilteredDeckButton.visibility = View.VISIBLE
            binding.fabBGLayout.visibility = View.VISIBLE
            binding.fabMain.backgroundTintList = ColorStateList.valueOf(fabPressedColor)
            binding.fabMain.setIconResource(addNoteIcon)
            binding.fabMain.extend()

            with(binding) {
                addSharedButton.animate().translationY(0f).duration = 150
                addDeckButton.animate().translationY(0f).duration = 150
                addFilteredDeckButton.animate().translationY(0f).duration = 150
                addSharedButton.animate().alpha(1f).duration = 150
                addDeckButton.animate().alpha(1f).duration = 150
                addFilteredDeckButton.animate().alpha(1f).duration = 150
            }
        } else {
            binding.addSharedButton.visibility = View.VISIBLE
            binding.addDeckButton.visibility = View.VISIBLE
            binding.addFilteredDeckButton.visibility = View.VISIBLE
            binding.fabBGLayout.visibility = View.VISIBLE
            binding.addSharedButton.alpha = 1f
            binding.addDeckButton.alpha = 1f
            binding.addFilteredDeckButton.alpha = 1f
            binding.addSharedButton.translationY = 0f
            binding.addDeckButton.translationY = 0f
            binding.addFilteredDeckButton.translationY = 0f
            binding.fabMain.isExtended = true

            binding.fabMain.apply {
                backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                setIconResource(addNoteIcon)
            }
        }
    }

    fun closeFloatingActionMenu(applyRiseAndShrinkAnimation: Boolean) {
        toggleListener?.onBeginToggle(isOpening = false)
        if (applyRiseAndShrinkAnimation) {
            linearLayout.alpha = 1f
            studyOptionsFrame?.let { it.alpha = 1f }
            isFABOpen = false
            binding.fabBGLayout.visibility = View.GONE
            if (deckPicker.animationEnabled()) {
                binding.fabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                binding.fabMain.setIconResource(addWhiteIcon)
                binding.fabMain.shrink()

                with(binding) {
                    addSharedButton.animate().alpha(0f).duration = 150
                    addDeckButton.animate().alpha(0f).duration = 150
                    addFilteredDeckButton.animate().alpha(0f).duration = 150
                    addSharedButton.animate().translationY(0f).duration = 150
                    addDeckButton
                        .animate()
                        .translationY(0f)
                        .setDuration(150)
                        .setListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animator: Animator) {}

                                override fun onAnimationEnd(animator: Animator) {
                                    if (!isFABOpen) {
                                        addSharedButton.visibility = View.GONE
                                        addDeckButton.visibility = View.GONE
                                        addFilteredDeckButton.visibility = View.GONE
                                    }
                                }

                                override fun onAnimationCancel(animator: Animator) {}

                                override fun onAnimationRepeat(animator: Animator) {}
                            },
                        )
                    addFilteredDeckButton
                        .animate()
                        .translationY(0f)
                        .setDuration(150)
                        .setListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animator: Animator) {}

                                override fun onAnimationEnd(animator: Animator) {
                                    if (!isFABOpen) {
                                        addSharedButton.visibility = View.GONE
                                        addDeckButton.visibility = View.GONE
                                        addFilteredDeckButton.visibility = View.GONE
                                    }
                                }

                                override fun onAnimationCancel(animator: Animator) {}

                                override fun onAnimationRepeat(animator: Animator) {}
                            },
                        )
                }
            } else {
                binding.addSharedButton.visibility = View.GONE
                binding.addDeckButton.visibility = View.GONE
                binding.addFilteredDeckButton.visibility = View.GONE
                binding.fabMain.isExtended = false
                binding.fabMain.setIconResource(addWhiteIcon)
            }
        } else {
            linearLayout.alpha = 1f
            studyOptionsFrame?.let { it.alpha = 1f }
            isFABOpen = false
            binding.fabBGLayout.visibility = View.GONE
            if (deckPicker.animationEnabled()) {
                binding.fabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                binding.fabMain.setIconResource(addWhiteIcon)
                binding.fabMain.shrink()

                with(binding) {
                    addSharedButton.animate().alpha(0f).duration = 150
                    addDeckButton.animate().alpha(0f).duration = 150
                    addFilteredDeckButton.animate().alpha(0f).duration = 150
                    addSharedButton.animate().translationY(0f).duration = 150
                    addDeckButton
                        .animate()
                        .translationY(0f)
                        .setDuration(150)
                        .setListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animator: Animator) {}

                                override fun onAnimationEnd(animator: Animator) {
                                    if (!isFABOpen) {
                                        addSharedButton.visibility = View.GONE
                                        addDeckButton.visibility = View.GONE
                                        addFilteredDeckButton.visibility = View.GONE
                                    }
                                }

                                override fun onAnimationCancel(animator: Animator) {}

                                override fun onAnimationRepeat(animator: Animator) {}
                            },
                        )
                    addFilteredDeckButton
                        .animate()
                        .translationY(0f)
                        .setDuration(150)
                        .setListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animator: Animator) {}

                                override fun onAnimationEnd(animator: Animator) {
                                    if (!isFABOpen) {
                                        addSharedButton.visibility = View.GONE
                                        addDeckButton.visibility = View.GONE
                                        addFilteredDeckButton.visibility = View.GONE
                                    }
                                }

                                override fun onAnimationCancel(animator: Animator) {}

                                override fun onAnimationRepeat(animator: Animator) {}
                            },
                        )
                }
            } else {
                binding.addSharedButton.visibility = View.GONE
                binding.addDeckButton.visibility = View.GONE
                binding.addFilteredDeckButton.visibility = View.GONE
                binding.fabMain.isExtended = false
                binding.fabMain.setIconResource(addWhiteIcon)
            }
        }
    }

    fun showFloatingActionButton() {
        if (!binding.fabMain.isShown) {
            Timber.i("DeckPicker:: showFloatingActionButton()")
            binding.fabMain.visibility = View.VISIBLE
        }
    }

    fun hideFloatingActionButton() {
        if (binding.fabMain.isShown) {
            closeFloatingActionMenu(false)
            Timber.i("DeckPicker:: hideFloatingActionButton()")
            binding.fabMain.visibility = View.GONE
        }
    }

    private fun createActivationKeyListener(
        logMessage: String,
        action: () -> Unit,
    ): View.OnKeyListener =
        View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                Timber.d(logMessage)
                action()
                return@OnKeyListener true
            }
            false
        }

    init {
        binding.fabMain.isExtended = false
        binding.fabMain.setOnTouchListener(
            object : DoubleTapListener(context) {
                override fun onDoubleTap(e: MotionEvent?) {
                    addNote()
                }

                override fun onUnconfirmedSingleTap(e: MotionEvent?) {
                    if (!isFABOpen) {
                        showFloatingActionMenu()
                    } else {
                        addNote()
                    }
                }
            },
        )

        binding.fabMain.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                        Timber.d("FAB main button: ENTER key pressed")
                        if (!isFABOpen) {
                            showFloatingActionMenu()
                        } else {
                            closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                            addNote()
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_ESCAPE -> {
                        if (isFABOpen) {
                            Timber.d("FAB main button: ESC key pressed - closing menu")
                            closeFloatingActionMenu(applyRiseAndShrinkAnimation = true)
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }

        binding.fabBGLayout.setOnClickListener { closeFloatingActionMenu(applyRiseAndShrinkAnimation = true) }
        val addDeckListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.showCreateDeckDialog()
                }
            }
        setCreateDeckButtonLabel()
        binding.addDeckButton.setOnClickListener(addDeckListener)

        val addDeckKeyListener =
            createActivationKeyListener("Add Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.showCreateDeckDialog()
                }
            }
        binding.addDeckButton.setOnKeyListener(addDeckKeyListener)
        val addFilteredDeckListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.showCreateFilteredDeckDialog()
                }
            }
        binding.addFilteredDeckButton.setOnClickListener(addFilteredDeckListener)

        val addFilteredDeckKeyListener =
            createActivationKeyListener("Add Filtered Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.showCreateFilteredDeckDialog()
                }
            }
        binding.addFilteredDeckButton.setOnKeyListener(addFilteredDeckKeyListener)
        val addSharedListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck")
                    deckPicker.openAnkiWebSharedDecks()
                }
            }
        binding.addSharedButton.setOnClickListener(addSharedListener)

        val addSharedKeyListener =
            createActivationKeyListener("Add Shared Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.openAnkiWebSharedDecks()
                }
            }
        binding.addSharedButton.setOnKeyListener(addSharedKeyListener)

        val fabMainClickListener =
            View.OnClickListener {
                if (!isFABOpen) {
                    showFloatingActionMenu()
                } else {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    Timber.d("configureFloatingActionsMenu::fabMain::onClickListener - Adding Note")
                    addNote()
                }
            }
        binding.fabMain.setOnClickListener(fabMainClickListener)
    }

    private fun addNote() {
        deckPicker.addNote()
    }

    private fun setCreateDeckButtonLabel() {
        binding.addDeckButton.apply {
            text = with(context) { TR.sentenceCase.createDeck }
            contentDescription = text
            isExtended = true
        }
    }

    fun interface FloatingActionBarToggleListener {
        fun onBeginToggle(isOpening: Boolean)
    }
}
