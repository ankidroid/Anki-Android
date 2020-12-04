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

package com.ichi2.ui;

import android.app.Activity;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;

import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.servicelayer.NightModeService;

public class MenuItemUtil {

    public static void setOnLongPressListener(Activity activity, MenuItem item, View.OnLongClickListener listener) {
        new Handler().post(() -> {
            View view = activity.findViewById(item.getItemId());

            if (view != null) {
                view.setOnLongClickListener(listener);
            }
        });
    }
}
