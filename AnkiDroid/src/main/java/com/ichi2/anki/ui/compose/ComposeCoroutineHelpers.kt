package com.ichi2.anki.ui.compose

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import kotlinx.coroutines.coroutineScope

suspend fun <T> AnkiActivity.withComposeProgress(
    message: String = getString(R.string.dialog_processing),
    op: suspend () -> T,
): T = coroutineScope {
    val view = ComposeView(this@withComposeProgress)
    view.setContent {
        LoadingIndicator()
    }
    // TODO: Add the view to the activity's view hierarchy
    // and remove it when the operation is complete.
    // For now, this is just a placeholder.
    op()
}
