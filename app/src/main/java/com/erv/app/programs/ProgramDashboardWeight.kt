package com.erv.app.programs

import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRoutine

/** Resolves a saved routine or builds a synthetic one from program exercise ids. */
fun weightRoutineForProgramBlock(block: ProgramDayBlock, library: WeightLibraryState): WeightRoutine? {
    block.weightRoutineId?.let { rid ->
        library.routines.firstOrNull { it.id == rid }?.let { return it }
    }
    val ids = block.weightExerciseIds.filter { library.exerciseById(it) != null }
    if (ids.isEmpty()) return null
    return WeightRoutine(
        id = "erv-program-block-${block.id}",
        name = block.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Workout",
        exerciseIds = ids
    )
}
