/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
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

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.utils.HelperUtil
import timber.log.Timber
import java.util.*
import kotlin.math.min

class HelperActivity : AnkiActivity() {
    lateinit var mHelpItemAdapter: HelpItemAdapter
    lateinit var mHelperUtilList: MutableList<HelperUtil>
    lateinit var mHelperRecyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_helper_class)
        try {
            val questionId = intent.extras!!.getInt("questionId")
            val answerId = intent.extras!!.getInt("answerId")
            mHelperRecyclerView = findViewById(R.id.helper_recycler_view)
            mHelperUtilList = ArrayList()
            mHelperRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            mHelperUtilList.addAll(getHelperList(questionId, answerId))
            mHelpItemAdapter = HelpItemAdapter(this, mHelperUtilList)
            mHelperRecyclerView.adapter = mHelpItemAdapter
        } catch (e: NullPointerException) {
            Timber.w(e, "HelperActivity: threw an error")
            UIUtils.showThemedToast(this, "can not able to show list of helper questions.", true)
            finishWithoutAnimation()
        }
        enableToolbar()
    }

    override fun enableToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar?.let { setSupportActionBar(it) }
    }

    private fun getHelperList(questionId: Int, answerId: Int): ArrayList<HelperUtil> {
        val helperUtils = ArrayList<HelperUtil>()
        val question = listOf(*resources.getStringArray(questionId))
        val answer = listOf(*resources.getStringArray(answerId))
        for (i in 0 until min(question.size, answer.size)) {
            helperUtils.add(HelperUtil(question[i], answer[i]))
        }
        return helperUtils
    }
}
