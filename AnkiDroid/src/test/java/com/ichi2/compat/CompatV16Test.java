/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.compat;

import android.os.StatFs;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompatV16Test {
    @Test
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public void getAvailableBytesDoesNotOverflowOnMultiplication() {

        StatFs statFs = mock(StatFs.class);
        when(statFs.getAvailableBlocks()).thenReturn(2000000000);
        when(statFs.getBlockSize()).thenReturn(8);
        long bytes = new CompatV16().getAvailableBytes(statFs);

        assertThat(bytes, greaterThan(0L));
        assertThat(bytes, is(8L * 2000000000L));
    }
}
