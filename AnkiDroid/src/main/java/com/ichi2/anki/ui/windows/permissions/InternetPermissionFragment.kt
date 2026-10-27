// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Akshita Tiwary <akshita.andev16@gmail.com>

package com.ichi2.anki.ui.windows.permissions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentInternetPermissionBinding

class InternetPermissionFragment : PermissionsFragment(R.layout.fragment_internet_permission) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = FragmentInternetPermissionBinding
        .inflate(inflater, container, false)
        .apply { internetPermission.initializeInternetPermissionItem() }
        .root
}
