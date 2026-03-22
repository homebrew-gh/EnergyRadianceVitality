package com.erv.app.ui.cardio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erv.app.MainActivity
import com.erv.app.R
import com.erv.app.cardio.CardioLiveWorkoutConstants
import com.erv.app.ui.theme.ErvTheme
import kotlinx.coroutines.delay

/** Compact UI inside a notification bubble (API 30+) during a live cardio timer. */
class CardioWorkoutBubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startedAt = intent.getLongExtra(CardioLiveWorkoutConstants.EXTRA_BUBBLE_SESSION_START, 0L)
        if (startedAt <= 0L) {
            finish()
            return
        }

        setContent {
            val dark = isSystemInDarkTheme()
            ErvTheme(darkTheme = dark) {
                var nowEpoch by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
                LaunchedEffect(startedAt) {
                    while (true) {
                        nowEpoch = System.currentTimeMillis() / 1000L
                        delay(1000L)
                    }
                }
                val elapsed = (nowEpoch - startedAt).coerceAtLeast(0L)
                val elapsedLabel = remember(elapsed) { formatCardioBubbleElapsed(elapsed) }

                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.cardio_live_bubble_title),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            elapsedLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                startActivity(
                                    Intent(this@CardioWorkoutBubbleActivity, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putExtra(CardioLiveWorkoutConstants.EXTRA_OPEN_CARDIO_LIVE, true)
                                    }
                                )
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.cardio_live_bubble_open_full))
                        }
                    }
                }
            }
        }
    }
}

private fun formatCardioBubbleElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
