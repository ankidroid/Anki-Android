/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
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
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import com.ichi2.libanki.utils.TimeManager
import timber.log.Timber
import java.io.FileNotFoundException

/**
 * Activity allowing the user to draw an image to be added the collection
 *
 * user can use all basic whiteboard functionally and can save image from this activity.
 *
 * To access this screen: Add/Edit Note - Attachment - Add Image - Drawing
 */
class DrawingActivity : AnkiActivity() {
    private lateinit var mColorPalette: LinearLayout
    private lateinit var mWhiteboard: Whiteboard

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setTitle(R.string.drawing)
        setContentView(R.layout.activity_drawing)
        enableToolbar()
        mColorPalette = findViewById(R.id.whiteboard_editor)
        mWhiteboard = Whiteboard.createInstance(this, true, null)
        mWhiteboard.setOnTouchListener { _: View?, event: MotionEvent? -> mWhiteboard.handleTouchEvent(event!!) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.drawing_menu, menu)
        val whiteboardEditItem = menu.findItem(R.id.action_whiteboard_edit)
        MenuItemCompat.setIconTintList(
            whiteboardEditItem,
            ContextCompat.getColorStateList(
                this,
                R.color.white
            )
        )
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                Timber.i("Drawing:: Save button pressed")
                finishWithSuccess()
            }
            R.id.action_whiteboard_edit -> {
                Timber.i("Drawing:: Pen Color button pressed")
                if (mColorPalette.visibility == View.GONE) {
                    mColorPalette.visibility = View.VISIBLE
                } else {
                    mColorPalette.visibility = View.GONE
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun finishWithSuccess() {
        try {
            val savedWhiteboardFileName = mWhiteboard.saveWhiteboard(TimeManager.time)
            val resultData = Intent()
            resultData.putExtra(EXTRA_RESULT_WHITEBOARD, savedWhiteboardFileName)
            setResult(RESULT_OK, resultData)
        } catch (e: FileNotFoundException) {
            Timber.w(e)
        } finally {
            finishActivityWithFade(this)
        }
    }

    companion object {
        const val EXTRA_RESULT_WHITEBOARD = "drawing.editedImage"
    }
}
