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

// ಠ_ಠ This is required to override a package-private method to block animations
package androidx.drawerlayout.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;

public class ClosableDrawerLayout extends DrawerLayout {
    @SuppressWarnings( {"FieldCanBeLocal", "unused", "RedundantSuppression"})
    private boolean mAnimationEnabled = true;

    public ClosableDrawerLayout(@NonNull Context context) {
        super(context);
    }


    public ClosableDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }


    public ClosableDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAnimationEnabled(boolean useAnimation) {
        mAnimationEnabled = useAnimation;
    }

    // This is called internally (onTouchEvent outside the control will close it), so we need it here
    @Override
    void closeDrawers(boolean peekingOnly) {
        // TODO: This fails due to #7344 - tapping on the left side partially opens the menu and blocks the UI
        // permanently. I didn't work too hard on resolving this, but it didn't seem like a simple fix.
        /*
        if (!mAnimationEnabled) {
            closeDrawer(GravityCompat.START, false);
        }
        */
        super.closeDrawers(peekingOnly);
    }
}
