package com.erv.app.ui.stretching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.groupedByCategory
import com.erv.app.stretching.stretchCategoryDisplayLabel

/**
 * Two-step picker: body area (catalog category), then stretches in that area — similar flow to weight exercise pick.
 */
@Composable
fun StretchPickStretchDialog(
    catalog: List<StretchCatalogEntry>,
    excludeIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val grouped = remember(catalog, excludeIds) {
        catalog
            .filter { it.id !in excludeIds }
            .groupedByCategory()
    }
    var selectedCategoryKey by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            when (val key = selectedCategoryKey) {
                null -> Text("Body area", style = MaterialTheme.typography.titleLarge)
                else -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { selectedCategoryKey = null }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Change body area"
                        )
                    }
                    Text(
                        text = stretchCategoryDisplayLabel(key),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    grouped.isEmpty() -> {
                        Text(
                            "No stretches left to add.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    selectedCategoryKey == null -> {
                        Text(
                            "Pick a body area, then choose a stretch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        grouped.forEach { (catKey, list) ->
                            TextButton(
                                onClick = { selectedCategoryKey = catKey },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${stretchCategoryDisplayLabel(catKey)} (${list.size})",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    else -> {
                        val inGroup = grouped.firstOrNull { it.first == selectedCategoryKey }?.second.orEmpty()
                        if (inGroup.isEmpty()) {
                            Text(
                                "No stretches in this area.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            inGroup.forEach { entry ->
                                TextButton(
                                    onClick = { onPick(entry.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val sideNote = if (entry.requiresBothSides) " · Both sides" else ""
                                    Text(
                                        "${entry.name}$sideNote",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
