package com.erv.app.programs

import java.util.UUID

private val benchId = "erv-weight-exercise-bench-v1"
private val deadliftId = "erv-weight-exercise-deadlift-v1"
private val squatId = "erv-weight-exercise-squat-v1"
private val ohpId = "erv-weight-exercise-ohp-v1"

enum class ProgramTemplateCategory {
    STRENGTH,
    CONDITIONING,
    RECOVERY,
    CHALLENGE
}

fun ProgramTemplateCategory.displayLabel(): String = when (this) {
    ProgramTemplateCategory.STRENGTH -> "Strength"
    ProgramTemplateCategory.CONDITIONING -> "Conditioning"
    ProgramTemplateCategory.RECOVERY -> "Recovery"
    ProgramTemplateCategory.CHALLENGE -> "Challenge"
}

data class ProgramTemplateOption(
    val id: String,
    val title: String,
    val description: String,
    val category: ProgramTemplateCategory,
    val frequencyLabel: String,
    val build: () -> FitnessProgram
)

/**
 * Built-in program presets: PPL (3-day), alternating full-body strength, cardio base + mobility,
 * and contrast / recovery — easy to duplicate and edit in the app.
 */
object ProgramTemplates {

    val allOptions: List<ProgramTemplateOption> = listOf(
        ProgramTemplateOption(
            id = "ppl_3day",
            title = "Push / Pull / Legs (3×/week)",
            description = "Classic split: push, pull, legs on alternating days; good default for general hypertrophy.",
            category = ProgramTemplateCategory.STRENGTH,
            frequencyLabel = "3 days / week",
            ::templatePplThreeDay
        ),
        ProgramTemplateOption(
            id = "full_body_ab",
            title = "Full body A / B (3×/week)",
            description = "Alternating two full-body sessions (StrongLifts-style frequency) using the four compounds.",
            category = ProgramTemplateCategory.STRENGTH,
            frequencyLabel = "3 days / week",
            ::templateFullBodyAlternating
        ),
        ProgramTemplateOption(
            id = "cardio_mobility",
            title = "Cardio + mobility week",
            description = "Mixed easy cardio, built-in stretches, and one contrast day.",
            category = ProgramTemplateCategory.CONDITIONING,
            frequencyLabel = "6 active days",
            ::templateCardioMobility
        ),
        ProgramTemplateOption(
            id = "recovery_contrast",
            title = "Recovery & contrast",
            description = "Light movement, stretching, sauna and cold plunge — useful as a deload or wellness block.",
            category = ProgramTemplateCategory.RECOVERY,
            frequencyLabel = "Recovery week",
            ::templateRecoveryContrast
        ),
        ProgramTemplateOption(
            id = "75_hard",
            title = "75 Hard–style week",
            description = "Daily checklist (diet, water, reading, Body Tracker photo, two workouts) plus two 45 min flex slots — each workout is cardio or weight training when you start it, not preset to runs.",
            category = ProgramTemplateCategory.CHALLENGE,
            frequencyLabel = "Daily structure",
            ::template75HardWeek
        ),
        ProgramTemplateOption(
            id = "75_soft",
            title = "75 Soft–style week",
            description = "Gentler daily habits and one 45 min flex workout per day (cardio or weights from the Programs sheet); customize rules and the weekly outdoor reminder.",
            category = ProgramTemplateCategory.CHALLENGE,
            frequencyLabel = "Daily structure",
            ::template75SoftWeek
        )
    )

    fun optionById(id: String): ProgramTemplateOption? = allOptions.firstOrNull { it.id == id }
}

private fun templatePplThreeDay(): FitnessProgram {
    val now = System.currentTimeMillis() / 1000
    return FitnessProgram(
        id = UUID.randomUUID().toString(),
        name = "PPL — 3 days / week",
        description = "Monday push, Wednesday pull, Friday legs. Add accessories in the builder as you like.",
        sourceLabel = "Template: PPL",
        createdAtEpochSeconds = now,
        lastModifiedEpochSeconds = now,
        weeklySchedule = listOf(
            ProgramWeekDay(
                1,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.WEIGHT,
                        title = "Push",
                        notes = "Chest, shoulders, triceps — bench and press plus accessories.",
                        weightExerciseIds = listOf(benchId, ohpId)
                    )
                )
            ),
            ProgramWeekDay(
                3,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.WEIGHT,
                        title = "Pull",
                        notes = "Back and biceps — add rows, pull-ups, or curls in the builder.",
                        weightExerciseIds = listOf(deadliftId)
                    )
                )
            ),
            ProgramWeekDay(
                5,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.WEIGHT,
                        title = "Legs",
                        notes = "Squat plus hamstring / calf work as needed.",
                        weightExerciseIds = listOf(squatId)
                    )
                )
            )
        )
    )
}

private fun templateFullBodyAlternating(): FitnessProgram {
    val now = System.currentTimeMillis() / 1000
    val blockA = ProgramDayBlock(
        kind = ProgramBlockKind.WEIGHT,
        title = "Full body A",
        notes = "Squat, bench, deadlift. Add a pulling accessory if you like.",
        weightExerciseIds = listOf(squatId, benchId, deadliftId)
    )
    val blockB = ProgramDayBlock(
        kind = ProgramBlockKind.WEIGHT,
        title = "Full body B",
        notes = "Squat, overhead press, deadlift (or swap deadlift for rows in the builder).",
        weightExerciseIds = listOf(squatId, ohpId, deadliftId)
    )
    return FitnessProgram(
        id = UUID.randomUUID().toString(),
        name = "Full body A / B",
        description = "Train Mon / Wed / Fri — alternate A, B, A one week and B, A, B the next, or rotate as you prefer.",
        sourceLabel = "Template: full body",
        createdAtEpochSeconds = now,
        lastModifiedEpochSeconds = now,
        weeklySchedule = listOf(
            ProgramWeekDay(1, listOf(blockA)),
            ProgramWeekDay(3, listOf(blockB)),
            ProgramWeekDay(5, listOf(blockA))
        )
    )
}

private fun templateCardioMobility(): FitnessProgram {
    val now = System.currentTimeMillis() / 1000
    val mobilityIds = listOf(
        "builtin_hip_flexor_lunge",
        "builtin_standing_hamstring",
        "builtin_shoulder_cross",
        "builtin_cat_cow"
    )
    return FitnessProgram(
        id = UUID.randomUUID().toString(),
        name = "Cardio + mobility",
        description = "Steady cardio most days, short mobility flows, optional intervals mid-week.",
        sourceLabel = "Template: cardio",
        createdAtEpochSeconds = now,
        lastModifiedEpochSeconds = now,
        weeklySchedule = listOf(
            ProgramWeekDay(
                1,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.CARDIO,
                        title = "Easy aerobic",
                        cardioActivity = "RUN",
                        targetMinutes = 30,
                        notes = "Conversational pace."
                    )
                )
            ),
            ProgramWeekDay(
                2,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.STRETCH_CATALOG,
                        title = "Mobility flow",
                        stretchCatalogIds = mobilityIds,
                        targetMinutes = 15
                    )
                )
            ),
            ProgramWeekDay(
                3,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.CARDIO,
                        title = "Bike or elliptical",
                        cardioActivity = "BIKE",
                        targetMinutes = 35
                    )
                )
            ),
            ProgramWeekDay(
                4,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.CARDIO,
                        title = "Intervals (optional)",
                        cardioActivity = "ROWING",
                        targetMinutes = 25,
                        notes = "Warm up, then 6–10 hard / easy intervals."
                    )
                )
            ),
            ProgramWeekDay(
                5,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.CARDIO,
                        cardioActivity = "WALK",
                        targetMinutes = 40
                    )
                )
            ),
            ProgramWeekDay(
                6,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.STRETCH_CATALOG,
                        title = "Longer stretch",
                        stretchCatalogIds = mobilityIds + "builtin_pigeon_glute",
                        targetMinutes = 20
                    )
                )
            ),
            ProgramWeekDay(
                7,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.REST,
                        title = "Rest or easy walk",
                        notes = "Full rest or 20–30 min very easy walk."
                    )
                )
            )
        )
    )
}

private fun templateRecoveryContrast(): FitnessProgram {
    val now = System.currentTimeMillis() / 1000
    return FitnessProgram(
        id = UUID.randomUUID().toString(),
        name = "Recovery & contrast",
        description = "Sauna, cold plunge, light cardio, and stretching spread across the week.",
        sourceLabel = "Template: recovery",
        createdAtEpochSeconds = now,
        lastModifiedEpochSeconds = now,
        weeklySchedule = listOf(
            ProgramWeekDay(
                1,
                listOf(
                    ProgramDayBlock(kind = ProgramBlockKind.CARDIO, cardioActivity = "WALK", targetMinutes = 30)
                )
            ),
            ProgramWeekDay(
                2,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.HEAT_COLD,
                        title = "Sauna",
                        heatColdMode = "SAUNA",
                        targetMinutes = 15,
                        notes = "Hydrate; stop if dizzy."
                    )
                )
            ),
            ProgramWeekDay(
                3,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.STRETCH_CATALOG,
                        title = "Gentle mobility",
                        stretchCatalogIds = listOf("builtin_cat_cow", "builtin_childs_pose", "builtin_neck_side"),
                        targetMinutes = 15
                    )
                )
            ),
            ProgramWeekDay(
                4,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.HEAT_COLD,
                        title = "Cold plunge",
                        heatColdMode = "COLD_PLUNGE",
                        targetMinutes = 5,
                        notes = "Build duration slowly; consult a clinician if you have cardiovascular concerns."
                    )
                )
            ),
            ProgramWeekDay(
                5,
                listOf(
                    ProgramDayBlock(kind = ProgramBlockKind.CARDIO, cardioActivity = "SWIM", targetMinutes = 25)
                )
            ),
            ProgramWeekDay(
                6,
                listOf(
                    ProgramDayBlock(
                        kind = ProgramBlockKind.CUSTOM,
                        title = "Contrast round (optional)",
                        notes = "If available: short sauna rounds with brief cold exposure; keep sessions conservative."
                    )
                )
            ),
            ProgramWeekDay(
                7,
                listOf(
                    ProgramDayBlock(kind = ProgramBlockKind.REST, title = "Full rest")
                )
            )
        )
    )
}

private fun template75HardWeek(): FitnessProgram {
    val now = System.currentTimeMillis() / 1000
    val habits = ProgramDayBlock(
        kind = ProgramBlockKind.OTHER,
        title = "75 Hard — daily habits",
        notes = "Check each line on the dashboard for the selected date. Log both workouts via Start on the Programs sheet (Cardio or Weight Training — not limited to running or walking).",
        checklistItems = listOf(
            "Follow your diet (no alcohol / cheat meals)",
            "Drink 1 gallon (≈3.8 L) water",
            "Read 10 pages of nonfiction",
            "Progress photo logged in Body Tracker for this date (on-device)",
            "Two separate 45+ min workouts completed; at least one was outdoors"
        )
    )
    val firstWorkout = ProgramDayBlock(
        kind = ProgramBlockKind.FLEX_TRAINING,
        title = "Workout 1 — cardio or weights (45+ min)",
        targetMinutes = 45,
        notes = "Tap Start and choose Cardio timer or Weight Training live session. This can be gym weights, bike, swim, sport, etc. — not just a run. For the outdoor rule, pick an outdoor cardio session or an outdoor-friendly strength session if that matches your challenge rules."
    )
    val secondWorkout = ProgramDayBlock(
        kind = ProgramBlockKind.FLEX_TRAINING,
        title = "Workout 2 — cardio or weights (45+ min)",
        targetMinutes = 45,
        notes = "Second session of the day: again Cardio or Weight Training from the Programs sheet. Split indoor vs outdoor however fits your plan; replace these flex blocks with fixed cardio or weight blocks in the builder if you want the same thing every day."
    )
    val blocks = listOf(habits, firstWorkout, secondWorkout)
    return FitnessProgram(
        id = UUID.randomUUID().toString(),
        name = "75 Hard–style (template)",
        description = "Not affiliated with any trademark. Every day: a habit checklist (including Body Tracker progress photos) plus two flex training slots. Each workout is either cardio or weight training when you start it — not preset to runs or walks. Edit the week, then Save.",
        sourceLabel = "Template: 75 Hard–style",
        createdAtEpochSeconds = now,
        lastModifiedEpochSeconds = now,
        weeklySchedule = (1..7).map { d -> ProgramWeekDay(dayOfWeek = d, blocks = blocks) }
    )
}

private fun template75SoftWeek(): FitnessProgram {
    val now = System.currentTimeMillis() / 1000
    val habits = ProgramDayBlock(
        kind = ProgramBlockKind.OTHER,
        title = "75 Soft — daily habits",
        notes = "Softer than 75 Hard — personalize checklist text. Check off items on the dashboard per day.",
        checklistItems = listOf(
            "Mindful eating (75 Soft often allows social drinking / occasional treats — define your rule)",
            "Drink ~3 L water",
            "Read 10 pages",
            "At least one outdoor workout this week (pick a day; see Saturday note or use Start → Cardio / Weight for an outdoor session)"
        )
    )
    val workout = ProgramDayBlock(
        kind = ProgramBlockKind.FLEX_TRAINING,
        title = "Daily workout — cardio or weights (45 min)",
        targetMinutes = 45,
        notes = "Tap Start on the Programs sheet for Cardio or Weight Training. Indoor or outdoor is up to you except where your checklist calls for an outdoor day."
    )
    val blocksMonSat = listOf(habits, workout)
    val outdoorNote = ProgramDayBlock(
        kind = ProgramBlockKind.CUSTOM,
        title = "Outdoor session reminder",
        notes = "Use one day this week for an outdoor workout (cardio or weights). Start from the Programs sheet, or add a dedicated cardio/weight block here if you prefer a fixed schedule."
    )
    return FitnessProgram(
        id = UUID.randomUUID().toString(),
        name = "75 Soft–style (template)",
        description = "Not affiliated with any trademark. Daily flex training is cardio or weight training — not locked to running. Includes a Saturday reminder for a weekly outdoor session; edit to match your rules.",
        sourceLabel = "Template: 75 Soft–style",
        createdAtEpochSeconds = now,
        lastModifiedEpochSeconds = now,
        weeklySchedule = (1..7).map { d ->
            when (d) {
                6 -> ProgramWeekDay(d, listOf(habits, workout, outdoorNote))
                else -> ProgramWeekDay(d, blocksMonSat)
            }
        }
    )
}
