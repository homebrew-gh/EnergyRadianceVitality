package com.erv.app.cycling

import androidx.compose.runtime.compositionLocalOf

val LocalCyclingCsc = compositionLocalOf<CyclingCscBleViewModel> {
    error("LocalCyclingCsc: CyclingCscBleViewModel not provided")
}
