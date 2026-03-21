package com.erv.app.ui.weighttraining

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.erv.app.R

@Composable
fun WeightLiveWorkoutFgsDisclosureDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_live_fgs_dialog_title)) },
        text = { Text(stringResource(R.string.weight_live_fgs_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.weight_live_fgs_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.weight_live_fgs_dialog_cancel))
            }
        }
    )
}
