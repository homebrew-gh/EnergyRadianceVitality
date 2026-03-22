package com.erv.app.ui.media

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.media.ErvMediaControlHelper

/**
 * Inline or sheet media UI: track info + transport, optional notification-listener onboarding.
 * @param useLightOnDarkBackground true for live timer gradient (white text); false for standard sheet surface.
 * @param showHeaderTitle when true, shows the "Media" title (bottom sheet); false for compact live overlay.
 */
@Composable
fun WorkoutMediaControlPanel(
    modifier: Modifier = Modifier,
    useLightOnDarkBackground: Boolean = false,
    showHeaderTitle: Boolean = true,
) {
    val context = LocalContext.current

    var listenerEnabled by remember { mutableStateOf(ErvMediaControlHelper.isNotificationListenerEnabled(context)) }
    LaunchedEffect(Unit) {
        listenerEnabled = ErvMediaControlHelper.isNotificationListenerEnabled(context)
    }

    var metadata by remember { mutableStateOf<MediaMetadata?>(null) }
    var playbackState by remember { mutableStateOf<PlaybackState?>(null) }
    var activeController by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(listenerEnabled) {
        if (Build.VERSION.SDK_INT < 21) {
            activeController = null
            metadata = null
            playbackState = null
            return@DisposableEffect onDispose { }
        }
        val controller = if (listenerEnabled) ErvMediaControlHelper.primaryMediaController(context) else null
        activeController = controller
        metadata = controller?.metadata
        playbackState = controller?.playbackState
        if (controller == null) {
            return@DisposableEffect onDispose { }
        }
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(m: MediaMetadata?) {
                metadata = m
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state
            }
        }
        controller.registerCallback(callback)
        onDispose {
            controller.unregisterCallback(callback)
        }
    }

    val onPrimary =
        if (useLightOnDarkBackground) Color.White else MaterialTheme.colorScheme.onSurface
    val onSecondary =
        if (useLightOnDarkBackground) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
    val onMuted =
        if (useLightOnDarkBackground) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant

    val scrollModifier =
        if (useLightOnDarkBackground) {
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
        } else {
            Modifier.verticalScroll(rememberScrollState())
        }

    Column(
        modifier = modifier.then(scrollModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHeaderTitle) {
            Text(
                stringResource(R.string.media_control_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = onPrimary
            )
        }

        if (Build.VERSION.SDK_INT >= 21) {
            if (!listenerEnabled) {
                Text(
                    stringResource(R.string.media_control_listener_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = onMuted
                )
                Text(
                    stringResource(R.string.media_control_restricted_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = onMuted
                )
                Text(
                    stringResource(R.string.media_control_messages_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = onMuted
                )
                OutlinedButton(
                    onClick = { ErvMediaControlHelper.openAppDetailsSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (useLightOnDarkBackground) {
                        ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    border = if (useLightOnDarkBackground) {
                        ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    } else {
                        ButtonDefaults.outlinedButtonBorder
                    }
                ) {
                    Text(stringResource(R.string.media_control_open_app_settings))
                }
                Button(
                    onClick = { ErvMediaControlHelper.openNotificationListenerSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (useLightOnDarkBackground) {
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.22f),
                            contentColor = Color.White
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(stringResource(R.string.media_control_open_listener_settings))
                }
            } else {
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                Text(
                    stringResource(R.string.media_control_now_playing_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = onSecondary
                )
                if (!title.isNullOrBlank() || !artist.isNullOrBlank()) {
                    Text(
                        title?.takeIf { it.isNotBlank() } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = onPrimary
                    )
                    if (!artist.isNullOrBlank()) {
                        Text(
                            artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = onSecondary
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.media_control_no_session),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onMuted
                    )
                }
                val c = activeController
                val playing = playbackState?.state == PlaybackState.STATE_PLAYING
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { c?.transportControls?.skipToPrevious() },
                        enabled = c != null
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.media_control_cd_skip_previous),
                            tint = onPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            val tc = c?.transportControls ?: return@IconButton
                            if (playing) tc.pause() else tc.play()
                        },
                        enabled = c != null
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.media_control_cd_play_pause),
                            tint = onPrimary
                        )
                    }
                    IconButton(
                        onClick = { c?.transportControls?.skipToNext() },
                        enabled = c != null
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.media_control_cd_skip_next),
                            tint = onPrimary
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.media_control_no_session),
                style = MaterialTheme.typography.bodyMedium,
                color = onMuted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutMediaControlSheet(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        WorkoutMediaControlPanel(
            useLightOnDarkBackground = false,
            showHeaderTitle = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        )
    }
}
