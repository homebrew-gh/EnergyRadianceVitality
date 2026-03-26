package com.erv.app.hr

import androidx.compose.runtime.compositionLocalOf

val LocalHeartRateBle = compositionLocalOf<HeartRateBleViewModel> {
    error("LocalHeartRateBle: HeartRateBleViewModel not provided")
}
