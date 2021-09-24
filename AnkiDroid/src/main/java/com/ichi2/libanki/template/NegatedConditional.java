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

import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class NegatedConditional extends ParsedNode {

    private final String mKey;
    private final ParsedNode mChild;


    public NegatedConditional(String key, ParsedNode child) {
        this.mKey = key;
        this.mChild = child;
    }

    @Override
    public boolean template_is_empty(@NonNull Set<String> nonempty_fields) {
        return nonempty_fields.contains(mKey) || mChild.template_is_empty(nonempty_fields);
    }

    @NonNull
    @Override
    public void render_into(Map<String, String> fields, Set<String> nonempty_fields, StringBuilder builder) throws TemplateError {
        if (!nonempty_fields.contains(mKey)) {
            mChild.render_into(fields, nonempty_fields, builder);
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (! (obj instanceof NegatedConditional)) {
            return false;
        }
        NegatedConditional other = (NegatedConditional) obj;
        return other.mKey.equals(mKey) && other.mChild.equals(mChild);
    }

    @NonNull
    @Override
    public String toString() {
        return "new NegatedConditional(\"" + mKey.replace("\\", "\\\\") + "," + mChild + "\")";
    }
}
