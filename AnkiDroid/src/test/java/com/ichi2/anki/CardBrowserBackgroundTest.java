package com.ichi2.anki;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(AndroidJUnit4.class)
public class CardBrowserBackgroundTest extends RobolectricBackgroundTest {
    private final CardBrowserTestDelegate mDelegate = new CardBrowserTestDelegate(this);



    @Test
    public void selectAllIsNotVisibleOnceCalled() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        mDelegate.selectMenuItem(browser, R.id.action_select_all);
        advanceRobolectricLooperWithSleep();
        assertThat(browser.isShowingSelectAll(), is(false));
    }

    @Test
    public void selectNoneIsVisibleOnceSelectAllCalled() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        mDelegate.selectMenuItem(browser, R.id.action_select_all);
        advanceRobolectricLooperWithSleep();
        assertThat(browser.isShowingSelectNone(), is(true));
    }

    @Test
    public void selectNoneIsVisibleWhenSelectingOne() {
        CardBrowser browser = mDelegate.getBrowserWithMultipleNotes();
        advanceRobolectricLooperWithSleep();
        mDelegate.selectOneOfManyCards(browser);
        advanceRobolectricLooperWithSleep();
        assertThat(browser.isShowingSelectNone(), is(true));
    }
}
