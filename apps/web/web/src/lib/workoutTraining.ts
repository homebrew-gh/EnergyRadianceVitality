/** Matches Android WorkoutSync — `erv/workouts/library`. */

import type { WeightCatalogExercise } from "./catalog";
import {
  exerciseSetLoggingStyle,
  type WeightSetLoggingStyle,
} from "./exerciseLogging";

export const WORKOUTS_LIBRARY_D_TAG = "erv/workouts/library";

export type { WeightSetLoggingStyle };
export { exerciseSetLoggingStyle };

export type WorkoutSegmentKind =
  | "straight_sets"
  | "superset"
  | "circuit"
  | "composite"
  | "cardio"
  | "mobility"
  | "interval";

export type WorkoutCardioLogField = "INCLINE" | "SPEED" | "DISTANCE" | "NOTES";

export type WorkoutCardioIntervalLeg = {
  workSeconds: number;
  restSeconds?: number;
  label?: string | null;
  hrTargetBpm?: number | null;
};

export type WorkoutCardioPrescription = {
  activity: string;
  mode?: "steady" | "interval_template" | "sprint_intervals";
  targetMinutes?: number | null;
  hrTargetBpm?: number | null;
  hrTargetMinBpm?: number | null;
  hrTargetMaxBpm?: number | null;
  hrZoneLabel?: string | null;
  logFields?: WorkoutCardioLogField[];
  cardioRoutineId?: string | null;
  outerRounds?: number | null;
  legs?: WorkoutCardioIntervalLeg[];
  rounds?: number | null;
  workSeconds?: number | null;
  restSeconds?: number | null;
};

export type WorkoutPrescriptionSetSide = "left" | "right" | "each" | "alternating";

export type WorkoutPrescriptionSet = {
  reps?: number | null;
  repsPerSide?: number | null;
  side?: WorkoutPrescriptionSetSide | null;
  weightKg?: number | null;
  targetWeightKg?: number | null;
  rir?: number | null;
  rpe?: number | null;
  durationSeconds?: number | null;
  targetReps?: number | null;
  targetDurationSeconds?: number | null;
};

export type WorkoutWeightPrescription = {
  mode?: "straight" | "interval" | "time_based" | "max_reps";
  setCount?: number | null;
  targetReps?: number | null;
  targetWeightKg?: number | null;
  repRangeMin?: number | null;
  repRangeMax?: number | null;
  targetRir?: number | null;
  restBetweenSetsSeconds?: number | null;
  restAfterExerciseSeconds?: number | null;
  durationSeconds?: number | null;
  sets?: WorkoutPrescriptionSet[];
};

export type WorkoutMobilityPrescription = {
  catalogId: string;
  holdSeconds?: number | null;
  holdSecondsPerSide?: number | null;
};

export type WorkoutItem =
  | {
      type: "weight";
      id?: string;
      title?: string | null;
      exerciseId: string;
      alternativeExerciseIds?: string[];
      prescription?: WorkoutWeightPrescription;
    }
  | {
      type: "cardio";
      id?: string;
      title?: string | null;
      cardio: WorkoutCardioPrescription;
    }
  | {
      type: "mobility";
      id?: string;
      title?: string | null;
      mobility: WorkoutMobilityPrescription;
    }
  | {
      type: "note";
      id?: string;
      title?: string | null;
      text: string;
    }
  | {
      type: "rest";
      id?: string;
      title?: string | null;
      durationSeconds: number;
    };

export type WorkoutRestPolicy = {
  restBetweenItemsSeconds?: number;
  restAfterRoundSeconds?: number;
};

export type WorkoutSegment = {
  id?: string;
  kind: WorkoutSegmentKind;
  title?: string | null;
  notes?: string | null;
  items?: WorkoutItem[];
  rounds?: number;
  restPolicy?: WorkoutRestPolicy;
  restAfterSeconds?: number | null;
};

export type Workout = {
  id: string;
  name: string;
  description?: string | null;
  sourceLabel?: string | null;
  tags?: string[];
  segments: WorkoutSegment[];
  createdAtEpochSeconds?: number;
  lastModifiedEpochSeconds?: number;
};

export type WorkoutLibraryPayload = {
  workouts: Workout[];
  libraryUpdatedAtEpochSeconds?: number;
};

export function parseWorkoutLibraryPayload(raw: string): Workout[] {
  const parsed = JSON.parse(raw) as WorkoutLibraryPayload;
  return Array.isArray(parsed.workouts) ? parsed.workouts : [];
}

export function workoutLibraryPayload(
  workouts: Workout[],
  libraryUpdatedAtEpochSeconds: number,
): string {
  return JSON.stringify({
    workouts,
    libraryUpdatedAtEpochSeconds,
  } satisfies WorkoutLibraryPayload);
}

export function upsertWorkout(workouts: Workout[], workout: Workout): Workout[] {
  const index = workouts.findIndex((w) => w.id === workout.id);
  if (index >= 0) {
    const next = [...workouts];
    next[index] = workout;
    return next;
  }
  return [...workouts, workout];
}

export function defaultWeightPrescription(
  segmentKind?: WorkoutSegmentKind,
  exercise?: WeightCatalogExercise | null,
): WorkoutWeightPrescription {
  const setCount =
    segmentKind === "circuit" || segmentKind === "superset" ? 1 : 3;
  const style = exercise ? exerciseSetLoggingStyle(exercise) : "reps";
  if (style === "time_only") {
    return {
      mode: "time_based",
      setCount,
      durationSeconds: 45,
    };
  }
  if (style === "reps_or_time") {
    return {
      mode: "straight",
      setCount,
      targetReps: 10,
    };
  }
  return {
    mode: "straight",
    setCount,
    repRangeMin: 8,
    repRangeMax: 12,
  };
}

export function defaultRestPolicy(): WorkoutRestPolicy {
  return {
    restBetweenItemsSeconds: 0,
    restAfterRoundSeconds: 90,
  };
}

export function newWeightItem(
  exerciseId: string,
  segmentKind?: WorkoutSegmentKind,
  exercise?: WeightCatalogExercise | null,
): Extract<WorkoutItem, { type: "weight" }> {
  return {
    type: "weight",
    id: crypto.randomUUID(),
    exerciseId,
    title: exercise?.name?.trim() || null,
    prescription: defaultWeightPrescription(segmentKind, exercise),
  };
}

export function newCardioItem(
  activityId: string,
  mode: WorkoutCardioPrescription["mode"] = "steady",
): Extract<WorkoutItem, { type: "cardio" }> {
  if (mode === "sprint_intervals") {
    return {
      type: "cardio",
      id: crypto.randomUUID(),
      cardio: {
        activity: activityId,
        mode: "sprint_intervals",
        rounds: 10,
        workSeconds: 60,
        restSeconds: 60,
      },
    };
  }
  if (mode === "interval_template") {
    return {
      type: "cardio",
      id: crypto.randomUUID(),
      cardio: {
        activity: activityId,
        mode: "interval_template",
        outerRounds: 3,
        legs: [{ workSeconds: 240, restSeconds: 240, label: "Work leg" }],
      },
    };
  }
  return {
    type: "cardio",
    id: crypto.randomUUID(),
    cardio: {
      activity: activityId,
      mode: "steady",
      targetMinutes: 10,
    },
  };
}

export function newMobilityItem(catalogId: string): Extract<WorkoutItem, { type: "mobility" }> {
  return {
    type: "mobility",
    id: crypto.randomUUID(),
    mobility: {
      catalogId,
      holdSeconds: 30,
    },
  };
}

export function newNoteItem(): Extract<WorkoutItem, { type: "note" }> {
  return { type: "note", id: crypto.randomUUID(), text: "" };
}

export function newRestItem(): Extract<WorkoutItem, { type: "rest" }> {
  return { type: "rest", id: crypto.randomUUID(), durationSeconds: 60 };
}

export type WorkoutLibraryKind = "weight" | "stretch" | "cardio";

export function segmentLibraryKinds(kind: WorkoutSegmentKind): WorkoutLibraryKind[] {
  switch (kind) {
    case "composite":
      return ["weight", "stretch", "cardio"];
    case "cardio":
    case "interval":
      return ["cardio"];
    case "mobility":
      return ["stretch"];
    default:
      return ["weight"];
  }
}

export function segmentAllowsInlineNotes(kind: WorkoutSegmentKind): boolean {
  return (
    kind === "composite" ||
    kind === "straight_sets" ||
    kind === "cardio" ||
    kind === "mobility" ||
    kind === "interval"
  );
}

export function defaultCardioModeForSegment(
  kind: WorkoutSegmentKind,
): WorkoutCardioPrescription["mode"] {
  return kind === "interval" ? "sprint_intervals" : "steady";
}

export function workoutItemKey(item: WorkoutItem): string {
  switch (item.type) {
    case "weight":
      return item.id ?? item.exerciseId;
    case "cardio":
      return item.id ?? item.cardio.activity;
    case "mobility":
      return item.id ?? item.mobility.catalogId;
    case "note":
    case "rest":
      return item.id ?? crypto.randomUUID();
  }
}

export function segmentItems(segment: WorkoutSegment): WorkoutItem[] {
  return segment.items ?? [];
}

export function segmentIsEmpty(segment: WorkoutSegment): boolean {
  return segmentItems(segment).length === 0;
}

export function cardioItemSummary(
  item: Extract<WorkoutItem, { type: "cardio" }>,
  activityLabel: string,
): string {
  const parts = [activityLabel];
  const mode = item.cardio.mode ?? "steady";
  if (mode === "sprint_intervals") {
    if (item.cardio.rounds != null && item.cardio.rounds > 0) {
      parts.push(`${item.cardio.rounds} rounds`);
    }
    if (item.cardio.workSeconds != null && item.cardio.workSeconds > 0) {
      const rest = item.cardio.restSeconds ?? 0;
      parts.push(`${item.cardio.workSeconds}s/${rest}s`);
    }
  } else if (mode === "interval_template") {
    if (item.cardio.outerRounds != null && item.cardio.outerRounds > 0) {
      parts.push(`${item.cardio.outerRounds} outer rounds`);
    }
    const leg = item.cardio.legs?.[0];
    if (leg != null) {
      parts.push(`${leg.workSeconds}s work`);
      if ((leg.restSeconds ?? 0) > 0) parts.push(`${leg.restSeconds}s rest`);
    }
  } else if (item.cardio.targetMinutes != null && item.cardio.targetMinutes > 0) {
    parts.push(`${item.cardio.targetMinutes} min`);
  }
  if (item.cardio.hrZoneLabel) {
    parts.push(item.cardio.hrZoneLabel);
  }
  if (
    item.cardio.hrTargetMinBpm != null &&
    item.cardio.hrTargetMaxBpm != null &&
    item.cardio.hrTargetMinBpm > 0 &&
    item.cardio.hrTargetMaxBpm > 0
  ) {
    parts.push(`${item.cardio.hrTargetMinBpm}–${item.cardio.hrTargetMaxBpm} bpm`);
  } else if (item.cardio.hrTargetBpm != null && item.cardio.hrTargetBpm > 0) {
    parts.push(`${item.cardio.hrTargetBpm} bpm`);
  }
  if (item.cardio.cardioRoutineId) {
    parts.push("saved routine");
  }
  if (item.cardio.logFields != null && item.cardio.logFields.length > 0) {
    parts.push(`log: ${item.cardio.logFields.join(", ").toLowerCase()}`);
  }
  return parts.join(" · ");
}

export function mobilityItemSummary(
  item: Extract<WorkoutItem, { type: "mobility" }>,
  stretchLabel: string,
): string {
  const parts = [stretchLabel];
  const hold = item.mobility.holdSecondsPerSide ?? item.mobility.holdSeconds;
  if (hold != null && hold > 0) {
    parts.push(item.mobility.holdSecondsPerSide != null ? `${hold}s/side` : `${hold}s hold`);
  }
  return parts.join(" · ");
}

export function updateWorkoutItem(
  segment: WorkoutSegment,
  itemKey: string,
  next: WorkoutItem,
): WorkoutSegment {
  return {
    ...segment,
    items: segmentItems(segment).map((item) =>
      workoutItemKey(item) === itemKey ? next : item,
    ),
  };
}

export function segmentKindLabel(kind: WorkoutSegmentKind): string {
  switch (kind) {
    case "straight_sets":
      return "Straight sets";
    case "superset":
      return "Superset";
    case "circuit":
      return "Circuit";
    case "composite":
      return "Flow block";
    case "cardio":
      return "Cardio";
    case "mobility":
      return "Mobility";
    case "interval":
      return "Intervals";
  }
}

/** Short hover hint for workout segment add buttons in the composer. */
export function segmentKindHint(kind: WorkoutSegmentKind): string {
  switch (kind) {
    case "composite":
      return "Mix cardio, stretches, lifts, and notes in order — ideal for warm-up or cooldown.";
    case "cardio":
      return "Steady-state cardio from your catalog (bike, row, walk, and similar).";
    case "interval":
      return "Timed work and rest rounds — HIIT, sprints, or custom interval templates.";
    case "mobility":
      return "Stretching and mobility only, from the stretch catalog.";
    case "straight_sets":
      return "One or more lifts with sets, reps, and rest between sets.";
    case "circuit":
      return "Rotate through exercises for multiple rounds; rest between rounds.";
    case "superset":
      return "Pair exercises back-to-back; rest after each pair or round.";
  }
}

export function defaultSegmentTitle(kind: WorkoutSegmentKind): string {
  switch (kind) {
    case "straight_sets":
      return "Main work";
    case "superset":
      return "Superset";
    case "circuit":
      return "Circuit";
    case "composite":
      return "Warm-up";
    case "cardio":
      return "Cardio";
    case "mobility":
      return "Mobility";
    case "interval":
      return "HIIT / intervals";
    default:
      return segmentKindLabel(kind);
  }
}

export function weightItems(segment: WorkoutSegment): Extract<WorkoutItem, { type: "weight" }>[] {
  return (segment.items ?? []).filter(
    (item): item is Extract<WorkoutItem, { type: "weight" }> => item.type === "weight",
  );
}

export function prescriptionSetCount(p: WorkoutWeightPrescription | undefined): number {
  if (p?.sets != null && p.sets.length > 0) return p.sets.length;
  return p?.setCount ?? 3;
}

export function ensurePrescriptionSets(p: WorkoutWeightPrescription): WorkoutPrescriptionSet[] {
  const count = Math.max(1, prescriptionSetCount(p));
  const existing = p.sets ?? [];
  return Array.from({ length: count }, (_, index) => ({ ...(existing[index] ?? {}) }));
}

export function syncPrescriptionSetCount(
  p: WorkoutWeightPrescription,
  setCount: number,
): WorkoutWeightPrescription {
  const count = Math.max(1, setCount);
  const sets = ensurePrescriptionSets({ ...p, setCount: count }).slice(0, count);
  while (sets.length < count) sets.push({});
  return { ...p, setCount: count, sets };
}

export function prescriptionUsesPerSide(p: WorkoutWeightPrescription | undefined): boolean {
  return (p?.sets ?? []).some((s) => (s.repsPerSide ?? 0) > 0);
}

export function prescriptionSummary(
  p: WorkoutWeightPrescription | undefined,
  formatWeight: (kg: number) => string = (kg) => `${kg} kg`,
): string {
  const sets = p?.sets ?? [];
  const setTotal = prescriptionSetCount(p);
  let repPart = "";
  if (p?.mode === "time_based" && p.durationSeconds != null && p.durationSeconds > 0) {
    repPart = `${p.durationSeconds}s hold`;
  } else if (p?.mode === "max_reps") {
    repPart = "max reps";
  } else if (prescriptionUsesPerSide(p)) {
    const first = sets.find((s) => (s.repsPerSide ?? 0) > 0);
    if (first?.repsPerSide != null) {
      repPart = `${first.repsPerSide}/side${first.side ? ` (${first.side})` : ""}`;
    }
  } else if (sets.length > 0 && sets.some((s) => (s.reps ?? 0) > 0 || (s.targetReps ?? 0) > 0)) {
    const reps = sets.map((s) => s.reps ?? s.targetReps).filter((v) => v != null && v > 0);
    if (reps.length === sets.length && new Set(reps).size === 1) {
      repPart = `${reps[0]} reps`;
    } else if (reps.length > 0) {
      repPart = "varied reps";
    }
  } else if (p?.targetReps != null && p.targetReps > 0) {
    repPart = `${p.targetReps} reps`;
  } else if (p?.repRangeMin != null && p?.repRangeMax != null && p.repRangeMin !== p.repRangeMax) {
    repPart = `${p.repRangeMin}–${p.repRangeMax} reps`;
  } else if (p?.repRangeMin != null) {
    repPart = `${p.repRangeMin} reps`;
  } else if (p?.repRangeMax != null) {
    repPart = `${p.repRangeMax} reps`;
  } else if (p?.durationSeconds != null && p.durationSeconds > 0) {
    repPart = `${p.durationSeconds}s hold`;
  }
  const parts = [repPart ? `${setTotal} sets · ${repPart}` : `${setTotal} sets`];
  const weightHint =
    p?.targetWeightKg ??
    p?.sets?.find((s) => (s.targetWeightKg ?? s.weightKg ?? 0) > 0)?.targetWeightKg ??
    p?.sets?.find((s) => (s.weightKg ?? 0) > 0)?.weightKg;
  if (weightHint != null && weightHint > 0) parts.push(formatWeight(weightHint));
  if (p?.targetRir != null) parts.push(`${p.targetRir} RIR`);
  if (p?.restBetweenSetsSeconds != null && p.restBetweenSetsSeconds > 0) {
    parts.push(`${p.restBetweenSetsSeconds}s between sets`);
  }
  if (p?.restAfterExerciseSeconds != null && p.restAfterExerciseSeconds > 0) {
    parts.push(`${p.restAfterExerciseSeconds}s after exercise`);
  }
  return parts.join(" · ");
}

export function duplicateWorkout(workout: Workout, nameSuffix = " (copy)"): Workout {
  const now = Math.floor(Date.now() / 1000);
  return {
    ...workout,
    id: crypto.randomUUID(),
    name: `${workout.name}${nameSuffix}`,
    segments: workout.segments.map((segment) => ({
      ...segment,
      id: crypto.randomUUID(),
      items: (segment.items ?? []).map((item) => {
        const id = crypto.randomUUID();
        switch (item.type) {
          case "weight":
            return {
              ...item,
              id,
              prescription: item.prescription
                ? {
                    ...item.prescription,
                    sets: item.prescription.sets?.map((s) => ({ ...s })),
                  }
                : undefined,
            };
          case "cardio":
            return {
              ...item,
              id,
              cardio: {
                ...item.cardio,
                legs: item.cardio.legs?.map((leg) => ({ ...leg })),
              },
            };
          case "mobility":
            return { ...item, id, mobility: { ...item.mobility } };
          case "note":
          case "rest":
            return { ...item, id };
        }
      }),
    })),
    lastModifiedEpochSeconds: now,
    createdAtEpochSeconds: now,
  };
}

export function parseNonNegativeInt(raw: string, fallback: number): number {
  const parsed = Number.parseInt(raw.trim(), 10);
  if (Number.isNaN(parsed)) return fallback;
  return Math.max(0, parsed);
}

export function parsePositiveInt(raw: string, fallback: number): number {
  const parsed = Number.parseInt(raw.trim(), 10);
  if (Number.isNaN(parsed)) return fallback;
  return Math.max(1, parsed);
}

/** Controlled numeric input: empty clears; invalid text keeps the current value. */
export function readOptionalNonNegativeInt(
  raw: string,
  current: number | null | undefined,
): number | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  const parsed = Number.parseInt(trimmed, 10);
  if (Number.isNaN(parsed)) return current ?? null;
  return Math.max(0, parsed);
}

/** Controlled numeric input: empty clears; invalid text keeps the current value. */
export function readOptionalPositiveInt(
  raw: string,
  current: number | null | undefined,
): number | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  const parsed = Number.parseInt(trimmed, 10);
  if (Number.isNaN(parsed)) return current ?? null;
  return Math.max(1, parsed);
}

export function updateWeightItemPrescription(
  segment: WorkoutSegment,
  itemId: string,
  prescription: WorkoutWeightPrescription,
): WorkoutSegment {
  return {
    ...segment,
    items: (segment.items ?? []).map((item) =>
      item.type === "weight" && (item.id ?? item.exerciseId) === itemId
        ? { ...item, prescription }
        : item,
    ),
  };
}
