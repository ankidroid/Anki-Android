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

package com.ichi2.anki.tests;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;

import net.ankiweb.rsdroid.BackendException;

import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;

public class RustTest extends InstrumentedTest {

    /** Ensure that the database isn't be locked
     * This happened before the database code was converted to use the Rust backend.
      */
    @Rule
    public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);

    @Test
    public void collectionIsVersion11AfterOpen() throws BackendException, IOException {
        // This test will be decommissioned, but before we get an upgrade strategy, we need to ensure we're not upgrading the database.
        String path = Shared.getTestFilePath(getTestContext(), "initial_version_2_12_1.anki2");
        Collection collection = Storage.Collection(getTestContext(), path);
        int ver = collection.getDb().queryScalar("select ver from col");
        MatcherAssert.assertThat(ver, is(11));
    }
}
