import type { WeightCatalogExercise } from "./catalog";

export type WeightSetLoggingStyle = "reps" | "time_only" | "reps_or_time";

const TIME_ONLY_PER_SET_IDS = new Set([
  "erv-weight-exercise-bw-plank-v1",
  "erv-weight-exercise-bw-side-plank-v1",
  "erv-weight-exercise-db-farmers-carry-v1",
  "erv-weight-exercise-kb-carry-v1",
]);

const TIME_ONLY_LOADABLE_IDS = new Set([
  "erv-weight-exercise-db-farmers-carry-v1",
  "erv-weight-exercise-kb-carry-v1",
]);

const KB_REP_ONLY_SKILL_IDS = new Set([
  "erv-weight-exercise-kb-windmill-v1",
  "erv-weight-exercise-kb-turkish-getup-v1",
]);

const NON_KB_REPS_OR_TIME_IDS = new Set([
  "erv-weight-exercise-db-swing-v1",
  "erv-weight-exercise-db-renegade-row-v1",
  "erv-weight-exercise-db-goblet-squat-v1",
  "erv-weight-exercise-db-lunge-v1",
  "erv-weight-exercise-db-reverse-lunge-v1",
  "erv-weight-exercise-db-step-up-v1",
  "erv-weight-exercise-db-bulgarian-split-v1",
  "erv-weight-exercise-db-sumo-squat-v1",
  "erv-weight-exercise-bw-pushup-v1",
  "erv-weight-exercise-bw-pike-pushup-v1",
  "erv-weight-exercise-bw-air-squat-v1",
  "erv-weight-exercise-bw-reverse-lunge-v1",
  "erv-weight-exercise-bw-mountain-climber-v1",
  "erv-weight-exercise-bw-burpee-v1",
  "erv-weight-exercise-bb-push-press-v1",
  "erv-weight-exercise-bb-lunge-walk-v1",
  "erv-weight-exercise-bw-pullup-v1",
  "erv-weight-exercise-bw-chinup-v1",
  "erv-weight-exercise-bw-dip-v1",
]);

export function builtinSetLoggingStyleForId(id: string): WeightSetLoggingStyle {
  if (TIME_ONLY_PER_SET_IDS.has(id)) return "time_only";
  if (NON_KB_REPS_OR_TIME_IDS.has(id)) return "reps_or_time";
  if (
    id.startsWith("erv-weight-exercise-kb-") &&
    !KB_REP_ONLY_SKILL_IDS.has(id) &&
    !TIME_ONLY_PER_SET_IDS.has(id)
  ) {
    return "reps_or_time";
  }
  return "reps";
}

export function exerciseSetLoggingStyle(exercise: WeightCatalogExercise): WeightSetLoggingStyle {
  if (exercise.id.startsWith("erv-weight-exercise-")) {
    return builtinSetLoggingStyleForId(exercise.id);
  }
  if (exercise.timePerSetCapable && exercise.repPerSetCapable === false) return "time_only";
  if (exercise.timePerSetCapable && exercise.repPerSetCapable !== false) return "reps_or_time";
  if (exercise.timePerSetCapable) return "time_only";
  return "reps";
}

export function exerciseSupportsTargetWeight(exercise: WeightCatalogExercise): boolean {
  if (TIME_ONLY_LOADABLE_IDS.has(exercise.id)) return true;
  return exerciseSetLoggingStyle(exercise) !== "time_only";
}

export function withResolvedBuiltinExerciseFlags(
  exercise: WeightCatalogExercise,
): WeightCatalogExercise {
  if (!exercise.id.startsWith("erv-weight-exercise-")) return exercise;
  const style = builtinSetLoggingStyleForId(exercise.id);
  return {
    ...exercise,
    timePerSetCapable: style !== "reps",
    repPerSetCapable: style !== "time_only",
  };
}
