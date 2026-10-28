// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews

/** Keeps the start and end of a tag path within two lines, exposing its full name to accessibility. */
@Composable
internal fun TagPathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val accessibilityText = remember(text) { AnnotatedString(text) }
    BoxWithConstraints(modifier.semantics(mergeDescendants = true) {}) {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        val displayPath =
            remember(text, width, style, measurer) {
                middleEllipsis(text) { candidate ->
                    !measurer
                        .measure(
                            text = candidate.replace("::", "::\u200B"),
                            style = style,
                            maxLines = 2,
                            constraints = Constraints(maxWidth = width),
                        ).hasVisualOverflow
                }.replace("::", "::\u200B")
            }
        Text(
            text = displayPath,
            modifier = Modifier.fillMaxWidth().semantics { this.text = accessibilityText },
            style = style,
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Finds a fitting middle ellipsis while preserving Unicode code-point boundaries. */
private fun middleEllipsis(
    text: String,
    fits: (String) -> Boolean,
): String {
    if (fits(text)) return text
    var low = 0
    var high = text.codePointCount(0, text.length) - 1
    var result = "…"
    while (low <= high) {
        val count = (low + high) / 2
        val prefixEnd = text.offsetByCodePoints(0, (count + 1) / 2)
        val suffixStart = text.offsetByCodePoints(text.length, -(count / 2))
        val candidate = text.substring(0, prefixEnd) + "…" + text.substring(suffixStart)
        if (fits(candidate)) {
            result = candidate
            low = count + 1
        } else {
            high = count - 1
        }
    }
    return result
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
