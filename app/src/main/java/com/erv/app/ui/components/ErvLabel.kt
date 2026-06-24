package com.erv.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Capitalize the first letter of each word for field labels and section headers. */
fun String.titleCaseWords(): String =
    Regex("\\b[a-z]").replace(this) { it.value.uppercase() }

/** OutlinedTextField / form control caption. */
@Composable
fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
) {
    Text(
        text = text.titleCaseWords(),
        modifier = modifier,
        style = style ?: androidx.compose.material3.LocalTextStyle.current,
    )
}

/** Form section caption above a group of controls (not an OutlinedTextField label). */
@Composable
fun FormSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    color: Color = Color.Unspecified,
) {
    Text(text = text.titleCaseWords(), style = style, color = color, modifier = modifier)
}

/** Form section caption using labelMedium typography. */
@Composable
fun FormSectionLabelMedium(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.titleCaseWords(),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
    )
}

/** Form section caption using titleSmall typography. */
@Composable
fun FormSectionLabelSmall(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text.titleCaseWords(),
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = modifier,
    )
}

/** Settings / form section heading. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.titleCaseWords(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
