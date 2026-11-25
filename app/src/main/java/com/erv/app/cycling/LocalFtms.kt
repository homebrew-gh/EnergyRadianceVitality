package com.erv.app.cycling

import androidx.compose.runtime.compositionLocalOf

val LocalFtms = compositionLocalOf<FtmsBleViewModel> {
    error("LocalFtms: FtmsBleViewModel not provided")
}
