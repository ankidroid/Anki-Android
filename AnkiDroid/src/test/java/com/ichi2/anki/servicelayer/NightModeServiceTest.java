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

package com.ichi2.anki.servicelayer;

import com.ichi2.testutils.FastTest;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NightModeServiceTest extends FastTest {
    @Test
    public void defaultManualNightModeIsOff() {
        assertThat("default night mode should be off", NightModeService.getNightMode().isNightModeEnabled(), is(false));
    }

    @Test
    public void setNightModeIntegrationTest() {
        NightModeService.setManualNightModeMode(true);
        assertThat("Night mode should be on if set", NightModeService.getNightMode().isNightModeEnabled(), is(true));
    }
}
