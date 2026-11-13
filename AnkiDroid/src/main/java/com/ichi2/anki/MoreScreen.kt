// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichi2.anki.dialogs.help.HelpItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    helpItems: List<HelpItem>,
    supportItems: List<HelpItem>,
    onSettingsClick: () -> Unit,
    onHelpItemClick: (HelpItem) -> Unit,
    onSupportItemClick: (HelpItem) -> Unit,
    scrollState: ScrollState,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text(stringResource(CommonString.bottom_nav_more)) },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection())
                .verticalScroll(scrollState)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            MoreListRow(
                icon = R.drawable.ic_settings_black,
                title = stringResource(CommonString.settings),
                minHeight = 56.dp,
                onClick = onSettingsClick,
            )
            MoreDivider()
            MoreSectionHeader(stringResource(CommonString.help))
            MoreGroup(
                rows = helpItems.map { item -> MoreRowItem(item.iconResId, stringResource(item.titleResId)) { onHelpItemClick(item) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            MoreDivider()
            MoreSectionHeader(stringResource(CommonString.help_title_support_ankidroid))
            MoreGroup(
                rows =
                    supportItems.map { item ->
                        MoreRowItem(item.iconResId, stringResource(item.titleResId)) { onSupportItemClick(item) }
                    },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun MoreSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun MoreDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun MoreListRow(
    @DrawableRes icon: Int,
    title: String,
    minHeight: Dp = 48.dp,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = minHeight)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private class MoreRowItem(
    val icon: Int,
    val title: String,
    val onClick: () -> Unit,
)

@Composable
private fun MoreGroup(
    rows: List<MoreRowItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row ->
            MoreGroupRow(row, shape = groupRowShape(index, rows.size))
        }
    }
}

private fun groupRowShape(
    index: Int,
    count: Int,
): Shape {
    val top = if (index == 0) 20.dp else 4.dp
    val bottom = if (index == count - 1) 20.dp else 4.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun MoreGroupRow(
    row: MoreRowItem,
    shape: Shape,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(role = Role.Button, onClick = row.onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(row.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp).size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
