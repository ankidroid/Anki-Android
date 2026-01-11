/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import androidx.fragment.app.Fragment
import com.ichi2.anki.R

/**
 * Helps a user build an advanced search string, explaining options listed in the
 *  [searching section of the Manual](https://docs.ankiweb.net/searching.html)
 *
 * These options are not easily representable via chips, such as:
 * * **boolean operations**: (`-cat`)
 * * **searches within types**: (`deck:a*`)
 * * **regex**: `"re:(some|another).*thing"`
 * * **properties**: `prop:ivl>=10`
 */
class AdvancedSearchFragment : Fragment(R.layout.fragment_advanced_search)
