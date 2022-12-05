/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2022 Brian Da Silva <brianjose2010@gmail.com>                          *
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

package com.ichi2.anki.ui.windows.managespace

import android.os.Bundle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.themes.Themes

class ManageSpaceActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        Themes.updateCurrentThemeByUiMode(resources.configuration.uiMode)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_space)

        // TODO Without this preferences don't get visually disabled. Must be called after onCreate.
        //   As I see it, legacy themes add action bar, which has no effect after onCreate,
        //   and also set disabled text color, which does. Make it so this is not needed.
        //   Comment by Brayan: For anyone else that wants to take this [task], just set
        //   `textColorSecondary` on the default themes to the same color used by the legacy themes.
        Themes.setThemeLegacy(this)

        enableToolbar().apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.pref__manage_space__screen_title)
        }
    }
}
