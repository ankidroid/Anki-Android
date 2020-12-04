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

import android.content.res.Configuration;

import com.ichi2.testutils.FastTest;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NightModeServiceTest extends FastTest {
    private Configuration mConfig;


    @Before
    @Override
    public void before() {
        super.before();
        this.mConfig = new Configuration();
    }

    @Test
    public void defaultNightModeFollowsSystem() {
        mConfig.uiMode = Configuration.UI_MODE_NIGHT_YES;
        assertThat("Night mode should follow UI night mode", getNightMode().isNightModeEnabled(), is(true));
    }

    @Test
    public void invalidNightModeIntegrationTest() {
        boolean manualNightModeMode = true;
        NightModeService.setManualNightModeMode(manualNightModeMode);

        assertThat("Should follow system night mode by default", NightModeService.isFollowingSystemNightMode(getSharedPrefs()), is(true));

        mConfig.uiMode = Configuration.UI_MODE_NIGHT_UNDEFINED;

        NightModeService.NightMode nightMode = getNightMode();

        assertThat("Should follow system night mode has been disabled", NightModeService.isFollowingSystemNightMode(getSharedPrefs()), is(false));
        assertThat("Night mode should follow manual night mode", nightMode.isNightModeEnabled(), is(manualNightModeMode));
        assertThat("Night mode is no longer following system", nightMode.isFollowingSystem(), is(false));
        assertThat("Night mode reported a problem", nightMode.isUsingFallback(), is(true));
    }


    @Test
    public void defaultManualNightModeIsOff() {
        NightModeService.setFollowingSystemNightMode(false);
        assertThat("default night mode should be off", getNightMode().isNightModeEnabled(), is(false));
    }

    @Test
    public void setNightModeIntegrationTest() {
        NightModeService.setManualNightModeMode(true);
        assertThat("Night mode should be on if set", getNightMode().isNightModeEnabled(), is(true));
    }


    protected NightModeService.NightMode getNightMode() {
        return NightModeService.setupNightMode(mConfig);
    }
}
