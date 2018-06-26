/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2015 Frank Oltmanns <frank.oltmanns@gmail.com>                         *
 * Copyright (c) 2015 Timothy Rae <timothy.rae@gmail.com>                               *
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>                                  *
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.anki.tests;

import android.Manifest;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;

import com.ichi2.anki.CollectionHelper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;

/**
 * This test case verifies that the directory initialization works even if the app is not yet fully initialized.
 */
@RunWith(AndroidJUnit4.class)
public class CollectionTest {

    @Rule public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Test
    public void testOpenCollection() {
        Assert.assertNotNull("Collection could not be opened",
                CollectionHelper.getInstance().getCol(InstrumentationRegistry.getTargetContext()));
    }
}