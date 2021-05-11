/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki.template;

import android.content.res.Resources;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public class Replacement extends ParsedNode {
    /**
     * The name of the field to show
     */
    private final String mKey;
    /**
     * List of filter to apply (from right to left)
     */
    private final List<String> mFilters;
    /**
     * The entire content between {{ and }}
     */
    private final String mTag;

    public Replacement(String key, List<String> filters, String tag) {
        mKey = key;
        mFilters = filters;
        mTag = tag;
    }

    // Only used for test
    @VisibleForTesting
    public Replacement(String key, String... filters) {
        this(key, Arrays.asList(filters), "");
    }


    @Override
    public boolean template_is_empty(@NonNull Set<String> nonempty_fields) {
        return !nonempty_fields.contains(mKey);
    }

    private static String runHint(String txt, String tag) {
        if (txt.trim().length() == 0) {
            return "";
        }
        Resources res = AnkiDroidApp.getAppResources();
        // random id
        String domid = "hint" + txt.hashCode();
        return "<a class=hint href=\"#\" onclick=\"this.style.display='none';document.getElementById('" +
                domid + "').style.display='block';_relinquishFocus();return false;\">" +
                res.getString(R.string.show_hint, tag) + "</a><div id=\"" +
                domid + "\" class=hint style=\"display: none\">" + txt + "</div>";
    }


    @NonNull
    @Override
    public void render_into(Map<String, String> fields, Set<String> nonempty_fields, StringBuilder builder) throws TemplateError.FieldNotFound {
        String txt = fields.get(mKey);
        if (txt == null) {
            if (mKey.trim().isEmpty() && !mFilters.isEmpty()) {
                txt = "";
            } else {
                throw new TemplateError.FieldNotFound(mFilters, mKey);
            }
        }
        for (String filter: mFilters) {
            txt = TemplateFilters.apply_filter(txt, filter, mKey, mTag);
        }
        builder.append(txt);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (! (obj instanceof Replacement)) {
            return false;
        }
        Replacement other = (Replacement) obj;
        return other.mKey.equals(mKey) && other.mFilters.equals(mFilters);
    }

    @NonNull
    @Override
    public String toString() {
        return "new Replacement(\"" + mKey.replace("\\", "\\\\")+ ", " + mFilters + "\")";
    }
}
