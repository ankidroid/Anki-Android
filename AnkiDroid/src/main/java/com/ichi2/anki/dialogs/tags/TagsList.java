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

import com.ichi2.utils.TagsUtil;
import com.ichi2.utils.UniqueArrayList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    @NonNull
    private final Set<String> mCheckedTags;
    /**
     * A Set containing the tags with indeterminate state
     */
    @NonNull
    private final Set<String> mIndeterminateTags;
    /**
     * List of all available tags
     */
    @NonNull
    private final UniqueArrayList<String> mAllTags;


    /**
     * Construct a new {@link TagsList}
     *
     * @param allTags A list of all available tags
     *                any duplicates will be ignored
     * @param checkedTags a list containing the currently selected tags
     *                    any duplicates will be ignored
     */
    public TagsList(@NonNull List<String> allTags, @NonNull List<String> checkedTags) {
        this(allTags, checkedTags, null);
    }

    /**
     * Construct a new {@link TagsList} with possibility of indeterminate tags,
     *
     * for a tag to be in indeterminate state it should be present in checkedTags and also in uncheckedTags,
     *
     * hierarchical tags will have their ancestors added temporarily
     *
     * @param allTags A list of all available tags
     *                any duplicates will be ignored
     * @param checkedTags a list containing the currently selected tags
     *                    any duplicates will be ignored
     * @param uncheckedTags a list containing the currently unselected tags
     *                    any duplicates will be ignored
     */
    public TagsList(@NonNull List<String> allTags, @NonNull List<String> checkedTags, @Nullable List<String> uncheckedTags) {
        mCheckedTags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        mCheckedTags.addAll(checkedTags);
        mAllTags = UniqueArrayList.from(allTags, String.CASE_INSENSITIVE_ORDER);
        mAllTags.addAll(mCheckedTags);
        mIndeterminateTags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (uncheckedTags != null) {
            mAllTags.addAll(uncheckedTags);
            // intersection between mCheckedTags and uncheckedTags
            mIndeterminateTags.addAll(mCheckedTags);
            Set<String> uncheckedSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            uncheckedSet.addAll(uncheckedTags);
            mIndeterminateTags.retainAll(uncheckedSet);
            mCheckedTags.removeAll(mIndeterminateTags);
        }
        prepareTagHierarchy();
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
        return mCheckedTags.contains(tag);
    }


    /**
     * Return true if a tag is indeterminate given its index in the list
     *
     * @param index index of the tag to check
     * @return whether the tag is indeterminate or not
     * @throws IndexOutOfBoundsException if the index is out of range
     *                                   (<tt>index &lt; 0 || index &gt;= size()</tt>)
     */
    public boolean isIndeterminate(int index) {
        return isIndeterminate(mAllTags.get(index));
    }


    /**
     * Return true if a tag is indeterminate
     *
     * @param tag the tag to check (case-insensitive)
     * @return whether the tag is indeterminate or not
     */
    public boolean isIndeterminate(String tag) {
        return mIndeterminateTags.contains(tag);
    }


    /**
     * Adds a tag to the list if it is not already present.
     * If the tag is hierarchical, its ancestors will also be added temporarily.
     *
     * @param tag the tag to add
     * @return true if tag was added (new tag)
     */
    public boolean add(String tag) {
        if (!mAllTags.add(tag)) {
            return false;
        }
        addAncestors(tag);
        return true;
    }


    /**
     * Mark a tag as checked tag.
     * Optionally mark ancestors as indeterminate
     *
     * @param tag the tag to be checked (case-insensitive)
     * @param processAncestors whether mark ancestors as indeterminate or not
     * @return true if the tag changed its check status
     *         false if the tag was already checked or not in the list
     */
    public boolean check(String tag, boolean processAncestors) {
        if (!mAllTags.contains(tag)) {
            return false;
        }
        mIndeterminateTags.remove(tag);
        if (!mCheckedTags.add(tag)) {
            return false;
        }
        if (processAncestors) {
            markAncestorsIndeterminate(tag);
        }
        return true;
    }

    /**
     * Mark a tag as checked tag.
     * @see #check(String, boolean)
     */
    public boolean check(String tag) {
        return check(tag, true);
    }

    /**
     * Mark a tag as unchecked tag
     *
     * @param tag the tag to be checked (case-insensitive)
     * @return true if the tag changed its check status
     *         false if the tag was already unchecked or not in the list
     */
    public boolean uncheck(String tag) {
        return mIndeterminateTags.remove(tag) || mCheckedTags.remove(tag);
    }


    /**
     * Mark a tag as indeterminate tag
     *
     * @param tag the tag to be turned into indeterminate (case-insensitive)
     * @return true if the tag changes into indeterminate
     *         false if the tag was already indeterminate, or not in the list
     */
    public boolean setIndeterminate(String tag) {
        if (!mAllTags.contains(tag)) {
            return false;
        }
        mCheckedTags.remove(tag);
        return mIndeterminateTags.add(tag);
    }


    /**
     * Toggle the status of all tags,
     * if all tags are checked, then uncheck them
     * otherwise check all tags
     *
     * @return true if this tag list changed as a result of the call
     */
    public boolean toggleAllCheckedStatuses() {
        mIndeterminateTags.clear();
        if (mAllTags.size() == mCheckedTags.size()) {
            mCheckedTags.clear();
            return true;
        }
        return mCheckedTags.addAll(mAllTags);
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
        return new ArrayList<>(mCheckedTags);
    }


    /**
     * @return return a copy of checked tags
     */
    public List<String> copyOfIndeterminateTagList() {
        return new ArrayList<>(mIndeterminateTags);
    }


    /**
     * @return return a copy of all tags list
     */
    public List<String> copyOfAllTagList() {
        return new ArrayList<>(mAllTags);
    }


    /**
     * Initialize the tag hierarchy.
     */
    private void prepareTagHierarchy() {
        List<String> allTags = copyOfAllTagList();
        for (String tag : allTags) {
            addAncestors(tag);
        }
        for (String tag : mCheckedTags) {
            markAncestorsIndeterminate(tag);
        }
    }


    /**
     * Add ancestors of the tag into the set of all tags.
     *
     * @param tag The tag whose ancestors will be added.
     */
    private void addAncestors(String tag) {
        mAllTags.addAll(TagsUtil.getTagAncestors(tag));
    }


    /**
     * Mark ancestors of the tag as indeterminate (if not a checked tag).
     *
     * @param tag The tag whose ancestors will be marked as indeterminate if they are not checked.
     */
    private void markAncestorsIndeterminate(String tag) {
        if (!mAllTags.contains(tag)) {
            return;
        }
        TagsUtil.getTagAncestors(tag)
                .stream().filter(s -> !isChecked(s))
                .forEach(this::setIndeterminate);
    }


    /**
     * Sort the tag list alphabetically ignoring the case, with priority for checked tags
     * A tag priors to another one if its root tag is checked or indeterminate while the other one's is not
     */
    public void sort() {
        mAllTags.sort((lhs, rhs) -> {
            String lhsRoot = TagsUtil.getTagRoot(lhs);
            String rhsRoot = TagsUtil.getTagRoot(rhs);
            boolean lhsChecked = isChecked(lhsRoot) || isIndeterminate(lhsRoot);
            boolean rhsChecked = isChecked(rhsRoot) || isIndeterminate(rhsRoot);

            if (lhsChecked != rhsChecked) {
                // checked tags must appear first
                return lhsChecked ? -1 : 1;
            } else {
                return TagsUtil.compareTag(lhs, rhs);
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
