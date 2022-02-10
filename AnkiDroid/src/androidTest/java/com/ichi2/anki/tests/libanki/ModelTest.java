/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.anki.tests.libanki;

import android.os.Build;

import com.ichi2.anki.tests.InstrumentedTest;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.ModelManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class ModelTest extends InstrumentedTest {

    private Collection mTestCol;

    @Before
    public void setUp() throws IOException {
        mTestCol = getEmptyCol();
    }

    @After
    public void tearDown() {
        mTestCol.close();
    }

    @Test
    public void bigQuery() {
        assumeTrue("This test is flaky on API29, ignoring", Build.VERSION.SDK_INT != Build.VERSION_CODES.Q);
        ModelManager models = mTestCol.getModels();
        Model model = models.all().get(0);
        final String testString = "test";
        final int size = testString.length() * 1024 * 1024;
        StringBuilder buf = new StringBuilder((int) (size * 1.01));
        // * 1.01 for padding
        for (int i = 0; i < 1024 * 1024 ; ++i ) {
            buf.append(testString);
        }
        model.put(testString, buf.toString());
        // Buf should be more than 4MB, so at least two chunks from database.
        models.flush();
        // Reload models
        mTestCol.load();
        Model newModel = models.all().get(0);
        assertEquals(newModel, model);
    }
}
