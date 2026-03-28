package com.erv.app.programs

import java.time.LocalDate

/** Stable key for per-day Program completion rows (per program, block, line, calendar day). */
fun programChecklistCompletionKey(
    programId: String,
    blockId: String,
    itemIndex: Int,
    date: LocalDate
): String = listOf(programId, blockId, itemIndex.toString(), date.toString()).joinToString("|")

/** Stable key for completion of a whole non-checklist block on a specific calendar day. */
fun programBlockCompletionKey(
    programId: String,
    blockId: String,
    date: LocalDate
): String = listOf(programId, blockId, "done", date.toString()).joinToString("|")

fun programCompletionDateString(key: String): String? {
    val raw = key.substringAfterLast('|', "")
    return raw.takeIf {
        runCatching { LocalDate.parse(it) }.isSuccess
    }
}
