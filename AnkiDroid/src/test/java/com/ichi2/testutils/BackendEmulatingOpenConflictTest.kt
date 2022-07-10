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

package com.ichi2.testutils;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.RobolectricTest;

import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class BackendEmulatingOpenConflictTest extends RobolectricTest {

    @Before
    @Override
    public void setUp() {
        super.setUp();
        BackendEmulatingOpenConflict.enable();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
        BackendEmulatingOpenConflict.disable();
    }

    @Test
    public void assumeMocksAreValid() {
        assertThrows(BackendDbLockedException.class, () -> CollectionHelper.getInstance().getCol(super.getTargetContext()));
    }

}
