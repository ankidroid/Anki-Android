/***************************************************************************************
 * Copyright (c) 2023 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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

package com.ichi2.testutils

import androidx.annotation.CallSuper
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.observability.ChangeManager
import kotlinx.coroutines.test.TestDispatcher
import org.junit.Before

open class JvmTest : InMemoryAnkiTest() {
    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        ChangeManager.clearSubscribers()
    }

    override fun setupTestDispatcher(dispatcher: TestDispatcher) {
        super.setupTestDispatcher(dispatcher)
        ioDispatcher = dispatcher
    }
}
