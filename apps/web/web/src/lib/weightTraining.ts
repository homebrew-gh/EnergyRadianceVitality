/** Matches Android WeightSync d-tags and JSON payloads. */

import {
  FALLBACK_WEIGHT_EXERCISES,
  mergeWeightExerciseLibrary,
  type WeightCatalogExercise,
} from "./catalog";

export const WEIGHT_EXERCISES_D_TAG = "erv/weight/exercises";
export const WEIGHT_ROUTINES_D_TAG = "erv/weight/routines";

export type WeightRoutine = {
  id: string;
  name: string;
  exerciseIds: string[];
  notes?: string | null;
  lastModifiedEpochSeconds: number;
};

export type WeightRoutinesPayload = {
  routines: WeightRoutine[];
};

export type WeightExercise = WeightCatalogExercise;

export type WeightExercisesPayload = {
  exercises: WeightExercise[];
};

export function parseRoutinesPayload(raw: string): WeightRoutine[] {
  const parsed = JSON.parse(raw) as WeightRoutinesPayload;
  return Array.isArray(parsed.routines) ? parsed.routines : [];
}

export function parseExercisesPayload(raw: string): WeightExercise[] {
  const parsed = JSON.parse(raw) as WeightExercisesPayload;
  return Array.isArray(parsed.exercises) ? parsed.exercises : [];
}

export function routinesPayload(routines: WeightRoutine[]): string {
  return JSON.stringify({ routines } satisfies WeightRoutinesPayload);
}

export function mergeExerciseCatalog(
  catalogExercises: WeightExercise[],
  relayExercises: WeightExercise[] = [],
): WeightExercise[] {
  return mergeWeightExerciseLibrary(catalogExercises, relayExercises);
}

export function exerciseLabel(
  exerciseId: string,
  catalog: WeightExercise[],
): string {
  return catalog.find((e) => e.id === exerciseId)?.name ?? exerciseId;
}

/** @deprecated Use relay catalog via mergeExerciseCatalog instead. */
export const BUILTIN_EXERCISES = FALLBACK_WEIGHT_EXERCISES;
