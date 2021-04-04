/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.Objects;

import androidx.annotation.NonNull;

import static androidx.core.content.ContextCompat.getSystemService;

public class AndroidUiUtils {
    public static boolean isRunningOnTv(Context context) {
        UiModeManager uiModeManager = getSystemService(context, UiModeManager.class);
        if (uiModeManager == null) {
            return false;
        }
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    /**
     * This method is used for setting the focus on an EditText and opening the keyboard for EditText
     * which are used in dialogs.
     * @param view The EditText which requires the focus to be set.
     * @param window The window where the view is present.
     */
    public static void setFocusAndOpenKeyboard(View view, @NonNull Window window) {
        view.requestFocus();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
}
