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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;

import timber.log.Timber;

public class IntentUtil {
    public static boolean canOpenIntent(Context context, Intent intent) {
        try {
            final PackageManager packageManager = context.getPackageManager();
            return intent.resolveActivity(packageManager) != null;
        } catch (Exception e) {
            Timber.w(e);
            return false;
        }
    }

    public static void tryOpenIntent(AnkiActivity activity, Intent intent) {
        try {
            if (canOpenIntent(activity, intent)) {
                activity.startActivityWithoutAnimation(intent);
            } else {
                final String errorMsg = activity.getString(R.string.feedback_no_suitable_app_found);
                UIUtils.showThemedToast(activity, errorMsg, true);
            }
        } catch (Exception e) {
            Timber.w(e);
            final String errorMsg = activity.getString(R.string.feedback_no_suitable_app_found);
            UIUtils.showThemedToast(activity, errorMsg, true);
        }
    }

}
