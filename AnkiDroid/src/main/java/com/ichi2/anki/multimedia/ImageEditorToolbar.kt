// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews

/**
 * Image actions hosted below the preview:
 *
 * * Replace
 * * Crop
 */
@Composable
internal fun ImageEditorToolbar(
    hasImage: Boolean,
    @DrawableRes replaceIcon: Int,
    onReplace: () -> Unit,
    onCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ImageEditorAction(CommonString.dialog_positive_replace, replaceIcon, onReplace, Modifier.weight(1f))
                ImageEditorDivider()
                ImageEditorAction(CommonString.crop_button, R.drawable.ic_crop, onCrop, Modifier.weight(1f), enabled = hasImage)
            }
        }
    }
}

@Composable
private fun ImageEditorAction(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ImageEditorDivider() {
    Box(modifier = Modifier.height(64.dp), contentAlignment = Alignment.Center) {
        VerticalDivider(
            modifier = Modifier.height(32.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@ThemePreviews
@Composable
private fun ImageEditorToolbarPreview() {
    ImageEditorToolbarPreviewContent(hasImage = true)
}

@ThemePreviews
@Composable
private fun EmptyImageEditorToolbarPreview() {
    ImageEditorToolbarPreviewContent(hasImage = false)
}

@Composable
private fun ImageEditorToolbarPreviewContent(hasImage: Boolean) {
    AnkiDroidPreview {
        ImageEditorToolbar(hasImage = hasImage, replaceIcon = R.drawable.ic_photo_library, onReplace = {}, onCrop = {})
    }
}
