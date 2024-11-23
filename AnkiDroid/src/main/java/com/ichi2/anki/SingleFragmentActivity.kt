/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
import android.view.KeyEvent
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.utils.ExtendedFragmentFactory
import com.ichi2.utils.getInstanceFromClassName
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Activity aimed to host a fragment on the entire screen.
 * For that, it uses [R.layout.single_fragment_activity], which has only a [FragmentContainerView]
 *
 * Useful to avoid creating a Activity for every new screen
 * while being able to reuse the fragment on other places.
 *
 * [getIntent] can be used as an easy way to build a [SingleFragmentActivity]
 */
open class SingleFragmentActivity : AnkiActivity(), CustomStudyDialog.CustomStudyListener {
    /** The displayed fragment. */
    lateinit var fragment: Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        // This page *may* host the CustomStudyDialog (CongratsPage)
        // CustomStudyDialog requires a custom factory install during lifecycle or it can
        // crash during lifecycle resume after background kill
        val customStudyDialogFactory = CustomStudyDialogFactory({ this.getColUnsafe }, this)
        customStudyDialogFactory.attachToActivity<ExtendedFragmentFactory>(this)

        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }
        setContentView(R.layout.single_fragment_activity)

        // avoid recreating the fragment on configuration changes
        // the fragment should handle state restoration
        if (savedInstanceState != null) {
            Timber.d("restoring fragment due to config changes")
            supportFragmentManager.findFragmentById(R.id.fragment_container)?.let { fragment ->
                this.fragment = fragment
                return
            }
            Timber.w("Fragment not found after config change. Recreating it")
        }

        val fragmentClassName = requireNotNull(intent.getStringExtra(FRAGMENT_NAME_EXTRA)) {
            "'$FRAGMENT_NAME_EXTRA' extra should be provided"
        }

        Timber.d("Creating fragment %s", fragmentClassName)

        fragment = getInstanceFromClassName<Fragment>(fragmentClassName).apply {
            arguments = intent.getBundleExtra(FRAGMENT_ARGS_EXTRA)
        }
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, FRAGMENT_TAG)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)!!
        return if (fragment is DispatchKeyEventListener) {
            fragment.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    companion object {
        const val FRAGMENT_NAME_EXTRA = "fragmentName"
        const val FRAGMENT_ARGS_EXTRA = "fragmentArgs"
        const val FRAGMENT_TAG = "SingleFragmentActivityTag"

        fun getIntent(context: Context, fragmentClass: KClass<out Fragment>, arguments: Bundle? = null, intentAction: String? = null): Intent {
            return Intent(context, SingleFragmentActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, fragmentClass.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
                action = intentAction
            }
        }
    }

    override val shortcuts: ShortcutGroup?
        get() = (fragment as? ShortcutGroupProvider)?.shortcuts

    // Begin - implementation of CustomStudyListener methods here for crash fix
    // TODO - refactor https://github.com/ankidroid/Anki-Android/pull/17508#pullrequestreview-2465561993
    private fun openStudyOptionsAndFinish() {
        val intent = Intent(this, StudyOptionsActivity::class.java).apply {
            putExtra("withDeckOptions", false)
        }
        startActivity(intent, null)
        this.finish()
    }

    override fun onExtendStudyLimits() {
        Timber.v("CustomStudyListener::onExtendStudyLimits()")
        openStudyOptionsAndFinish()
    }

    override fun showDialogFragment(newFragment: DialogFragment) {
        Timber.v("CustomStudyListener::showDialogFragment()")
        newFragment.show(supportFragmentManager, null)
    }

    override fun startActivity(intent: Intent) {
        Timber.v("CustomStudyListener::startActivity() - not handled")
    }

    override fun onCreateCustomStudySession() {
        Timber.v("CustomStudyListener::onCreateCustomStudySession()")
        openStudyOptionsAndFinish()
    }

    // END CustomStudyListener temporary implementation - should refactor out
}

interface DispatchKeyEventListener {
    fun dispatchKeyEvent(event: KeyEvent): Boolean
}
