package com.erv.app.nostr

import androidx.compose.runtime.compositionLocalOf

val LocalKeyManager = compositionLocalOf<KeyManager> {
    error("LocalKeyManager not provided")
}
