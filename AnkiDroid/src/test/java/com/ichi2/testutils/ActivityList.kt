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
package com.ichi2.testutils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.CheckResult
import com.canhub.cropper.CropImageActivity
import com.ichi2.anki.*
import com.ichi2.anki.multimediacard.activity.LoadPronunciationActivity
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity
import com.ichi2.anki.multimediacard.activity.TranslationActivity
import com.ichi2.anki.services.ReminderService.Companion.getReviewDeckIntent
import com.ichi2.testutils.ActivityList.ActivityLaunchParam.Companion.get
import com.ichi2.utils.KotlinCleanup
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.util.*
import java.util.function.Function

@KotlinCleanup("asList() --> mutableListOf()")
object ActivityList {
    // TODO: This needs a test to ensure that all activities are valid with the given intents
    // Otherwise, ActivityStartupUnderBackup and other classes could be flaky
    @CheckResult
    fun allActivitiesAndIntents(): List<ActivityLaunchParam> {
        return Arrays.asList(
            get(DeckPicker::class.java), // IntentHandler has unhandled intents
            ActivityLaunchParam[
                IntentHandler::class.java, { ctx: Context? ->
                    getReviewDeckIntent(
                        ctx!!, 1L
                    )
                }
            ],
            get(StudyOptionsActivity::class.java),
            get(CardBrowser::class.java),
            get(ModelBrowser::class.java),
            get(ModelFieldEditor::class.java), // Likely has unhandled intents
            get(Reviewer::class.java),
            get(VideoPlayer::class.java),
            get(MyAccount::class.java),
            get(Preferences::class.java),
            get(DeckOptions::class.java),
            get(CropImageActivity::class.java),
            get(FilteredDeckOptions::class.java),
            get(DrawingActivity::class.java), // Info has unhandled intents
            get(Info::class.java), // NoteEditor has unhandled intents
            get(NoteEditor::class.java),
            get(Statistics::class.java),
            get(Previewer::class.java),
            get(CardTemplatePreviewer::class.java),
            get(MultimediaEditFieldActivity::class.java),
            get(TranslationActivity::class.java),
            get(LoadPronunciationActivity::class.java),
            get(CardInfo::class.java),
            ActivityLaunchParam[CardTemplateEditor::class.java, { intentForCardTemplateEditor() }],
            ActivityLaunchParam[CardTemplateBrowserAppearanceEditor::class.java, { intentForCardTemplateBrowserAppearanceEditor() }],
            get(SharedDecksActivity::class.java)
        )
    }

    private fun intentForCardTemplateBrowserAppearanceEditor(): Intent {
        // bundle != null
        val intent = Intent()
        intent.putExtra(CardTemplateBrowserAppearanceEditor.INTENT_QUESTION_FORMAT, "{{Front}}")
        intent.putExtra(
            CardTemplateBrowserAppearanceEditor.INTENT_ANSWER_FORMAT,
            "{{FrontSide}}\n{{Back}}"
        )
        return intent
    }

    private fun intentForCardTemplateEditor(): Intent {
        val intent = Intent()
        intent.putExtra("modelId", 1L)
        return intent
    }

    class ActivityLaunchParam(
        var activity: Class<out Activity?>,
        private var intentBuilder: Function<Context?, Intent>
    ) {
        val simpleName: String
            get() = activity.simpleName

        fun build(context: Context?): ActivityController<out Activity?> {
            return Robolectric.buildActivity(activity, intentBuilder.apply(context))
        }

        val className: String
            get() = activity.name

        companion object {
            @JvmOverloads
            operator fun get(
                clazz: Class<out Activity?>,
                i: Function<Context?, Intent> = Function { Intent() }
            ): ActivityLaunchParam {
                return ActivityLaunchParam(clazz, i)
            }
        }
    }
}
