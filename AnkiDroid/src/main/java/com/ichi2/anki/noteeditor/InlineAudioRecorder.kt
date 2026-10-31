// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun InlineAudioRecorder(
    state: RecorderState,
    elapsedSeconds: Long,
    levels: List<Float?>,
    fieldName: String,
    onRecordPress: () -> Unit,
    onRecordRelease: (held: Boolean) -> Unit,
    onRecordClick: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onInsert: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val recordingRed = colorResource(R.color.audio_recorder_red)
    Surface(
        modifier = modifier.padding(contentPadding).padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                Row(
                    modifier = Modifier.height(40.dp).padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_mic),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = fieldName,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RecorderIconButton(R.drawable.close_icon, CommonString.close, onClose)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.heightIn(min = 52.dp).padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(isRecording = state == RecorderState.Recording, recordingColor = recordingRed)
                        Text(
                            text = DateUtils.formatElapsedTime(elapsedSeconds),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                        )
                        RecordingWaveform(
                            levels = levels,
                            color = if (state == RecorderState.Recording) recordingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 12.dp),
                        )
                        when (state) {
                            is RecorderState.Recorded -> {
                                FilledIconButton(onClick = onInsert) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_done),
                                        contentDescription = stringResource(CommonString.insert_recording),
                                    )
                                }
                                RecorderIconButton(R.drawable.ic_delete, CommonString.delete_audio_button_description, onDiscard)
                            }
                            else -> {
                                RecordButton(
                                    state = state,
                                    color = recordingRed,
                                    onPress = onRecordPress,
                                    onRelease = onRecordRelease,
                                    onClick = onRecordClick,
                                )
                                if (state == RecorderState.Idle) {
                                    Spacer(Modifier.size(40.dp))
                                } else {
                                    RecorderIconButton(R.drawable.ic_stop, CommonString.save_voice, onSave)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(
    isRecording: Boolean,
    recordingColor: Color,
) {
    val alpha =
        if (isRecording) {
            val pulse by rememberInfiniteTransition(label = "recording").animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
                label = "recording dot",
            )
            pulse
        } else {
            1f
        }
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(
                if (isRecording) recordingColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                CircleShape,
            ),
    )
}

@Composable
private fun RecorderIconButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(description))
    }
}

@Composable
private fun RecordButton(
    state: RecorderState,
    color: Color,
    onPress: () -> Unit,
    onRelease: (held: Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val isRecording = state == RecorderState.Recording
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                ).pointerInput(interactionSource) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val press = PressInteraction.Press(down.position)
                        interactionSource.tryEmit(press)
                        currentOnPress()
                        val up = awaitAllPointersUp()
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        currentOnRelease(up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isRecording) R.drawable.round_pause_24 else R.drawable.ic_action_mic),
            contentDescription =
                stringResource(
                    when (state) {
                        RecorderState.Recording -> CommonString.pause_recording
                        RecorderState.Paused -> CommonString.resume_recording
                        else -> CommonString.start_recording
                    },
                ),
            tint = Color.White,
        )
    }
}

private suspend fun AwaitPointerEventScope.awaitAllPointersUp(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) return event.changes.first()
    }
}

@ThemePreviews
@Composable
private fun InlineAudioRecorderReadyPreview() = InlineAudioRecorderPreview(RecorderState.Idle, elapsedSeconds = 0, levels = emptyList())

@ThemePreviews
@Composable
private fun InlineAudioRecorderRecordingPreview() = InlineAudioRecorderPreview(RecorderState.Recording)

@ThemePreviews
@Composable
private fun InlineAudioRecorderPausedPreview() = InlineAudioRecorderPreview(RecorderState.Paused)

@ThemePreviews
@Composable
private fun InlineAudioRecorderRecordedPreview() = InlineAudioRecorderPreview(RecorderState.Recorded(File("recording.3gp")))

@Composable
private fun InlineAudioRecorderPreview(
    state: RecorderState,
    elapsedSeconds: Long = 7,
    levels: List<Float?> = List(40) { abs(sin(it * 0.9f) * cos(it * 0.31f)) },
) {
    AnkiDroidPreview {
        InlineAudioRecorder(
            state = state,
            elapsedSeconds = elapsedSeconds,
            levels = levels,
            fieldName = "Front",
            onRecordPress = {},
            onRecordRelease = {},
            onRecordClick = {},
            onSave = {},
            onDiscard = {},
            onInsert = {},
            onClose = {},
        )
    }
}
