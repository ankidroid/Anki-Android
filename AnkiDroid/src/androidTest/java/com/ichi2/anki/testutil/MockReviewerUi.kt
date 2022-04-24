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

package com.ichi2.anki.testutil;

import com.ichi2.anki.reviewer.ReviewerUi;

public class MockReviewerUi implements ReviewerUi {
    private boolean mDisplayingAnswer;


    public static ReviewerUi displayingAnswer() {
        MockReviewerUi mockReviewerUi = new MockReviewerUi();
        mockReviewerUi.mDisplayingAnswer = true;
        return mockReviewerUi;
    }


    @Override
    public ControlBlock getControlBlocked() {
        return null;
    }


    @Override
    public boolean isControlBlocked() {
        return false;
    }


    @Override
    public boolean isDisplayingAnswer() {
        return mDisplayingAnswer;
    }
}
