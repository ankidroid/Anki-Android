/****************************************************************************************
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
 * this program. If not, see <http://www.gnu.org/licenses/>.                            *
 ****************************************************************************************/
package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.constraintlayout.widget.Group
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.description
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Decks
import com.ichi2.libanki.Utils
import com.ichi2.utils.HtmlUtils.convertNewlinesToHtml
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import timber.log.Timber

class StudyOptionsFragment :
    Fragment(),
    ChangeManager.Subscriber,
    MenuProvider {
    /**
     * Preferences
     */
    private var currentContentView = CONTENT_STUDY_OPTIONS

    /** Alerts to inform the user about different situations  */
    @Suppress("Deprecation")
    private var progressDialog: android.app.ProgressDialog? = null

    /** Whether we are closing in order to go to the reviewer. If it's the case, UPDATE_VALUES_FROM_DECK should not be
     * cancelled as the counts will be used in review.  */
    private var toReviewer = false

    /**
     * UI elements for "Study Options" view
     */
    private var studyOptionsView: View? = null
    private lateinit var deckInfoLayout: Group
    private lateinit var buttonStart: Button
    private lateinit var textDeckName: TextView
    private lateinit var textDeckDescription: TextView
    private lateinit var buryInfoLabel: TextView
    private lateinit var newCountText: TextView
    private lateinit var newBuryText: TextView
    private lateinit var learningCountText: TextView
    private lateinit var learningBuryText: TextView
    private lateinit var reviewCountText: TextView
    private lateinit var reviewBuryText: TextView
    private lateinit var totalNewCardsCount: TextView
    private lateinit var totalCardsCount: TextView

    private var retryMenuRefreshJob: Job? = null

    // Flag to indicate if the fragment should load the deck options immediately after it loads
    private var loadWithDeckOptions = false
    private var fragmented = false
    private var fullNewCountThread: Thread? = null
    private lateinit var listener: StudyOptionsListener

    /**
     * Callbacks for UI events
     */
    private val buttonClickListener =
        View.OnClickListener { v: View ->
            if (v.id == R.id.studyoptions_start) {
                Timber.i("StudyOptionsFragment:: start study button pressed")
                if (currentContentView != CONTENT_CONGRATS) {
                    openReviewer()
                } else {
                    showCustomStudyContextMenu()
                }
            }
        }

    interface StudyOptionsListener {
        fun onRequireDeckListUpdate()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener =
            try {
                context as StudyOptionsListener
            } catch (e: ClassCastException) {
                throw ClassCastException("$context must implement StudyOptionsListener")
            }
    }

    /**
     * Open the FilteredDeckOptions activity to allow the user to modify the parameters of the
     * filtered deck.
     * @param defaultConfig If true, signals to the FilteredDeckOptions activity that the filtered
     * deck has no options associated with it yet and should use a default
     * set of values.
     */
    private fun openFilteredDeckOptions(defaultConfig: Boolean = false) {
        val i = Intent(activity, FilteredDeckOptions::class.java)
        i.putExtra("defaultConfig", defaultConfig)
        Timber.i("openFilteredDeckOptions()")
        onDeckOptionsActivityResult.launch(i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If we're being restored, don't launch deck options again.
        if (savedInstanceState == null && arguments != null) {
            loadWithDeckOptions = requireArguments().getBoolean("withDeckOptions")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        Timber.i("onCreateView()")
        val studyOptionsView = inflater.inflate(R.layout.studyoptions_fragment, container, false)
        this.studyOptionsView = studyOptionsView
        fragmented = requireActivity().javaClass != StudyOptionsActivity::class.java
        initAllContentViews(studyOptionsView)
        refreshInterface()
        ChangeManager.subscribe(this)
        return studyOptionsView
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateMenu(
        menu: Menu,
        menuInflater: MenuInflater,
    ) {
        menuInflater.inflate(R.menu.study_options_fragment, menu)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (fullNewCountThread != null) {
            fullNewCountThread!!.interrupt()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshInterface()
    }

    private fun closeStudyOptions(result: Int) {
        val a: Activity? = activity
        if (!fragmented && a != null) {
            a.setResult(result)
            a.finish()
        } else if (a == null) {
            // getActivity() can return null if reference to fragment lingers after parent activity has been closed,
            // which is particularly relevant when using AsyncTasks.
            Timber.e("closeStudyOptions() failed due to getActivity() returning null")
        }
    }

    private fun openReviewer() {
        Timber.i("openReviewer()")
        val reviewer = Reviewer.getIntent(requireContext())
        if (fragmented) {
            toReviewer = true
            Timber.i("openReviewer() fragmented mode")
            onRequestReviewActivityResult.launch(reviewer)
            // TODO #8913 should we finish the activity here? when it comes back from review it's dead and mToolbar is null and it crashes
        } else {
            // Go to DeckPicker after studying when not tablet
            reviewer.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
            startActivity(reviewer)
            requireActivity().finish()
        }
    }

    private fun initAllContentViews(studyOptionsView: View) {
        studyOptionsView.findViewById<View>(R.id.studyoptions_gradient).visibility =
            if (fragmented) View.VISIBLE else View.GONE
        deckInfoLayout = studyOptionsView.findViewById(R.id.group_counts)
        textDeckName = studyOptionsView.findViewById(R.id.studyoptions_deck_name)
        textDeckDescription = studyOptionsView.findViewById(R.id.studyoptions_deck_description)
        // make links clickable
        textDeckDescription.movementMethod = LinkMovementMethod.getInstance()
        buryInfoLabel =
            studyOptionsView.findViewById<TextView>(R.id.studyoptions_bury_counts_label).apply {
                // TODO see if we could further improve the display and discoverability of buried cards here
                text = TR.studyingCountsDiffer()
            }
        // Code common to both fragmented and non-fragmented view
        newCountText = studyOptionsView.findViewById(R.id.studyoptions_new_count)
        studyOptionsView.findViewById<TextView>(R.id.studyoptions_new_count_label).text = TR.actionsNew()
        newBuryText = studyOptionsView.findViewById(R.id.studyoptions_new_bury)
        learningCountText = studyOptionsView.findViewById(R.id.studyoptions_learning_count)
        studyOptionsView.findViewById<TextView>(R.id.studyoptions_learning_count_label).text = TR.schedulingLearning()
        learningBuryText = studyOptionsView.findViewById(R.id.studyoptions_learning_bury)
        reviewCountText = studyOptionsView.findViewById(R.id.studyoptions_review_count)
        studyOptionsView.findViewById<TextView>(R.id.studyoptions_review_count_label).text = TR.studyingToReview()
        reviewBuryText = studyOptionsView.findViewById(R.id.studyoptions_review_bury)
        buttonStart =
            studyOptionsView.findViewById<Button?>(R.id.studyoptions_start).apply {
                setOnClickListener(buttonClickListener)
            }
        totalNewCardsCount = studyOptionsView.findViewById(R.id.studyoptions_total_new_count)
        totalCardsCount = studyOptionsView.findViewById(R.id.studyoptions_total_count)
    }

    /**
     * Show the context menu for the custom study options
     */
    private fun showCustomStudyContextMenu() {
        val dialog = CustomStudyDialog.createInstance(deckId = col!!.decks.selected())
        requireActivity().showDialogFragment(dialog)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_deck_or_study_options -> {
                Timber.i("StudyOptionsFragment:: Deck or study options button pressed")
                if (col!!.decks.isFiltered(col!!.decks.selected())) {
                    openFilteredDeckOptions()
                } else {
                    val i =
                        com.ichi2.anki.pages.DeckOptions
                            .getIntent(requireContext(), col!!.decks.current().id)
                    Timber.i("Opening deck options for activity result")
                    onDeckOptionsActivityResult.launch(i)
                }
                return true
            }
            R.id.action_custom_study -> {
                Timber.i("StudyOptionsFragment:: custom study button pressed")
                showCustomStudyContextMenu()
                return true
            }
            R.id.action_unbury -> {
                Timber.i("StudyOptionsFragment:: unbury button pressed")
                launchCatchingTask {
                    withCol { sched.unburyDeck(decks.getCurrentId()) }
                }
                refreshInterface(true)
                item.isVisible = false
                return true
            }
            R.id.action_rebuild -> {
                Timber.i("StudyOptionsFragment:: rebuild cram deck button pressed")
                launchCatchingTask { rebuildCram() }
                return true
            }
            R.id.action_empty -> {
                Timber.i("StudyOptionsFragment:: empty cram deck button pressed")
                launchCatchingTask { emptyCram() }
                return true
            }
            else -> return false
        }
    }

    private suspend fun rebuildCram() {
        val result =
            requireActivity().withProgress(resources.getString(R.string.rebuild_filtered_deck)) {
                withCol {
                    Timber.d("doInBackground - RebuildCram")
                    sched.rebuildDyn(decks.selected())
                    fetchStudyOptionsData()
                }
            }
        rebuildUi(result, true)
    }

    @VisibleForTesting
    suspend fun emptyCram() {
        val result =
            requireActivity().withProgress(resources.getString(R.string.empty_filtered_deck)) {
                withCol {
                    Timber.d("doInBackgroundEmptyCram")
                    sched.emptyDyn(decks.selected())
                    fetchStudyOptionsData()
                }
            }
        rebuildUi(result, true)
    }

    private fun configureToolbar() {
        activity?.invalidateMenu()
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        Timber.i("configureToolbarInternal()")
        try {
            // Switch on or off rebuild/empty/custom study depending on whether or not filtered deck
            if (col != null && col!!.decks.isFiltered(col!!.decks.selected())) {
                menu.findItem(R.id.action_rebuild).isVisible = true
                menu.findItem(R.id.action_empty).isVisible = true
                menu.findItem(R.id.action_custom_study).isVisible = false
                menu.findItem(R.id.action_deck_or_study_options).setTitle(R.string.menu__study_options)
            } else {
                menu.findItem(R.id.action_rebuild).isVisible = false
                menu.findItem(R.id.action_empty).isVisible = false
                menu.findItem(R.id.action_custom_study).isVisible = true
                menu.findItem(R.id.action_deck_or_study_options).setTitle(R.string.menu__deck_options)
            }
            // Don't show custom study icon if congrats shown
            if (currentContentView == CONTENT_CONGRATS) {
                menu.findItem(R.id.action_custom_study).isVisible = false
            }
            // Switch on or off unbury depending on if there are cards to unbury
            menu.findItem(R.id.action_unbury).isVisible = col != null && col!!.sched.haveBuried()
        } catch (e: IllegalStateException) {
            if (!CollectionManager.isOpenUnsafe()) {
                // This will allow a maximum of one invalidate menu attempt in order to workaround
                // database closes caused by sync on startup where this might be running then have
                // the collection close
                Timber.i(e, "Database closed while working. Probably auto-sync. Will re-try after sleep.")
                if (retryMenuRefreshJob != null) {
                    return // we already are doing a refresh, so abort to avoid entering an endless loop
                }
                retryMenuRefreshJob =
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(1000)
                        retryMenuRefreshJob = null
                        activity?.invalidateMenu()
                    }
            }
        }
    }

    private var onRequestReviewActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            Timber.i("StudyOptionsFragment::mOnRequestReviewActivityResult")

            if (!isAdded) {
                Timber.d("Fragment not added to the activity")
                CrashReportService.sendExceptionReport("Fragment is not added to activity", "StudyOptionsFragment")
                return@registerForActivityResult
            }

            Timber.d("Handling onActivityResult for StudyOptionsFragment (openReview, resultCode = %d)", result.resultCode)
            configureToolbar()
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR || result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
                closeStudyOptions(result.resultCode)
                return@registerForActivityResult
            }
            if (result.resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
                // If no more cards getting returned while counts > 0 (due to learn ahead limit) then show a snackbar
                if (col!!.sched.totalCount() > 0 && studyOptionsView != null) {
                    studyOptionsView!!
                        .findViewById<View>(R.id.studyoptions_main)
                        .showSnackbar(R.string.studyoptions_no_cards_due)
                }
            }
        }
    private var onDeckOptionsActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            Timber.i("StudyOptionsFragment::mOnDeckOptionsActivityResult")

            if (!isAdded) {
                Timber.d("Fragment not added to the activity")
                CrashReportService.sendExceptionReport("Fragment is not added to activity", "StudyOptionsFragment")
                return@registerForActivityResult
            }

            Timber.d(
                "Handling onActivityResult for StudyOptionsFragment (deckOptions/filteredDeckOptions, resultCode = %d)",
                result.resultCode,
            )
            configureToolbar()
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR || result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
                closeStudyOptions(result.resultCode)
                return@registerForActivityResult
            }
            if (loadWithDeckOptions) {
                loadWithDeckOptions = false
                val deck = col!!.decks.current()
                if (deck.isFiltered && deck.has("empty")) {
                    deck.remove("empty")
                }
                launchCatchingTask { rebuildCram() }
            } else {
                refreshInterface()
            }
        }

    private fun dismissProgressDialog() {
        if (studyOptionsView != null && studyOptionsView!!.findViewById<View?>(R.id.progress_bar) != null) {
            studyOptionsView!!.findViewById<View>(R.id.progress_bar).visibility = View.GONE
        }
        // for rebuilding cram decks
        if (progressDialog != null && progressDialog!!.isShowing) {
            try {
                progressDialog!!.dismiss()
            } catch (e: Exception) {
                Timber.e("onPostExecute - Dialog dismiss Exception = %s", e.message)
            }
        }
    }

    private var updateValuesFromDeckJob: Job? = null

    /**
     * Rebuild the fragment's interface to reflect the status of the currently selected deck.
     *
     * @param resetDecklist Indicates whether to call back to the parent activity in order to
     *                      also refresh the deck list.
     */
    fun refreshInterface(resetDecklist: Boolean = false) {
        Timber.d("Refreshing StudyOptionsFragment")
        updateValuesFromDeckJob?.cancel()
        // Load the deck counts for the deck from Collection asynchronously
        updateValuesFromDeckJob =
            launchCatchingTask {
                if (CollectionManager.isOpenUnsafe()) {
                    val result = withCol { fetchStudyOptionsData() }
                    rebuildUi(result, resetDecklist)
                }
            }
    }

    class DeckStudyData(
        /**
         * The number of new card to see today in a deck, including subdecks.
         */
        val newCardsToday: Int,
        /**
         * The number of (repetition of) card in learning to see today in a deck, including subdecks. The exact way cards with multiple steps are counted depends on the scheduler
         */
        val lrnCardsToday: Int,
        /**
         * The number of review card to see today in a deck, including subdecks.
         */
        val revCardsToday: Int,
        val buriedNew: Int,
        val buriedLearning: Int,
        val buriedReview: Int,
        val totalNewCards: Int,
        /**
         * Number of cards in this decks and its subdecks.
         */
        val numberOfCardsInDeck: Int,
    )

    /** Open cram deck option if deck is opened for the first time
     * @return Whether we opened the deck options */
    private fun tryOpenCramDeckOptions(): Boolean {
        if (!loadWithDeckOptions) {
            return false
        }
        openFilteredDeckOptions(true)
        loadWithDeckOptions = false
        return true
    }

    private val col: Collection?
        get() {
            try {
                return CollectionManager.getColUnsafe()
            } catch (e: Exception) {
                // This may happen if the backend is locked or similar.
            }
            return null
        }

    override fun onPause() {
        super.onPause()
        if (!toReviewer) {
            // In the reviewer, we need the count. So don't cancel it. Otherwise, (e.g. go to browser, selecting another
            // deck) cancel counts.
            updateValuesFromDeckJob?.cancel()
        }
    }

    /**
     * Rebuilds the interface.
     *
     * @param refreshDecklist If true, the listener notifies the parent activity to update its deck list
     *                        to reflect the latest values.
     * @param result the new DeckStudyData using which UI is to be rebuilt
     */
    private fun rebuildUi(
        result: DeckStudyData?,
        refreshDecklist: Boolean,
    ) {
        dismissProgressDialog()
        if (result != null) {
            // Don't do anything if the fragment is no longer attached to it's Activity or col has been closed
            if (activity == null) {
                Timber.e("StudyOptionsFragment.mRefreshFragmentListener :: can't refresh")
                return
            }

            // #5506 If we have no view, short circuit all UI logic
            if (studyOptionsView == null) {
                tryOpenCramDeckOptions()
                return
            }

            val col =
                col
                    ?: throw NullPointerException("StudyOptionsFragment:: Collection is null while rebuilding Ui")

            // Reinitialize controls in case changed to filtered deck
            initAllContentViews(studyOptionsView!!)
            // Set the deck name
            val deck = col.decks.current()
            // Main deck name
            val fullName = deck.getString("name")
            val name = Decks.path(fullName)
            val nameBuilder = StringBuilder()
            if (name.isNotEmpty()) {
                nameBuilder.append(name[0])
            }
            if (name.size > 1) {
                nameBuilder.append("\n").append(name[1])
            }
            if (name.size > 3) {
                nameBuilder.append("...")
            }
            if (name.size > 2) {
                nameBuilder.append("\n").append(name[name.size - 1])
            }
            textDeckName.text = nameBuilder.toString()
            if (tryOpenCramDeckOptions()) {
                return
            }

            // Switch between the empty view, the ordinary view, and the "congratulations" view
            val isDynamic = deck.isFiltered
            if (result.numberOfCardsInDeck == 0 && !isDynamic) {
                currentContentView = CONTENT_EMPTY
                deckInfoLayout.visibility = View.VISIBLE
                buttonStart.visibility = View.GONE
            } else if (result.newCardsToday + result.lrnCardsToday + result.revCardsToday == 0) {
                currentContentView = CONTENT_CONGRATS
                if (!isDynamic) {
                    deckInfoLayout.visibility = View.GONE
                    buttonStart.visibility = View.VISIBLE
                    buttonStart.text = TR.actionsCustomStudy().toSentenceCase(this, R.string.sentence_custom_study)
                } else {
                    buttonStart.visibility = View.GONE
                }
            } else {
                currentContentView = CONTENT_STUDY_OPTIONS
                deckInfoLayout.visibility = View.VISIBLE
                buttonStart.visibility = View.VISIBLE
                buttonStart.setText(R.string.studyoptions_start)
            }

            // Set deck description
            val desc: String =
                if (isDynamic) {
                    resources.getString(R.string.dyn_deck_desc)
                } else {
                    col.decks.current().description
                }
            if (desc.isNotEmpty()) {
                textDeckDescription.text = formatDescription(desc)
                textDeckDescription.visibility = View.VISIBLE
            } else {
                textDeckDescription.visibility = View.GONE
            }

            // Set new/learn/review card counts
            newCountText.text = result.newCardsToday.toString()
            learningCountText.text = result.lrnCardsToday.toString()
            reviewCountText.text = result.revCardsToday.toString()
            // set bury numbers
            buryInfoLabel.isVisible =
                result.buriedNew > 0 ||
                result.buriedLearning > 0 ||
                result.buriedReview > 0
            if (result.buriedNew > 0) {
                newBuryText.text =
                    requireContext().resources.getQuantityString(
                        R.plurals.studyoptions_buried_count,
                        result.buriedNew,
                        result.buriedNew,
                    )
                newBuryText.isVisible = true
            } else {
                newBuryText.isVisible = false
            }
            if (result.buriedLearning > 0) {
                learningBuryText.text =
                    requireContext().resources.getQuantityString(
                        R.plurals.studyoptions_buried_count,
                        result.buriedLearning,
                        result.buriedLearning,
                    )
                learningBuryText.isVisible = true
            } else {
                learningBuryText.isVisible = false
            }
            if (result.buriedReview > 0) {
                reviewBuryText.text =
                    requireContext().resources.getQuantityString(
                        R.plurals.studyoptions_buried_count,
                        result.buriedReview,
                        result.buriedReview,
                    )
                reviewBuryText.isVisible = true
            } else {
                reviewBuryText.isVisible = false
            }
            reviewBuryText.isVisible = result.buriedReview != 0
            totalNewCardsCount.text = result.totalNewCards.toString()
            totalCardsCount.text = result.numberOfCardsInDeck.toString()
            // Rebuild the options menu
            configureToolbar()
        }

        // If in fragmented mode, refresh the deck list
        if (fragmented && refreshDecklist) {
            listener.onRequireDeckListUpdate()
        }
    }

    /**
     * See https://github.com/ankitects/anki/blob/b05c9d15986ab4e33daa2a47a947efb066bb69b6/qt/aqt/overview.py#L226-L272
     */
    private fun Collection.fetchStudyOptionsData(): DeckStudyData {
        val deckId = decks.current().id
        val counts = sched.counts()
        var buriedNew = 0
        var buriedLearning = 0
        var buriedReview = 0
        val tree = sched.deckDueTree(deckId)
        if (tree != null) {
            buriedNew = tree.newCount - counts.new
            buriedLearning = tree.learnCount - counts.lrn
            buriedReview = tree.reviewCount - counts.rev
        }
        return DeckStudyData(
            newCardsToday = counts.new,
            lrnCardsToday = counts.lrn,
            revCardsToday = counts.rev,
            buriedNew = buriedNew,
            buriedLearning = buriedLearning,
            buriedReview = buriedReview,
            totalNewCards = sched.totalNewForCurrentDeck(),
            numberOfCardsInDeck = decks.cardCount(deckId, includeSubdecks = true),
        )
    }

    companion object {
        /**
         * Available options performed by other activities
         */
        @Suppress("unused")
        private const val BROWSE_CARDS = 3

        @Suppress("unused")
        private const val STATISTICS = 4

        @Suppress("unused")
        private const val DECK_OPTIONS = 5

        /**
         * Constants for selecting which content view to display
         */
        private const val CONTENT_STUDY_OPTIONS = 0
        private const val CONTENT_CONGRATS = 1
        private const val CONTENT_EMPTY = 2

        /**
         * Get a new instance of the fragment.
         * @param withDeckOptions If true, the fragment will load a new activity on top of itself
         * which shows the current deck's options. Set to true when programmatically
         * opening a new filtered deck for the first time.
         */
        fun newInstance(withDeckOptions: Boolean): StudyOptionsFragment {
            val f = StudyOptionsFragment()
            val args = Bundle()
            args.putBoolean("withDeckOptions", withDeckOptions)
            f.arguments = args
            return f
        }

        @VisibleForTesting
        fun formatDescription(
            @Language("HTML") desc: String,
        ): Spanned {
            // #5715: In deck description, ignore what is in style and script tag
            // Since we don't currently execute the JS/CSS, it's not worth displaying.
            val withStrippedTags = Utils.stripHTMLScriptAndStyleTags(desc)
            // #5188 - fromHtml displays newlines as " "
            val withFixedNewlines = convertNewlinesToHtml(withStrippedTags)
            return HtmlCompat.fromHtml(withFixedNewlines!!, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        if (activity != null) {
            refreshInterface(true)
        }
    }
}
