/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.ui;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MenuItem;

import com.ichi2.anki.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import timber.log.Timber;

public class BadgeDrawableBuilder {
    private final Resources mResources;
    private char mChar;
    private Integer mColor;


    public BadgeDrawableBuilder(@NonNull Resources resources) {
        mResources = resources;
    }


    public static void removeBadge(MenuItem menuItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        Drawable icon = menuItem.getIcon();
        if (icon instanceof BadgeDrawable) {
            BadgeDrawable bd = (BadgeDrawable) icon;
            menuItem.setIcon(bd.getCurrent());
            Timber.d("Badge removed");
        }
    }


    @NonNull
    public BadgeDrawableBuilder withText(char c) {
        this.mChar = c;
        return this;
    }


    @NonNull
    public BadgeDrawableBuilder withColor(@Nullable Integer color) {
        this.mColor = color;
        return this;
    }


    public void replaceBadge(@NonNull MenuItem menuItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        Timber.d("Adding badge");

        Drawable originalIcon = menuItem.getIcon();
        if (originalIcon instanceof BadgeDrawable) {
            BadgeDrawable bd = (BadgeDrawable) originalIcon;
            originalIcon = bd.getCurrent();
        }

        BadgeDrawable badge = new BadgeDrawable(originalIcon);
        if (mChar != '\0') {
            badge.setText(mChar);
        }
        if (mColor != null) {
            Drawable badgeDrawable = VectorDrawableCompat.create(mResources, R.drawable.badge_drawable, null);
            if (badgeDrawable == null) {
                Timber.w("Unable to find badge_drawable - not drawing badge");
                return;
            }
            Drawable mutableDrawable = badgeDrawable.mutate();
            mutableDrawable.setTint(mColor);
            badge.setBadgeDrawable(mutableDrawable);
            menuItem.setIcon(badge);
        }
    }
}
