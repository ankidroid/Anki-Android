/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditorFragment.Companion.shouldReplaceNewlines
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.utils.postDelayed
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import com.ichi2.themes.Themes
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class NoteEditor :
    AnkiActivity(),
    DeckSelectionListener,
    SubtitleListener,
    TagsDialogListener,
    BaseSnackbarBuilderProvider,
    DispatchKeyEventListener,
    MenuProvider,
    ShortcutGroupProvider {
    /**
     * The frame containing the Previewer. Non null only in layout x-large.
     */
    private var previewerFrame: FragmentContainerView? = null
    private val refreshPreviewerFragmentHandler = Handler(Looper.getMainLooper())
    var fragmented: Boolean = false

    override val baseSnackbarBuilder: SnackbarBuilder = { }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }

        setContentView(R.layout.note_editor)

        previewerFrame = findViewById(R.id.previewer_frame)

        /**
         * Check if previewerFrame is not null and if its visibility is set to VISIBLE.
         * If both conditions are true, assign true to the variable [fragmented], otherwise assign false.
         * [fragmented] will be true if the view size is large otherwise false
         */
        fragmented = previewerFrame?.visibility == View.VISIBLE
        Timber.i("Using split Note Editor: %b", fragmented)

        if (fragmented) {
            loadNoteEditorPreviewer()
            val parentLayout = findViewById<LinearLayout>(R.id.note_editor_xl_view)
            val divider = findViewById<View>(R.id.note_editor_resizing_divider)
            val leftPane = findViewById<View>(R.id.note_editor_frame)
            val rightPane = findViewById<View>(R.id.previewer_frame)
            if (parentLayout != null && divider != null && leftPane != null && rightPane != null) {
                ResizablePaneManager(
                    parentLayout = parentLayout,
                    divider = divider,
                    leftPane = leftPane,
                    rightPane = rightPane,
                    sharedPrefs = Prefs.getUiConfig(this),
                    leftPaneWeightKey = PREF_NOTE_EDITOR_PANE_WEIGHT,
                    rightPaneWeightKey = PREF_PREVIEWER_PANE_WEIGHT,
                )
            }
        }

        // Create and launch the NoteEditorFragment based on the launcher intent
        val launcher =
            intent.extras?.let { bundle ->
                // Convert intent extras to NoteEditorLauncher
                NoteEditorLauncher.PassArguments(bundle)
            } ?: NoteEditorLauncher.AddNote()

        supportFragmentManager.commit {
            replace(R.id.note_editor_frame, NoteEditorFragment.newInstance(launcher))
        }

        startLoadingCollection()
    }

    /**
     * Loads or reloads editorNote in [previewerFrame] if the view is fragmented. Do nothing otherwise.
     */
    fun loadNoteEditorPreviewer() {
        if (!fragmented) {
            return
        }
        launchCatchingTask {
            val noteEditorFragment = noteEditorFragment ?: return@launchCatchingTask

            val convertNewlines = shouldReplaceNewlines()

            fun String?.toFieldText(): String = NoteService.convertToHtmlNewline(this.toString(), convertNewlines)
            val fields = noteEditorFragment.editFields?.mapTo(mutableListOf()) { it.fieldText.toFieldText() } ?: mutableListOf()
            val tags = noteEditorFragment.selectedTags ?: mutableListOf()

            val ord =
                if (noteEditorFragment.editorNote!!.notetype.isCloze) {
                    val tempNote = withCol { Note.fromNotetypeId(this@withCol, noteEditorFragment.editorNote!!.notetype.id) }
                    tempNote.fields = fields // makes possible to get the cloze numbers from the fields
                    val clozeNumbers = withCol { clozeNumbersInNote(tempNote) }
                    if (clozeNumbers.isNotEmpty()) {
                        clozeNumbers.first() - 1
                    } else {
                        0
                    }
                } else {
                    noteEditorFragment.currentEditedCard?.ord ?: 0
                }

            val args =
                TemplatePreviewerArguments(
                    notetypeFile = NotetypeFile(this@NoteEditor, noteEditorFragment.editorNote!!.notetype),
                    fields = fields,
                    tags = tags,
                    id = noteEditorFragment.editorNote!!.id,
                    ord = ord,
                    fillEmpty = true,
                )

            val backgroundColor = Themes.getColorFromAttr(this@NoteEditor, R.attr.alternativeBackgroundColor)
            val previewerFragment = TemplatePreviewerFragment.newInstance(args, backgroundColor)

            // Use commit and post to ensure proper fragment transaction ordering
            supportFragmentManager.commit {
                replace(R.id.previewer_frame, previewerFragment)
            }

            // Post to ensure the fragment is attached before accessing its viewModel
            findViewById<View>(R.id.previewer_frame).post {
                val previewerTabLayout = findViewById<TabLayout>(R.id.previewer_tab_layout)
                previewerTabLayout.removeAllTabs()
                previewerTabLayout.setBackgroundColor(backgroundColor)

                // Now that the previewerFragment is attached, we can safely access its viewModel
                val previewerViewModel = previewerFragment.viewModel

                lifecycleScope.launch {
                    val cardsWithEmptyFronts = previewerViewModel.cardsWithEmptyFronts?.await()
                    for ((index, templateName) in previewerViewModel.getTemplateNames().withIndex()) {
                        val tabTitle =
                            if (cardsWithEmptyFronts?.get(index) == true) {
                                getString(R.string.card_previewer_empty_front_indicator, templateName)
                            } else {
                                templateName
                            }
                        val newTab = previewerTabLayout.newTab().setText(tabTitle)
                        previewerTabLayout.addTab(newTab)
                    }
                    previewerTabLayout.selectTab(previewerTabLayout.getTabAt(previewerViewModel.getCurrentTabIndex()))
                    previewerTabLayout.addOnTabSelectedListener(
                        object : OnTabSelectedListener {
                            override fun onTabSelected(tab: TabLayout.Tab) {
                                Timber.v("Selected tab %d", tab.position)
                                previewerViewModel.onTabSelected(tab.position)
                            }

                            override fun onTabUnselected(tab: TabLayout.Tab) {
                                // do nothing
                            }

                            override fun onTabReselected(tab: TabLayout.Tab) {
                                // do nothing
                            }
                        },
                    )
                }
            }
        }
    }

    val noteEditorWatcher: TextWatcher =
        object : TextWatcher {
            /**
             * Declare a nullable variable refreshPreviewerFragmentRunnable of type Runnable.
             * This will hold a reference to the Runnable that refreshes the previewer noteEditorFragment.
             * It is used to manage delayed noteEditorFragment updates and can be null if no updates in card.
             */
            private var refreshPreviewerFragmentRunnable: Runnable? = null

            override fun afterTextChanged(arg0: Editable) {
                refreshPreviewerFragmentRunnable?.let { refreshPreviewerFragmentHandler.removeCallbacks(it) }
                val updateRunnable =
                    Runnable {
                        loadNoteEditorPreviewer()
                    }
                refreshPreviewerFragmentRunnable = updateRunnable
                refreshPreviewerFragmentHandler.postDelayed(updateRunnable, REFRESH_NOTE_EDITOR_PREVIEW_DELAY)
            }

            override fun beforeTextChanged(
                arg0: CharSequence,
                arg1: Int,
                arg2: Int,
                arg3: Int,
            ) {
                // do nothing
            }

            override fun onTextChanged(
                arg0: CharSequence,
                arg1: Int,
                arg2: Int,
                arg3: Int,
            ) {
                // do nothing
            }
        }

    /**
     * Retrieves the [NoteEditorFragment]
     */
    val noteEditorFragment: NoteEditorFragment?
        get() = supportFragmentManager.findFragmentById(R.id.note_editor_frame) as? NoteEditorFragment

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()
    }

    override fun onDeckSelected(deck: DeckSelectionDialog.SelectableDeck?) {
        noteEditorFragment?.onDeckSelected(deck)
    }

    override val subtitleText: String
        get() = noteEditorFragment?.subtitleText ?: ""

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        noteEditorFragment?.onSelectedTags(selectedTags, indeterminateTags, stateFilter)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        noteEditorFragment?.dispatchKeyEvent(event) ?: false || super.dispatchKeyEvent(event)

    override fun onCreateMenu(
        menu: Menu,
        menuInflater: android.view.MenuInflater,
    ) {
        noteEditorFragment?.onCreateMenu(menu, menuInflater)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean = noteEditorFragment?.onMenuItemSelected(item) ?: false

    override val shortcuts: ShortcutGroup
        get() = noteEditorFragment?.shortcuts ?: ShortcutGroup(emptyList(), 0)

    companion object {
        // Keys for saving pane weights in SharedPreferences
        private const val PREF_NOTE_EDITOR_PANE_WEIGHT = "noteEditorPaneWeight"
        private const val PREF_PREVIEWER_PANE_WEIGHT = "previewerPaneWeight"

        private val REFRESH_NOTE_EDITOR_PREVIEW_DELAY = 1.seconds
    }
}
