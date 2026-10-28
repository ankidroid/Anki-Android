// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.tags.UserMessage.ClearedUnusedTags
import com.ichi2.anki.tags.UserMessage.TagMoved
import com.ichi2.anki.tags.UserMessage.TagRemoved
import com.ichi2.anki.tags.UserMessage.TagRenamed
import com.ichi2.anki.tags.UserMessage.UnexpectedError
import com.ichi2.anki.tags.UserMessage.UnusedTagNotMoved
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Hosts tag management in the existing fragment navigation. */
class ManageTagsFragment : Fragment() {
    private val viewModel: ManageTagsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            id = R.id.manage_tags_compose_view
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    ManageTagsScreen(
                        viewModel = viewModel,
                        onNavigateUp = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, ManageTagsFragment::class)
    }
}

/** Connects [ManageTagsViewModel] to the screen and displays queued messages as snackbars. */
@Composable
private fun ManageTagsScreen(
    viewModel: ManageTagsViewModel,
    onNavigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, context) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pendingMessages
                .map { it.firstOrNull() }
                .distinctUntilChanged()
                .collectLatest { pending ->
                    if (pending != null) {
                        snackbarHost.showSnackbar(pending.message.text(context))
                        viewModel.messageShown(pending.id)
                    }
                }
        }
    }
    ManageTagsScreen(
        state = state,
        searchQuery = query,
        onSearch = viewModel::filter,
        onToggleCollapsed = { viewModel.toggleCollapsed(it) },
        tagExists = viewModel::tagExists,
        onAction = { action, tag, input ->
            when (action) {
                TagAction.RENAME -> viewModel.renameTag(requireNotNull(tag), requireNotNull(TagName.build(input)))
                TagAction.DELETE -> viewModel.removeTag(requireNotNull(tag))
                TagAction.CLEAR_UNUSED -> viewModel.clearUnusedTags()
            }
        },
        onNavigateUp = onNavigateUp,
        onRetry = { viewModel.refreshTags() },
        onDismissError = { error -> if (state is ManageTagsState.Content) viewModel.errorDismissed(error) else onNavigateUp() },
        snackbarHostState = snackbarHost,
    )
}

private fun UserMessage.text(context: Context): String =
    when (this) {
        is TagRemoved -> context.resources.getQuantityString(CommonPlurals.manage_tags_removed_from_notes, notesAffected, notesAffected)
        is TagRenamed ->
            if (notesAffected == 0) {
                TR.browsingTagRenameWarningEmpty()
            } else {
                context.resources.getQuantityString(CommonPlurals.manage_tags_renamed_on_notes, notesAffected, notesAffected)
            }
        is TagMoved -> context.resources.getQuantityString(CommonPlurals.manage_tags_moved_on_notes, notesAffected, notesAffected)
        is ClearedUnusedTags -> context.resources.getQuantityString(CommonPlurals.manage_tags_unused_cleared, count, count)
        UnusedTagNotMoved, UnexpectedError -> context.getString(CommonString.something_wrong)
    }
