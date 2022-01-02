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

package com.ichi2.anki.noteeditor;

import android.text.TextUtils;

import com.ichi2.utils.HashUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

import static com.ichi2.libanki.Consts.FIELD_SEPARATOR;

public class CustomToolbarButton {

    private static final int KEEP_EMPTY_ENTRIES = -1;

    private int mIndex;
    private final String mPrefix;
    private final String mSuffix;


    public CustomToolbarButton(int index, String prefix, String suffix) {
        mIndex = index;
        mPrefix = prefix;
        mSuffix = suffix;
    }

    @Nullable
    public static CustomToolbarButton fromString(String s) {
        if (s == null || s.length() == 0) {
            return null;
        }

        String[] fields = s.split(FIELD_SEPARATOR, KEEP_EMPTY_ENTRIES);

        if (fields.length != 3) {
            return null;
        }

        int index;
        try {
            index = Integer.parseInt(fields[0]);
        } catch (Exception e) {
            Timber.w(e);
            return null;
        }

        return new CustomToolbarButton(index, fields[1], fields[2]);
    }


    @NonNull
    public static ArrayList<CustomToolbarButton> fromStringSet(Set<String> hs) {
        ArrayList<CustomToolbarButton> buttons = new ArrayList<>(hs.size());

        for (String s : hs) {
            CustomToolbarButton customToolbarButton = CustomToolbarButton.fromString(s);
            if (customToolbarButton != null) {
                buttons.add(customToolbarButton);
            }
        }
        Collections.sort(buttons, (o1, o2) -> Integer.compare(o1.getIndex(), o2.getIndex()));

        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).mIndex = i;
        }

        return buttons;
    }


    public static Set<String> toStringSet(ArrayList<CustomToolbarButton> buttons) {
        HashSet<String> ret = HashUtil.HashSetInit(buttons.size());
        for (CustomToolbarButton b : buttons) {
            String[] values = new String[] { Integer.toString(b.mIndex), b.mPrefix, b.mSuffix };

            for (int i = 0; i < values.length; i++) {
                values[i] = values[i].replace(FIELD_SEPARATOR, "");
            }

            ret.add(TextUtils.join(FIELD_SEPARATOR, values));
        }
        return ret;
    }


    public Toolbar.TextFormatter toFormatter() {
        return new Toolbar.TextWrapper(mPrefix, mSuffix);
    }


    public int getIndex() {
        return mIndex;
    }
}
