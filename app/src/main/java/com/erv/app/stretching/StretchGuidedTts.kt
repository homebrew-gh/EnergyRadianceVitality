package com.erv.app.stretching

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.erv.app.data.StretchGuidedTtsVoice
import java.util.Locale

private fun matchesLocale(voice: Voice, locale: Locale): Boolean {
    if (voice.locale.language != locale.language) return false
    if (locale.country.isNotEmpty() && voice.locale.country.isNotEmpty() &&
        voice.locale.country != locale.country
    ) {
        return false
    }
    return true
}

/**
 * Best-effort gender hint from voice name and [Voice.getFeatures] (Google and other engines vary).
 * Returns null when unknown.
 */
private fun voiceGenderHint(voice: Voice): String? {
    val combined = buildString {
        append(voice.name.lowercase())
        append(' ')
        voice.features.forEach { append(it.lowercase()).append(' ') }
    }
    if (combined.contains("female") || combined.contains("woman")) return "female"
    if (combined.contains("#female")) return "female"
    if (Regex("\\bmale\\b").containsMatchIn(combined)) return "male"
    if (combined.contains("#male") && !combined.contains("#female")) return "male"
    return null
}

/**
 * Applies [locale] and, unless [pref] is [StretchGuidedTtsVoice.SYSTEM_DEFAULT], selects a [Voice]
 * from the engine when possible. Gender matching is heuristic and may fall back to any voice for the locale.
 * Tries fallback locales (e.g. US English) so third-party engines (Sherpa, offline packs) can still get a language.
 */
fun applyStretchGuidedTtsVoice(tts: TextToSpeech, locale: Locale, pref: StretchGuidedTtsVoice) {
    val localesToTry = buildList {
        add(locale)
        if (locale != Locale.US) add(Locale.US)
        if (locale.language != Locale.ENGLISH.language) add(Locale.ENGLISH)
    }.distinctBy { it.toLanguageTag() }
    var appliedLocale: Locale? = null
    for (loc in localesToTry) {
        val r = tts.setLanguage(loc)
        if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
            appliedLocale = loc
            break
        }
    }
    if (appliedLocale == null) {
        for (loc in listOf(Locale.US, Locale.UK, Locale.ENGLISH)) {
            val r = tts.setLanguage(loc)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                appliedLocale = loc
                break
            }
        }
        if (appliedLocale == null) {
            tts.setLanguage(Locale.US)
            return
        }
    }
    when (pref) {
        StretchGuidedTtsVoice.SYSTEM_DEFAULT -> {
            val def = tts.defaultVoice
            if (def != null) {
                tts.setVoice(def)
            }
        }
        StretchGuidedTtsVoice.PREFER_FEMALE, StretchGuidedTtsVoice.PREFER_MALE -> {
            val voices = tts.voices ?: return
            val candidates = voices.filter { matchesLocale(it, appliedLocale) }
                .ifEmpty { voices.filter { it.locale.language == appliedLocale.language } }
            if (candidates.isEmpty()) return
            val wantFemale = pref == StretchGuidedTtsVoice.PREFER_FEMALE
            val wanted = if (wantFemale) "female" else "male"
            val preferred = candidates.filter { voiceGenderHint(it) == wanted }
            val chosen = preferred.firstOrNull() ?: candidates.firstOrNull()
            chosen?.let { tts.setVoice(it) }
        }
    }
}
