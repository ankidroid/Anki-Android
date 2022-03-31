/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki.dialogs.customstudy

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.*
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuConfiguration.*
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.*
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.async.CollectionTask.RebuildCram
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Consts.DYN_PRIORITY
import com.ichi2.libanki.Deck
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONObject
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.util.*

class CustomStudyDialog(private val collection: Collection, private val customStudyListener: CustomStudyListener?) : AnalyticsDialogFragment(), TagsDialogListener {
    interface CustomStudyListener : CreateCustomStudySessionListener.Callback {
        fun onExtendStudyLimits()
        fun showDialogFragment(newFragment: DialogFragment?)
        fun dismissAllDialogFragments()
        fun startActivityForResultWithoutAnimation(intent: Intent?, requestCode: Int)
    }

    @JvmOverloads
    fun withArguments(contextMenuAttribute: ContextMenuAttribute<*>, did: Long, jumpToReviewer: Boolean = false): CustomStudyDialog {
        var args = this.arguments
        if (args == null) {
            args = Bundle()
        }
        args.putInt("id", contextMenuAttribute.value)
        args.putLong("did", did)
        args.putBoolean("jumpToReviewer", jumpToReviewer)
        this.arguments = args
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerFragmentResultReceiver(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val dialogId = requireArguments().getInt("id")
        return if (dialogId < 100) {
            // Select the specified deck
            collection.decks?.select(requireArguments().getLong("did"))
            buildContextMenu(dialogId)
        } else {
            buildInputDialog(ContextMenuOption.fromInt(dialogId))
        }
    }

    /**
     * Build a context menu for custom study
     * @param id the id type of the dialog
     */
    private fun buildContextMenu(id: Int): MaterialDialog {
        val listIds = getListIds(ContextMenuConfiguration.fromInt(id)).map { it.value }.toIntArray()
        val jumpToReviewer = requireArguments().getBoolean("jumpToReviewer")

        return MaterialDialog.Builder(requireActivity())
            .title(R.string.custom_study)
            .cancelable(true)
            .itemsIds(listIds)
            .items(*getValuesFromKeys(keyValueMap, listIds))
            .itemsCallback { _: MaterialDialog?, view: View, _: Int, _: CharSequence? ->
                when (ContextMenuOption.fromInt(view.id)) {
                    DECK_OPTIONS -> {
                        // User asked to permanently change the deck options
                        val i = Intent(requireContext(), DeckOptions::class.java)
                        i.putExtra("did", requireArguments().getLong("did"))
                        requireActivity().startActivity(i)
                    }
                    MORE_OPTIONS -> {
                        // User asked to see all custom study options
                        val d = CustomStudyDialog(collection, customStudyListener)
                            .withArguments(
                                STANDARD,
                                requireArguments().getLong("did"),
                                jumpToReviewer
                            )
                        customStudyListener?.showDialogFragment(d)
                    }
                    STUDY_TAGS -> {

                        /*
                         * This is a special Dialog for CUSTOM STUDY, where instead of only collecting a
                         * number, it is necessary to collect a list of tags. This case handles the creation
                         * of that Dialog.
                         */
                        val currentDeck = requireArguments().getLong("did")

                        val dialogFragment = TagsDialog().withArguments(
                            TagsDialog.DialogType.CUSTOM_STUDY_TAGS, ArrayList(),
                            ArrayList(collection.tags.byDeck(currentDeck, true))
                        )
                        customStudyListener?.showDialogFragment(dialogFragment)
                    }
                    else -> {

                        // User asked for a standard custom study option
                        val d = CustomStudyDialog(collection, customStudyListener)
                            .withArguments(
                                ContextMenuOption.fromInt(view.id),
                                requireArguments().getLong("did"),
                                jumpToReviewer
                            )
                        customStudyListener?.showDialogFragment(d)
                    }
                }
            }.build()
    }

    @KotlinCleanup("make this use enum instead of Int")
    fun getValuesFromKeys(map: HashMap<Int, String>, keys: IntArray): Array<String?> {
        val values = arrayOfNulls<String>(keys.size)
        for (i in keys.indices) {
            values[i] = map[keys[i]]
        }
        return values
    }

    /**
     * Build an input dialog that is used to get a parameter related to custom study from the user
     * @param contextMenuOption the option of the dialog
     */
    private fun buildInputDialog(contextMenuOption: ContextMenuOption): MaterialDialog {
        /*
            TODO: Try to change to a standard input dialog (currently the thing holding us back is having the extra
            TODO: hint line for the number of cards available, and having the pre-filled text selected by default)
        */
        // Input dialogs
        // Show input dialog for an individual custom study dialog
        @SuppressLint("InflateParams") val v = requireActivity().layoutInflater.inflate(R.layout.styled_custom_study_details_dialog, null)
        val textView1 = v.findViewById<TextView>(R.id.custom_study_details_text1)
        val textView2 = v.findViewById<TextView>(R.id.custom_study_details_text2)
        val editText = v.findViewById<EditText>(R.id.custom_study_details_edittext2)
        // Set the text
        textView1.text = text1
        textView2.text = text2
        editText.setText(defaultValue)
        // Give EditText focus and show keyboard
        editText.setSelectAllOnFocus(true)
        editText.requestFocus()
        if (contextMenuOption == STUDY_NEW || contextMenuOption == STUDY_REV) {
            editText.inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_SIGNED
        }
        // deck id
        val did = requireArguments().getLong("did")
        // Whether or not to jump straight to the reviewer
        val jumpToReviewer = requireArguments().getBoolean("jumpToReviewer")
        // Set builder parameters
        val builder = MaterialDialog.Builder(requireActivity())
            .customView(v, true)
            .positiveText(R.string.dialog_ok)
            .negativeText(R.string.dialog_cancel)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                // Get the value selected by user
                val n: Int = try {
                    editText.text.toString().toInt()
                } catch (e: Exception) {
                    Timber.w(e)
                    // This should never happen because we disable positive button for non-parsable inputs
                    return@onPositive
                }
                when (contextMenuOption) {
                    STUDY_NEW -> {
                        AnkiDroidApp.getSharedPrefs(requireActivity()).edit().putInt("extendNew", n).apply()
                        val deck = collection.decks.get(did)
                        deck.put("extendNew", n)
                        collection.decks.save(deck)
                        collection.sched.extendLimits(n, 0)
                        onLimitsExtended(jumpToReviewer)
                    }
                    STUDY_REV -> {
                        AnkiDroidApp.getSharedPrefs(requireActivity()).edit().putInt("extendRev", n).apply()
                        val deck = collection.decks.get(did)
                        deck.put("extendRev", n)
                        collection.decks.save(deck)
                        collection.sched.extendLimits(0, n)
                        onLimitsExtended(jumpToReviewer)
                    }
                    STUDY_FORGOT -> {
                        val ar = JSONArray()
                        ar.put(0, 1)
                        createCustomStudySession(
                            ar,
                            arrayOf(
                                String.format(
                                    Locale.US,
                                    "rated:%d:1", n
                                ),
                                Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM
                            ),
                            false
                        )
                    }
                    STUDY_AHEAD -> {
                        createCustomStudySession(
                            JSONArray(),
                            arrayOf(
                                String.format(
                                    Locale.US,
                                    "prop:due<=%d", n
                                ),
                                Consts.DYN_MAX_SIZE, Consts.DYN_DUE
                            ),
                            true
                        )
                    }
                    STUDY_RANDOM -> {
                        createCustomStudySession(JSONArray(), arrayOf("", n, Consts.DYN_RANDOM), true)
                    }
                    STUDY_PREVIEW -> {
                        createCustomStudySession(
                            JSONArray(),
                            arrayOf(
                                "is:new added:" +
                                    n,
                                Consts.DYN_MAX_SIZE, Consts.DYN_OLDEST
                            ),
                            false
                        )
                    }
                    STUDY_TAGS,
                    DECK_OPTIONS,
                    MORE_OPTIONS -> TODO("This branch has not been covered before")
                }
            }
            .onNegative { _: MaterialDialog?, _: DialogAction? -> customStudyListener?.dismissAllDialogFragments() }
        val dialog = builder.build()
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                try {
                    editText.text.toString().toInt()
                    dialog.getActionButton(DialogAction.POSITIVE).isEnabled = true
                } catch (e: Exception) {
                    Timber.w(e)
                    dialog.getActionButton(DialogAction.POSITIVE).isEnabled = false
                }
            }
        })

        // Show soft keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    private val keyValueMap: HashMap<Int, String>
        get() {
            val res = resources
            val keyValueMap = HashMapInit<Int, String>(10)
            keyValueMap[STANDARD.value] = res.getString(R.string.custom_study)
            keyValueMap[STUDY_NEW.value] = res.getString(R.string.custom_study_increase_new_limit)
            keyValueMap[STUDY_REV.value] = res.getString(R.string.custom_study_increase_review_limit)
            keyValueMap[STUDY_FORGOT.value] = res.getString(R.string.custom_study_review_forgotten)
            keyValueMap[STUDY_AHEAD.value] = res.getString(R.string.custom_study_review_ahead)
            keyValueMap[STUDY_RANDOM.value] = res.getString(R.string.custom_study_random_selection)
            keyValueMap[STUDY_PREVIEW.value] = res.getString(R.string.custom_study_preview_new)
            keyValueMap[STUDY_TAGS.value] = res.getString(R.string.custom_study_limit_tags)
            keyValueMap[DECK_OPTIONS.value] = res.getString(R.string.menu__deck_options)
            keyValueMap[MORE_OPTIONS.value] = res.getString(R.string.more_options)
            return keyValueMap
        }

    /**
     * Gathers the final selection of tags and type of cards,
     * Generates the search screen for the custom study deck.
     */
    override fun onSelectedTags(selectedTags: List<String>, indeterminateTags: List<String>, option: Int) {
        val sb = StringBuilder()
        when (option) {
            1 -> sb.append("is:new ")
            2 -> sb.append("is:due ")
        }
        val arr: MutableList<String?> = ArrayList(selectedTags.size)
        if (selectedTags.isNotEmpty()) {
            for (tag in selectedTags) {
                arr.add(String.format("tag:'%s'", tag))
            }
            sb.append("(").append(TextUtils.join(" or ", arr)).append(")")
        }
        createCustomStudySession(
            JSONArray(),
            arrayOf(
                sb.toString(),
                Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM
            ),
            true
        )
    }

    /**
     * Retrieve the list of ids to put in the context menu list
     * @param dialogId option to specify which tasks are shown in the list
     * @return the ids of which values to show
     */
    private fun getListIds(dialogId: ContextMenuConfiguration): List<ContextMenuOption> {
        when (dialogId) {
            STANDARD -> {
                // Standard context menu
                val dialogOptions = mutableListOf<ContextMenuOption>()
                dialogOptions.add(STUDY_NEW)
                dialogOptions.add(STUDY_REV)
                dialogOptions.add(STUDY_FORGOT)
                dialogOptions.add(STUDY_AHEAD)
                dialogOptions.add(STUDY_RANDOM)
                dialogOptions.add(STUDY_PREVIEW)
                dialogOptions.add(STUDY_TAGS)
                if (collection.sched?.totalNewForCurrentDeck() == 0) {
                    // If no new cards we wont show CUSTOM_STUDY_NEW
                    dialogOptions.remove(STUDY_NEW)
                }
                return dialogOptions.toList()
            }
            LIMITS -> // Special custom study options to show when the daily study limit has been reached
                return if (collection.sched?.newDue() != true && collection.sched?.revDue() != true) {
                    listOf(STUDY_NEW, STUDY_REV, DECK_OPTIONS, MORE_OPTIONS)
                } else {
                    if (collection.sched.newDue()) {
                        listOf(STUDY_NEW, DECK_OPTIONS, MORE_OPTIONS)
                    } else {
                        listOf(STUDY_REV, DECK_OPTIONS, MORE_OPTIONS)
                    }
                }
            EMPTY_SCHEDULE -> // Special custom study options to show when extending the daily study limits is not applicable
                return listOf(
                    STUDY_FORGOT, STUDY_AHEAD, STUDY_RANDOM,
                    STUDY_PREVIEW, STUDY_TAGS, DECK_OPTIONS
                )
        }
    }

    private val text1: String
        get() {
            val res = AnkiDroidApp.getAppResources()
            return when (ContextMenuOption.fromInt(requireArguments().getInt("id"))) {
                STUDY_NEW -> res.getString(R.string.custom_study_new_total_new, collection.sched?.totalNewForCurrentDeck())
                STUDY_REV -> res.getString(R.string.custom_study_rev_total_rev, collection.sched?.totalRevForCurrentDeck())
                else -> ""
            }
        }
    private val text2: String
        get() {
            val res = AnkiDroidApp.getAppResources()
            return when (ContextMenuOption.fromInt(requireArguments().getInt("id"))) {
                STUDY_NEW -> res.getString(R.string.custom_study_new_extend)
                STUDY_REV -> res.getString(R.string.custom_study_rev_extend)
                STUDY_FORGOT -> res.getString(R.string.custom_study_forgotten)
                STUDY_AHEAD -> res.getString(R.string.custom_study_ahead)
                STUDY_RANDOM -> res.getString(R.string.custom_study_random)
                STUDY_PREVIEW -> res.getString(R.string.custom_study_preview)
                else -> ""
            }
        }
    private val defaultValue: String
        get() {
            val prefs = AnkiDroidApp.getSharedPrefs(requireActivity())
            return when (ContextMenuOption.fromInt(requireArguments().getInt("id"))) {
                STUDY_NEW -> prefs.getInt("extendNew", 10).toString()
                STUDY_REV -> prefs.getInt("extendRev", 50).toString()
                STUDY_FORGOT -> prefs.getInt("forgottenDays", 1).toString()
                STUDY_AHEAD -> prefs.getInt("aheadDays", 1).toString()
                STUDY_RANDOM -> prefs.getInt("randomCards", 100).toString()
                STUDY_PREVIEW -> prefs.getInt("previewDays", 1).toString()
                else -> ""
            }
        }

    /**
     * Create a custom study session
     * @param delays delay options for scheduling algorithm
     * @param terms search terms
     * @param resched whether to reschedule the cards based on the answers given (or ignore them if false)
     */
    private fun createCustomStudySession(delays: JSONArray, terms: Array<Any>, resched: Boolean) {
        val dyn: Deck
        val did = requireArguments().getLong("did")

        val decks = collection.decks
        val deckToStudyName = decks?.get(did)?.getString("name")
        val customStudyDeck = resources.getString(R.string.custom_study_deck_name)
        val cur = decks?.byName(customStudyDeck)
        if (cur != null) {
            Timber.i("Found deck: '%s'", customStudyDeck)
            if (cur.isStd) {
                Timber.w("Deck: '%s' was non-dynamic", customStudyDeck)
                showThemedToast(requireContext(), getString(R.string.custom_study_deck_exists), true)
                return
            } else {
                Timber.i("Emptying dynamic deck '%s' for custom study", customStudyDeck)
                // safe to empty
                collection.sched?.emptyDyn(cur.getLong("id"))
                // reuse; don't delete as it may have children
                dyn = cur
                decks.select(cur.getLong("id"))
            }
        } else {
            Timber.i("Creating Dynamic Deck '%s' for custom study", customStudyDeck)
            dyn = try {
                decks!!.get(decks.newDyn(customStudyDeck))
            } catch (ex: DeckRenameException) {
                showThemedToast(requireActivity(), ex.getLocalizedMessage(this.resources), true)
                return
            }
        }
        if (!dyn.has("terms")) {
            // #5959 - temp code to diagnose why terms doesn't exist.
            // normally we wouldn't want to log this much, but we need to know how deep the corruption is to fix the
            // issue
            Timber.w("Invalid Dynamic Deck: %s", dyn)
            AnkiDroidApp.sendExceptionReport("Custom Study Deck had no terms", "CustomStudyDialog - createCustomStudySession")
            showThemedToast(this.context, getString(R.string.custom_study_rebuild_deck_corrupt), false)
            return
        }
        // and then set various options
        if (delays.length() > 0) {
            dyn.put("delays", delays)
        } else {
            dyn.put("delays", JSONObject.NULL)
        }
        val ar = dyn.getJSONArray("terms")
        ar.getJSONArray(0).put(0, "deck:\"" + deckToStudyName + "\" " + terms[0])
        ar.getJSONArray(0).put(1, terms[1])
        @DYN_PRIORITY val priority = terms[2] as Int
        ar.getJSONArray(0).put(2, priority)
        dyn.put("resched", resched)
        // Rebuild the filtered deck
        Timber.i("Rebuilding Custom Study Deck")
        // PERF: Should be in background
        collection.decks?.save(dyn)
        TaskManager.launchCollectionTask(RebuildCram(), createCustomStudySessionListener())

        // Hide the dialogs
        customStudyListener?.dismissAllDialogFragments()
    }

    private fun onLimitsExtended(jumpToReviewer: Boolean) {
        if (jumpToReviewer) {
            customStudyListener?.startActivityForResultWithoutAnimation(Intent(requireContext(), Reviewer::class.java), AnkiActivity.REQUEST_REVIEW)
        } else {
            customStudyListener?.onExtendStudyLimits()
        }
        customStudyListener?.dismissAllDialogFragments()
    }

    private fun createCustomStudySessionListener(): CreateCustomStudySessionListener {
        return CreateCustomStudySessionListener(customStudyListener)
    }

    /**
     * Interface that enables mixed usage of ContextMenuOptions and ContextMenuConfigurations.
     *
     * @see ContextMenuConfiguration
     * @see ContextMenuOption
     */
    interface ContextMenuAttribute<T> where T : Enum<*> {
        val value: Int
    }

    /**
     * Different context menu configurations for the custom study dialog.
     *
     * @see ContextMenuAttribute
     */
    enum class ContextMenuConfiguration(override val value: Int) : ContextMenuAttribute<ContextMenuConfiguration> {
        STANDARD(1),
        LIMITS(2),
        EMPTY_SCHEDULE(3);

        companion object {
            fun fromInt(value: Int): ContextMenuConfiguration = values().first { it.value == value }
        }
    }

    /**
     * Possible context menu options that could be shown in the custom study dialog.
     *
     * @see ContextMenuAttribute
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    enum class ContextMenuOption(override val value: Int) : ContextMenuAttribute<ContextMenuOption> {
        STUDY_NEW(100),
        STUDY_REV(101),
        STUDY_FORGOT(102),
        STUDY_AHEAD(103),
        STUDY_RANDOM(104),
        STUDY_PREVIEW(105),
        STUDY_TAGS(106),
        DECK_OPTIONS(107),
        MORE_OPTIONS(108);

        companion object {
            fun fromInt(value: Int): ContextMenuOption = values().first { it.value == value }
        }
    }
}
