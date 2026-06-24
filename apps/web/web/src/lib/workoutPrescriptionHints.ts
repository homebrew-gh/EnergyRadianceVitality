/**
 * W4 — Rule-based load suggestions from training snapshot and last-week logs.
 */

import type { TrainingProgressionStyle, TrainingProfilePayload } from "./trainingProfile";
import { addDays, formatIsoDate, type WeightDayLog, type WeightSet } from "./trainingHistory";
import type { TrainingSnapshot } from "./trainingSnapshot";
import {
  duplicateWorkout,
  segmentItems,
  type Workout,
  type WorkoutWeightPrescription,
} from "./workoutTraining";

export function roundWeightKg(kg: number): number {
  return Math.round(kg * 2) / 2;
}

export function progressionIncrementKg(
  progressionStyle?: TrainingProgressionStyle | null,
): number {
  switch (progressionStyle) {
    case "conservative":
      return 1.25;
    case "aggressive":
      return 5;
    case "moderate":
    default:
      return 2.5;
  }
}

function pickRepresentativeSet(sets: WeightSet[]): WeightSet | null {
  const working = sets.filter((s) => s.reps > 0 && (s.weightKg ?? 0) > 0);
  if (working.length === 0) return null;
  const inRange = working.filter((s) => s.reps >= 4 && s.reps <= 12);
  const pool = inRange.length > 0 ? inRange : working;
  return pool.reduce((best, s) => {
    const w = s.weightKg ?? 0;
    const bw = best.weightKg ?? 0;
    if (w > bw) return s;
    if (w === bw && s.reps > best.reps) return s;
    return best;
  });
}

export function loggedWeightKgForExercise(sets: WeightSet[]): number | null {
  const set = pickRepresentativeSet(sets);
  const kg = set?.weightKg ?? null;
  return kg != null && kg > 0 ? kg : null;
}

export function workingWeightForExercise(
  exerciseId: string,
  snapshot: TrainingSnapshot | null | undefined,
) {
  return snapshot?.workingWeights.find((w) => w.exerciseId === exerciseId) ?? null;
}

export function suggestedTargetWeightKg(
  exerciseId: string,
  snapshot: TrainingSnapshot | null | undefined,
  incrementKg = 0,
): number | null {
  const entry = workingWeightForExercise(exerciseId, snapshot);
  if (!entry) return null;
  const base = entry.weightKg;
  if (incrementKg <= 0) return roundWeightKg(base);
  return roundWeightKg(base + incrementKg);
}

export function setPrescriptionTargetWeightKg(
  prescription: WorkoutWeightPrescription,
  targetKg: number,
): WorkoutWeightPrescription {
  const next: WorkoutWeightPrescription = {
    ...prescription,
    targetWeightKg: targetKg,
  };
  if (prescription.sets?.length) {
    next.sets = prescription.sets.map((s) => ({
      ...s,
      targetWeightKg: targetKg,
      weightKg: targetKg,
    }));
  }
  return next;
}

export function applyTargetWeightToPrescription(
  prescription: WorkoutWeightPrescription | undefined,
  targetKg: number | null,
): WorkoutWeightPrescription | undefined {
  if (targetKg == null) return prescription;
  return setPrescriptionTargetWeightKg(prescription ?? {}, targetKg);
}

/** Match logged session from ~5–9 days ago by workout name. */
export function findLastWeekSessionWeights(
  workoutName: string,
  weightLogs: WeightDayLog[],
  computedAtMs = Date.now(),
): Map<string, number> | null {
  const endIso = formatIsoDate(new Date(computedAtMs));
  const windowStart = addDays(endIso, -9);
  const windowEnd = addDays(endIso, -5);
  const normalizedName = workoutName.trim().toLowerCase();

  const inWindow = weightLogs
    .filter((l) => l.date >= windowStart && l.date <= windowEnd)
    .sort((a, b) => b.date.localeCompare(a.date));

  for (const log of inWindow) {
    for (const session of log.workouts) {
      const sessionLabel = session.routineName?.trim().toLowerCase() ?? "";
      if (!sessionLabel || sessionLabel !== normalizedName) continue;
      const map = new Map<string, number>();
      for (const entry of session.entries) {
        const kg = loggedWeightKgForExercise(entry.sets);
        if (kg != null) map.set(entry.exerciseId, kg);
      }
      if (map.size > 0) return map;
    }
  }
  return null;
}

function resolveProgressedTargetKg(
  exerciseId: string,
  prescription: WorkoutWeightPrescription | undefined,
  incrementKg: number,
  snapshot: TrainingSnapshot | null | undefined,
  lastWeekWeights: Map<string, number> | null,
): number | null {
  const lastWeekKg = lastWeekWeights?.get(exerciseId);
  if (lastWeekKg != null) return roundWeightKg(lastWeekKg + incrementKg);

  const fromSnapshot = suggestedTargetWeightKg(exerciseId, snapshot, incrementKg);
  if (fromSnapshot != null) return fromSnapshot;

  const existing =
    prescription?.targetWeightKg ??
    prescription?.sets?.find((s) => (s.targetWeightKg ?? s.weightKg ?? 0) > 0)?.targetWeightKg ??
    prescription?.sets?.find((s) => (s.weightKg ?? 0) > 0)?.weightKg ??
    null;
  if (existing != null && existing > 0) return roundWeightKg(existing + incrementKg);
  return null;
}

export function applyLoadsToWorkout(
  workout: Workout,
  resolveTarget: (exerciseId: string, prescription: WorkoutWeightPrescription | undefined) => number | null,
): Workout {
  return {
    ...workout,
    segments: workout.segments.map((segment) => ({
      ...segment,
      items: segmentItems(segment).map((item) => {
        if (item.type !== "weight") return item;
        const target = resolveTarget(item.exerciseId, item.prescription);
        if (target == null) return item;
        return {
          ...item,
          prescription: applyTargetWeightToPrescription(item.prescription, target),
        };
      }),
    })),
  };
}

export function applyBaselineLoadsToWorkout(
  workout: Workout,
  snapshot: TrainingSnapshot,
  incrementKg = 0,
): Workout {
  return applyLoadsToWorkout(workout, (exerciseId) =>
    suggestedTargetWeightKg(exerciseId, snapshot, incrementKg),
  );
}

export function duplicateWorkoutWithProgression(
  workout: Workout,
  options: {
    snapshot?: TrainingSnapshot | null;
    weightLogs?: WeightDayLog[];
    profile?: TrainingProfilePayload | null;
    computedAtMs?: number;
    nameSuffix?: string;
  },
): Workout {
  const increment = progressionIncrementKg(options.profile?.progressionStyle);
  const copy = duplicateWorkout(workout, options.nameSuffix ?? " (+ progress)");
  const lastWeekWeights =
    options.weightLogs != null
      ? findLastWeekSessionWeights(workout.name, options.weightLogs, options.computedAtMs)
      : null;

  return applyLoadsToWorkout(copy, (exerciseId, prescription) =>
    resolveProgressedTargetKg(
      exerciseId,
      prescription,
      increment,
      options.snapshot,
      lastWeekWeights,
    ),
  );
}
