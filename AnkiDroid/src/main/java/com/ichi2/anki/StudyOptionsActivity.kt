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

import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.commit
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.utils.ExtendedFragmentFactory
import com.ichi2.widget.WidgetStatus
import timber.log.Timber

class StudyOptionsActivity : NavigationDrawerActivity(), StudyOptionsListener, CustomStudyListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        val customStudyDialogFactory = CustomStudyDialogFactory({ this.getColUnsafe }, this)
        customStudyDialogFactory.attachToActivity<ExtendedFragmentFactory>(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studyoptions)
        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer(findViewById(android.R.id.content))
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        if (item.itemId == android.R.id.home) {
            closeStudyOptions()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun closeStudyOptions(result: Int = RESULT_OK) {
        // mCompat.invalidateOptionsMenu(this);
        setResult(result)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isDrawerOpen) {
            super.onBackPressed()
        } else {
            Timber.i("Back key pressed")
            closeStudyOptions()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (colIsOpenUnsafe()) {
            WidgetStatus.updateInBackground(this)
        }
    }

    public override fun onResume() {
        super.onResume()
        selectNavigationItem(-1)
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
}
