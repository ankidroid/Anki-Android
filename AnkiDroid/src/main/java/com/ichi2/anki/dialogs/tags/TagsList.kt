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
package com.ichi2.anki.dialogs.tags

import com.ichi2.utils.UniqueArrayList
import java.util.*

/**
 * A container class that keeps track of tags and their status, handling of tags are done in a case insensitive matter
 * For example adding tags with different letter casing (eg. TAG, tag, Tag) will only add the first one.
 *
 * {@link TagsList} provides an iterator over all tags
 *
 * Construct a new {@link TagsList} with possibility of indeterminate tags,
 *
 * for a tag to be in indeterminate state it should be present in checkedTags and also in uncheckedTags
 *
 * @param allTags A list of all available tags
 *                any duplicates will be ignored
 * @param checkedTags a list containing the currently selected tags
 *                    any duplicates will be ignored
 * @param uncheckedTags a list containing the currently unselected tags
 *                    any duplicates will be ignored
 */
class TagsList @JvmOverloads constructor(allTags: List<String>, checkedTags: List<String>, uncheckedTags: List<String>? = null) : Iterable<String?> {
    /**
     * A Set containing the currently selected tags
     */
    private val mCheckedTags: MutableSet<String>

    /**
     * A Set containing the tags with indeterminate state
     */
    private var mIndeterminateTags: MutableSet<String>

    /**
     * List of all available tags
     */
    private val mAllTags: UniqueArrayList<String>

    /**
     * Return true if a tag is checked given its index in the list
     *
     * @param index index of the tag to check
     * @return whether the tag is checked or not
     * @throws IndexOutOfBoundsException if the index is out of range
     * (<tt>index &lt; 0 || index &gt;= size()</tt>)
     */
    fun isChecked(index: Int): Boolean {
        return isChecked(mAllTags[index])
    }

    /**
     * Return true if a tag is checked
     *
     * @param tag the tag to check (case-insensitive)
     * @return whether the tag is checked or not
     */
    fun isChecked(tag: String): Boolean {
        return mCheckedTags.contains(tag)
    }

    /**
     * Return true if a tag is indeterminate given its index in the list
     *
     * @param index index of the tag to check
     * @return whether the tag is indeterminate or not
     * @throws IndexOutOfBoundsException if the index is out of range
     * (<tt>index &lt; 0 || index &gt;= size()</tt>)
     */
    fun isIndeterminate(index: Int): Boolean {
        return isIndeterminate(mAllTags[index])
    }

    /**
     * Return true if a tag is indeterminate
     *
     * @param tag the tag to check (case-insensitive)
     * @return whether the tag is indeterminate or not
     */
    fun isIndeterminate(tag: String): Boolean {
        return mIndeterminateTags.contains(tag)
    }

    /**
     * Adds a tag to the list if it is not already present.
     *
     * @param tag  the tag to add
     * @return true if tag was added (new tag)
     */
    fun add(tag: String?): Boolean {
        return mAllTags.add(tag)
    }

    /**
     * Mark a tag as checked tag
     *
     * @param tag the tag to be checked (case-insensitive)
     * @return true if the tag changed its check status
     * false if the tag was already checked or not in the list
     */
    fun check(tag: String?): Boolean {
        if (!mAllTags.contains(tag)) {
            return false
        }
        mIndeterminateTags.remove(tag)
        return mCheckedTags.add(tag!!)
    }

    /**
     * Mark a tag as unchecked tag
     *
     * @param tag the tag to be checked (case-insensitive)
     * @return true if the tag changed its check status
     * false if the tag was already unchecked or not in the list
     */
    fun uncheck(tag: String): Boolean {
        return mIndeterminateTags.remove(tag) || mCheckedTags.remove(tag)
    }

    /**
     * Toggle the status of all tags,
     * if all tags are checked, then uncheck them
     * otherwise check all tags
     *
     * @return true if this tag list changed as a result of the call
     */
    fun toggleAllCheckedStatuses(): Boolean {
        mIndeterminateTags.clear()
        if (mAllTags.size == mCheckedTags.size) {
            mCheckedTags.clear()
            return true
        }
        return mCheckedTags.addAll(mAllTags)
    }

    /**
     * @return Number of tags in the list
     */
    fun size(): Int {
        return mAllTags.size
    }

    /**
     * Returns the tag at the specified position in this list.
     *
     * @param index index of the tag to return
     * @return the tag at the specified position in this list
     * @throws IndexOutOfBoundsException if the index is out of range
     * (<tt>index &lt; 0 || index &gt;= size()</tt>)
     */
    operator fun get(index: Int): String {
        return mAllTags[index]
    }

    /**
     * @return true if there is no tags in the list
     */
    val isEmpty: Boolean
        get() = mAllTags.isEmpty()

    /**
     * @return return a copy of checked tags
     */
    fun copyOfCheckedTagList(): List<String> {
        return ArrayList(mCheckedTags)
    }

    /**
     * @return return a copy of checked tags
     */
    fun copyOfIndeterminateTagList(): List<String> {
        return ArrayList(mIndeterminateTags)
    }

    /**
     * @return return a copy of all tags list
     */
    fun copyOfAllTagList(): List<String> {
        return ArrayList(mAllTags)
    }

    /**
     * Sort the tag list alphabetically ignoring the case, with priority for checked tags
     */
    fun sort() {
        mAllTags.sortWith { lhs: String, rhs: String ->
            val lhsChecked = isChecked(lhs) || isIndeterminate(lhs)
            val rhsChecked = isChecked(rhs) || isIndeterminate(rhs)
            if (lhsChecked != rhsChecked) {
                // checked tags must appear first
                return@sortWith if (lhsChecked) -1 else 1
            } else {
                return@sortWith lhs.compareTo(rhs, ignoreCase = true)
            }
        }
    }

    /**
     * @return Iterator over all tags
     */
    override fun iterator(): MutableIterator<String> {
        return mAllTags.iterator()
    }

    init {
        mCheckedTags = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)
        mCheckedTags.addAll(checkedTags)
        mAllTags = UniqueArrayList.from(allTags, java.lang.String.CASE_INSENSITIVE_ORDER)
        mAllTags.addAll(mCheckedTags)
        if (uncheckedTags != null) {
            mAllTags.addAll(uncheckedTags)
            mIndeterminateTags = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)
            // intersection between mCheckedTags and uncheckedTags
            mIndeterminateTags.addAll(mCheckedTags)
            val uncheckedSet: MutableSet<String> = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)
            uncheckedSet.addAll(uncheckedTags)
            mIndeterminateTags.retainAll(uncheckedSet)
            mCheckedTags.removeAll(mIndeterminateTags)
        } else {
            mIndeterminateTags = mutableSetOf()
        }
    }
}
