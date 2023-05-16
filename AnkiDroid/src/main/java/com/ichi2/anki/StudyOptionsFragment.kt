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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.Fragment
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.slide
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.servicelayer.ComputeResult
import com.ichi2.anki.servicelayer.Undo
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.CollectionTask.*
import com.ichi2.async.TaskListener
import com.ichi2.async.TaskManager
import com.ichi2.async.updateValuesFromDeck
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Decks
import com.ichi2.libanki.Utils
import com.ichi2.ui.RtlCompliantActionProvider
import com.ichi2.utils.FragmentFactoryUtils.instantiate
import com.ichi2.utils.HtmlUtils.convertNewlinesToHtml
import com.ichi2.utils.KotlinCleanup
import kotlinx.coroutines.Job
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber

class StudyOptionsFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    /**
     * Preferences
     */
    private var mCurrentContentView = CONTENT_STUDY_OPTIONS

    /** Alerts to inform the user about different situations  */
    @Suppress("Deprecation")
    private var mProgressDialog: android.app.ProgressDialog? = null

    /** Whether we are closing in order to go to the reviewer. If it's the case, UPDATE_VALUES_FROM_DECK should not be
     * cancelled as the counts will be used in review.  */
    private var mToReviewer = false

    /**
     * UI elements for "Study Options" view
     */
    private var mStudyOptionsView: View? = null
    private lateinit var deckInfoLayout: View
    private var mButtonStart: Button? = null
    private lateinit var textDeckName: TextView
    private var mTextDeckDescription: TextView? = null
    private var mTextTodayNew: TextView? = null
    private var mTextTodayLrn: TextView? = null
    private var mTextTodayRev: TextView? = null
    private var mTextNewTotal: TextView? = null
    private var mTextTotal: TextView? = null
    private lateinit var textETA: TextView
    private lateinit var textCongratsMessage: TextView
    private var mToolbar: Toolbar? = null

    // Flag to indicate if the fragment should load the deck options immediately after it loads
    private var mLoadWithDeckOptions = false
    private var mFragmented = false
    private var mFullNewCountThread: Thread? = null
    private var mListener: StudyOptionsListener? = null

    /**
     * Callbacks for UI events
     */
    private val mButtonClickListener = View.OnClickListener { v: View ->
        if (v.id == R.id.studyoptions_start) {
            Timber.i("StudyOptionsFragment:: start study button pressed")
            if (mCurrentContentView != CONTENT_CONGRATS) {
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
        mListener = try {
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
        slide(requireActivity(), ActivityTransitionAnimation.Direction.FADE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("onCreate()")
        super.onCreate(savedInstanceState)
        // If we're being restored, don't launch deck options again.
        if (savedInstanceState == null && arguments != null) {
            mLoadWithDeckOptions = requireArguments().getBoolean("withDeckOptions")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Timber.i("onCreateView()")
        if (container == null) {
            // Currently in a layout without a container, so no reason to create our view.
            return null
        }
        val studyOptionsView = inflater.inflate(R.layout.studyoptions_fragment, container, false)
        mStudyOptionsView = studyOptionsView
        mFragmented = requireActivity().javaClass != StudyOptionsActivity::class.java
        initAllContentViews(studyOptionsView)
        mToolbar = studyOptionsView.findViewById(R.id.studyOptionsToolbar)
        if (mToolbar != null) {
            mToolbar!!.inflateMenu(R.menu.study_options_fragment)
            configureToolbar()
        }
        refreshInterface(true)
        return studyOptionsView
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mFullNewCountThread != null) {
            mFullNewCountThread!!.interrupt()
        }
        Timber.i("onDestroy()")
    }

    override fun onResume() {
        super.onResume()
        Timber.i("onResume()")
        refreshInterface(true)
    }

    private fun closeStudyOptions(result: Int) {
        val a: Activity? = activity
        if (!mFragmented && a != null) {
            a.setResult(result)
            a.finish()
            slide(a, ActivityTransitionAnimation.Direction.END)
        } else if (a == null) {
            // getActivity() can return null if reference to fragment lingers after parent activity has been closed,
            // which is particularly relevant when using AsyncTasks.
            Timber.e("closeStudyOptions() failed due to getActivity() returning null")
        }
    }

    private fun openReviewer() {
        Timber.i("openReviewer()")
        val reviewer = Intent(activity, Reviewer::class.java)
        if (mFragmented) {
            mToReviewer = true
            Timber.i("openReviewer() fragmented mode")
            onRequestReviewActivityResult.launch(reviewer)
            // TODO #8913 should we finish the activity here? when it comes back from review it's dead and mToolbar is null and it crashes
        } else {
            // Go to DeckPicker after studying when not tablet
            reviewer.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
            startActivity(reviewer)
            requireActivity().finish()
        }
        animateLeft()
    }

    private fun animateLeft() {
        slide(requireActivity(), ActivityTransitionAnimation.Direction.START)
    }

    private fun initAllContentViews(studyOptionsView: View) {
        if (mFragmented) {
            studyOptionsView.findViewById<View>(R.id.studyoptions_gradient).visibility = View.VISIBLE
        }
        deckInfoLayout = studyOptionsView.findViewById(R.id.studyoptions_deckcounts)
        textDeckName = studyOptionsView.findViewById(R.id.studyoptions_deck_name)
        mTextDeckDescription = studyOptionsView.findViewById(R.id.studyoptions_deck_description)
        // make links clickable
        mTextDeckDescription!!.movementMethod = LinkMovementMethod.getInstance()
        mButtonStart = studyOptionsView.findViewById(R.id.studyoptions_start)
        textCongratsMessage = studyOptionsView.findViewById(R.id.studyoptions_congrats_message)
        // Code common to both fragmented and non-fragmented view
        mTextTodayNew = studyOptionsView.findViewById(R.id.studyoptions_new)
        mTextTodayLrn = studyOptionsView.findViewById(R.id.studyoptions_lrn)
        mTextTodayRev = studyOptionsView.findViewById(R.id.studyoptions_rev)
        mTextNewTotal = studyOptionsView.findViewById(R.id.studyoptions_total_new)
        mTextTotal = studyOptionsView.findViewById(R.id.studyoptions_total)
        textETA = studyOptionsView.findViewById(R.id.studyoptions_eta)
        mButtonStart!!.setOnClickListener(mButtonClickListener)
    }

    /**
     * Show the context menu for the custom study options
     */
    private fun showCustomStudyContextMenu() {
        val ankiActivity = requireActivity() as AnkiActivity
        val contextMenu = instantiate(ankiActivity, CustomStudyDialog::class.java)
        contextMenu.withArguments(CustomStudyDialog.ContextMenuConfiguration.STANDARD, col!!.decks.selected())
        ankiActivity.showDialogFragment(contextMenu)
    }

    fun setFragmentContentView(newView: View?) {
        val parent = this.view as ViewGroup?
        parent!!.removeAllViews()
        parent.addView(newView)
    }

    private val mUndoListener: TaskListener<Unit, ComputeResult?> = object : TaskListener<Unit, ComputeResult?>() {
        override fun onPreExecute() {}
        override fun onPostExecute(result: ComputeResult?) {
            openReviewer()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_undo -> {
                Timber.i("StudyOptionsFragment:: Undo button pressed")
                if (BackendFactory.defaultLegacySchema) {
                    Undo().runWithHandler(mUndoListener)
                } else {
                    launchCatchingTask {
                        if (requireActivity().backendUndoAndShowPopup()) {
                            openReviewer()
                        } else {
                            Undo().runWithHandler(mUndoListener)
                        }
                    }
                }
                return true
            }
            R.id.action_deck_or_study_options -> {
                Timber.i("StudyOptionsFragment:: Deck or study options button pressed")
                if (col!!.decks.isDyn(col!!.decks.selected())) {
                    openFilteredDeckOptions()
                } else {
                    val i = if (BackendFactory.defaultLegacySchema) {
                        Intent(activity, DeckOptionsActivity::class.java)
                    } else {
                        com.ichi2.anki.pages.DeckOptions.getIntent(requireContext(), col!!.decks.current().id)
                    }
                    Timber.i("Opening deck options for activity result")
                    onDeckOptionsActivityResult.launch(i)
                    slide(requireActivity(), ActivityTransitionAnimation.Direction.FADE)
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
                col!!.sched.unburyCardsForDeck()
                refreshInterfaceAndDecklist(true)
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
            R.id.action_rename -> {
                (activity as DeckPicker).renameDeckDialog(col!!.decks.selected())
                return true
            }
            R.id.action_delete -> {
                (activity as DeckPicker).confirmDeckDeletion(col!!.decks.selected())
                return true
            }
            R.id.action_export -> {
                (activity as DeckPicker).exportDeck(col!!.decks.selected())
                return true
            }
            else -> return false
        }
    }

    suspend fun rebuildCram() {
        val result = requireActivity().withProgress(resources.getString(R.string.rebuild_filtered_deck)) {
            withCol {
                Timber.d("doInBackground - RebuildCram")
                sched.rebuildDyn(decks.selected())
                updateValuesFromDeck(this, true)
            }
        }
        rebuildUi(result, true)
    }

    @VisibleForTesting
    suspend fun emptyCram() {
        val result = requireActivity().withProgress(resources.getString(R.string.empty_filtered_deck)) {
            withCol {
                Timber.d("doInBackgroundEmptyCram")
                sched.emptyDyn(decks.selected())
                updateValuesFromDeck(this, true)
            }
        }
        rebuildUi(result, true)
    }

    fun configureToolbar() {
        configureToolbarInternal(true)
    }

    // This will allow a maximum of one recur in order to workaround database closes
    // caused by sync on startup where this might be running then have the collection close
    @NeedsTest("test whether the navigationIcon and navigationOnClickListener are set properly")
    private fun configureToolbarInternal(recur: Boolean) {
        Timber.i("configureToolbarInternal()")
        try {
            mToolbar!!.setOnMenuItemClickListener(this)
            val menu = mToolbar!!.menu
            // Switch on or off rebuild/empty/custom study depending on whether or not filtered deck
            if (col != null && col!!.decks.isDyn(col!!.decks.selected())) {
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
            if (mCurrentContentView == CONTENT_CONGRATS) {
                menu.findItem(R.id.action_custom_study).isVisible = false
            }
            // Switch on rename / delete / export if tablet layout
            if (mFragmented) {
                menu.findItem(R.id.action_rename).isVisible = true
                menu.findItem(R.id.action_delete).isVisible = true
                menu.findItem(R.id.action_export).isVisible = true
            } else {
                menu.findItem(R.id.action_rename).isVisible = false
                menu.findItem(R.id.action_delete).isVisible = false
                menu.findItem(R.id.action_export).isVisible = false
            }
            // Switch on or off unbury depending on if there are cards to unbury
            menu.findItem(R.id.action_unbury).isVisible = col != null && col!!.sched.haveBuried()
            // Set the proper click target for the undo button's ActionProvider
            val undoActionProvider: RtlCompliantActionProvider? = MenuItemCompat.getActionProvider(
                menu.findItem(R.id.action_undo)
            ) as? RtlCompliantActionProvider
            undoActionProvider?.clickHandler = { _, menuItem -> onMenuItemClick(menuItem) }
            // Switch on or off undo depending on whether undo is available
            if (col == null || !col!!.undoAvailable()) {
                menu.findItem(R.id.action_undo).isVisible = false
            } else {
                menu.findItem(R.id.action_undo).isVisible = true
                val res = AnkiDroidApp.appResources
                menu.findItem(R.id.action_undo).title = res.getString(R.string.studyoptions_congrats_undo, col!!.undoName(res))
            }
            // Set the back button listener
            if (!mFragmented) {
                val icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_arrow_back_white)
                icon!!.isAutoMirrored = true
                mToolbar!!.navigationIcon = icon
                mToolbar!!.setNavigationOnClickListener { (activity as AnkiActivity).finishWithAnimation(ActivityTransitionAnimation.Direction.END) }
            }
        } catch (e: IllegalStateException) {
            if (!CollectionHelper.instance.colIsOpen()) {
                if (recur) {
                    Timber.i(e, "Database closed while working. Probably auto-sync. Will re-try after sleep.")
                    try {
                        Thread.sleep(1000)
                    } catch (ex: InterruptedException) {
                        Timber.i(ex, "Thread interrupted while waiting to retry. Likely unimportant.")
                        Thread.currentThread().interrupt()
                    }
                    configureToolbarInternal(false)
                } else {
                    Timber.w(e, "Database closed while working. No re-tries left.")
                }
            }
        }
    }

    var onRequestReviewActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        Timber.i("StudyOptionsFragment::mOnRequestReviewActivityResult")
        Timber.d("Handling onActivityResult for StudyOptionsFragment (openReview, resultCode = %d)", result.resultCode)
        if (mToolbar != null) {
            configureToolbar() // FIXME we were crashing here because mToolbar is null #8913
        } else {
            CrashReportService.sendExceptionReport("mToolbar null after return from tablet review session? Issue 8913", "StudyOptionsFragment")
        }
        if (result.resultCode == DeckPicker.RESULT_DB_ERROR || result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            closeStudyOptions(result.resultCode)
            return@registerForActivityResult
        }
        if (result.resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
            // If no more cards getting returned while counts > 0 (due to learn ahead limit) then show a snackbar
            if (col!!.sched.count() > 0 && mStudyOptionsView != null) {
                mStudyOptionsView!!.findViewById<View>(R.id.studyoptions_main)
                    .showSnackbar(R.string.studyoptions_no_cards_due)
            }
        }
    }
    private var onDeckOptionsActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        Timber.i("StudyOptionsFragment::mOnDeckOptionsActivityResult")
        Timber.d("Handling onActivityResult for StudyOptionsFragment (deckOptions/filteredDeckOptions, resultCode = %d)", result.resultCode)
        configureToolbar()
        if (result.resultCode == DeckPicker.RESULT_DB_ERROR || result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            closeStudyOptions(result.resultCode)
            return@registerForActivityResult
        }
        if (mLoadWithDeckOptions) {
            mLoadWithDeckOptions = false
            val deck = col!!.decks.current()
            if (deck.isDyn && deck.has("empty")) {
                deck.remove("empty")
            }
            launchCatchingTask { rebuildCram() }
        } else {
            TaskManager.waitToFinish()
            refreshInterface(true)
        }
    }

    private fun dismissProgressDialog() {
        if (mStudyOptionsView != null && mStudyOptionsView!!.findViewById<View?>(R.id.progress_bar) != null) {
            mStudyOptionsView!!.findViewById<View>(R.id.progress_bar).visibility = View.GONE
        }
        // for rebuilding cram decks
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            try {
                mProgressDialog!!.dismiss()
            } catch (e: Exception) {
                Timber.e("onPostExecute - Dialog dismiss Exception = %s", e.message)
            }
        }
    }

    fun refreshInterface() {
        refreshInterface(resetSched = false, resetDecklist = false)
    }

    @KotlinCleanup("default value + add overloads")
    private fun refreshInterfaceAndDecklist(resetSched: Boolean) {
        refreshInterface(resetSched, true)
    }

    fun refreshInterface(resetSched: Boolean) {
        refreshInterface(resetSched, false)
    }

    /**
     * Rebuild the fragment's interface to reflect the status of the currently selected deck.
     *
     * @param resetSched    Indicates whether to rebuild the queues as well. Set to true for any
     *                      task that modifies queues (e.g., unbury or empty filtered deck).
     * @param resetDecklist Indicates whether to call back to the parent activity in order to
     *                      also refresh the deck list.
     */
    private var updateValuesFromDeckJob: Job? = null
    private fun refreshInterface(resetSched: Boolean = false, resetDecklist: Boolean = false) {
        Timber.d("Refreshing StudyOptionsFragment")
        updateValuesFromDeckJob?.cancel()
        // Load the deck counts for the deck from Collection asynchronously
        updateValuesFromDeckJob = launchCatchingTask {
            val result = withCol { updateValuesFromDeck(this, resetSched) }
            rebuildUi(result, resetDecklist)
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
        /**
         * The number of new cards in this decks, and subdecks.
         */
        val numberOfNewCardsInDeck: Int,
        /**
         * Number of cards in this decks and its subdecks.
         */
        val numberOfCardsInDeck: Int,
        /**
         * Expected time spent today to review all due cards in this deck.
         */
        val eta: Int
    )

    /** Open cram deck option if deck is opened for the first time
     * @return Whether we opened the deck options */
    private fun tryOpenCramDeckOptions(): Boolean {
        if (!mLoadWithDeckOptions) {
            return false
        }
        openFilteredDeckOptions(true)
        mLoadWithDeckOptions = false
        return true
    }

    private val col: Collection?
        get() {
            try {
                return CollectionHelper.instance.getCol(context)
            } catch (e: Exception) {
                // This may happen if the backend is locked or similar.
            }
            return null
        }

    override fun onPause() {
        super.onPause()
        if (!mToReviewer) {
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
    // TODO: Make this a suspend function and move string operations and db-query to a background dispatcher
    private fun rebuildUi(result: DeckStudyData?, refreshDecklist: Boolean) {
        dismissProgressDialog()
        if (result != null) {
            // Don't do anything if the fragment is no longer attached to it's Activity or col has been closed
            if (activity == null) {
                Timber.e("StudyOptionsFragment.mRefreshFragmentListener :: can't refresh")
                return
            }

            // #5506 If we have no view, short circuit all UI logic
            if (mStudyOptionsView == null) {
                tryOpenCramDeckOptions()
                return
            }

            // Reinitialize controls in case changed to filtered deck
            initAllContentViews(mStudyOptionsView!!)
            // Set the deck name
            val deck = col!!.decks.current()
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
            val isDynamic = deck.isDyn
            if (result.numberOfCardsInDeck == 0 && !isDynamic) {
                mCurrentContentView = CONTENT_EMPTY
                deckInfoLayout.visibility = View.VISIBLE
                textCongratsMessage.visibility = View.VISIBLE
                textCongratsMessage.setText(R.string.studyoptions_empty)
                mButtonStart!!.visibility = View.GONE
            } else if (result.newCardsToday + result.lrnCardsToday + result.revCardsToday == 0) {
                mCurrentContentView = CONTENT_CONGRATS
                if (!isDynamic) {
                    deckInfoLayout.visibility = View.GONE
                    mButtonStart!!.visibility = View.VISIBLE
                    mButtonStart!!.setText(R.string.custom_study)
                } else {
                    mButtonStart!!.visibility = View.GONE
                }
                textCongratsMessage.visibility = View.VISIBLE
                textCongratsMessage.text = col!!.sched.finishedMsg(requireActivity())
            } else {
                mCurrentContentView = CONTENT_STUDY_OPTIONS
                deckInfoLayout.visibility = View.VISIBLE
                textCongratsMessage.visibility = View.GONE
                mButtonStart!!.visibility = View.VISIBLE
                mButtonStart!!.setText(R.string.studyoptions_start)
            }

            // Set deck description
            val desc: String = if (isDynamic) {
                resources.getString(R.string.dyn_deck_desc)
            } else {
                col!!.decks.getActualDescription()
            }
            if (desc.isNotEmpty()) {
                mTextDeckDescription!!.text = formatDescription(desc)
                mTextDeckDescription!!.visibility = View.VISIBLE
            } else {
                mTextDeckDescription!!.visibility = View.GONE
            }

            // Set new/learn/review card counts
            mTextTodayNew!!.text = result.newCardsToday.toString()
            mTextTodayLrn!!.text = result.lrnCardsToday.toString()
            mTextTodayRev!!.text = result.revCardsToday.toString()

            // Set the total number of new cards in deck
            if (result.numberOfNewCardsInDeck < NEW_CARD_COUNT_TRUNCATE_THRESHOLD) {
                // if it hasn't been truncated by libanki then just set it usually
                mTextNewTotal!!.text = result.numberOfNewCardsInDeck.toString()
            } else {
                // if truncated then make a thread to allow full count to load
                mTextNewTotal!!.text = ">1000"
                if (mFullNewCountThread != null) {
                    // a thread was previously made -- interrupt it
                    mFullNewCountThread!!.interrupt()
                }
                mFullNewCountThread = Thread {
                    val collection = col
                    // TODO: refactor code to not rewrite this query, add to Sched.totalNewForCurrentDeck()
                    val query = "SELECT count(*) FROM cards WHERE did IN " +
                        Utils.ids2str(collection!!.decks.active()) +
                        " AND queue = " + Consts.QUEUE_TYPE_NEW
                    val fullNewCount = collection.db.queryScalar(query)
                    if (fullNewCount > 0) {
                        val setNewTotalText = Runnable { mTextNewTotal!!.text = fullNewCount.toString() }
                        if (!Thread.currentThread().isInterrupted) {
                            mTextNewTotal!!.post(setNewTotalText)
                        }
                    }
                }
                mFullNewCountThread!!.start()
            }

            // Set total number of cards
            mTextTotal!!.text = result.numberOfCardsInDeck.toString()
            // Set estimated time remaining
            if (result.eta != -1) {
                textETA.text = result.eta.toString()
            } else {
                textETA.text = "-"
            }
            // Rebuild the options menu
            configureToolbar()
        }

        // If in fragmented mode, refresh the deck list
        if (mFragmented && refreshDecklist) {
            mListener!!.onRequireDeckListUpdate()
        }
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

        // Threshold at which the total number of new cards is truncated by libanki
        private const val NEW_CARD_COUNT_TRUNCATE_THRESHOLD = 99999

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
        fun formatDescription(desc: String): Spanned {
            // #5715: In deck description, ignore what is in style and script tag
            // Since we don't currently execute the JS/CSS, it's not worth displaying.
            val withStrippedTags = Utils.stripHTMLScriptAndStyleTags(desc)
            // #5188 - fromHtml displays newlines as " "
            val withFixedNewlines = convertNewlinesToHtml(withStrippedTags)
            return HtmlCompat.fromHtml(withFixedNewlines!!, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }
}
