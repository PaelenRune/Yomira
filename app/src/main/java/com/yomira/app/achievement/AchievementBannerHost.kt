package com.yomira.app.achievement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yomira.app.i18n.LocaleManagerCompat
import com.yomira.domain.journey.AchievementUnlockEvent
import com.yomira.feature.journey.YomiraAchievementBanner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AchievementBannerHost(events: SharedFlow<AchievementUnlockEvent>) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current
    val sound = remember(context) { AchievementSoundPlayer(context.applicationContext) }
    var current by remember { mutableStateOf<AchievementUnlockEvent?>(null) }
    var visible by remember { mutableStateOf(false) }
    var dismissal by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

    LaunchedEffect(events, lifecycle, sound, view) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                // Buffer here, not in the award path: simultaneous tiers each get a full celebration.
                events.buffer(Channel.UNLIMITED).collect { event ->
                    current = event
                    val dismissed = CompletableDeferred<Unit>()
                    dismissal = dismissed
                    visible = true
                    sound.haptic(view)
                    val playback = launch { sound.play() }
                    withTimeoutOrNull(3_500) { dismissed.await() }
                    visible = false
                    playback.cancel()
                    delay(400) // Complete the outgoing animation before showing the next badge.
                    current = null
                    dismissal = null
                }
            } finally {
                visible = false
                current = null
                dismissal = null
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        YomiraAchievementBanner(
            event = current,
            visible = visible,
            onDismiss = { dismissal?.complete(Unit) },
            modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            turkish = LocaleManagerCompat.isTurkish(),
        )
    }
}
