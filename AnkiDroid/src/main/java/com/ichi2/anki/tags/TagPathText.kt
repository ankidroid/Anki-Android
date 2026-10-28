// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews

/** Keeps both ends of a tag path visible on one line, with the full name available to accessibility. */
@Composable
internal fun TagPathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

/** @see TagPathText */
@ThemePreviews
@Composable
private fun TagPathTextPreview() {
    AnkiDroidPreview {
        Column(Modifier.padding(16.dp)) {
            TagPathText("History")
            TagPathText("AnKing::Step_1::Biochemistry::Molecular_biology::DNA_replication")
            TagPathText("AnKing::Step_1::Biochemistry::Molecular_biology::RNA_replication")
        }
    }
}
