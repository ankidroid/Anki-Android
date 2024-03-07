/*
 *  Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.ichi2.anki.dialogs.DiscardChangesDialog
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Handles adding and editing Image Occlusion cards.
 *
 * Based in [SingleFragmentActivity], but with `configChanges="orientation|screenSize"`
 * to avoid unwanted activity recreations
 */
class ImageOcclusionActivity : SingleFragmentActivity() {

    override fun onStart() {
        super.onStart()
        onBackPressedDispatcher.addCallback(this) {
            DiscardChangesDialog.showDialog(this@ImageOcclusionActivity) {
                closeIOEditor()
            }
        }
    }

    private fun closeIOEditor() {
        finish()
    }

    companion object {

        fun getIntent(context: Context, fragmentClass: KClass<out Fragment>, arguments: Bundle? = null): Intent {
            return Intent(context, ImageOcclusionActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, fragmentClass.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
            }
        }
    }
}
