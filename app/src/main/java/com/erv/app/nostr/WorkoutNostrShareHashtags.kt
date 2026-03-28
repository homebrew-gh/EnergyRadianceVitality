package com.erv.app.nostr

private val HASHTAG_IN_TEXT = Regex("#([a-zA-Z0-9_]+)")

/** Topic tags always included in kind 1 workout share content and `t` tags. */
internal val workoutShareBaseTopicTags = listOf("erv", "workout", "fitness")

/**
 * Parses optional user hashtag input. Only `#word` tokens count (word: letters, digits, underscore).
 * Duplicates and [workoutShareBaseTopicTags] are dropped. At most 20 tags.
 */
internal fun parseExtraWorkoutHashtagTopics(raw: String): List<String> {
    val skip = workoutShareBaseTopicTags.toSet()
    val out = LinkedHashSet<String>()
    for (m in HASHTAG_IN_TEXT.findAll(raw)) {
        if (out.size >= 20) break
        val t = m.groupValues[1].lowercase()
        if (t.isNotEmpty() && t !in skip) out.add(t)
    }
    return out.toList()
}

internal fun buildWorkoutShareHashtagContentLine(extraTopics: List<String>): String {
    val base = workoutShareBaseTopicTags.joinToString(" ") { "#$it" }
    if (extraTopics.isEmpty()) return base
    return base + extraTopics.joinToString("") { " #$it" }
}

/** Parses the full hashtag line the user wants in the note, preserving their chosen base tags. */
internal fun parseWorkoutShareTopics(raw: String): List<String> {
    val out = LinkedHashSet<String>()
    for (m in HASHTAG_IN_TEXT.findAll(raw)) {
        if (out.size >= 20) break
        val t = m.groupValues[1].lowercase()
        if (t.isNotEmpty()) out.add(t)
    }
    return out.toList()
}

internal fun buildWorkoutShareHashtagContentLineFromTopics(topics: List<String>): String =
    topics.joinToString(" ") { "#$it" }

/** Full `t` tag rows for a kind 1 workout note: base topics plus extras. */
internal fun workoutShareKind1TopicTags(extraTopics: List<String>): List<List<String>> {
    val tags = workoutShareBaseTopicTags.map { listOf("t", it) }.toMutableList()
    val seen = workoutShareBaseTopicTags.toMutableSet()
    for (t in extraTopics) {
        if (t !in seen) {
            seen.add(t)
            tags.add(listOf("t", t))
        }
    }
    return tags
}

internal fun workoutShareKind1TopicTagsFromTopics(topics: List<String>): List<List<String>> =
    topics.map { listOf("t", it) }
