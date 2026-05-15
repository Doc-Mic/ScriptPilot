package com.mic.scriptpilot.ui.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatProjectTimestamp(epochMillis: Long): String {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    return fmt.format(Date(epochMillis))
}
