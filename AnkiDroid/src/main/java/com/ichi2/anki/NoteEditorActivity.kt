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
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.NoteEditorActivity.Companion.FRAGMENT_ARGS_EXTRA
import com.ichi2.anki.NoteEditorActivity.Companion.FRAGMENT_NAME_EXTRA
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.noteeditor.NoteEditorFragmentDelegate
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.themes.Themes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.milliseconds

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
    ShortcutGroupProvider,
    NoteEditorFragmentDelegate {
    override val baseSnackbarBuilder: SnackbarBuilder = { }

    lateinit var noteEditorFragment: NoteEditorFragment

    private val mainToolbar: androidx.appcompat.widget.Toolbar
        get() = findViewById(R.id.toolbar)

    /**
     * Reference to the previewer container that exists only on larger screens.
     * Non-null if and only if the layout is x-large and includes the previewer frame
     *
     * Unlike lateinit variables, this will remain null throughout the activity
     * lifecycle on smaller screens that don't include the previewer frame.
     *
     * Fragmentation is determined by this view's visibility after inflation.
     */
    private var previewerFrame: FragmentContainerView? = null

    /**
     * Job for managing delayed previewer refresh operations.
     * Automatically cancelled when the lifecycle scope is destroyed, preventing memory leaks.
     */
    private var refreshPreviewerJob: Job? = null

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
        Timber.i("Note Editor is in %s mode", if (fragmented) "split" else "single-pane")

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
                    noteEditorFragment.setDelegate(this@NoteEditorActivity)
                }
            }
        } else {
            noteEditorFragment = existingFragment as NoteEditorFragment
            noteEditorFragment.setDelegate(this)
        }

        enableToolbar()

        // R.id.home is handled in setNavigationOnClickListener
        // Set a listener for back button clicks in the toolbar
        mainToolbar.setNavigationOnClickListener {
            Timber.i("NoteEditor:: Back button on the menu was pressed")
            onBackPressedDispatcher.onBackPressed()
        }

        if (fragmented) {
            // Defer previewer loading to avoid blocking onCreate
            findViewById<View>(R.id.previewer_frame).post {
                loadNoteEditorPreviewer()
            }
            val parentLayout = findViewById<LinearLayout>(R.id.note_editor_xl_view)
            val divider = findViewById<View>(R.id.note_editor_resizing_divider)
            val noteEditorPane = findViewById<View>(R.id.note_editor_fragment_frame)
            val previewerPane = findViewById<View>(R.id.previewer_frame_layout)
            ResizablePaneManager(
                parentLayout = parentLayout,
                divider = divider,
                leftPane = noteEditorPane,
                rightPane = previewerPane,
                sharedPrefs = Prefs.getUiConfig(this),
                leftPaneWeightKey = PREF_NOTE_EDITOR_PANE_WEIGHT,
                rightPaneWeightKey = PREF_PREVIEWER_PANE_WEIGHT,
            )
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
     *
     * TODO: Optimize to update existing fragment instead of replacing it entirely
     * Currently this method recreates the entire previewer fragment on every content change,
     * which is expensive and loses user state like scroll position and selected tab.
     *
     * BUG: Fragment replacement loses user state
     * - Scroll position is reset when user has scrolled through long card content
     * - Selected tab is reset when user switches between card templates
     * - Any other fragment-specific state is lost on each update
     *
     * Ideally, we should:
     * 1. Check if existing fragment can be updated in-place
     * 2. Preserve scroll position and selected tab when possible
     * 3. Only replace fragment when note type changes or other structural changes occur
     * 4. Use fragment's update methods instead of full recreation for content changes
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

        // Check if editorNote is available before proceeding
        if (noteEditorFragment.editorNote == null) {
            Timber.w("loadNoteEditorPreviewer called before editorNote was available")
            return
        }

        // Launch in a coroutine to avoid blocking the UI thread
        lifecycleScope.launch {
            try {
                val fields = noteEditorFragment.prepareNoteFields()
                val tags = noteEditorFragment.selectedTags ?: mutableListOf()
                val ord = noteEditorFragment.determineCardOrdinal(fields)

                val previewerFragment = createPreviewerFragment(fields, tags, ord)

                // Switch back to main thread for UI operations
                supportFragmentManager.commit {
                    replace(R.id.previewer_frame, previewerFragment)
                }

                // Configure the tab layout for the previewer
                configurePreviewerTabs(previewerFragment)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load note editor previewer")
            }
        }
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
        fields: List<String>,
        tags: List<String>,
        ord: Int,
    ): TemplatePreviewerFragment {
        val args =
            TemplatePreviewerArguments(
                notetypeFile =
                    NotetypeFile(
                        this@NoteEditorActivity,
                        noteEditorFragment.editorNote!!.notetype,
                    ),
                fields = fields.toMutableList(),
                tags = tags.toMutableList(),
                id = noteEditorFragment.editorNote!!.id,
                ord = ord,
                fillEmpty = false,
            )

        val backgroundColor = Themes.getColorFromAttr(this@NoteEditorActivity, R.attr.alternativeBackgroundColor)
        val previewerFragment = TemplatePreviewerFragment.newInstance(args, backgroundColor)
        return previewerFragment
    }

    /**
     * Configures the tab layout for the previewer with appropriate tabs for each card template.
     * Delegates the tab setup responsibility to the TemplatePreviewerFragment
     *
     * @param previewerFragment The previewer fragment that will manage the tabs
     */
    private fun configurePreviewerTabs(previewerFragment: TemplatePreviewerFragment) {
        // Post to ensure the fragment is attached before accessing its viewModel
        findViewById<View>(R.id.previewer_frame).post {
            val previewerTabLayout = findViewById<TabLayout>(R.id.previewer_tab_layout)
            lifecycleScope.launch {
                previewerFragment.setupTabs(previewerTabLayout)
            }
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

    override fun onResume() {
        super.onResume()
        // Refresh the previewer when activity resumes, if needed
        if (fragmented) {
            loadNoteEditorPreviewer()
        }
    }

    // ----------------------------------------------------------------------------
    // NoteEditorFragmentDelegate Protocol Methods
    // ----------------------------------------------------------------------------

    override fun onNoteEditorReady() {
        // Load the if fragmented, else does nothing
        if (!fragmented) return

        loadNoteEditorPreviewer()
    }

    override fun onNoteTextChanged() {
        if (!fragmented) return

        refreshPreviewerJob?.cancel()
        refreshPreviewerJob =
            lifecycleScope.launch {
                delay(REFRESH_NOTE_EDITOR_PREVIEW_DELAY)
                loadNoteEditorPreviewer()
            }
    }

    override fun onNoteSaved() {
        if (!fragmented) return

        loadNoteEditorPreviewer()
    }

    override fun onNoteTypeChanged() {
        if (!fragmented) return

        loadNoteEditorPreviewer()
    }

    companion object {
        const val FRAGMENT_ARGS_EXTRA = "fragmentArgs"
        const val FRAGMENT_NAME_EXTRA = "fragmentName"
        const val FRAGMENT_TAG = "NoteEditorFragmentTag"

        // Keys for saving pane weights in SharedPreferences
        private const val PREF_NOTE_EDITOR_PANE_WEIGHT = "noteEditorPaneWeight"
        private const val PREF_PREVIEWER_PANE_WEIGHT = "previewerPaneWeight"

        private val REFRESH_NOTE_EDITOR_PREVIEW_DELAY = 100.milliseconds

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
