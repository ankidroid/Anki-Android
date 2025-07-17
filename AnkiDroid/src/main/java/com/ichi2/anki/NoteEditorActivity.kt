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
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

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

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }

        setContentView(R.layout.note_editor)

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

        startLoadingCollection()
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

    companion object {
        const val FRAGMENT_ARGS_EXTRA = "fragmentArgs"
        const val FRAGMENT_NAME_EXTRA = "fragmentName"
        const val FRAGMENT_TAG = "NoteEditorFragmentTag"

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
