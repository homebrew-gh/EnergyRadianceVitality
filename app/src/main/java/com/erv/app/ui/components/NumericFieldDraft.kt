package com.erv.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.erv.app.weighttraining.numericTextMatches

/**
 * Keeps what the athlete is typing in a numeric field that is backed by a stored number.
 *
 * Without this, a field whose text is rebuilt from the stored value on every keystroke loses
 * in-progress input: typing `2.` snaps back to `2`, so `2.5` can never be entered. The draft is
 * shown while it still stands for the stored value; when the stored value changes from elsewhere
 * (a timer, a prefill, a unit switch), the stored text wins.
 */
class NumericFieldDraft internal constructor(private val draft: MutableState<String>) {
    /** Text to show in the field for the stored value's text [stored]. */
    fun display(stored: String): String =
        if (numericTextMatches(draft.value, stored)) draft.value else stored

    /** Call from `onValueChange` before parsing [text] into the stored value. */
    fun onEdit(text: String) {
        draft.value = text
    }
}

@Composable
fun rememberNumericFieldDraft(initial: String): NumericFieldDraft =
    remember { NumericFieldDraft(mutableStateOf(initial)) }
