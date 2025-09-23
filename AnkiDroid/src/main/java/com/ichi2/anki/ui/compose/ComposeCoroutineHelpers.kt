package com.ichi2.anki.ui.compose

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.ichi2.anki.AnkiActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> AnkiActivity.withComposeProgress(
    op: suspend () -> T,
): T {
    val view = ComposeView(this)
    view.setContent {
        LoadingIndicator()
    }

    val rootView = findViewById<ViewGroup>(android.R.id.content)
    withContext(Dispatchers.Main) {
        rootView.addView(view)
    }

    try {
        return op()
    } finally {
        withContext(Dispatchers.Main) {
            rootView.removeView(view)
        }
    }
}
