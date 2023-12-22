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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.ichi2.themes.setTransparentStatusBar
import com.ichi2.utils.getInstanceFromClassName
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
class SingleFragmentActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.single_fragment_activity)
        setTransparentStatusBar()

        val fragmentClassName =
            requireNotNull(intent.getStringExtra(FRAGMENT_NAME_EXTRA)) {
                "'$FRAGMENT_NAME_EXTRA' extra should be provided"
            }
        val fragment =
            getInstanceFromClassName<Fragment>(fragmentClassName).apply {
                arguments = intent.getBundleExtra(FRAGMENT_ARGS_EXTRA)
            }
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
        }
    }

    companion object {
        private const val FRAGMENT_NAME_EXTRA = "fragmentName"
        private const val FRAGMENT_ARGS_EXTRA = "fragmentArgs"

        fun getIntent(
            context: Context,
            fragmentClass: KClass<out Fragment>,
            arguments: Bundle? = null,
        ): Intent {
            return Intent(context, SingleFragmentActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, fragmentClass.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
            }
        }
    }
}
