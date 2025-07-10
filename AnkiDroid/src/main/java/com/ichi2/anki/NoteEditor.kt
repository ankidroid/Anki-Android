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
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * To find the actual note Editor, @see [NoteEditorFragment]
 * This activity contains the NoteEditorFragment, and, on x-large screens, the previewer fragment.
 * It also ensures that changes in the note are transmitted to the previewer
 */

// TODO: Move intent handling to [NoteEditor] from [NoteEditorFragment]
class NoteEditor :
    AnkiActivity(),
    SubtitleListener,
    TagsDialogListener,
    BaseSnackbarBuilderProvider,
    DispatchKeyEventListener,
    ShortcutGroupProvider {
    override val baseSnackbarBuilder: SnackbarBuilder = { }

    /**
     * Retrieves the [NoteEditorFragment]
     */
    lateinit var noteEditorFragment: NoteEditorFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }

        setContentView(R.layout.note_editor)

        // Create and launch the NoteEditorFragment based on the launcher intent
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

        supportFragmentManager.commit {
            replace(R.id.note_editor_fragment_frame, NoteEditorFragment.newInstance(launcher), FRAGMENT_TAG)
            // Execute the transaction immediately to ensure the fragment is available
            setReorderingAllowed(true)
            runOnCommit {
                // Initialize the noteEditorFragment reference
                noteEditorFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as NoteEditorFragment
            }
        }

        startLoadingCollection()
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()
    }

    override val subtitleText: String
        get() = noteEditorFragment.subtitleText

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        noteEditorFragment.onSelectedTags(selectedTags, indeterminateTags, stateFilter)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        noteEditorFragment.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override val shortcuts: ShortcutGroup
        get() = noteEditorFragment.shortcuts

    companion object {
        const val FRAGMENT_ARGS_EXTRA = "fragment_args"
        const val FRAGMENT_NAME_EXTRA = "fragmentName"
        const val FRAGMENT_TAG = "NoteEditorFragmentTag"

        fun getIntent(
            context: Context,
            fragmentClass: KClass<out Fragment>,
            arguments: Bundle? = null,
            intentAction: String? = null,
        ): Intent =
            Intent(context, NoteEditor::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, fragmentClass.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
                action = intentAction
            }
    }
}
