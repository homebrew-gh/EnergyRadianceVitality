/**
 * Personal records for one lift. Same kinds and rules as Android's `WeightRecords.kt`.
 * Warm-up sets and interval blocks never count.
 */
import type { WeightDayLog, WeightSet, WeightWorkoutSession } from "./trainingHistory";

/** Sets above this many reps say little about a single-rep max, so they give no estimate. */
export const EST_ONE_RM_MAX_REPS = 12;

/** Estimated one-rep max (Epley). A single is its own weight; long sets give no estimate. */
export function estimatedOneRmKg(weightKg: number, reps: number): number | null {
  if (reps <= 0 || weightKg <= 0 || reps > EST_ONE_RM_MAX_REPS) return null;
  if (reps === 1) return weightKg;
  return weightKg * (1 + reps / 30);
}

export type LiftRecordKind =
  /** Best estimated one-rep max; value in kg. */
  | "EST_ONE_RM"
  /** Heaviest weight lifted for 10 reps or more in one set; value in kg. */
  | "TEN_REP_MAX"
  /** Heaviest weight in any set; value in kg, more reps breaks a tie. */
  | "HEAVIEST"
  /** Most reps × weight in one workout; value in kg. */
  | "BEST_VOLUME"
  /** Most reps in one set; value in reps, more weight breaks a tie. */
  | "MOST_REPS"
  /** Longest timed hold in one set; value in seconds. */
  | "LONGEST_HOLD";

export const LIFT_RECORD_KINDS: LiftRecordKind[] = [
  "EST_ONE_RM",
  "TEN_REP_MAX",
  "HEAVIEST",
  "BEST_VOLUME",
  "MOST_REPS",
  "LONGEST_HOLD",
];

export const LIFT_RECORD_TITLES: Record<LiftRecordKind, string> = {
  EST_ONE_RM: "Est. 1RM",
  TEN_REP_MAX: "10 Rep Max",
  HEAVIEST: "Heaviest",
  BEST_VOLUME: "Best Volume",
  MOST_REPS: "Most Reps",
  LONGEST_HOLD: "Longest Hold",
};

export type LiftRecord = {
  exerciseId: string;
  kind: LiftRecordKind;
  value: number;
  workoutId: string;
  /** yyyy-mm-dd the workout was logged. */
  date: string;
  reps?: number | null;
  weightKg?: number | null;
  durationSeconds?: number | null;
};

const EPSILON = 0.001;

/** True when [record] beats [other] for the same kind (or there is none yet). */
export function beatsRecord(record: LiftRecord, other: LiftRecord | null | undefined): boolean {
  if (!other) return true;
  if (record.value > other.value + EPSILON) return true;
  if (record.value < other.value - EPSILON) return false;
  if (record.kind === "HEAVIEST") return (record.reps ?? 0) > (other.reps ?? 0);
  if (record.kind === "MOST_REPS") return (record.weightKg ?? 0) > (other.weightKg ?? 0) + EPSILON;
  return false;
}

function isWorking(set: WeightSet): boolean {
  return set.warmup !== true && (set.reps > 0 || (set.durationSeconds ?? 0) > 0);
}

/** Every record [workout] holds for [exerciseId] on its own, in kind order. */
export function liftRecordsOf(
  workout: WeightWorkoutSession,
  date: string,
  exerciseId: string,
): LiftRecord[] {
  const sets = workout.entries
    .filter((e) => e.exerciseId === exerciseId && !e.hiitBlock)
    .flatMap((e) => e.sets)
    .filter(isWorking);
  if (sets.length === 0) return [];
  const out = new Map<LiftRecordKind, LiftRecord>();
  const offer = (record: LiftRecord) => {
    if (beatsRecord(record, out.get(record.kind))) out.set(record.kind, record);
  };
  const make = (kind: LiftRecordKind, value: number, set: WeightSet | null): LiftRecord => ({
    exerciseId,
    kind,
    value,
    workoutId: workout.id,
    date,
    reps: set && set.reps > 0 ? set.reps : null,
    weightKg: set && (set.weightKg ?? 0) > 0 ? set.weightKg : null,
    durationSeconds: set && (set.durationSeconds ?? 0) > 0 ? set.durationSeconds : null,
  });
  let volume = 0;
  for (const set of sets) {
    const kg = (set.weightKg ?? 0) > 0 ? (set.weightKg as number) : null;
    if (set.reps > 0) {
      offer(make("MOST_REPS", set.reps, set));
      if (kg != null) {
        volume += kg * set.reps;
        offer(make("HEAVIEST", kg, set));
        const e1rm = estimatedOneRmKg(kg, set.reps);
        if (e1rm != null) offer(make("EST_ONE_RM", e1rm, set));
        if (set.reps >= 10) offer(make("TEN_REP_MAX", kg, set));
      }
    }
    if ((set.durationSeconds ?? 0) > 0) offer(make("LONGEST_HOLD", set.durationSeconds as number, set));
  }
  if (volume > 0) offer(make("BEST_VOLUME", volume, null));
  return LIFT_RECORD_KINDS.flatMap((kind) => {
    const r = out.get(kind);
    return r ? [r] : [];
  });
}

/** The best of each kind for [exerciseId] across all logged workouts, in kind order. */
export function bestLiftRecords(
  weightLogs: WeightDayLog[],
  exerciseId: string,
  excludeWorkoutIds: ReadonlySet<string> = new Set(),
): LiftRecord[] {
  const best = new Map<LiftRecordKind, LiftRecord>();
  // Oldest first, so a tie keeps the day it was first set.
  const days = [...weightLogs].sort((a, b) => a.date.localeCompare(b.date));
  for (const day of days) {
    for (const workout of day.workouts) {
      if (excludeWorkoutIds.has(workout.id)) continue;
      for (const record of liftRecordsOf(workout, day.date, exerciseId)) {
        if (beatsRecord(record, best.get(record.kind))) best.set(record.kind, record);
      }
    }
  }
  return LIFT_RECORD_KINDS.flatMap((kind) => {
    const r = best.get(kind);
    return r ? [r] : [];
  });
}
