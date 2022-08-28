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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public class ParsedNodes extends ParsedNode {
    private final List<ParsedNode> mChildren;

    @VisibleForTesting
    public ParsedNodes(List<ParsedNode> nodes) {
        this.mChildren = nodes;
    }

    // Only used for testing
    @VisibleForTesting
    public ParsedNodes(ParsedNode... nodes) {
        this.mChildren = new ArrayList(Arrays.asList(nodes));
    }


    /**
     * @param nodes A list of nodes to put in a tree
     * @return The list of node, as compactly as possible.
     */
    public static @NonNull ParsedNode create(List<ParsedNode> nodes) {
        if (nodes.isEmpty()) {
            return new EmptyNode();
        } else if (nodes.size() == 1) {
            return nodes.get(0);
        } else {
            return new ParsedNodes(nodes);
        }
    }


    @Override
    public boolean template_is_empty(@NonNull Set<String> nonempty_fields) {
        for (ParsedNode child : mChildren) {
            if (!child.template_is_empty(nonempty_fields)) {
                return false;
            }
        }
        return true;
    }


    @NonNull
    public void render_into(Map<String, String> fields, Set<String> nonempty_fields, StringBuilder builder) throws TemplateError {
        for (ParsedNode child: mChildren) {
            child.render_into(fields, nonempty_fields, builder);
        }
    }


    @Override
    public boolean equals(@Nullable Object obj) {
        if (! (obj instanceof ParsedNodes)) {
            return false;
        }
        ParsedNodes other = (ParsedNodes) obj;
        return mChildren.equals(other.mChildren);
    }

    @NonNull
    @Override
    public String toString() {
        String t = "new ParsedNodes(Arrays.asList(";
        for (ParsedNode child: mChildren) {
            t += child;
        }
        return  t + "))";
    }
}
