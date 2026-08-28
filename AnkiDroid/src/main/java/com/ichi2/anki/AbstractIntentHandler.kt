// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki

import android.app.Activity
import android.os.Bundle
import com.ichi2.anki.common.android.themes.disableXiaomiForceDarkMode
import com.ichi2.themes.Themes

/**
 * This class is an abstract base class that extends Activity and provides common initialization logic for [IntentHandler] and [IntentHandler2].
 * By centralizing common setup tasks here, it promotes code reuse.
 */
abstract class AbstractIntentHandler : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Themes.setTheme(this, savedInstanceState)
        disableXiaomiForceDarkMode(this)
        setContentView(R.layout.activity_progress_bar)
    }
}
