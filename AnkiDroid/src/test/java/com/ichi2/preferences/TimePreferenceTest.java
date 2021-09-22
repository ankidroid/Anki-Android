/**
 * Copyright (c) 2021 Diego Rodriguez <diego.vincent.rodriguez@gmail.com>
 * 
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later   
 * version.                                                                     
 *                                                                              
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.     
 *                                                                              
 * You should have received a copy of the GNU General Public License along with 
 * this program.  If not, see <http://www.gnu.org/licenses/>.                   
 */

package com.ichi2.preferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class TimePreferenceTest {
    private final String mParsableHour;
    private final int mExpectedHour;


    public TimePreferenceTest(String parsableHour, int expectedHour) {
        this.mParsableHour = parsableHour;
        this.mExpectedHour = expectedHour;
    }


    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"00:00", 0},
                {"01:00", 1},
                {"24:00", 24}
        });
    }
    
    @Test
    public void shouldParseHours() {
        int actualHour = TimePreference.parseHours(this.mParsableHour);

        assertEquals(mExpectedHour, actualHour);
    }
}
