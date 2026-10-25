package com.erv.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.R

@Composable
fun WorkoutKind1SharePreviewDialog(
    content: String,
    socialRelayUrls: List<String>,
    routeImageNote: String? = null,
    confirming: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    headerContent: @Composable (() -> Unit)? = null,
) {
    val canPost = socialRelayUrls.isNotEmpty() && !confirming
    AlertDialog(
        onDismissRequest = { if (!confirming) onDismiss() },
        title = { Text(stringResource(R.string.kind1_share_preview_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                headerContent?.invoke()
                if (headerContent != null) {
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = stringResource(R.string.kind1_share_preview_relays_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                if (socialRelayUrls.isEmpty()) {
                    Text(
                        text = stringResource(R.string.kind1_share_no_social_relays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    socialRelayUrls.forEach { url ->
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.kind1_share_preview_note_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                )
                routeImageNote?.let { note ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.kind1_share_preview_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canPost) {
                Text(
                    if (confirming) {
                        stringResource(R.string.kind1_share_publishing)
                    } else {
                        stringResource(R.string.kind1_share_confirm)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !confirming) {
                Text(stringResource(R.string.kind1_share_cancel))
            }
        },
    )
}
