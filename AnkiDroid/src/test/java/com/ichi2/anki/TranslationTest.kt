// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.i18n.GeneratedTranslations
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.TranslationTest.Companion.BASELINE_CASE_INSENSITIVE_DUPLICATES
import com.ichi2.anki.TranslationTest.Companion.BASELINE_DUPLICATES
import com.ichi2.testutils.BackendTranslation
import com.ichi2.testutils.XmlStringResource
import com.ichi2.testutils.getAndroidManifestStringResourceNames
import com.ichi2.testutils.getBackendNonArgStrings
import com.ichi2.testutils.getTranslatableXmlStrings
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.fail

/**
 * Ensures that translatable strings defined in our XML resource files
 * (01-core, 02-strings, etc.) do not duplicate strings already available
 * from the backend via [GeneratedTranslations]/[TR].
 */
@RunWith(AndroidJUnit4::class) // TODO: no Android dependencies; could be JvmTest
class TranslationTest : RobolectricTest() {
    @Test
    fun `CrowdIn-managed files do not reappear in this module`() {
        val staleFiles =
            File("src/main/res")
                .listFiles { dir -> dir.name == "values" || dir.name.startsWith("values-") }!!
                .flatMap { dir -> dir.listFiles()?.toList() ?: emptyList() }
                .filter { file -> file.name.matches(Regex("\\d+-.*\\.xml")) }
        if (staleFiles.isNotEmpty()) {
            fail(
                "CrowdIn-managed string files belong in common/android/src/main/res, not AnkiDroid/src/main/res:\n" +
                    staleFiles.joinToString("\n"),
            )
        }
    }

    @Test
    fun `translatable strings do not duplicate GeneratedTranslations`() =
        runTest {
            val backendStrings =
                getBackendNonArgStrings()
                    .filterNot { it.methodName in IGNORED_BACKEND_TRANSLATIONS }
            val backendByText = backendStrings.groupBy { it.text }
            val backendByTextLower = backendStrings.groupBy { it.text.lowercase() }
            val xmlStrings = getTranslatableXmlStrings()

            // strings referenced from AndroidManifest.xml cannot be converted to use the backend
            val manifestStringsLower = ANDROID_MANIFEST_STRINGS.mapTo(mutableSetOf()) { it.lowercase() }

            val exactDuplicates =
                xmlStrings
                    .filter { it.text !in BASELINE_DUPLICATES }
                    .filter { it.text.lowercase() !in manifestStringsLower }
                    .filter { it.text in backendByText }

            if (exactDuplicates.isNotEmpty()) {
                val xmlByText = xmlStrings.groupBy { it.text }
                val entries =
                    exactDuplicates
                        .map { it.text }
                        .distinct()
                        .sorted()
                        .joinToString("\n") { text ->
                            formatBaselineEntry(text, xmlByText[text]!!, backendByText[text]!!)
                        }
                fail(
                    "${exactDuplicates.size} XML string(s) duplicate a backend translation.\n" +
                        "If caused by a backend update, add to BASELINE_DUPLICATES:\n$entries\n\n" +
                        "If referenced from AndroidManifest.xml, add to ANDROID_MANIFEST_STRINGS.\n" +
                        "If manually added, use a string from `TR`.",
                )
            }

            // case-insensitive match check (excluding exact matches already baselined)
            val caseInsensitiveBaseline = BASELINE_CASE_INSENSITIVE_DUPLICATES.mapTo(mutableSetOf()) { it.lowercase() }
            val caseInsensitiveDuplicates =
                xmlStrings
                    .filter { it.text !in BASELINE_DUPLICATES }
                    .filter { it.text.lowercase() !in caseInsensitiveBaseline }
                    .filter { it.text.lowercase() !in manifestStringsLower }
                    .filter { it.text !in backendByText } // not an exact match
                    .filter { it.text.lowercase() in backendByTextLower }

            if (caseInsensitiveDuplicates.isNotEmpty()) {
                val xmlByTextLower = xmlStrings.groupBy { it.text.lowercase() }
                val entries =
                    caseInsensitiveDuplicates
                        .map { it.text }
                        .distinct()
                        .sorted()
                        .joinToString("\n") { text ->
                            formatBaselineEntry(text, xmlByTextLower[text.lowercase()]!!, backendByTextLower[text.lowercase()]!!)
                        }
                fail(
                    "${caseInsensitiveDuplicates.size} XML string(s) case-insensitively duplicate a backend translation.\n" +
                        "If caused by a backend update, add to BASELINE_CASE_INSENSITIVE_DUPLICATES:\n$entries\n\n" +
                        "If referenced from AndroidManifest.xml, add to ANDROID_MANIFEST_STRINGS.\n" +
                        "If caused by a manually added XML string, use TR instead of defining a new string resource.",
                )
            }

            // ensure baselines don't contain stale entries
            val xmlTexts = xmlStrings.mapTo(mutableSetOf()) { it.text }
            val unusedExact = BASELINE_DUPLICATES.filter { it !in xmlTexts || it !in backendByText }
            val xmlTextsLower = xmlStrings.mapTo(mutableSetOf()) { it.text.lowercase() }
            val unusedCaseInsensitive =
                BASELINE_CASE_INSENSITIVE_DUPLICATES.filter {
                    it.lowercase() !in xmlTextsLower || it.lowercase() !in backendByTextLower
                }
            val manifestXmlTextsLower =
                xmlStrings
                    .filter { it.name in getAndroidManifestStringResourceNames() }
                    .mapTo(mutableSetOf()) { it.text.lowercase() }
            val unusedManifest =
                ANDROID_MANIFEST_STRINGS.filter {
                    it.lowercase() !in manifestXmlTextsLower || it.lowercase() !in backendByTextLower
                }
            if (unusedExact.isNotEmpty() || unusedCaseInsensitive.isNotEmpty() || unusedManifest.isNotEmpty()) {
                val details =
                    buildString {
                        if (unusedExact.isNotEmpty()) {
                            appendLine("Unused BASELINE_DUPLICATES (remove these):")
                            unusedExact.sorted().forEach { appendLine("  \"$it\"") }
                        }
                        if (unusedCaseInsensitive.isNotEmpty()) {
                            appendLine("Unused BASELINE_CASE_INSENSITIVE_DUPLICATES (remove these):")
                            unusedCaseInsensitive.sorted().forEach { appendLine("  \"$it\"") }
                        }
                        if (unusedManifest.isNotEmpty()) {
                            appendLine("Unused ANDROID_MANIFEST_STRINGS (remove these):")
                            unusedManifest.sorted().forEach { appendLine("  \"$it\"") }
                        }
                    }
                fail(details.trim())
            }
        }

    companion object {
        /**
         * Formats a baseline entry as copy-pasteable Kotlin code.
         *
         * @see BASELINE_DUPLICATES
         * @see BASELINE_CASE_INSENSITIVE_DUPLICATES
         */
        private fun formatBaselineEntry(
            text: String,
            xmlResources: List<XmlStringResource>,
            trMethods: List<BackendTranslation>,
        ): String {
            val rStrings = xmlResources.map { "R.string.${it.name}" }.distinct().sorted()
            val trNames = trMethods.map { "TR.${it.methodName}()" }.distinct().sorted()

            val indent = " ".repeat("\"$text\", ".length)
            val isOneToOne = rStrings.size == 1 && trNames.size == 1

            return if (isOneToOne) {
                "\"$text\", // ${rStrings[0]} | ${trNames[0]}"
            } else {
                val lines = mutableListOf<String>()
                lines.add("\"$text\", // ${rStrings.joinToString(", ")}")
                trNames.forEach { lines.add("$indent// $it") }
                lines.joinToString("\n")
            }
        }

        /**
         * English string values which exist in both the app's XML resources and
         * [GeneratedTranslations] (exact match).
         *
         * TODO: These should be migrated to use [TR] and removed from this set.
         *
         * Do not remove this set when empty.
         *
         * If a backend update adds new translations that overlap with existing
         * XML strings, add entries here. Do not remove the R.string definitions
         * in the same PR — migration of R.string usages to TR should be done
         * separately.
         *
         * If a manually added XML string matches a backend translation, use
         * TR directly instead of adding to this baseline.
         */
        private val BASELINE_DUPLICATES =
            setOf(
                // example usages:
                // "Add",                        // S.import_message_add, S.menu_add
                // "General",                    // S.deck_conf_general, S.pref_cat_general
                //                               // TR.preferencesGeneral()
                //                               // TR.schedulingGeneral()
                "Add", // S.import_message_add, S.menu_add
                // TR.actionsAdd()
                "Add tag", // S.add_tag | TR.editingTagsAdd()
                "Advanced", // S.pref_cat_advanced | TR.deckConfigAdvancedTitle()
                "Again", // S.ease_button_again
                // TR.browsingAgainToday()
                // TR.studyingAgain()
                "All", // S.hide_system_bars_all_bars | TR.statisticsTrueRetentionAll()
                "Always", // S.sync_media_always
                // TR.preferencesAlways()
                // TR.importingUpdateAlways()
                "Answer", // S.card_side_answer | TR.browsingAnswer()
                "Back", // S.back_field_name, S.previewer_back
                // TR.notetypesBackField()
                "Cancel", // S.dialog_cancel
                // TR.actionsCancel()
                // TR.syncCancelButton()
                "Card", // S.card, S.reviewer_frame_style_card
                // TR.browsingCard()
                "Cards", // S.show_cards
                // TR.browsingCards()
                // TR.editingCards()
                // TR.notetypesCards()
                "Close", // S.close | TR.actionsClose()
                "Collapse", // S.collapse
                // TR.editingCollapse()
                // TR.browsingSidebarCollapse()
                // TR.changeNotetypeCollapse()
                "Continue", // S.dialog_continue | TR.studyingContinue()
                "Copied to clipboard", // S.about_ankidroid_successfully_copied_debug_info
                // TR.aboutCopiedToClipboard()
                // TR.errorsCopiedToClipboard()
                "Dark", // S.night_theme_dark | TR.preferencesThemeDark()
                "Delete", // S.dialog_positive_delete
                // TR.actionsDelete()
                // TR.editingImageOcclusionDelete()
                // TR.emptyCardsDeleteButton()
                "Description", // S.deck_description_field_hint
                // TR.fieldsDescription()
                // TR.schedulingDescription()
                "Discard", // S.discard | TR.actionsDiscard()
                "Due", // S.tags_dialog_option_due_cards
                // TR.decksReviewHeader()
                // TR.statisticsDueCount()
                // TR.statisticsDueDate()
                // TR.browsingSidebarDueToday()
                "Easy", // S.ease_button_easy | TR.studyingEasy()
                "Editing", // S.pref_cat_editing | TR.preferencesEditing()
                "Empty", // S.empty_cram_label | TR.studyingEmpty()
                "Error", // S.import_title_error, S.pref__etc__summary__error
                // S.pref__widget_text__error, S.vague_error
                // TR.qtMiscError()
                "Expand", // S.expand
                // TR.editingExpand()
                // TR.browsingSidebarExpand()
                // TR.changeNotetypeExpand()
                "Fields", // S.standard_fields_tab_header
                // TR.editingFields()
                // TR.notetypesFields()
                // TR.changeNotetypeFields()
                "Flags", // S.filter_by_flags | TR.browsingSidebarFlags()
                "Flip", // S.image_cropper_action_flip | TR.cardTemplatesFlip()
                "General", // S.deck_conf_general, S.pref_cat_general
                // TR.preferencesGeneral()
                // TR.schedulingGeneral()
                "Good", // S.ease_button_good | TR.studyingGood()
                "Hard", // S.ease_button_hard | TR.studyingHard()
                "Help", // S.help | TR.actionsHelp()
                "Language", // S.language | TR.preferencesLanguage()
                "Later", // S.button_backup_later | TR.schedulingUpdateLaterButton()
                "Learn More", // S.scoped_storage_learn_more | TR.schedulingUpdateMoreInfoButton()
                "Learn ahead limit", // S.learn_cutoff | TR.preferencesLearnAheadLimit()
                "Light", // S.day_theme_light | TR.preferencesThemeLight()
                "Media", // S.media
                // TR.editingMedia()
                // TR.preferencesMedia()
                "Never", // S.sync_media_never | TR.importingUpdateNever()
                "New", // S.tags_dialog_option_new_cards
                // TR.actionsNew()
                // TR.changeNotetypeNew()
                // TR.statisticsCountsNewCards()
                "Note", // S.note
                // TR.browsingNote()
                // TR.preferencesNote()
                // TR.notetypesOcclusionNote()
                "Notes", // S.show_notes | TR.browsingNotes()
                "OK", // S.dialog_ok
                // TR.customStudyOk()
                // TR.helpOk()
                "Open", // S.open | TR.profilesOpen()
                "Options", // S.error_handling_options, S.study_options
                // TR.actionsOptions()
                // TR.notetypesOptions()
                // TR.cardTemplatesPreviewSettings()
                "Preview", // S.card_editor_preview_card
                // TR.actionsPreview()
                // TR.cardTemplatesPreviewBox()
                "Question", // S.card_side_question | TR.browsingQuestion()
                "Record audio", // S.multimedia_editor_popup_audio | TR.editingRecordAudio()
                "Redo", // S.redo | TR.undoRedo()
                "Rename", // S.rename | TR.actionsRename()
                "Reposition", // S.card_editor_reposition_card, S.card_template_reposition_template
                // TR.actionsReposition()
                "Reschedule", // S.card_editor_reschedule_card | TR.browsingReschedule()
                "Reviews", // S.pref_controls_reviews_tab
                // TR.schedulingReviews()
                // TR.cardStatsReviewCount()
                // TR.deckConfigFsrsSimulatorRadioCount()
                // TR.statisticsReviewsTitle()
                "Save", // S.save
                // TR.actionsSave()
                // TR.deckConfigSaveButton()
                "Scheduling", // S.pref_cat_scheduling | TR.preferencesScheduling()
                "Search", // S.card_browser_cram_search, S.card_browser_search_hint
                // S.deck_conf_cram_search
                // TR.actionsSearch()
                // TR.statisticsRangeSearch()
                "Select", // S.select
                // TR.actionsSelect()
                // TR.customStudySelect()
                // TR.editingImageOcclusionSelectTool()
                "Show remaining card count", // S.show_progress_summ | TR.preferencesShowRemainingCardCount()
                "Study", // S.studyoptions_start | TR.decksStudy()
                "Sync", // S.button_sync, S.pref_cat_sync
                // TR.qtMiscSync()
                "Synchronization", // S.sync_title | TR.preferencesTabSynchronisation()
                "Tags", // S.card_details_tags
                // TR.editingTags()
                // TR.browsingSidebarTags()
                "Theme", // S.app_theme | TR.preferencesTheme()
                "Timebox time limit", // S.time_limit | TR.preferencesTimeboxTimeLimit()
                "Undo", // S.undo | TR.undoUndo()
            )

        /**
         * English string values which case-insensitively match a [GeneratedTranslations]
         * value but differ in casing (e.g. "Card browser" vs "Card Browser").
         *
         * Do not remove this set when empty.
         *
         * TODO: These should be migrated to use [TR] and removed from this set.
         *
         * If a backend update adds new translations that overlap with existing
         * XML strings, add entries here. Do not remove the R.string definitions
         * in the same PR — migration of R.string usages to TR should be done
         * separately.
         *
         * If a manually added XML string matches a backend translation, use
         * TR directly instead of adding to this baseline.
         */
        private val BASELINE_CASE_INSENSITIVE_DUPLICATES =
            setOf(
                // example usages:
                // "Add field",          // R.string.model_field_editor_add | TR.fieldsAddField()
                // "Check media",        // R.string.check_media
                //                       // TR.mediaCheckCheckMediaAction()
                //                       // TR.mediaCheckWindowTitle()
                "Answer buttons", // S.answer_buttons | TR.statisticsAnswerButtonsTitle()
                "Follow system", // S.theme_follow_system | TR.preferencesThemeFollowSystem()
                "Select all", // S.card_browser_select_all | TR.editingImageOcclusionSelectAll()
                "Show answer", // S.show_answer
                // TR.studyingShowAnswer()
                // TR.deckConfigQuestionActionShowAnswer()
            )

        /**
         * English string values which match a [GeneratedTranslations] value, but are referenced
         * from `AndroidManifest.xml`.
         *
         * These cannot be converted until we extract the backend resources at build time/
         *
         *
         * ept for reference, alternate framing of [getAndroidManifestStringResourceNames].
         *
         */
        private val ANDROID_MANIFEST_STRINGS =
            setOf(
                "Add note", // S.menu_add_note | TR.actionsAddNote()
                "Image Occlusion", // S.image_occlusion | TR.notetypesImageOcclusionName()
                "Manage note types", // S.model_browser_label
                // TR.browsingManageNoteTypes()
                // TR.qtMiscManageNoteTypes()
            )

        /**
         * Backend translation method names (e.g. `TR.xx()`) excluded from the
         * duplicate check as the strings are unrelated conceptually.
         *
         * Do not remove this set when empty.
         */
        private val IGNORED_BACKEND_TRANSLATIONS =
            setOf(
                "launcherOff", // "Off" - unrelated to S.full_screen_off
            )
    }
}
