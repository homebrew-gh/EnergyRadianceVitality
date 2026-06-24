/**
 * Web mirror of Android WeightExerciseAvailability.kt — equipment-aware exercise picker.
 */

import type { WeightCatalogExercise } from "./catalog";
import { isCustomWeightCatalogExercise } from "./catalog";
import type { EquipmentCatalogKind, OwnedEquipmentItem } from "./fitnessEquipment";
import { normalizeEnabledPackIds } from "./weightExercisePacks";

export type WeightExercisePickerFilter = "ALL" | "HOME_READY";

const BUILTIN_NO_EQUIPMENT_EXERCISE_IDS = new Set([
  "erv-weight-exercise-bw-pushup-v1",
  "erv-weight-exercise-bw-pike-pushup-v1",
  "erv-weight-exercise-bw-air-squat-v1",
  "erv-weight-exercise-bw-reverse-lunge-v1",
  "erv-weight-exercise-bw-glute-bridge-v1",
  "erv-weight-exercise-bw-situp-v1",
  "erv-weight-exercise-bw-crunch-v1",
  "erv-weight-exercise-bw-plank-v1",
  "erv-weight-exercise-bw-side-plank-v1",
  "erv-weight-exercise-bw-superman-v1",
  "erv-weight-exercise-bw-mountain-climber-v1",
  "erv-weight-exercise-bw-burpee-v1",
]);

export function isVisibleForEnabledPacks(
  exercise: WeightCatalogExercise,
  enabledPackIds: Set<string>,
): boolean {
  if (!exercise.exercisePackId) return true;
  return enabledPackIds.has(exercise.exercisePackId);
}

export function filterWeightExercisesForEnabledPacks(
  exercises: WeightCatalogExercise[],
  enabledPackIds: string[] | Set<string>,
): WeightCatalogExercise[] {
  const packSet =
    enabledPackIds instanceof Set ? enabledPackIds : new Set(normalizeEnabledPackIds([...enabledPackIds]));
  return exercises.filter((ex) => isVisibleForEnabledPacks(ex, packSet));
}

function normalizeEquipmentKind(raw: string): string {
  return raw.trim().toLowerCase();
}

function ownedCatalogKinds(ownedEquipment: OwnedEquipmentItem[]): Set<EquipmentCatalogKind> {
  const kinds = new Set<EquipmentCatalogKind>();
  for (const item of ownedEquipment) {
    if (item.catalogKind) kinds.add(item.catalogKind);
  }
  return kinds;
}

function hasManualWeightTool(ownedEquipment: OwnedEquipmentItem[]): boolean {
  return ownedEquipment.some((item) => {
    if (item.catalogKind !== "MANUAL") return false;
    const mods = item.modalities ?? [];
    return mods.length === 0 || mods.includes("WEIGHT_TRAINING") || mods.includes("HIIT");
  });
}

export function isHomeReadyFor(
  exercise: WeightCatalogExercise,
  ownedEquipment: OwnedEquipmentItem[],
  enabledPackIds: string[] | Set<string> = [],
): boolean {
  const packSet =
    enabledPackIds instanceof Set ? enabledPackIds : new Set(normalizeEnabledPackIds([...enabledPackIds]));

  if (isCustomWeightCatalogExercise(exercise.id)) return true;
  if (exercise.exercisePackId) return packSet.has(exercise.exercisePackId);
  if (BUILTIN_NO_EQUIPMENT_EXERCISE_IDS.has(exercise.id)) return true;

  const kinds = ownedCatalogKinds(ownedEquipment);
  const manual = hasManualWeightTool(ownedEquipment);
  const equipment = normalizeEquipmentKind(exercise.equipment);

  switch (equipment) {
    case "barbell":
      return kinds.has("BARBELL");
    case "dumbbell":
      return kinds.has("DUMBBELLS");
    case "kettlebell":
      return kinds.has("KETTLEBELLS");
    case "machine":
      return false;
    case "other":
      return (
        manual ||
        kinds.has("PULL_UP_DIP") ||
        kinds.has("MOBILITY_TOOLS") ||
        kinds.has("PARALLETTE_RINGS") ||
        kinds.has("SUSPENSION_TRAINER")
      );
    default:
      return false;
  }
}

export function filterWeightExercisesForPicker(
  exercises: WeightCatalogExercise[],
  filter: WeightExercisePickerFilter,
  ownedEquipment: OwnedEquipmentItem[],
  enabledPackIds: string[] | Set<string> = [],
): WeightCatalogExercise[] {
  const visible = filterWeightExercisesForEnabledPacks(exercises, enabledPackIds);
  if (filter === "ALL") return visible;
  return visible.filter((ex) => isHomeReadyFor(ex, ownedEquipment, enabledPackIds));
}
