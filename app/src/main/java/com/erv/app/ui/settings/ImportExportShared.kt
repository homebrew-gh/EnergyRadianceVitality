package com.erv.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

internal object ImportExportDocAssets {
    const val KEY_WEIGHT_AI = "weight_ai"
    const val KEY_WEIGHT_CSV = "weight_csv"
    const val KEY_WEIGHT_BUILTIN = "weight_builtin"
    const val KEY_CARDIO_AI = "cardio_ai"
    const val KEY_CARDIO_CSV = "cardio_csv"
    const val KEY_CARDIO_NOSTR = "cardio_nostr"
    const val KEY_PROGRAM_AI = "program_ai"
    const val KEY_PRIVACY_POLICY = "privacy_policy"
    const val KEY_PERMISSIONS_GUIDE = "permissions_guide"

    fun pathForKey(docKey: String): String? = when (docKey) {
        KEY_WEIGHT_AI -> "import_export/weight_training_import_ai_guide.md"
        KEY_WEIGHT_CSV -> "import_export/weight_training_import_csv_guide.md"
        KEY_WEIGHT_BUILTIN -> "import_export/weight_training_builtin_exercise_ids.md"
        KEY_CARDIO_AI -> "import_export/cardio_training_import_ai_guide.md"
        KEY_CARDIO_CSV -> "import_export/cardio_training_import_csv_guide.md"
        KEY_CARDIO_NOSTR -> "import_export/cardio_training_nostr_events_reference.md"
        KEY_PROGRAM_AI -> "import_export/programs_import_ai_guide.md"
        KEY_PRIVACY_POLICY -> "privacy_policy.md"
        KEY_PERMISSIONS_GUIDE -> "permissions_guide.md"
        else -> null
    }

    fun titleForKey(docKey: String): String = when (docKey) {
        KEY_WEIGHT_AI -> "Weight Training Import AI Guide"
        KEY_WEIGHT_CSV -> "Weight Training Import CSV Guide"
        KEY_WEIGHT_BUILTIN -> "Weight Training Built-In Exercise IDs"
        KEY_CARDIO_AI -> "Cardio Training Import AI Guide"
        KEY_CARDIO_CSV -> "Cardio Training Import CSV Guide"
        KEY_CARDIO_NOSTR -> "Cardio Training Nostr Events Reference"
        KEY_PROGRAM_AI -> "Programs Import AI / Coach Guide"
        KEY_PRIVACY_POLICY -> "Privacy Policy"
        KEY_PERMISSIONS_GUIDE -> "Android Permissions"
        else -> "Reference"
    }

    val weightReferenceKeys: List<String> = listOf(KEY_WEIGHT_AI, KEY_WEIGHT_CSV, KEY_WEIGHT_BUILTIN)
    val cardioReferenceKeys: List<String> = listOf(KEY_CARDIO_AI, KEY_CARDIO_CSV, KEY_CARDIO_NOSTR)
    val programReferenceKeys: List<String> = listOf(KEY_PROGRAM_AI, KEY_WEIGHT_BUILTIN)

    fun requirePathForKey(docKey: String): String =
        pathForKey(docKey) ?: error("Unknown import/export doc key: $docKey")
}

fun readAssetUtf8(context: Context, assetPath: String): String =
    context.assets.open(assetPath).use { stream ->
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
    }

fun buildCombinedImportReferenceMarkdown(
    context: Context,
    keys: List<String>,
    bundleTitle: String,
): String = buildString {
    appendLine("# $bundleTitle")
    appendLine()
    appendLine(
        "This file combines every in-app import reference for this training type. " +
            "Share or save it and attach it to your AI assistant when building import files."
    )
    for (key in keys) {
        val path = ImportExportDocAssets.requirePathForKey(key)
        val docTitle = ImportExportDocAssets.titleForKey(key)
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("# $docTitle")
        appendLine()
        appendLine(readAssetUtf8(context, path).trimEnd())
        appendLine()
    }
}

fun shareMarkdownFromCache(context: Context, file: File): Boolean = try {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, "Save Or Share Reference Bundle").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
    true
} catch (_: Exception) {
    false
}

@Composable
fun ImportSectionTitle(text: String, first: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = if (first) 0.dp else 18.dp, bottom = 6.dp)
    )
}

@Composable
fun ImportReferenceCollapsibleSection(
    sectionTitle: String,
    summaryWhenCollapsed: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onShareBundle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(sectionTitle, style = MaterialTheme.typography.titleSmall)
                if (!expanded) {
                    Text(
                        summaryWhenCollapsed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onShareBundle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(
                        "Save Or Share All Reference Docs For AI",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun DocLinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
            supportingContent = {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsImportDocViewerScreen(
    docKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val body = remember(docKey, context) {
        val path = runCatching { ImportExportDocAssets.requirePathForKey(docKey) }.getOrNull()
        if (path == null) {
            "Unknown document."
        } else {
            try {
                readAssetUtf8(context, path)
            } catch (_: Exception) {
                "Could not load this document from the app bundle."
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ImportExportDocAssets.titleForKey(docKey)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}
