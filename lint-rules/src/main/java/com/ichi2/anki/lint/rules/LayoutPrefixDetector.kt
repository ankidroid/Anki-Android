/*
 * Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.google.common.annotations.VisibleForTesting
import org.w3c.dom.Document

/**
 * Lint rule to enforce that layouts used in the app follow our conventions related to names, ex:
 *  layout used by a Fragment => layout name has "fragment_" prefix
 */
class LayoutPrefixDetector : LayoutDetector() {
    override fun visitDocument(
        context: XmlContext,
        document: Document,
    ) {
        val layoutFileName = context.file.name

        // TODO: decide on this to enforce or fix
        // undecided(?): "item_" prefix for layouts used by items in an adapter => item_locale.xml
        if (layoutFileName.startsWith("item_")) return

        // TODO: decide on this to enforce or fix
        // undecided(?): "include_" prefix for layouts that will be included in other layouts => include_toolbar.xml
        if (layoutFileName.startsWith("include_")) return

        // TODO: decide on this to enforce or fix
        // undecided(?): "widget_" prefix for layouts that will be used by widgets => widget_card_analysis.xml
        if (layoutFileName.startsWith("widget_")) return

        // TODO: fix these and remove this check
        if (layoutFileName in TEMPORARILY_IGNORED) return

        if (!ENFORCED_PREFIXES.any { prefix -> layoutFileName.startsWith(prefix) }) {
            context.report(
                ISSUE,
                context.getNameLocation(document),
                "Layout doesn't follow naming convention: ${context.file.name} " +
                    "See https://github.com/ankidroid/Anki-Android/wiki/Code-style#files-in-the-resourceslayout-folders-should-use-the-following-prefixes",
            )
        }
    }

    companion object {
        @VisibleForTesting
        const val ID = "LayoutPrefixDetector"

        @VisibleForTesting
        const val DESCRIPTION = "Layout filename doesn't follow naming conventions"
        private const val EXPLANATION =
            "Layouts filenames need to be prefixed based on where they are used, " +
                "see https://github.com/ankidroid/Anki-Android/wiki/Code-style#files-in-the-resourceslayout-folders-should-use-the-following-prefixes"
        private val IMPLEMENTATION =
            Implementation(
                LayoutPrefixDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
            )
        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Category.CORRECTNESS,
                6,
                Severity.ERROR,
                IMPLEMENTATION,
            )

        val ENFORCED_PREFIXES =
            listOf("activity_", "fragment_", "dialog_", "view_")

        /**
         * TODO Go over the entries in the list and fix. Some of the files require further
         *  discussions on names conventions.
         */
        val TEMPORARILY_IGNORED =
            listOf(
                "about_layout.xml",
                "add_edit_reminder_dialog.xml",
                "alert_dialog_checkbox.xml",
                "alert_dialog_title_with_help.xml",
                "anki_progress.xml",
                "bottomsheet_multimedia.xml",
                "browser_column_cell.xml",
                "browser_column_heading.xml",
                "browser_columns_selection.xml",
                "browser_columns_selection_entry.xml",
                "browser_columns_selection_heading.xml",
                "button_color_brush.xml",
                "card_browser_appearance.xml",
                "card_browser_fragment.xml",
                "card_browser_item_my_searches_dialog.xml",
                "card_browser_searchview_fragment.xml",
                "card_item_browser.xml",
                "card_template_editor.xml",
                "card_template_editor_item.xml",
                "card_template_editor_main.xml",
                "card_template_editor_top.xml",
                "change_note_type_dialog.xml",
                "check_pronunciation_fragment.xml",
                "colorpicker_flag_bubble.xml",
                "control_preference.xml",
                "control_preference_list_item.xml",
                "controls_tab_layout.xml",
                "deck_item.xml",
                "deck_picker.xml",
                "deck_picker_dialog_list_item.xml",
                "drawing_fragment.xml",
                "edit_flag_item.xml",
                "extended_category.xml",
                "feedback.xml",
                "floating_add_button.xml",
                "grade_now_list_item.xml",
                "image_occlusion.xml",
                "info.xml",
                "instant_editor_field_layout.xml",
                "introduction_activity.xml",
                "introduction_layout.xml",
                "locale_dialog_fragment_textview.xml",
                "locale_selection_dialog.xml",
                "material_dialog_list_item.xml",
                "multiline_spinner_item.xml",
                "my_account.xml",
                "my_account_logged_in.xml",
                "navdrawer_header.xml",
                "navigation_drawer.xml",
                "navigation_drawer_layout.xml",
                "navigation_drawer_layout_fullscreen.xml",
                "note_editor_fragment.xml",
                "note_editor_toolbar.xml",
                "note_editor_toolbar_add_custom_item.xml",
                "note_editor_toolbar_edit_custom_item.xml",
                "note_type_field_editor.xml",
                "notifications_permission.xml",
                "page_fragment.xml",
                "permissions_activity.xml",
                "permissions_bottom_sheet.xml",
                "popup_brush_options.xml",
                "popup_eraser_options.xml",
                "preference_material_switch_widget.xml",
                "preference_slider.xml",
                "preference_widget_switch_with_separator.xml",
                "preference_widget_text.xml",
                "preferences.xml",
                "preferences_reviewer_menu.xml",
                "previewer.xml",
                "progress_bar.xml",
                "rename_flag_layout.xml",
                "resizing_divider_internal.xml",
                "reviewer.xml",
                "reviewer2.xml",
                "reviewer_answer_buttons.xml",
                "reviewer_flashcard.xml",
                "reviewer_flashcard_fullscreen.xml",
                "reviewer_flashcard_fullscreen_noanswers.xml",
                "reviewer_fullscreen.xml",
                "reviewer_fullscreen_noanswers.xml",
                "reviewer_menu_display_type.xml",
                "reviewer_menu_item.xml",
                "reviewer_mic_tool_bar.xml",
                "reviewer_topbar.xml",
                "reviewer_whiteboard_editor.xml",
                "schedule_reminders_list_item.xml",
                "set_due_date_range.xml",
                "set_due_date_single.xml",
                "single_fragment_activity.xml",
                "spinner_dropdown_item_with_radio.xml",
                "statistics.xml",
                "studyoptions.xml",
                "studyoptions_fragment.xml",
                "sync_progress_layout.xml",
                "tab_layout_icon_on_end.xml",
                "tags_dialog_title.xml",
                "tags_item_list_dialog.xml",
                "template_previewer_container.xml",
                "toolbar.xml",
            )
    }
}
