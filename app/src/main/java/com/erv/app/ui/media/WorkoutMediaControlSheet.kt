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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.media.ErvMediaControlHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutMediaControlSheet(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var volume by remember {
        mutableFloatStateOf(
            ErvMediaControlHelper.musicStreamVolume(context).toFloat()
        )
    }
    val maxVol = remember { ErvMediaControlHelper.musicStreamMaxVolume(context).toFloat().coerceAtLeast(1f) }

    var listenerEnabled by remember { mutableStateOf(ErvMediaControlHelper.isNotificationListenerEnabled(context)) }
    LaunchedEffect(visible) {
        if (visible) {
            listenerEnabled = ErvMediaControlHelper.isNotificationListenerEnabled(context)
        }
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

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            delay(600)
            volume = ErvMediaControlHelper.musicStreamVolume(context).toFloat()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.media_control_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                stringResource(R.string.media_control_volume_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    ErvMediaControlHelper.setMusicStreamVolume(context, it.toInt())
                },
                valueRange = 0f..maxVol,
                steps = (maxVol - 1).toInt().coerceAtLeast(0)
            )

            if (Build.VERSION.SDK_INT >= 21) {
                if (!listenerEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.media_control_listener_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { ErvMediaControlHelper.openNotificationListenerSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.media_control_open_listener_settings))
                    }
                } else {
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    if (!title.isNullOrBlank() || !artist.isNullOrBlank()) {
                        Text(
                            title ?: "—",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!artist.isNullOrBlank()) {
                            Text(
                                artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.media_control_no_session),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.media_control_cd_skip_previous))
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
                                contentDescription = stringResource(R.string.media_control_cd_play_pause)
                            )
                        }
                        IconButton(
                            onClick = { c?.transportControls?.skipToNext() },
                            enabled = c != null
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.media_control_cd_skip_next))
                        }
                    }
                }
            }
        }
    }
}
