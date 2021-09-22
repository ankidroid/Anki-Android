/*
 *  Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>
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

package com.ichi2.utils;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.mockito.Mockito.*;

@RunWith(AndroidJUnit4.class)
public class MaxExecFunctionTest extends TestCase {

    private Runnable mFunction;

    @Before
    public void before(){
        mFunction = mock(Runnable.class);
    }

    @Test
    public void doNotExceedMaxExecs(){
        final MaxExecFunction m = new MaxExecFunction(3, mFunction);

        for (int i = 0; i < 50; i++) {
            m.exec();
        }

        verify(mFunction,times(3)).run();
    }

    @Test
    public void onlyOnceForAReference(){
        final Object ref = new Object();
        final MaxExecFunction m = new MaxExecFunction(3, mFunction);

        for (int i = 0; i < 50; i++) {
            m.execOnceForReference(ref);
        }

        verify(mFunction,times(1)).run();
    }


    @Test
    public void doNotExceedMaxExecsWithMultipleReferences(){
        final MaxExecFunction m = new MaxExecFunction(3, mFunction);

        for (int i = 0; i < 10; i++) {
            final Object ref = new Object();
            for (int j = 0; j < 10; j++) {
                m.execOnceForReference(ref);
            }
        }

        verify(mFunction,times(3)).run();
    }

}