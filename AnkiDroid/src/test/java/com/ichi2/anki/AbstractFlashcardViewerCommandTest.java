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

package com.ichi2.anki;


import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.reviewer.CardMarker.FlagDef;
import com.ichi2.libanki.Card;

import org.junit.Test;

import static com.ichi2.anki.reviewer.CardMarker.FLAG_BLUE;
import static com.ichi2.anki.reviewer.CardMarker.FLAG_GREEN;
import static com.ichi2.anki.reviewer.CardMarker.FLAG_NONE;
import static com.ichi2.anki.reviewer.CardMarker.FLAG_ORANGE;
import static com.ichi2.anki.reviewer.CardMarker.FLAG_RED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractFlashcardViewerCommandTest {

    @Test
    public void doubleTapSetsNone() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED);
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED);

        assertThat(viewer.getLastFlag(), is(FLAG_NONE));
    }

    @Test
    public void noneDoesNothing() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_UNSET_FLAG);

        assertThat(viewer.getLastFlag(), is(FLAG_NONE));
    }

    @Test
    public void doubleNoneDoesNothing() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_UNSET_FLAG);
        viewer.executeCommand(ViewerCommand.COMMAND_UNSET_FLAG);

        assertThat(viewer.getLastFlag(), is(FLAG_NONE));
    }

    @Test
    public void flagCanBeChanged() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED);
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE);

        assertThat(viewer.getLastFlag(), is(FLAG_BLUE));
    }

    @Test
    public void unsetUnsets() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED);
        viewer.executeCommand(ViewerCommand.COMMAND_UNSET_FLAG);

        assertThat(viewer.getLastFlag(), is(FLAG_NONE));
    }

    @Test
    public void tapRedFlagSetsRed() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED);

        assertThat(viewer.getLastFlag(), is(FLAG_RED));
    }

    @Test
    public void tapOrangeFlagSetsOrange() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_ORANGE);

        assertThat(viewer.getLastFlag(), is(FLAG_ORANGE));
    }

    @Test
    public void tapGreenFlagSesGreen() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_GREEN);

        assertThat(viewer.getLastFlag(), is(FLAG_GREEN));
    }

    @Test
    public void tapBlueFlagSetsBlue() {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE);

        assertThat(viewer.getLastFlag(), is(FLAG_BLUE));
    }

    @Test
    public void doubleTapRedUnsets() {
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_RED);
    }

    @Test
    public void doubleTapOrangeUnsets() {
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_ORANGE);
    }

    @Test
    public void doubleTapGreenUnsets() {
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_GREEN);
    }

    @Test
    public void doubleTapBlueUnsets() {
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE);
    }

    private void testDoubleTapUnsets(int command) {
        CommandTestCardViewer viewer = getViewer();

        viewer.executeCommand(command);
        viewer.executeCommand(command);

        assertThat(viewer.getLastFlag(), is(FLAG_NONE));
    }


    private CommandTestCardViewer getViewer() {
        return new CommandTestCardViewer(cardWith(FLAG_NONE));
    }


    private Card cardWith(@FlagDef int flag) {
        Card c = mock(Card.class);
        int[] flags = new int[] { flag };
        when(c.userFlag()).then((invocation) -> flags[0]);
        doAnswer(invocation -> {
            flags[0] = invocation.getArgument(0);
            return null;
        }).when(c).setUserFlag(anyInt());
        return c;
    }

    private static class CommandTestCardViewer extends AbstractFlashcardViewer {

        private int mFlag;


        public CommandTestCardViewer(Card currentCard) {
            mCurrentCard = currentCard;
        }
        @Override
        protected void setTitle() {
            //Intentionally blank
        }


        @Override
        protected void performReload() {
            // intentionally blank
        }


        @Override
        public ControlBlock getControlBlocked() {
            return ControlBlock.UNBLOCKED;
        }

        @Override
        public boolean isControlBlocked() {
            return getControlBlocked() != ControlBlock.UNBLOCKED;
        }

        @Override
        protected void onFlag(Card card, @FlagDef int flag) {
            this.mFlag = flag;
            mCurrentCard.setUserFlag(flag);
        }

        public int getLastFlag() {
            return mFlag;
        }
    }
}
