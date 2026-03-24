package com.erv.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErvSummarySheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "About ERV",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Energy Radiance Vitality",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Your data is yours",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "ERV does not collect your health logs or run company analytics on what you track. " +
                            "Your information stays on your device. If you turn on Nostr, you choose which relays " +
                            "receive encrypted backups or public posts—ERV never holds your keys on our servers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text(
                text = "What you can do",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val bullets = listOf(
                "Log supplements with routines and reminders.",
                "Run light therapy sessions and keep a daily log.",
                "Track cardio with timers, optional GPS routes, routines, and quick starts.",
                "Record weight training—including live workouts—and browse your history.",
                "Log sauna and cold plunge sessions with optional timers.",
                "Track stretching and guided routines.",
                "Set weekly goals on the dashboard and see progress at a glance.",
                "Optionally sync encrypted activity to Nostr relays you add in Settings.",
                "Share workouts or notes socially over Nostr when you enable it—only what you choose to publish.",
                "Import and manage data from Settings when you want to move or archive it.",
            )
            bullets.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            Text(
                text = "What is Nostr?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Nostr is an open protocol for simple, decentralized feeds: you control an identity (keys), " +
                    "pick relays that store or forward your events, and use any compatible app. " +
                    "No single company owns the network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExternalLinkButton("nostr.com — overview", "https://nostr.com/", uriHandler)
            ExternalLinkButton("nostr.how — beginner guide", "https://nostr.how/", uriHandler)
            ExternalLinkButton("Nostr protocol & NIPs (technical)", "https://github.com/nostr-protocol/nips", uriHandler)

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Social posting",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "When you connect a Nostr account in Settings, ERV can publish public notes—such as workout " +
                    "shares—so people who follow you in other clients can see them. Your followers use whatever " +
                    "Nostr app they like; you stay in control of what gets posted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Popular Nostr clients (examples)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ExternalLinkButton("Amethyst (Android)", "https://play.google.com/store/apps/details?id=com.vitorpamplona.amethyst", uriHandler)
            ExternalLinkButton("Damus (iPhone)", "https://apps.apple.com/app/damus/id1628663131", uriHandler)
            ExternalLinkButton("Wisp (Android)", "https://wisp.mobile/", uriHandler)
            ExternalLinkButton("Nostur (iPhone & Mac)", "https://nostur.com/", uriHandler)
            ExternalLinkButton(
                "Primal (Google Play)",
                "https://play.google.com/store/apps/details?id=net.primal.android",
                uriHandler,
            )
            ExternalLinkButton("Ditto", "https://ditto.pub/", uriHandler)
        }
    }
}

@Composable
private fun ExternalLinkButton(label: String, url: String, uriHandler: UriHandler) {
    TextButton(
        onClick = { uriHandler.openUri(url) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
