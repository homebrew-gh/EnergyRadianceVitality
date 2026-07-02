/** Built-in exercise/stretch/cardio catalogs synced via kind-30078.
 *
 * Offline / non-Nostr users rely on bundled APK catalogs only.
 * Nostr + web users treat relay catalogs as authoritative when present; bundled copies bootstrap
 * empty relays and remain the fallback when offline.
 */

import { withResolvedBuiltinExerciseFlags } from "./exerciseLogging";
import { invalidateAppDataCache } from "./appDataCache";

export const WEIGHT_CATALOG_D_TAG = "erv/catalog/weight";
export const STRETCH_CATALOG_D_TAG = "erv/catalog/stretch";
export const CARDIO_CATALOG_D_TAG = "erv/catalog/cardio";

export const CATALOG_D_TAGS = [
  WEIGHT_CATALOG_D_TAG,
  STRETCH_CATALOG_D_TAG,
  CARDIO_CATALOG_D_TAG,
] as const;

export type WeightCatalogExercise = {
  id: string;
  name: string;
  muscleGroup: string;
  pushOrPull: "push" | "pull";
  equipment: string;
  exercisePackId?: string | null;
  hiitCapable?: boolean;
  timePerSetCapable?: boolean;
  /** When false with timePerSetCapable, exercise is time-only. Default true. */
  repPerSetCapable?: boolean;
};

export type WeightCatalogPayload = {
  catalogVersion: number;
  publishedAtEpochSeconds: number;
  exercises: WeightCatalogExercise[];
};

export type StretchCatalogEntry = {
  id: string;
  name: string;
  category: string;
  requiresBothSides: boolean;
  targetBodyParts: string[];
  procedure: string;
};

export type StretchCatalogPayload = {
  catalogVersion: number;
  publishedAtEpochSeconds: number;
  stretches: StretchCatalogEntry[];
};

export type CardioCatalogActivity = {
  id: string;
  displayName: string;
  section: "steady" | "hybrid" | "hiit" | string;
  offersHiitIntervalTemplate: boolean;
  supportsTreadmillModality: boolean;
};

export type CardioCatalogPayload = {
  catalogVersion: number;
  publishedAtEpochSeconds: number;
  activities: CardioCatalogActivity[];
};

export type ErvCatalogs = {
  weight: WeightCatalogExercise[];
  stretch: StretchCatalogEntry[];
  cardio: CardioCatalogActivity[];
  catalogVersion: number | null;
};

export const EMPTY_CATALOGS: ErvCatalogs = {
  weight: [],
  stretch: [],
  cardio: [],
  catalogVersion: null,
};

/** Minimal fallback until the relay has a synced catalog from Android. */
export const FALLBACK_WEIGHT_EXERCISES: WeightCatalogExercise[] = [
  {
    id: "erv-weight-exercise-bench-v1",
    name: "Bench Press",
    muscleGroup: "chest",
    pushOrPull: "push",
    equipment: "barbell",
  },
  {
    id: "erv-weight-exercise-squat-v1",
    name: "Squat",
    muscleGroup: "legs",
    pushOrPull: "push",
    equipment: "barbell",
  },
  {
    id: "erv-weight-exercise-deadlift-v1",
    name: "Deadlift",
    muscleGroup: "back",
    pushOrPull: "pull",
    equipment: "barbell",
  },
  {
    id: "erv-weight-exercise-ohp-v1",
    name: "Military Press",
    muscleGroup: "shoulders",
    pushOrPull: "push",
    equipment: "barbell",
  },
  {
    id: "erv-weight-exercise-bb-bent-over-row-v1",
    name: "Bent-Over Row",
    muscleGroup: "back",
    pushOrPull: "pull",
    equipment: "barbell",
  },
];

/** Minimal fallback until the relay has a synced cardio catalog from Android. */
export const FALLBACK_CARDIO_ACTIVITIES: CardioCatalogActivity[] = [
  // Steady distance
  { id: "WALK", displayName: "Walking", section: "steady", offersHiitIntervalTemplate: false, supportsTreadmillModality: true },
  { id: "RUN", displayName: "Running", section: "steady", offersHiitIntervalTemplate: false, supportsTreadmillModality: true },
  { id: "HIKE", displayName: "Hiking", section: "steady", offersHiitIntervalTemplate: false, supportsTreadmillModality: false },
  { id: "RUCK", displayName: "Rucking", section: "steady", offersHiitIntervalTemplate: false, supportsTreadmillModality: true },
  { id: "BIKE", displayName: "Cycling", section: "steady", offersHiitIntervalTemplate: false, supportsTreadmillModality: false },
  { id: "SWIM", displayName: "Swimming", section: "steady", offersHiitIntervalTemplate: false, supportsTreadmillModality: false },
  { id: "ELLIPTICAL", displayName: "Elliptical", section: "steady", offersHiitIntervalTemplate: false, supportsTreadmillModality: false },
  { id: "OTHER", displayName: "Other", section: "steady", offersHiitIntervalTemplate: false, supportsTreadmillModality: false },
  // Hybrid / erg
  { id: "ROWING", displayName: "Rowing", section: "hybrid", offersHiitIntervalTemplate: true, supportsTreadmillModality: false },
  { id: "STATIONARY_BIKE", displayName: "Stationary Bike", section: "hybrid", offersHiitIntervalTemplate: true, supportsTreadmillModality: false },
  { id: "AIR_BIKE", displayName: "Air Bike", section: "hybrid", offersHiitIntervalTemplate: true, supportsTreadmillModality: false },
  { id: "SKI_ERG", displayName: "SkiErg / Skier", section: "hybrid", offersHiitIntervalTemplate: true, supportsTreadmillModality: false },
  // Sprints & intervals
  { id: "SPRINT", displayName: "Sprinting", section: "hiit", offersHiitIntervalTemplate: true, supportsTreadmillModality: true },
  { id: "JUMP_ROPE", displayName: "Jump Rope", section: "hiit", offersHiitIntervalTemplate: true, supportsTreadmillModality: false },
  { id: "BATTLE_ROPE", displayName: "Battle Rope", section: "hiit", offersHiitIntervalTemplate: true, supportsTreadmillModality: false },
  { id: "BURPEES", displayName: "Burpees", section: "hiit", offersHiitIntervalTemplate: true, supportsTreadmillModality: false },
  { id: "JUMPING_JACKS", displayName: "Jumping Jacks", section: "hiit", offersHiitIntervalTemplate: true, supportsTreadmillModality: false },
];

export function resolveCardioCatalog(
  activities: CardioCatalogActivity[],
): CardioCatalogActivity[] {
  return activities.length > 0 ? activities : FALLBACK_CARDIO_ACTIVITIES;
}

export function parseWeightCatalog(raw: string): WeightCatalogPayload | null {
  try {
    const parsed = JSON.parse(raw) as WeightCatalogPayload;
    if (!Array.isArray(parsed.exercises)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function parseStretchCatalog(raw: string): StretchCatalogPayload | null {
  try {
    const parsed = JSON.parse(raw) as StretchCatalogPayload;
    if (!Array.isArray(parsed.stretches)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function parseCardioCatalog(raw: string): CardioCatalogPayload | null {
  try {
    const parsed = JSON.parse(raw) as CardioCatalogPayload;
    if (!Array.isArray(parsed.activities)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function catalogsFromAppData(
  records: Array<{ d_tag?: string | null; plaintext?: string | null }>,
): ErvCatalogs {
  const withTag = records.filter(
    (record): record is { d_tag: string; plaintext?: string | null } =>
      typeof record.d_tag === "string" && record.d_tag.length > 0,
  );
  const weightRecord = withTag.find((r) => r.d_tag === WEIGHT_CATALOG_D_TAG);
  const stretchRecord = withTag.find((r) => r.d_tag === STRETCH_CATALOG_D_TAG);
  const cardioRecord = withTag.find((r) => r.d_tag === CARDIO_CATALOG_D_TAG);

  const weightPayload = weightRecord?.plaintext
    ? parseWeightCatalog(weightRecord.plaintext)
    : null;
  const stretchPayload = stretchRecord?.plaintext
    ? parseStretchCatalog(stretchRecord.plaintext)
    : null;
  const cardioPayload = cardioRecord?.plaintext
    ? parseCardioCatalog(cardioRecord.plaintext)
    : null;

  const versions = [
    weightPayload?.catalogVersion,
    stretchPayload?.catalogVersion,
    cardioPayload?.catalogVersion,
  ].filter((v): v is number => typeof v === "number");

  return {
    weight: (weightPayload?.exercises ?? []).map(withResolvedBuiltinExerciseFlags),
    stretch: stretchPayload?.stretches ?? [],
    cardio: resolveCardioCatalog(cardioPayload?.activities ?? []),
    catalogVersion: versions.length > 0 ? Math.max(...versions) : null,
  };
}

/** Weight catalog exercises + user custom exercises from `erv/weight/exercises`. */
export function mergeWeightExerciseLibrary(
  catalogExercises: WeightCatalogExercise[],
  relayUserExercises: WeightCatalogExercise[],
): WeightCatalogExercise[] {
  const catalog =
    catalogExercises.length > 0 ? catalogExercises : FALLBACK_WEIGHT_EXERCISES;
  const byId = new Map<string, WeightCatalogExercise>();
  for (const exercise of catalog) byId.set(exercise.id, exercise);
  for (const exercise of relayUserExercises) {
    if (!exercise.id.startsWith("erv-weight-exercise-")) {
      byId.set(exercise.id, exercise);
    }
  }
  return [...byId.values()]
    .map(withResolvedBuiltinExerciseFlags)
    .sort((a, b) => a.name.localeCompare(b.name));
}

export function stretchLabel(
  stretchId: string,
  catalog: StretchCatalogEntry[],
): string {
  return catalog.find((entry) => entry.id === stretchId)?.name ?? stretchId;
}

export function cardioActivityLabel(
  activityId: string,
  catalog: CardioCatalogActivity[],
): string {
  return catalog.find((entry) => entry.id === activityId)?.displayName ?? activityId;
}

export const WEIGHT_MUSCLE_GROUP_ORDER = [
  "chest",
  "back",
  "legs",
  "shoulders",
  "biceps",
  "triceps",
  "core",
] as const;

export const STRETCH_CATEGORY_ORDER = [
  "neck",
  "shoulders",
  "arms",
  "chest",
  "back",
  "core",
  "glutes",
  "legs",
  "other",
] as const;

export const STRETCH_TARGET_BODY_PART_OPTIONS = [
  "neck",
  "upper back",
  "shoulders",
  "forearms",
  "wrists",
  "chest",
  "back",
  "core",
  "hips",
  "obliques",
  "hamstrings",
  "calves",
  "quads",
  "adductors",
  "it band",
  "thoracic spine",
  "glutes",
] as const;

export const CARDIO_SECTION_ORDER = ["steady", "hybrid", "hiit"] as const;

export const WEIGHT_EQUIPMENT_OPTIONS = [
  "barbell",
  "dumbbell",
  "kettlebell",
  "machine",
  "other",
] as const;

const CATEGORY_LABELS: Record<string, string> = {
  chest: "Chest",
  back: "Back",
  legs: "Legs",
  shoulders: "Shoulders",
  biceps: "Biceps",
  triceps: "Triceps",
  core: "Core",
  neck: "Neck",
  arms: "Arms",
  glutes: "Glutes",
  machine: "Machine",
  other: "Body Weight",
  steady: "Steady distance",
  hybrid: "Hybrid / erg",
  hiit: "Sprints & intervals",
};

export function formatCategoryLabel(key: string): string {
  const normalized = key.trim().toLowerCase();
  if (CATEGORY_LABELS[normalized]) return CATEGORY_LABELS[normalized];
  return normalized
    .split(/[\s_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

/** Known order first, then catalog keys, then optional current value (for custom groups). */
export function collectGroupedCatalogKeys(
  order: readonly string[],
  keysFromItems: string[],
  current?: string | null,
): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  const add = (raw: string) => {
    const key = raw.trim().toLowerCase();
    if (!key || seen.has(key)) return;
    seen.add(key);
    result.push(key);
  };
  for (const key of order) add(key);
  for (const key of keysFromItems) add(key);
  if (current) add(current);
  return result;
}

export function collectMuscleGroupOptions(
  exercises: WeightCatalogExercise[],
  current?: string | null,
): string[] {
  return collectGroupedCatalogKeys(
    WEIGHT_MUSCLE_GROUP_ORDER,
    exercises.map((exercise) => exercise.muscleGroup),
    current,
  );
}

export function collectStretchCategoryOptions(
  entries: StretchCatalogEntry[],
  current?: string | null,
): string[] {
  return collectGroupedCatalogKeys(
    STRETCH_CATEGORY_ORDER,
    entries.map((entry) => entry.category),
    current,
  );
}

export function normalizeCatalogGroupKey(raw: string): string {
  return raw.trim().toLowerCase().replace(/\s+/g, "_");
}

export type GroupedCatalog<T> = {
  key: string;
  label: string;
  items: T[];
};

function groupByKey<T>(
  items: T[],
  keyOf: (item: T) => string,
  order: readonly string[],
): GroupedCatalog<T>[] {
  const buckets = new Map<string, T[]>();
  for (const item of items) {
    const key = keyOf(item).trim().toLowerCase() || "other";
    const list = buckets.get(key) ?? [];
    list.push(item);
    buckets.set(key, list);
  }

  const seen = new Set<string>();
  const groups: GroupedCatalog<T>[] = [];

  for (const key of order) {
    const groupItems = buckets.get(key);
    if (!groupItems?.length) continue;
    seen.add(key);
    groups.push({
      key,
      label: formatCategoryLabel(key),
      items: groupItems,
    });
  }

  const extras = [...buckets.keys()]
    .filter((key) => !seen.has(key))
    .sort((a, b) => formatCategoryLabel(a).localeCompare(formatCategoryLabel(b)));

  for (const key of extras) {
    const groupItems = buckets.get(key);
    if (!groupItems?.length) continue;
    groups.push({
      key,
      label: formatCategoryLabel(key),
      items: groupItems,
    });
  }

  return groups;
}

export function groupWeightExercises(
  exercises: WeightCatalogExercise[],
): GroupedCatalog<WeightCatalogExercise>[] {
  return groupByKey(
    exercises,
    (exercise) => exercise.muscleGroup,
    WEIGHT_MUSCLE_GROUP_ORDER,
  ).map((group) => ({
    ...group,
    items: [...group.items].sort((a, b) => a.name.localeCompare(b.name)),
  }));
}

export function groupStretchEntries(
  stretches: StretchCatalogEntry[],
): GroupedCatalog<StretchCatalogEntry>[] {
  return groupByKey(
    stretches,
    (entry) => entry.category,
    STRETCH_CATEGORY_ORDER,
  ).map((group) => ({
    ...group,
    items: [...group.items].sort((a, b) => a.name.localeCompare(b.name)),
  }));
}

export function groupCardioActivities(
  activities: CardioCatalogActivity[],
): GroupedCatalog<CardioCatalogActivity>[] {
  return groupByKey(
    activities,
    (activity) => activity.section,
    CARDIO_SECTION_ORDER,
  ).map((group) => ({
    ...group,
    items: [...group.items].sort((a, b) =>
      a.displayName.localeCompare(b.displayName),
    ),
  }));
}

export function nextCatalogVersion(current: number | null | undefined): number {
  const base = typeof current === "number" && current > 0 ? current : 1;
  return base + 1;
}

export function serializeWeightCatalog(
  exercises: WeightCatalogExercise[],
  catalogVersion: number,
): string {
  return JSON.stringify({
    catalogVersion,
    publishedAtEpochSeconds: Math.floor(Date.now() / 1000),
    exercises,
  } satisfies WeightCatalogPayload);
}

export function serializeStretchCatalog(
  stretches: StretchCatalogEntry[],
  catalogVersion: number,
): string {
  return JSON.stringify({
    catalogVersion,
    publishedAtEpochSeconds: Math.floor(Date.now() / 1000),
    stretches,
  } satisfies StretchCatalogPayload);
}

export function serializeCardioCatalog(
  activities: CardioCatalogActivity[],
  catalogVersion: number,
): string {
  return JSON.stringify({
    catalogVersion,
    publishedAtEpochSeconds: Math.floor(Date.now() / 1000),
    activities,
  } satisfies CardioCatalogPayload);
}

export function isCustomWeightCatalogExercise(id: string): boolean {
  return id.startsWith("erv-weight-exercise-web-");
}

export function isBuiltInWeightCatalogId(id: string): boolean {
  return id.startsWith("erv-weight-exercise-") && !isCustomWeightCatalogExercise(id);
}

export function isBuiltInStretchCatalogId(id: string): boolean {
  return id.startsWith("builtin_");
}

export function newCustomWeightExerciseId(): string {
  return `erv-weight-exercise-web-${crypto.randomUUID()}`;
}

export function newCustomStretchId(): string {
  return `custom_stretch_${crypto.randomUUID().replace(/-/g, "").slice(0, 12)}`;
}

export const CATALOG_PUBLISHED_EVENT = "erv-catalog-published";

export function notifyCatalogPublished() {
  invalidateAppDataCache();
  window.dispatchEvent(new CustomEvent(CATALOG_PUBLISHED_EVENT));
}
