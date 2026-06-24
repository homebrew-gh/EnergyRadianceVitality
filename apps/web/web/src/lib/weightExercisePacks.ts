/** Built-in specialty weight exercise packs (matches Android WeightExercisePacks.kt). */

export const IRON_NECK_EXERCISE_PACK_ID = "iron-neck";
export const FREAK_ATHLETE_HYPER_PRO_EXERCISE_PACK_ID = "freak-athlete-hyper-pro";

export type WeightExercisePackDefinition = {
  id: string;
  title: string;
  description: string;
};

export const WEIGHT_EXERCISE_PACKS: WeightExercisePackDefinition[] = [
  {
    id: IRON_NECK_EXERCISE_PACK_ID,
    title: "Iron Neck",
    description:
      "Unlock neck-specific drills that rely on Iron Neck resistance work.",
  },
  {
    id: FREAK_ATHLETE_HYPER_PRO_EXERCISE_PACK_ID,
    title: "Freak Athlete Hyper Pro",
    description:
      "Adds reverse hyper, GHD, Nordic, and back-extension style movements.",
  },
];

/** Exercise counts from the built-in Android catalog (for display when relay catalog lacks pack tags). */
const PACK_EXERCISE_COUNTS: Record<string, number> = {
  [IRON_NECK_EXERCISE_PACK_ID]: 6,
  [FREAK_ATHLETE_HYPER_PRO_EXERCISE_PACK_ID]: 5,
};

export function weightExercisePackExerciseCount(
  packId: string,
  catalogExercises?: { exercisePackId?: string | null }[],
): number {
  if (catalogExercises?.length) {
    const fromCatalog = catalogExercises.filter((ex) => ex.exercisePackId === packId).length;
    if (fromCatalog > 0) return fromCatalog;
  }
  return PACK_EXERCISE_COUNTS[packId] ?? 0;
}

export function normalizeEnabledPackIds(ids: string[]): string[] {
  return [...new Set(ids.filter((id) => id.trim().length > 0))].sort();
}
