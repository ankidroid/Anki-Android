// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import timber.log.Timber

/**
 * Add/remove list of website domains the blocker gates. Input is normalized
 * through [SupportedBrowsers.parseHost] so `https://www.X.com/foo` becomes `x.com`.
 */
class BlockedDomainsFragment : Fragment(R.layout.fragment_blocked_domains) {
    private val domains = mutableListOf<String>()
    private lateinit var adapter: DomainsAdapter
    private lateinit var emptyLabel: TextView

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().finish()
        }
        emptyLabel = view.findViewById(R.id.empty_label)
        domains += BlockerPrefs.blockedDomains.sorted()
        adapter = DomainsAdapter()
        view.findViewById<RecyclerView>(R.id.domains_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BlockedDomainsFragment.adapter
        }

        val input = view.findViewById<TextInputEditText>(R.id.domain_input)
        val addButton = view.findViewById<Button>(R.id.add_domain_button)
        addButton.setOnClickListener { addDomain(input) }
        input.setOnEditorActionListener { _, _, _ ->
            addDomain(input)
            true
        }
        input.doOnTextChanged { _, _, _, _ -> input.error = null }
        refreshEmptyState()
    }

    private fun addDomain(input: TextInputEditText) {
        val host = SupportedBrowsers.parseHost(input.text?.toString())
        if (host == null) {
            showThemedToast(requireContext(), getString(R.string.blocker_invalid_domain), true)
            return
        }
        input.setText("")
        if (host in domains) return
        Timber.i("Blocker: blocking domain %s", host)
        domains += host
        domains.sort()
        persist()
        adapter.notifyDataSetChanged()
        refreshEmptyState()
    }

    private fun removeDomain(host: String) {
        Timber.i("Blocker: unblocking domain %s", host)
        domains -= host
        persist()
        adapter.notifyDataSetChanged()
        refreshEmptyState()
    }

    private fun persist() {
        BlockerPrefs.blockedDomains = domains.toSet()
    }

    private fun refreshEmptyState() {
        emptyLabel.isVisible = domains.isEmpty()
    }

    private inner class DomainsAdapter : RecyclerView.Adapter<DomainsAdapter.Holder>() {
        inner class Holder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val label: TextView = view.findViewById(R.id.domain_label)
            val remove: ImageButton = view.findViewById(R.id.remove_domain_button)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): Holder =
            Holder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_blocked_domain, parent, false),
            )

        override fun getItemCount() = domains.size

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val host = domains[position]
            holder.label.text = host
            holder.remove.setOnClickListener { removeDomain(host) }
        }
    }
}
