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

import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Text extends ParsedNode {
    private final String mText;


    public Text(String text) {
        this.mText = text;
    }


    @Override
    public boolean template_is_empty(@NonNull Set<String> nonempty_fields) {
        return true;
    }


    @Override
    public void render_into(Map<String, String> fields, Set<String> nonempty_fields, StringBuilder builder) {
        builder.append(mText);
    }


    @Override
    public boolean equals(@Nullable Object obj) {
        if (! (obj instanceof Text)) {
            return false;
        }
        Text other = (Text) obj;
        return other.mText.equals(mText);
    }


    @NonNull
    @Override
    public String toString() {
        return "new Text(\"" + mText.replace("\\", "\\\\").replace("\"", "\\\"")+ "\")";
    }
}
