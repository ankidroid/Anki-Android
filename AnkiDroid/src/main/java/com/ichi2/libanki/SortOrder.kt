/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki

import net.ankiweb.rsdroid.RustCleanup

/** Helper class, libAnki uses a union
 *
 * https://github.com/david-allison-1/anki/blob/2296461136ada51437da076fda41cf735743c0e0/pylib/anki/collection.py#L417-L438
 *
 */
abstract class SortOrder {
    class NoOrdering : SortOrder()

    /** Based on config: sortType and sortBackwards */
    class UseCollectionOrdering : SortOrder()

    /** A custom SQL string placed after "order by" */
    class AfterSqlOrderBy(val customOrdering: String) : SortOrder()

    @Deprecated("Not yet usable - unhandled in Java backend")
    @RustCleanup("remove @Deprecated once Java backend is gone")
    class BuiltinSortKind(val value: String, val reverse: Boolean) : SortOrder()
}
