/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils.libanki;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.backend.exception.DeckRenameException;

public class FilteredDeckUtil {
    public static long createFilteredDeck(Collection col, String name, String search) {
        long filteredDid = 0;
        try {
            filteredDid = col.getDecks().newDyn(name);
        } catch (DeckRenameException filteredAncestor) {
            throw new RuntimeException(filteredAncestor);
        }

        DeckConfig conf = col.getDecks().confForDid(filteredDid);

        conf.getJSONArray("terms").getJSONArray(0).put(0, search);

        col.getDecks().save(conf);

        return filteredDid;
    }
}
