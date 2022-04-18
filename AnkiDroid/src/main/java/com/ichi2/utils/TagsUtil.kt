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

package com.ichi2.utils

import java.util.stream.Collectors

object TagsUtil {
    @JvmStatic
    fun getUpdatedTags(
        previous: List<String?>,
        selected: List<String?>,
        indeterminate: List<String?>
    ): List<String?> {
        if (indeterminate.isEmpty()) {
            return selected
        }
        val updated: MutableList<String?> = ArrayList()
        val previousSet: Set<String?> = HashSet(previous)
        updated.addAll(selected)
        updated.addAll(indeterminate.stream().filter { o: String? -> previousSet.contains(o) }.collect(Collectors.toList()))
        return updated
    }

    /**
     * Utility method that decomposes a hierarchy tag into several parts.
     */
    @JvmStatic
    fun getTagParts(tag: String): List<String> {
        val res = ArrayList<String>()
        var lastColons = -2
        while (lastColons + 2 <= tag.length) {
            var nextColons = tag.indexOf("::", lastColons + 2)
            if (nextColons == -1) {
                nextColons = tag.length
            }
            // same as the way Anki Desktop deals with an empty tag subpart
            if (lastColons + 2 == nextColons) {
                res.add("blank")
            } else {
                res.add(tag.substring(lastColons + 2, nextColons))
            }
            lastColons = nextColons
        }
        return res
    }

    /**
     * Utility that uniforms a hierarchy tag.
     */
    @JvmStatic
    fun getUniformedTag(tag: String): String {
        val tagParts = getTagParts(tag)
        val sb = StringBuilder()
        for (i in tagParts.indices) {
            if (i > 0) {
                sb.append("::")
            }
            sb.append(tagParts[i])
        }
        return sb.toString()
    }

    /**
     * Utility method that gets the root part of a tag.
     */
    @JvmStatic
    fun getTagRoot(tag: String): String {
        var firstColons = tag.indexOf("::")
        if (firstColons == -1) {
            firstColons = tag.length
        }
        return tag.substring(0, firstColons)
    }

    /**
     * Utility method that gets the ancestors of a tag.
     */
    @JvmStatic
    fun getTagAncestors(tag: String): List<String> {
        val res = java.util.ArrayList<String>()
        val parts = getTagParts(tag)
        val sb = StringBuilder()
        for (i in 0 until parts.size - 1) {
            if (i > 0) {
                sb.append("::")
            }
            sb.append(parts[i])
            res.add(sb.toString())
        }
        return res
    }

    /**
     * Check if a tag is a hierarchy tag
     */
    @JvmStatic
    fun isTagHierarchy(tag: String): Boolean {
        return tag.indexOf("::") != -1
    }

    /**
     * Compare two tags with hierarchy comparison
     * Used to sort all tags firstly in DFN order, secondly in dictionary order
     */
    @JvmStatic
    fun compareTag(lhs: String, rhs: String): Int {
        var lhsLast = -2
        var rhsLast = -2
        while (lhsLast + 2 < lhs.length && rhsLast + 2 < rhs.length) {
            var lhsNext = lhs.indexOf("::", lhsLast + 2)
            if (lhsNext == -1) {
                lhsNext = lhs.length
            }
            var rhsNext = rhs.indexOf("::", rhsLast + 2)
            if (rhsNext == -1) {
                rhsNext = rhs.length
            }
            val cmp = lhs.substring(lhsLast + 2, lhsNext).compareTo(rhs.substring(rhsLast + 2, rhsNext), true)
            if (cmp != 0) {
                return cmp
            }
            lhsLast = lhsNext
            rhsLast = rhsNext
        }
        if (lhsLast == lhs.length && rhsLast == rhs.length) {
            return 0
        }
        return if (lhsLast < lhs.length) 1 else -1
    }
}
