/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.anki

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.ichi2.anki.OnboardingUtils.Companion.isVisited
import com.ichi2.anki.OnboardingUtils.Companion.setVisited
import com.ichi2.utils.HandlerUtils.executeFunctionUsingHandler

/**
 * Suppose a tutorial needs to be added for an activity called MyActivity.
 * Steps for doing it:
 *
 * 1. If an inner class for MyActivity exists, then use it otherwise create a new class
 * inside Onboarding and initialise an object of that class inside MyActivity. In this case,
 * call onCreate() if the tutorial needs to be displayed when the screen is opened and does not have
 * any particular time or view visibility on which it depends. If the class already exists,
 * go to step 3.
 *
 * 2. If MyActivity does not already exist, then create it by extending Onboarding and
 * then create a new enum class implementing OnboardingFlag.
 *
 * 3. Create a new method to display the tutorial.
 *
 * 4. Add the function using TutorialArgument data class to the list of tutorials in the init block
 * if it has to be invoked which the screen is opened. If the function has to be invoked at a particular
 * time, then call it from MyActivity.
 *
 * 5. For any extra condition that needs to be checked before displaying a tutorial, add it as the
 * 'mOnboardingCondition' parameter in TutorialArguments.
 */

// Contains classes for various screens. Each sub-class has methods to show onboarding tutorials.
abstract class Onboarding<Feature, ActivityType>(
    private val mContext: ActivityType,
    val mTutorials: MutableList<TutorialArguments<Feature, ActivityType>>
) where Feature : Enum<Feature>, Feature : OnboardingFlag, ActivityType : Activity {

    companion object {
        // Constants being used for onboarding preferences should not be modified.
        const val DECK_PICKER_ONBOARDING = "DeckPickerOnboarding"
        const val ABSTRACT_FLASHCARD_VIEWER_ONBOARDING = "AbstractFlashcardViewerOnboarding"
        const val REVIEWER_ONBOARDING = "ReviewerOnboarding"
        const val NOTE_EDITOR_ONBOARDING = "NoteEditorOnboarding"
        const val CARD_BROWSER_ONBOARDING = "CardBrowserOnboarding"
    }

    /**
     * Contains the logic for iterating through various tutorials of a screen and displaying the first one
     * in the list which is not visited yet and condition (if any) also holds true for it.
     *
     * @param mContext Context of the Activity
     * @param mTutorials List of tutorials for the Activity
     */
    fun onCreate() {
        mTutorials.forEach {
            // If tutorial is visited or condition is false then return from the loop.
            if (isVisited(it.mFeatureIdentifier, mContext) || it.mOnboardingCondition?.invoke() == false) {
                return@forEach
            }

            // Invoke the function to display the tutorial.
            it.mOnboardingFunction.invoke()

            // Return so that other tutorials are not displayed.
            return
        }
    }

    /**
     * Arguments required to handle the tutorial for a screen.
     *
     * @param mFeatureIdentifier Enum constant for the feature.
     * @param mOnboardingFunction Function which has to be invoked if the tutorial needs to be displayed.
     * @param mOnboardingCondition Condition to be checked before displaying the tutorial. Tutorial should
     * be displayed only if mOnboardingCondition is not null and returns true. Default value is null
     * which indicates that no condition is required.
     */
    data class TutorialArguments<Feature, ActivityType>(
        val mFeatureIdentifier: Feature,
        val mOnboardingFunction: () -> Unit,
        val mOnboardingCondition: (() -> Boolean)? = null
    )
            where Feature : Enum<Feature>, Feature : OnboardingFlag, ActivityType : Activity

    class DeckPicker(
        private val mActivityContext: com.ichi2.anki.DeckPicker,
        private val mRecyclerViewLayoutManager: LinearLayoutManager
    ) : Onboarding<DeckPicker.DeckPickerOnboardingEnum, com.ichi2.anki.DeckPicker>(mActivityContext, mutableListOf()) {

        init {
            mTutorials.add(TutorialArguments(DeckPickerOnboardingEnum.FAB, this::showTutorialForFABIfNew))
            mTutorials.add(TutorialArguments(DeckPickerOnboardingEnum.DECK_NAME, this::showTutorialForDeckIfNew, mActivityContext::hasAtLeastOneDeckBeingDisplayed))
            mTutorials.add(TutorialArguments(DeckPickerOnboardingEnum.COUNTS_LAYOUT, this::showTutorialForCountsLayoutIfNew, mActivityContext::hasAtLeastOneDeckBeingDisplayed))
        }

        private fun showTutorialForFABIfNew() {
            CustomMaterialTapTargetPromptBuilder(mActivityContext, DeckPickerOnboardingEnum.FAB)
                .createCircleWithDimmedBackground()
                .setFocalColourResource(R.color.material_blue_500)
                .setDismissedListener { onCreate() }
                .setTarget(R.id.fab_main)
                .setPrimaryText(R.string.fab_tutorial_title)
                .setSecondaryText(R.string.fab_tutorial_desc)
                .show()
        }

        private fun showTutorialForDeckIfNew() {
            CustomMaterialTapTargetPromptBuilder(mActivityContext, DeckPickerOnboardingEnum.DECK_NAME)
                .createRectangleWithDimmedBackground()
                .setDismissedListener { showTutorialForCountsLayoutIfNew() }
                .setTarget(mRecyclerViewLayoutManager.getChildAt(0)?.findViewById(R.id.deck_name_linear_layout))
                .setPrimaryText(R.string.start_studying)
                .setSecondaryText(R.string.start_studying_desc)
                .show()
        }

        private fun showTutorialForCountsLayoutIfNew() {
            CustomMaterialTapTargetPromptBuilder(mActivityContext, DeckPickerOnboardingEnum.COUNTS_LAYOUT)
                .createRectangleWithDimmedBackground()
                .setTarget(mRecyclerViewLayoutManager.getChildAt(0)?.findViewById(R.id.counts_layout))
                .setPrimaryText(R.string.menu__study_options)
                .setSecondaryText(R.string.study_options_desc)
                .show()
        }

        enum class DeckPickerOnboardingEnum(var mValue: Int) : OnboardingFlag {
            FAB(0), DECK_NAME(1), COUNTS_LAYOUT(2);

            override fun getOnboardingEnumValue(): Int {
                return mValue
            }

            override fun getFeatureConstant(): String {
                return DECK_PICKER_ONBOARDING
            }
        }
    }

    class Reviewer(private val mActivityContext: com.ichi2.anki.Reviewer) :
        Onboarding<Reviewer.ReviewerOnboardingEnum, com.ichi2.anki.Reviewer>(mActivityContext, mutableListOf()) {

        init {
            mTutorials.add(TutorialArguments(ReviewerOnboardingEnum.SHOW_ANSWER, this::onQuestionShown))
            mTutorials.add(TutorialArguments(ReviewerOnboardingEnum.FLAG, this::showTutorialForFlagIfNew))
        }

        private fun onQuestionShown() {
            CustomMaterialTapTargetPromptBuilder(mActivityContext, ReviewerOnboardingEnum.SHOW_ANSWER)
                .createRectangleWithDimmedBackground()
                .setDismissedListener { onCreate() }
                .setTarget(R.id.flip_card)
                .setPrimaryText(R.string.see_answer)
                .setSecondaryText(R.string.see_answer_desc)
                .show()
        }

        /**
         * Called when the difficulty buttons are displayed after clicking on 'Show Answer'.
         */
        fun onAnswerShown() {
            if (isVisited(ReviewerOnboardingEnum.DIFFICULTY_RATING, mActivityContext)) {
                return
            }

            CustomMaterialTapTargetPromptBuilder(mActivityContext, ReviewerOnboardingEnum.DIFFICULTY_RATING)
                .createRectangleWithDimmedBackground()
                .setTarget(R.id.ease_buttons)
                .setPrimaryText(R.string.select_difficulty)
                .setSecondaryText(R.string.select_difficulty_desc)
                .show()
        }

        private fun showTutorialForFlagIfNew() {
            // Handler is required here to show feature prompt on menu items. Reference: https://github.com/sjwall/MaterialTapTargetPrompt/issues/73#issuecomment-320681655
            executeFunctionUsingHandler {
                CustomMaterialTapTargetPromptBuilder(mActivityContext, ReviewerOnboardingEnum.FLAG)
                    .createCircle()
                    .setFocalColourResource(R.color.material_blue_500)
                    .setTarget(R.id.action_flag)
                    .setPrimaryText(R.string.menu_flag_card)
                    .setSecondaryText(R.string.flag_card_desc)
                    .show()
            }
        }

        /**
         * Show after undo button goes into enabled state
         */
        fun onUndoButtonEnabled() {
            if (isVisited(ReviewerOnboardingEnum.UNDO, mActivityContext)) {
                return
            }

            CustomMaterialTapTargetPromptBuilder(mActivityContext, ReviewerOnboardingEnum.UNDO)
                .createCircleWithDimmedBackground()
                .setFocalColourResource(R.color.material_blue_500)
                .setTarget(R.id.action_undo)
                .setPrimaryText(R.string.undo)
                .setSecondaryText(R.string.undo_desc)
                .show()
        }

        enum class ReviewerOnboardingEnum(var mValue: Int) : OnboardingFlag {
            SHOW_ANSWER(0), DIFFICULTY_RATING(1), FLAG(2), UNDO(3);

            override fun getOnboardingEnumValue(): Int {
                return mValue
            }

            override fun getFeatureConstant(): String {
                return REVIEWER_ONBOARDING
            }
        }
    }

    class NoteEditor(private val mActivityContext: com.ichi2.anki.NoteEditor) :
        Onboarding<NoteEditor.NoteEditorOnboardingEnum, com.ichi2.anki.NoteEditor>(mActivityContext, mutableListOf()) {

        init {
            mTutorials.add(TutorialArguments(NoteEditorOnboardingEnum.FRONT_BACK, this::showTutorialForFrontAndBackIfNew))
            mTutorials.add(TutorialArguments(NoteEditorOnboardingEnum.FORMATTING_TOOLS, this::showTutorialForFormattingTools))
        }

        private fun showTutorialForFrontAndBackIfNew() {
            CustomMaterialTapTargetPromptBuilder(mActivityContext, NoteEditorOnboardingEnum.FRONT_BACK)
                .createRectangleWithDimmedBackground()
                .setDismissedListener { onCreate() }
                .setTarget(R.id.CardEditorEditFieldsLayout)
                .setPrimaryText(R.string.card_contents)
                .setSecondaryText(R.string.card_contents_desc)
                .show()
        }

        private fun showTutorialForFormattingTools() {
            CustomMaterialTapTargetPromptBuilder(mActivityContext, NoteEditorOnboardingEnum.FORMATTING_TOOLS)
                .createRectangleWithDimmedBackground()
                .setTarget(R.id.editor_toolbar)
                .setPrimaryText(R.string.format_content)
                .setSecondaryText(R.string.format_content_desc)
                .show()
        }

        enum class NoteEditorOnboardingEnum(var mValue: Int) : OnboardingFlag {
            FRONT_BACK(0), FORMATTING_TOOLS(1);

            override fun getOnboardingEnumValue(): Int {
                return mValue
            }

            override fun getFeatureConstant(): String {
                return NOTE_EDITOR_ONBOARDING
            }
        }
    }

    class CardBrowser(private val mActivityContext: com.ichi2.anki.CardBrowser) :
        Onboarding<CardBrowser.CardBrowserOnboardingEnum, com.ichi2.anki.CardBrowser>(mActivityContext, mutableListOf()) {

        init {
            mTutorials.add(TutorialArguments(CardBrowserOnboardingEnum.DECK_CHANGER, this::showTutorialForDeckChangerIfNew))
            mTutorials.add(TutorialArguments(CardBrowserOnboardingEnum.CARD_PRESS_AND_HOLD, this::showTutorialForCardClickIfNew))
        }

        private fun showTutorialForDeckChangerIfNew() {
            CustomMaterialTapTargetPromptBuilder(mActivityContext, CardBrowserOnboardingEnum.DECK_CHANGER)
                .createRectangleWithDimmedBackground()
                .setFocalColourResource(R.color.material_blue_500)
                .setDismissedListener { onCreate() }
                .setTarget(R.id.toolbar_spinner)
                .setPrimaryText(R.string.deck_changer_card_browser)
                .setSecondaryText(R.string.deck_changer_card_browser_desc)
                .show()
        }

        private fun showTutorialForCardClickIfNew() {
            val cardBrowserTutorial: FrameLayout = mActivityContext.findViewById(R.id.card_browser_tutorial)
            cardBrowserTutorial.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    visibility = View.GONE
                }
            }
            setVisited(CardBrowserOnboardingEnum.CARD_PRESS_AND_HOLD, mActivityContext)
        }

        enum class CardBrowserOnboardingEnum(var mValue: Int) : OnboardingFlag {
            DECK_CHANGER(0), CARD_PRESS_AND_HOLD(1);

            override fun getOnboardingEnumValue(): Int {
                return mValue
            }

            override fun getFeatureConstant(): String {
                return CARD_BROWSER_ONBOARDING
            }
        }
    }
}
