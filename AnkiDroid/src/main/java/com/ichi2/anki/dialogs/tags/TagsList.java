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
package com.ichi2.anki.dialogs.tags;

import com.ichi2.utils.UniqueArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import androidx.annotation.NonNull;

/**
 * A container class that keeps track of tags and their status, handling of tags are done in a case insensitive matter
 * For example adding tags with different letter casing (eg. TAG, tag, Tag) will only add the first one.
 *
 * {@link TagsList} provides an iterator over all tags
 */
public class TagsList implements Iterable<String> {
    /**
     * A Set containing the currently selected tags
     */
    private final @NonNull
    TreeSet<String> mCurrentTags;
    /**
     * List of all available tags
     */
    private final @NonNull
    UniqueArrayList<String> mAllTags;


    /**
     * Construct a new {@link TagsList}
     *
     * @param allTags A list of all available tags
     *                any duplicates will be ignored
     * @param currentTags a list containing the currently selected tags
     *                    any duplicates will be ignored
     */
    public TagsList(@NonNull List<String> allTags, @NonNull List<String> currentTags) {
        mCurrentTags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        mCurrentTags.addAll(currentTags);
        mAllTags = UniqueArrayList.from(allTags, String.CASE_INSENSITIVE_ORDER);
        mAllTags.addAll(mCurrentTags);
    }


    /**
     * Return true if a tag is checked given its index in the list
     *
     * @param index index of the tag to check
     * @return whether the tag is checked or not
     * @throws IndexOutOfBoundsException if the index is out of range
     *                                   (<tt>index &lt; 0 || index &gt;= size()</tt>)
     */
    public boolean isChecked(int index) {
        return isChecked(mAllTags.get(index));
    }


    /**
     * Return true if a tag is checked
     *
     * @param tag the tag to check (case-insensitive)
     * @return whether the tag is checked or not
     */
    public boolean isChecked(String tag) {
        return mCurrentTags.contains(tag);
    }


    /**
     * Adds a tag to the list if it is not already present.
     *
     * @param tag  the tag to add
     * @return true if tag was added (new tag)
     */
    public boolean add(String tag) {
        return mAllTags.add(tag);
    }


    /**
     * Mark a tag as checked tag
     *
     * @param tag the tag to be checked (case-insensitive)
     * @return true if the tag changed its check status
     *         false if the tag was already checked or not in the list
     */
    public boolean check(String tag) {
        if (!mAllTags.contains(tag)) {
            return false;
        }
        return mCurrentTags.add(tag);
    }

    /**
     * Mark a tag as unchecked tag
     *
     * @param tag the tag to be checked (case-insensitive)
     * @return true if the tag changed its check status
     *         false if the tag was already unchecked or not in the list
     */
    public boolean uncheck(String tag) {
        return mCurrentTags.remove(tag);
    }


    /**
     * Toggle the status of all tags,
     * if all tags are checked, then uncheck them
     * otherwise check all tags
     *
     * @return true if this tag list changed as a result of the call
     */
    public boolean toggleAllCheckedStatuses() {
        if (mAllTags.size() == mCurrentTags.size()) {
            mCurrentTags.clear();
            return true;
        }
        return mCurrentTags.addAll(mAllTags);
    }


    /**
     * @return Number of tags in the list
     */
    public int size() {
        return mAllTags.size();
    }


    /**
     * Returns the tag at the specified position in this list.
     *
     * @param index index of the tag to return
     * @return the tag at the specified position in this list
     * @throws IndexOutOfBoundsException if the index is out of range
     *                                   (<tt>index &lt; 0 || index &gt;= size()</tt>)
     */
    @NonNull
    public String get(int index) {
        return mAllTags.get(index);
    }


    /**
     * @return true if there is no tags in the list
     */
    public boolean isEmpty() {
        return mAllTags.isEmpty();
    }


    /**
     * @return return a copy of checked tags
     */
    public List<String> copyOfCheckedTagList() {
        return new ArrayList<>(mCurrentTags);
    }


    /**
     * @return return a copy of all tags list
     */
    public List<String> copyOfAllTagList() {
        return new ArrayList<>(mAllTags);
    }


    /**
     * Sort the tag list alphabetically ignoring the case, with priority for checked tags
     */
    public void sort() {
        mAllTags.sort((lhs, rhs) -> {
            boolean lhsChecked = isChecked(lhs);
            boolean rhsChecked = isChecked(rhs);

            if (lhsChecked != rhsChecked) {
                // checked tags must appear first
                return lhsChecked ? -1 : 1;
            } else {
                return lhs.compareToIgnoreCase(rhs);
            }
        });
    }


    /**
     * @return Iterator over all tags
     */
    @NonNull
    @Override
    public Iterator<String> iterator() {
        return mAllTags.iterator();
    }
}
