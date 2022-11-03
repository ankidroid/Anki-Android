/****************************************************************************************
 * Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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
package com.ichi2.anki.lint.utils

import org.jetbrains.uast.UClass

object LintUtils {
    /**
     * A helper method to check for special classes(Time and SystemTime) where the rules related to time apis shouldn't
     * be applied.
     *
     * @param classes the list of classes to look through
     * @param allowedClasses  the list of classes where the checks should be ignored
     * @return true if this is a class where the checks should not be applied, false otherwise
     */
    fun isAnAllowedClass(classes: List<UClass>, vararg allowedClasses: String): Boolean {
        return classes.any { uClass -> uClass.name!! in allowedClasses }
    }
}
