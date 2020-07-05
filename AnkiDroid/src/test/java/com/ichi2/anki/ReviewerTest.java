package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.anki.AbstractFlashcardViewer.JavaScriptFunction;
import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Note;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.AbstractFlashcardViewer.EASE_4;
import static com.ichi2.anki.AbstractFlashcardViewer.RESULT_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReviewerTest extends RobolectricTest {

    @Test
    public void verifyStartupNoCollection() {
        try (ActivityScenario<NullCollectionReviewer> scenario = ActivityScenario.launch(NullCollectionReviewer.class)) {
            scenario.onActivity(reviewer -> assertNull("Collection should have been null", reviewer.getCol()));
        }
    }

    @Test
    public void verifyNormalStartup() {
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNotNull("Collection should be non-null", reviewer.getCol()));
        }
    }

    @Test
    public void exitCommandWorksAfterControlsAreBlocked() {
        ensureCollectionLoadIsSynchronous();
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> {
                reviewer.blockControls(true);
                reviewer.executeCommand(ViewerCommand.COMMAND_EXIT);
            });
            assertThat(scenario.getResult().getResultCode(), is(RESULT_DEFAULT));
        }
    }

    @Test
    public void jsTime4ShouldBeBlankIfButtonUnavailable() {
        // #6623 - easy should be blank when displaying a card with 3 buttons (after displaying a review)
        Note firstNote = addNoteUsingBasicModel("Hello", "World");
        moveToReviewQueue(firstNote.cards().get(0));

        addNoteUsingBasicModel("Hello", "World2");

        Reviewer reviewer = startReviewer();
        JavaScriptFunction javaScriptFunction = reviewer.new JavaScriptFunction();


        // The answer needs to be displayed to be able to get the time.
        displayAnswer(reviewer);
        assertThat("4 buttons should be displayed", reviewer.getAnswerButtonCount(), is(4));

        String nextTime = javaScriptFunction.ankiGetNextTime4();
        assertThat(nextTime, not(isEmptyString()));

        // Display the next answer
        reviewer.answerCard(EASE_4);

        displayAnswer(reviewer);

        assertThat("The 4th button should not be visible", reviewer.getAnswerButtonCount(), is(3));

        String learnTime = javaScriptFunction.ankiGetNextTime4();
        assertThat("If the 4th button is not visible, there should be no time4 in JS", learnTime, isEmptyString());
    }


    private void displayAnswer(Reviewer reviewer) {
        waitForAsyncTasksToComplete();
        reviewer.displayCardAnswer();
        waitForAsyncTasksToComplete();
    }


    private Reviewer startReviewer() {
        Reviewer reviewer = super.startActivityNormallyOpenCollectionWithIntent(Reviewer.class, new Intent());
        waitForAsyncTasksToComplete();
        return reviewer;
    }


    private void moveToReviewQueue(Card reviewCard) {
        reviewCard.setQueue(Consts.QUEUE_TYPE_REV);
        reviewCard.setType(Consts.CARD_TYPE_REV);
        reviewCard.setDue(0);
        reviewCard.flush();
    }
}

