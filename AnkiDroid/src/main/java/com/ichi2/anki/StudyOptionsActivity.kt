/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.libanki.ChangeManager
import com.ichi2.ui.RtlCompliantActionProvider
import com.ichi2.utils.ExtendedFragmentFactory
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.launch
import timber.log.Timber

class StudyOptionsActivity :
    AnkiActivity(),
    StudyOptionsListener,
    CustomStudyListener,
    ChangeManager.Subscriber {

    private var undoState = UndoState()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        val customStudyDialogFactory = CustomStudyDialogFactory({ this.getColUnsafe }, this)
        customStudyDialogFactory.attachToActivity<ExtendedFragmentFactory>(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studyoptions)
        enableToolbar().apply { title = "" }
        if (savedInstanceState == null) {
            loadStudyOptionsFragment()
        }
    }

    private fun loadStudyOptionsFragment() {
        var withDeckOptions = false
        if (intent.extras != null) {
            withDeckOptions = intent.extras!!.getBoolean("withDeckOptions")
        }
        val currentFragment = StudyOptionsFragment.newInstance(withDeckOptions)
        supportFragmentManager.commit {
            replace(R.id.studyoptions_frame, currentFragment)
        }
    }

    private val currentFragment: StudyOptionsFragment?
        get() = supportFragmentManager.findFragmentById(R.id.studyoptions_frame) as StudyOptionsFragment?

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_study_options, menu)
        val undoMenuItem = menu.findItem(R.id.action_undo)
        val undoActionProvider = MenuItemCompat.getActionProvider(undoMenuItem) as? RtlCompliantActionProvider
        // Set the proper click target for the undo button's ActionProvider
        undoActionProvider?.clickHandler = { _, menuItem -> onOptionsItemSelected(menuItem) }
        undoMenuItem.isVisible = undoState.hasAction
        undoMenuItem.title = undoState.label
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                closeStudyOptions()
                true
            }
            R.id.action_undo -> {
                launchCatchingTask {
                    undoAndShowSnackbar()
                    // TODO why are we going to the Reviewer from here? Desktop doesn't do this
                    Intent(this@StudyOptionsActivity, Reviewer::class.java)
                        .apply { flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT }
                        .also { startActivity(it) }
                    finish()
                }
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun closeStudyOptions(result: Int = RESULT_OK) {
        // mCompat.invalidateOptionsMenu(this);
        setResult(result)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Timber.i("Back key pressed")
        closeStudyOptions()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        refreshUndoState()
    }

    public override fun onStop() {
        super.onStop()
        if (colIsOpenUnsafe()) {
            WidgetStatus.updateInBackground(this)
        }
    }

    override fun onRequireDeckListUpdate() {
        currentFragment!!.refreshInterface()
    }

    /**
     * Callback methods from CustomStudyDialog
     */
    override fun onCreateCustomStudySession() {
        // Sched already reset by CollectionTask in CustomStudyDialog
        currentFragment!!.refreshInterface()
    }

    override fun onExtendStudyLimits() {
        // Sched needs to be reset so provide true argument
        currentFragment!!.refreshInterface()
    }

    override fun opExecuted(changes: OpChanges, handler: Any?) {
        refreshUndoState()
    }

    private fun refreshUndoState() {
        lifecycleScope.launch {
            val newUndoState = withCol {
                UndoState(
                    hasAction = undoAvailable(),
                    label = undoLabel()
                )
            }
            if (undoState != newUndoState) {
                undoState = newUndoState
                invalidateOptionsMenu()
            }
        }
    }

    private data class UndoState(
        val hasAction: Boolean = false,
        val label: String? = null
    )
}
