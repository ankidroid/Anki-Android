/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

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
package com.ichi2.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;

public class TagsUtil {

    @NonNull
    public static List<String> getUpdatedTags(@NonNull List<String> previous,
                                              @NonNull List<String> selected,
                                              @NonNull List<String> indeterminate) {
        if (indeterminate.isEmpty()) {
            return selected;
        }
        List<String> updated = new ArrayList<>();
        Set<String> previousSet = new HashSet<>(previous);
        updated.addAll(selected);
        updated.addAll(indeterminate.stream().filter(previousSet::contains).collect(Collectors.toList()));
        return updated;
    }

}
