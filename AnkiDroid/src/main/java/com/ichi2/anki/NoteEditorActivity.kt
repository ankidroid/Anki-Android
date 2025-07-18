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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditorFragment.Companion.shouldReplaceNewlines
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.previewer.TemplatePreviewerViewModel
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.utils.postDelayed
import com.ichi2.themes.Themes
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.seconds

/**
 * To find the actual note Editor, @see [NoteEditorFragment]
 * This activity contains the NoteEditorFragment, and, on x-large screens, the previewer fragment.
 * It also ensures that changes in the note are transmitted to the previewer
 */

// TODO: Move intent handling to [NoteEditorActivity] from [NoteEditorFragment]
class NoteEditorActivity :
    AnkiActivity(),
    BaseSnackbarBuilderProvider,
    DispatchKeyEventListener,
    ShortcutGroupProvider {
    override val baseSnackbarBuilder: SnackbarBuilder = { }

    lateinit var noteEditorFragment: NoteEditorFragment

    private val mainToolbar: androidx.appcompat.widget.Toolbar
        get() = findViewById(R.id.toolbar)

    private var previewerFrame: FragmentContainerView? = null
    private val refreshPreviewerFragmentHandler = Handler(Looper.getMainLooper())

    // Add a property to store the last queued runnable for cleanup
    private var lastRefreshRunnable: Runnable? = null

    val fragmented: Boolean
        get() = previewerFrame?.isVisible == true

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
        Timber.i("Using split Note Editor: %b", fragmented)

        /**
         * The [NoteEditorActivity] activity supports multiple note editing workflows using fragments.
         * It dynamically chooses the appropriate fragment to load and the arguments to pass to it,
         * based on intent extras provided at launch time.
         *
         * - [FRAGMENT_NAME_EXTRA]: Fully qualified name of the fragment class to instantiate.
         *   If set to [NoteEditorFragment], the activity initializes it with the arguments in
         *   [FRAGMENT_ARGS_EXTRA].
         *
         * - [FRAGMENT_ARGS_EXTRA]: Bundle containing parameters for the fragment (e.g. note ID,
         *   deck ID, etc.). Used to populate fields or determine editor behavior.
         *
         * This logic is encapsulated in the [launcher]  assignment, which selects the correct
         * fragment mode (e.g. add note, edit note) based on intent contents.
         */
        val launcher =
            if (intent.hasExtra(FRAGMENT_NAME_EXTRA)) {
                val fragmentClassName = intent.getStringExtra(FRAGMENT_NAME_EXTRA)
                if (fragmentClassName == NoteEditorFragment::class.java.name) {
                    val fragmentArgs = intent.getBundleExtra(FRAGMENT_ARGS_EXTRA)
                    if (fragmentArgs != null) {
                        NoteEditorLauncher.PassArguments(fragmentArgs)
                    } else {
                        NoteEditorLauncher.AddNote()
                    }
                } else {
                    NoteEditorLauncher.AddNote()
                }
            } else {
                // Regular NoteEditor intent handling
                intent.getBundleExtra(FRAGMENT_ARGS_EXTRA)?.let { fragmentArgs ->
                    // If FRAGMENT_ARGS_EXTRA is provided, use it directly
                    NoteEditorLauncher.PassArguments(fragmentArgs)
                } ?: intent.extras?.let { bundle ->
                    // Check if the bundle contains FRAGMENT_ARGS_EXTRA (for launchers that wrap their args)
                    bundle.getBundle(FRAGMENT_ARGS_EXTRA)?.let { wrappedFragmentArgs ->
                        NoteEditorLauncher.PassArguments(wrappedFragmentArgs)
                    } ?: NoteEditorLauncher.PassArguments(bundle)
                } ?: NoteEditorLauncher.AddNote()
            }

        val existingFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)

        if (existingFragment == null) {
            supportFragmentManager.commit {
                replace(R.id.note_editor_fragment_frame, NoteEditorFragment.newInstance(launcher), FRAGMENT_TAG)
                setReorderingAllowed(true)
                /**
                 * Initializes the noteEditorFragment reference only after the transaction is committed.
                 * This ensures the fragment is fully created and available in the activity before
                 * any code attempts to interact with it, preventing potential null reference issues.
                 */
                runOnCommit {
                    noteEditorFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as NoteEditorFragment
                }
            }
        } else {
            noteEditorFragment = existingFragment as NoteEditorFragment
        }

        enableToolbar()

        // R.id.home is handled in setNavigationOnClickListener
        // Set a listener for back button clicks in the toolbar
        mainToolbar.setNavigationOnClickListener {
            Timber.i("NoteEditor:: Back button on the menu was pressed")
            onBackPressedDispatcher.onBackPressed()
        }

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

        startLoadingCollection()
    }

    /**
     * Loads and configures the note editor previewer.
     *
     * This method orchestrates the entire preview process including:
     * - Processing the current note fields and tags
     * - Setting up the previewer fragment with the appropriate configuration
     * - Configuring the tab layout for card template navigation
     *
     * The preview will reflect the current state of the note being edited,
     * allowing users to see how their cards will appear during review.
     */
    fun loadNoteEditorPreviewer() {
        if (!fragmented) {
            return
        }

        // Check if noteEditorFragment is initialized before proceeding
        if (!::noteEditorFragment.isInitialized) {
            Timber.w("loadNoteEditorPreviewer called before noteEditorFragment was initialized")
            return
        }

        launchCatchingTask {
            val fields = prepareNoteFields()
            val tags = noteEditorFragment.selectedTags ?: mutableListOf()
            val ord = determineCardOrdinal(fields)

            val previewerFragment = createPreviewerFragment(fields, tags, ord)

            // Use commit and post to ensure proper fragment transaction ordering
            supportFragmentManager.commit {
                replace(R.id.previewer_frame, previewerFragment)
            }

            // Configure the tab layout for the previewer
            configurePreviewerTabs(previewerFragment)
        }
    }

    /**
     * Prepares the note fields for the previewer by converting them to the appropriate format.
     *
     * @return A list of field values properly formatted for display
     */
    private fun prepareNoteFields(): MutableList<String> {
        val convertNewlines = shouldReplaceNewlines()

        fun String?.toFieldText(): String = NoteService.convertToHtmlNewline(this.toString(), convertNewlines)

        return noteEditorFragment.editFields?.mapTo(mutableListOf()) { it.fieldText.toFieldText() } ?: mutableListOf()
    }

    /**
     * Determines the appropriate card ordinal (template position) to display.
     *
     * For cloze notes, it identifies the first cloze number present in the fields.
     * For standard notes, it uses the currently edited card ordinal if available.
     *
     * @param fields The processed note fields
     * @return The ordinal (position) of the card template to display
     */
    private suspend fun determineCardOrdinal(fields: MutableList<String>): Int {
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
        return ord
    }

    /**
     * Creates and configures the template previewer fragment.
     *
     * @param fields The processed note fields
     * @param tags The selected tags for the note
     * @param ord The ordinal (position) of the card template to display
     * @return The configured previewer fragment
     */
    private fun createPreviewerFragment(
        fields: MutableList<String>,
        tags: MutableList<String>,
        ord: Int,
    ): TemplatePreviewerFragment {
        val args =
            TemplatePreviewerArguments(
                notetypeFile =
                    NotetypeFile(
                        this@NoteEditorActivity,
                        noteEditorFragment.editorNote!!.notetype,
                    ),
                fields = fields,
                tags = tags,
                id = noteEditorFragment.editorNote!!.id,
                ord = ord,
                fillEmpty = true,
            )

        val backgroundColor = Themes.getColorFromAttr(this@NoteEditorActivity, R.attr.alternativeBackgroundColor)
        val previewerFragment = TemplatePreviewerFragment.newInstance(args, backgroundColor)
        return previewerFragment
    }

    /**
     * Configures the tab layout for the previewer with appropriate tabs for each card template.
     * Sets up tab selection listeners to handle navigation between different card templates.
     *
     * @param previewerFragment The previewer fragment that contains the view model
     */
    private fun configurePreviewerTabs(previewerFragment: TemplatePreviewerFragment) {
        // Post to ensure the fragment is attached before accessing its viewModel
        findViewById<View>(R.id.previewer_frame).post {
            val previewerTabLayout = findViewById<TabLayout>(R.id.previewer_tab_layout)
            previewerTabLayout.removeAllTabs()

            val backgroundColor =
                Themes.getColorFromAttr(this@NoteEditorActivity, R.attr.alternativeBackgroundColor)
            previewerTabLayout.setBackgroundColor(backgroundColor)

            // Now that the previewerFragment is attached, we can safely access its viewModel
            val previewerViewModel = previewerFragment.viewModel

            lifecycleScope.launch {
                setupPreviewerTabs(previewerTabLayout, previewerViewModel)
            }
        }
    }

    /**
     * Sets up the previewer tabs with appropriate titles and selection handling.
     *
     * @param previewerTabLayout The tab layout to configure
     * @param previewerViewModel The view model containing template information
     */
    private suspend fun setupPreviewerTabs(
        previewerTabLayout: TabLayout,
        previewerViewModel: TemplatePreviewerViewModel,
    ) {
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
            object : TabLayout.OnTabSelectedListener {
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

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        noteEditorFragment.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override val shortcuts: ShortcutGroup
        get() = noteEditorFragment.shortcuts

    override fun onDestroy() {
        // Remove any pending callbacks to prevent memory leaks
        lastRefreshRunnable?.let { refreshPreviewerFragmentHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object {
        const val FRAGMENT_ARGS_EXTRA = "fragmentArgs"
        const val FRAGMENT_NAME_EXTRA = "fragmentName"
        const val FRAGMENT_TAG = "NoteEditorFragmentTag"

        // Keys for saving pane weights in SharedPreferences
        private const val PREF_NOTE_EDITOR_PANE_WEIGHT = "noteEditorPaneWeight"
        private const val PREF_PREVIEWER_PANE_WEIGHT = "previewerPaneWeight"

        private val REFRESH_NOTE_EDITOR_PREVIEW_DELAY = 1.seconds

        /**
         * Creates an Intent to launch the NoteEditor activity with a specific fragment class and arguments.
         *
         * @param context The context from which the intent will be launched
         * @param fragmentClass The Kotlin class of the Fragment to instantiate
         * @param arguments Optional bundle of arguments to pass to the fragment
         * @param intentAction Optional action to set on the intent
         * @return An Intent configured to launch NoteEditor with the specified fragment
         */
        fun getIntent(
            context: Context,
            fragmentClass: KClass<out Fragment>,
            arguments: Bundle? = null,
            intentAction: String? = null,
        ): Intent =
            Intent(context, NoteEditorActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, fragmentClass.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
                action = intentAction
            }
    }
}
