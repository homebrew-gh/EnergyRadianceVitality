package com.erv.app.dataexport

enum class UserDataSection(
    val label: String,
    val description: String,
    val exportCategory: DataExportCategory,
    val relayBacked: Boolean,
) {
    WEIGHT_TRAINING(
        label = "Weight training",
        description = "Exercises, routines, and workout logs",
        exportCategory = DataExportCategory.WEIGHT_TRAINING,
        relayBacked = true,
    ),
    CARDIO(
        label = "Cardio",
        description = "Activities, routines, quick launches, and logs",
        exportCategory = DataExportCategory.CARDIO,
        relayBacked = true,
    ),
    STRETCHING(
        label = "Stretching",
        description = "Stretch routines and logs",
        exportCategory = DataExportCategory.STRETCHING,
        relayBacked = true,
    ),
    HEAT_COLD(
        label = "Heat and cold",
        description = "Sauna and cold-plunge logs",
        exportCategory = DataExportCategory.HEAT_COLD,
        relayBacked = true,
    ),
    LIGHT_THERAPY(
        label = "Light therapy",
        description = "Devices, routines, and session logs",
        exportCategory = DataExportCategory.LIGHT_THERAPY,
        relayBacked = true,
    ),
    SUPPLEMENTS(
        label = "Supplements",
        description = "Supplements, routines, and intake logs",
        exportCategory = DataExportCategory.SUPPLEMENTS,
        relayBacked = true,
    ),
    PROGRAMS(
        label = "Programs",
        description = "Programs and completion history",
        exportCategory = DataExportCategory.PROGRAMS,
        relayBacked = true,
    ),
    UNIFIED_ROUTINES(
        label = "Unified workouts",
        description = "Routines, sessions, and active run state",
        exportCategory = DataExportCategory.UNIFIED_ROUTINES,
        relayBacked = false,
    ),
    BODY_TRACKER(
        label = "Body tracker",
        description = "Measurements, notes, and photos",
        exportCategory = DataExportCategory.BODY_TRACKER,
        relayBacked = true,
    ),
    REMINDERS(
        label = "Reminders",
        description = "Routine reminder schedules",
        exportCategory = DataExportCategory.REMINDERS,
        relayBacked = false,
    ),
    PERSONAL_DATA(
        label = "Personal data",
        description = "Goals, gym equipment, saved devices, and local profile",
        exportCategory = DataExportCategory.PERSONAL_DATA,
        relayBacked = true,
    );
}
