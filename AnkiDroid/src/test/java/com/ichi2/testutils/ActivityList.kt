// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.annotation.CheckResult
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CardTemplateBrowserAppearanceEditor
import com.ichi2.anki.CardTemplateBrowserAppearanceEditor.Companion.INTENT_ANSWER_FORMAT
import com.ichi2.anki.CardTemplateBrowserAppearanceEditor.Companion.INTENT_QUESTION_FORMAT
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.Info
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.IntentHandler.Companion.getReviewDeckIntent
import com.ichi2.anki.IntentHandler2
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.Reviewer
import com.ichi2.anki.SharedDecksActivity
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.notetype.ManageNotetypes
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.ui.windows.managespace.ManageSpaceActivity
import com.ichi2.anki.ui.windows.permissions.AllPermissionsExplanationActivity
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.ActivityList.ActivityLaunchParam.Companion.get
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import com.ichi2.widget.deckpicker.DeckPickerWidgetConfig
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.util.function.Function

object ActivityList {
    // TODO: This needs a test to ensure that all activities are valid with the given intents
    // Otherwise, ActivityStartupUnderBackup and other classes could be flaky
    @CheckResult
    fun allActivitiesAndIntents(): List<ActivityLaunchParam> =
        listOf(
            get(DeckPicker::class.java),
            // IntentHandler has unhandled intents
            get(IntentHandler::class.java) { ctx: Context ->
                getReviewDeckIntent(
                    ctx,
                    1L,
                )
            },
            get(IntentHandler2::class.java),
            get(StudyOptionsActivity::class.java),
            get(CardBrowser::class.java),
            get(NoteTypeFieldEditor::class.java),
            get(NoteEditorActivity::class.java),
            // Likely has unhandled intents
            get(Reviewer::class.java),
            get(PreferencesActivity::class.java),
            get(Info::class.java),
            get(CardTemplateEditor::class.java) { intentForCardTemplateEditor() },
            get(CardTemplateBrowserAppearanceEditor::class.java) { intentForCardTemplateBrowserAppearanceEditor() },
            get(SharedDecksActivity::class.java),
            get(IntroductionActivity::class.java),
            get(ManageNotetypes::class.java),
            get(ManageSpaceActivity::class.java),
            get(PermissionsActivity::class.java),
            get(AllPermissionsExplanationActivity::class.java),
            get(SingleFragmentActivity::class.java),
            get(ConfigAwareSingleFragmentActivity::class.java),
            get(CardViewerActivity::class.java),
            get(InstantNoteEditorActivity::class.java),
            get(MultimediaActivity::class.java),
            get(DeckPickerWidgetConfig::class.java),
            get(CardAnalysisWidgetConfig::class.java) { intentForWidgetConfig() },
            get(AccountActivity::class.java),
        )

    private fun intentForCardTemplateBrowserAppearanceEditor(): Intent {
        // bundle != null
        return Intent().apply {
            putExtra(INTENT_QUESTION_FORMAT, "{{Front}}")
            putExtra(INTENT_ANSWER_FORMAT, "{{FrontSide}}\n{{Back}}")
        }
    }

    private fun intentForCardTemplateEditor(): Intent = Intent().apply { putExtra("noteTypeId", 1L) }

    private fun intentForWidgetConfig(): Intent = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1) }

    class ActivityLaunchParam(
        var activity: Class<out Activity>,
        private var intentBuilder: Function<Context, Intent>,
    ) {
        val simpleName: String = activity.simpleName

        fun build(context: Context): ActivityController<out Activity> =
            Robolectric
                .buildActivity(activity, buildIntent(context))

        fun buildIntent(context: Context): Intent = intentBuilder.apply(context)

        val className: String = activity.name

        companion object {
            operator fun get(
                clazz: Class<out Activity>,
                i: Function<Context, Intent> = Function { Intent() },
            ): ActivityLaunchParam = ActivityLaunchParam(clazz, i)
        }
    }
}
