/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.widgets.NotificationPreferenceAdapter
import com.ichi2.async.CollectionTask
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.sched.AbstractDeckTreeNode
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.sched.TreeNode
import com.ichi2.libanki.sched.findInDeckTree
import timber.log.Timber

/**
 * Fragment with preferences related to notifications
 */
class NotificationsSettingsFragment : Fragment() {

    private lateinit var mDeckListAdapter: NotificationPreferenceAdapter
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mProgressBar: ProgressBar
    var dueTree: List<TreeNode<AbstractDeckTreeNode>>? = null
    val col: Collection
        get() = CollectionHelper.instance.getCol(requireContext())!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_preference_notification, container, false)
        initViews(view)

        // create and set an adapter for the RecyclerView
        mDeckListAdapter = NotificationPreferenceAdapter(layoutInflater, requireContext()).apply {
            setTimeClickListener(mOnTimeClickListener)
            setDeckExpanderClickListener(mDeckExpanderClickListener)
        }
        mRecyclerView.apply {
            adapter = mDeckListAdapter
            layoutManager = LinearLayoutManager(context)
        }

        // Fetch Deck Data
        TaskManager.launchCollectionTask(
            CollectionTask.LoadDeckCounts(),
            UpdateDeckListListener(this)
        )

        return view
    }

    private val mOnTimeClickListener = View.OnClickListener {
        TODO("Open Time picker bottom sheet.")
    }

    private val mDeckExpanderClickListener = View.OnClickListener { view ->
        toggleDeckExpand(view.tag as Long)
    }

    @Suppress("UNCHECKED_CAST")
    fun toggleDeckExpand(did: DeckId) {
        if (!col.decks.children(did).isEmpty()) {
            // update DB
            col.decks.collapse(did)
            // update stored state
            val deck: List<TreeNode<DeckDueTreeNode>> = dueTree!! as List<TreeNode<DeckDueTreeNode>>
            Timber.d(dueTree!!.toString() + " " + did)
            findInDeckTree(deck, did)?.run {
                collapsed = !collapsed
            }
            mDeckListAdapter.buildDeckList(dueTree!!, col)
        }
    }

    private fun initViews(view: View) {
        mRecyclerView = view.findViewById(R.id.preference_notification_rv)
        mProgressBar = view.findViewById(R.id.preference_notification_progressbar)
    }

    fun <T : AbstractDeckTreeNode> onDecksLoaded(result: List<TreeNode<T>>?) {
        Timber.i("Updating deck list UI")
        // Make sure the fragment is visible

        if (result == null) {
            Timber.e("null result loading deck counts")
            showCollectionErrorMessage()
            return
        }
        dueTree = result.map { x -> x.unsafeCastToType() }
        mDeckListAdapter.buildDeckList(dueTree!!, col)
        hideProgressBar()
        Timber.d("Startup - Deck List UI Completed")
    }

    private fun showProgressBar() {
        mRecyclerView.visibility = View.GONE
        mProgressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        mRecyclerView.visibility = View.VISIBLE
        mProgressBar.visibility = View.GONE
    }

    private fun showCollectionErrorMessage() {
        UIUtils.showThemedToast(context, "Unable to Access Collection", true)
    }

    private class UpdateDeckListListener<T : AbstractDeckTreeNode>(context: NotificationsSettingsFragment) :
        TaskListenerWithContext<NotificationsSettingsFragment, Void, List<TreeNode<T>>?>(context) {
        override fun actualOnPreExecute(context: NotificationsSettingsFragment) {

            if (!CollectionHelper.instance.colIsOpen()) {
                context.showProgressBar()
            }
            Timber.d("Refreshing deck list")
        }

        override fun actualOnPostExecute(
            context: NotificationsSettingsFragment,
            result: List<TreeNode<T>>?
        ) = context.onDecksLoaded(result)
    }
}
