/****************************************************************************************
 * Copyright (c) 2020 arthur milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki.utils;

import com.ichi2.libanki.Note;

import java.util.AbstractSet;
import java.util.List;

public class NoteUtils {

    /** Set the set tags of currentNote to tagsList.  We make no
     assumption on the content of tagsList, appart that its strings
     are valid tags (i.e. no spaces in it).*/
    public static void setTags(Note currentNote, List<String> tagsList) {
        String[] currentTags = currentNote.getTags().toArray(new String[0]);
        for (String tag : currentTags) {
            currentNote.delTag(tag);
        }
        if (tagsList != null) {
            AbstractSet<String> tagsSet = currentNote.getCol().getTags().canonify(tagsList);
            currentNote.addTags(tagsSet);
        }
    }
}
