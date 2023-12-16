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

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.LinearLayoutManager
import com.ichi2.anki.OnboardingUtils.Companion.addFeatures
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
// TODO: Move OnboardingUtils.featureConstants to a DI container rather than a mutable singleton
abstract class Onboarding<Feature>(
    private val context: Context,
    val tutorials: MutableList<TutorialArguments<Feature>>,
) where Feature : Enum<Feature>, Feature : OnboardingFlag {

    companion object {
        // Constants being used for onboarding preferences should not be modified.
        const val DECK_PICKER_ONBOARDING = "DeckPickerOnboarding"
        const val REVIEWER_ONBOARDING = "ReviewerOnboarding"
        const val NOTE_EDITOR_ONBOARDING = "NoteEditorOnboarding"
        const val CARD_BROWSER_ONBOARDING = "CardBrowserOnboarding"

        init {
            addDefaultFeatures()
        }

        @VisibleForTesting
        fun resetOnboardingForTesting() {
            OnboardingUtils.featureConstants.clear()
            addDefaultFeatures()
        }

        private fun addDefaultFeatures() {
            addFeatures(
                listOf(
                    DECK_PICKER_ONBOARDING,
                    REVIEWER_ONBOARDING,
                    NOTE_EDITOR_ONBOARDING,
                    CARD_BROWSER_ONBOARDING
                )
            )
        }
    }

    /**
     * Contains the logic for iterating through various tutorials of a screen and displaying the first one
     * in the list which is not visited yet and condition (if any) also holds true for it.
     *
     */
    fun onCreate() {
        tutorials.forEach {
            // If tutorial is visited or condition is false then return from the loop.
            if (isVisited(it.featureIdentifier, context) || it.onboardingCondition?.invoke() == false) {
                return@forEach
            }

            // Invoke the function to display the tutorial.
            it.onboardingFunction.invoke()

            // Return so that other tutorials are not displayed.
            return
        }
    }

    /**
     * Arguments required to handle the tutorial for a screen.
     *
     * @param featureIdentifier Enum constant for the feature.
     * @param onboardingFunction Function which has to be invoked if the tutorial needs to be displayed.
     * @param onboardingCondition Condition to be checked before displaying the tutorial. Tutorial should
     * be displayed only if mOnboardingCondition is not null and returns true. Default value is null
     * which indicates that no condition is required.
     */
    data class TutorialArguments<Feature>(
        val featureIdentifier: Feature,
        val onboardingFunction: () -> Unit,
        val onboardingCondition: (() -> Boolean)? = null,
    )
            where Feature : Enum<Feature>, Feature : OnboardingFlag

    class DeckPicker(
        private val activityContext: com.ichi2.anki.DeckPicker,
        private val recyclerViewLayoutManager: LinearLayoutManager,
    ) : Onboarding<DeckPicker.DeckPickerOnboardingEnum>(activityContext, mutableListOf()) {

        init {
            tutorials.add(TutorialArguments(DeckPickerOnboardingEnum.FAB, this::showTutorialForFABIfNew))
            tutorials.add(TutorialArguments(DeckPickerOnboardingEnum.DECK_NAME, this::showTutorialForDeckIfNew, activityContext::hasAtLeastOneDeckBeingDisplayed))
            tutorials.add(TutorialArguments(DeckPickerOnboardingEnum.COUNTS_LAYOUT, this::showTutorialForCountsLayoutIfNew, activityContext::hasAtLeastOneDeckBeingDisplayed))
        }

        private fun showTutorialForFABIfNew() {
            CustomMaterialTapTargetPromptBuilder(activityContext, DeckPickerOnboardingEnum.FAB)
                .createCircleWithDimmedBackground()
                .setFocalColourResource(R.color.material_blue_500)
                .setDismissedListener { onCreate() }
                .setTarget(R.id.fab_main)
                .setPrimaryText(R.string.fab_tutorial_title)
                .setSecondaryText(R.string.fab_tutorial_desc)
                .show()
        }

        private fun showTutorialForDeckIfNew() {
            CustomMaterialTapTargetPromptBuilder(activityContext, DeckPickerOnboardingEnum.DECK_NAME)
                .createRectangleWithDimmedBackground()
                .setDismissedListener { showTutorialForCountsLayoutIfNew() }
                .setTarget(recyclerViewLayoutManager.getChildAt(0)?.findViewById(R.id.deck_name_linear_layout))
                .setPrimaryText(R.string.start_studying)
                .setSecondaryText(R.string.start_studying_desc)
                .show()
        }

        private fun showTutorialForCountsLayoutIfNew() {
            CustomMaterialTapTargetPromptBuilder(activityContext, DeckPickerOnboardingEnum.COUNTS_LAYOUT)
                .createRectangleWithDimmedBackground()
                .setTarget(recyclerViewLayoutManager.getChildAt(0)?.findViewById(R.id.counts_layout))
                .setPrimaryText(R.string.menu__study_options)
                .setSecondaryText(R.string.study_options_desc)
                .show()
        }

        enum class DeckPickerOnboardingEnum(var value: Int) : OnboardingFlag {
            FAB(0), DECK_NAME(1), COUNTS_LAYOUT(2);

            override fun getOnboardingEnumValue(): Int {
                return value
            }

            override fun getFeatureConstant(): String {
                return DECK_PICKER_ONBOARDING
            }
        }
    }

    class Reviewer(private val activityContext: com.ichi2.anki.Reviewer) :
        Onboarding<Reviewer.ReviewerOnboardingEnum>(activityContext, mutableListOf()) {

        init {
            tutorials.add(TutorialArguments(ReviewerOnboardingEnum.SHOW_ANSWER, this::onQuestionShown))
            tutorials.add(TutorialArguments(ReviewerOnboardingEnum.FLAG, this::showTutorialForFlagIfNew))
        }

        private fun onQuestionShown() {
            CustomMaterialTapTargetPromptBuilder(activityContext, ReviewerOnboardingEnum.SHOW_ANSWER)
                .createRectangleWithDimmedBackground()
                .setDismissedListener { onCreate() }
                // FIXME .setTarget(R.id.flip_card)
                .setPrimaryText(R.string.see_answer)
                .setSecondaryText(R.string.see_answer_desc)
                .show()
        }

        /**
         * Called when the difficulty buttons are displayed after clicking on 'Show Answer'.
         */
        fun onAnswerShown() {
            if (isVisited(ReviewerOnboardingEnum.DIFFICULTY_RATING, activityContext)) {
                return
            }

            CustomMaterialTapTargetPromptBuilder(activityContext, ReviewerOnboardingEnum.DIFFICULTY_RATING)
                .createRectangleWithDimmedBackground()
                .setTarget(R.id.ease_buttons)
                .setPrimaryText(R.string.select_difficulty)
                .setSecondaryText(R.string.select_difficulty_desc)
                .show()
        }

        private fun showTutorialForFlagIfNew() {
            // Handler is required here to show feature prompt on menu items. Reference: https://github.com/sjwall/MaterialTapTargetPrompt/issues/73#issuecomment-320681655
            executeFunctionUsingHandler {
                CustomMaterialTapTargetPromptBuilder(activityContext, ReviewerOnboardingEnum.FLAG)
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
            if (isVisited(ReviewerOnboardingEnum.UNDO, activityContext)) {
                return
            }

            CustomMaterialTapTargetPromptBuilder(activityContext, ReviewerOnboardingEnum.UNDO)
                .createCircleWithDimmedBackground()
                .setFocalColourResource(R.color.material_blue_500)
                .setTarget(R.id.action_undo)
                .setPrimaryText(R.string.undo)
                .setSecondaryText(R.string.undo_desc)
                .show()
        }

        enum class ReviewerOnboardingEnum(var value: Int) : OnboardingFlag {
            SHOW_ANSWER(0), DIFFICULTY_RATING(1), FLAG(2), UNDO(3);

            override fun getOnboardingEnumValue(): Int {
                return value
            }

            override fun getFeatureConstant(): String {
                return REVIEWER_ONBOARDING
            }
        }
    }

    class NoteEditor(private val activityContext: com.ichi2.anki.NoteEditor) :
        Onboarding<NoteEditor.NoteEditorOnboardingEnum>(activityContext, mutableListOf()) {

        init {
            tutorials.add(TutorialArguments(NoteEditorOnboardingEnum.FRONT_BACK, this::showTutorialForFrontAndBackIfNew))
            tutorials.add(TutorialArguments(NoteEditorOnboardingEnum.FORMATTING_TOOLS, this::showTutorialForFormattingTools))
        }

        private fun showTutorialForFrontAndBackIfNew() {
            CustomMaterialTapTargetPromptBuilder(activityContext, NoteEditorOnboardingEnum.FRONT_BACK)
                .createRectangleWithDimmedBackground()
                .setDismissedListener { onCreate() }
                .setTarget(R.id.CardEditorEditFieldsLayout)
                .setPrimaryText(R.string.card_contents)
                .setSecondaryText(R.string.card_contents_desc)
                .show()
        }

        private fun showTutorialForFormattingTools() {
            CustomMaterialTapTargetPromptBuilder(activityContext, NoteEditorOnboardingEnum.FORMATTING_TOOLS)
                .createRectangleWithDimmedBackground()
                .setTarget(R.id.editor_toolbar)
                .setPrimaryText(R.string.format_content)
                .setSecondaryText(R.string.format_content_desc)
                .show()
        }

        enum class NoteEditorOnboardingEnum(var value: Int) : OnboardingFlag {
            FRONT_BACK(0), FORMATTING_TOOLS(1);

            override fun getOnboardingEnumValue(): Int {
                return value
            }

            override fun getFeatureConstant(): String {
                return NOTE_EDITOR_ONBOARDING
            }
        }
    }

    class CardBrowser(private val activityContext: com.ichi2.anki.CardBrowser) :
        Onboarding<CardBrowser.CardBrowserOnboardingEnum>(activityContext, mutableListOf()) {

        init {
            tutorials.add(TutorialArguments(CardBrowserOnboardingEnum.DECK_CHANGER, this::showTutorialForDeckChangerIfNew))
            tutorials.add(TutorialArguments(CardBrowserOnboardingEnum.CARD_PRESS_AND_HOLD, this::showTutorialForCardClickIfNew))
        }

        private fun showTutorialForDeckChangerIfNew() {
            CustomMaterialTapTargetPromptBuilder(activityContext, CardBrowserOnboardingEnum.DECK_CHANGER)
                .createRectangleWithDimmedBackground()
                .setFocalColourResource(R.color.material_blue_500)
                .setDismissedListener { onCreate() }
                .setTarget(R.id.toolbar_spinner)
                .setPrimaryText(R.string.deck_changer_card_browser)
                .setSecondaryText(R.string.deck_changer_card_browser_desc)
                .show()
        }

        private fun showTutorialForCardClickIfNew() {
            val cardBrowserTutorial: FrameLayout = activityContext.findViewById(R.id.card_browser_tutorial)
            cardBrowserTutorial.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    visibility = View.GONE
                }
            }
            setVisited(CardBrowserOnboardingEnum.CARD_PRESS_AND_HOLD, activityContext)
        }

        enum class CardBrowserOnboardingEnum(var value: Int) : OnboardingFlag {
            DECK_CHANGER(0), CARD_PRESS_AND_HOLD(1);

            override fun getOnboardingEnumValue(): Int {
                return value
            }

            override fun getFeatureConstant(): String {
                return CARD_BROWSER_ONBOARDING
            }
        }
    }
}
