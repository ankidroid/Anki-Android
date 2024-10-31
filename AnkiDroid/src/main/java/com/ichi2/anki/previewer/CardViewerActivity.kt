/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.utils.navBarNeedsScrim
import com.ichi2.themes.Themes
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * @see PreviewerFragment
 * @see TemplatePreviewerFragment
 */
class CardViewerActivity : SingleFragmentActivity() {
    @Suppress("deprecation", "API35 properly handle edge-to-edge")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // TODO assess moving this to SingleFragmentActivity
        super.onCreate(savedInstanceState)

        // use the screen background color if the nav bar doesn't need a scrim when using a
        // transparent background. e.g. when navigation gestures are enabled
        if (!navBarNeedsScrim) {
            window.navigationBarColor = Themes.getColorFromAttr(this, R.attr.alternativeBackgroundColor)
        }
    }

    companion object {
        fun getIntent(context: Context, fragmentClass: KClass<out Fragment>, arguments: Bundle? = null): Intent {
            return Intent(context, CardViewerActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, fragmentClass.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
            }
        }
    }
}
