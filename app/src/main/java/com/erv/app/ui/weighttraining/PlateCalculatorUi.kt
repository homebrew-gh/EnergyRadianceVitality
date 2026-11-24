package com.erv.app.ui.weighttraining

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.PlateCalculatorBar
import com.erv.app.weighttraining.PlateLoadOption
import com.erv.app.weighttraining.PlateLoadingStyle
import com.erv.app.weighttraining.PlateStack
import com.erv.app.weighttraining.calculatePlateLoad
import com.erv.app.weighttraining.formatDecimalForField
import com.erv.app.weighttraining.formatPlateList
import com.erv.app.weighttraining.nominalWeight
import com.erv.app.weighttraining.nominalWeightToKg
import com.erv.app.weighttraining.weightLoadUnitSuffix

/** What a barbell exercise's set card needs to show plates per side. */
data class PlateCalculatorUiState(
    val loading: PlateLoadingStyle,
    val bars: List<PlateCalculatorBar>,
    val selectedBar: PlateCalculatorBar,
    val plates: List<PlateStack>,
    /** True when no bars or plates are entered under Equipment and a standard gym set is used. */
    val assumedInventory: Boolean,
    val onSelectBar: (String) -> Unit,
)

/**
 * "Per side: 45 · 25 · 5" under a set's weight. When the weight can't be loaded it offers the
 * nearest loadable weights. Tapping the line opens a bar picker.
 */
@Composable
fun PlateLoadLine(
    weightKg: Double?,
    loadUnit: BodyWeightUnit,
    calc: PlateCalculatorUiState,
    onUseWeightKg: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (calc.loading == PlateLoadingStyle.NONE || weightKg == null || weightKg <= 0) return
    val target = nominalWeight(weightKg, loadUnit)
    val result = remember(target, calc.selectedBar, calc.plates, calc.loading) {
        calculatePlateLoad(target, calc.selectedBar.weight, calc.plates, calc.loading)
    }
    val suffix = weightLoadUnitSuffix(loadUnit)
    var showBarPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        val exact = result.exact
        Text(
            text = if (exact != null) {
                "${formatPlateList(exact.plates, calc.loading)} · ${calc.selectedBar.label}"
            } else {
                "Can't load ${formatDecimalForField(target)} $suffix on ${calc.selectedBar.label}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (exact != null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showBarPicker = true }
                .padding(vertical = 2.dp),
        )
        if (exact == null && (result.below != null || result.above != null)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOfNotNull(result.below, result.above).forEach { option ->
                    NearestWeightButton(option, suffix) {
                        onUseWeightKg(nominalWeightToKg(option.total, loadUnit))
                    }
                }
            }
        }
    }

    if (showBarPicker) {
        AlertDialog(
            onDismissRequest = { showBarPicker = false },
            title = { Text("Bar") },
            text = {
                Column {
                    calc.bars.forEach { bar ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { calc.onSelectBar(bar.id) },
                        ) {
                            RadioButton(
                                selected = bar.id == calc.selectedBar.id,
                                onClick = { calc.onSelectBar(bar.id) },
                            )
                            Text(bar.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (calc.assumedInventory) {
                        Text(
                            "Using a standard bar and gym plates. Add yours in Settings under Your Equipment & Gym.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBarPicker = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun NearestWeightButton(option: PlateLoadOption, suffix: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(
            "Use ${formatDecimalForField(option.total)} $suffix",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
