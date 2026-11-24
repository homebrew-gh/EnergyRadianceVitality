/**
 * W4 — Rule-based load suggestions from training snapshot and last-week logs.
 */

import type { TrainingProgressionStyle, TrainingProfilePayload } from "./trainingProfile";
import { addDays, formatIsoDate, type WeightDayLog, type WeightSet } from "./trainingHistory";
import type { TrainingSnapshot } from "./trainingSnapshot";
import { lbToKg, type BodyWeightUnit } from "./fitnessEquipment";
import {
  duplicateWorkout,
  segmentItems,
  type Workout,
  type WorkoutWeightPrescription,
} from "./workoutTraining";

/** Rounds to a loadable step in the unit you lift in: 0.5 kg, or 2.5 lb (stored back as kg). */
export function roundWeightKg(kg: number, unit: BodyWeightUnit = "KG"): number {
  if (unit === "LB") return lbToKg(Math.round(kg / lbToKg(2.5)) * 2.5);
  return Math.round(kg * 2) / 2;
}

/**
 * Load added per progression step, in kg. In pounds the steps are 2.5, 5 and 10 lb, which plates
 * can actually make, instead of 1.25, 2.5 and 5 kg (2.8, 5.5 and 11 lb).
 */
export function progressionIncrementKg(
  progressionStyle?: TrainingProgressionStyle | null,
  unit: BodyWeightUnit = "KG",
): number {
  const steps = unit === "LB" ? [2.5, 5, 10].map(lbToKg) : [1.25, 2.5, 5];
  switch (progressionStyle) {
    case "conservative":
      return steps[0]!;
    case "aggressive":
      return steps[2]!;
    case "moderate":
    default:
      return steps[1]!;
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
  unit: BodyWeightUnit = "KG",
): number | null {
  const entry = workingWeightForExercise(exerciseId, snapshot);
  if (!entry) return null;
  return roundWeightKg(entry.weightKg + Math.max(0, incrementKg), unit);
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
  unit: BodyWeightUnit,
): number | null {
  const lastWeekKg = lastWeekWeights?.get(exerciseId);
  if (lastWeekKg != null) return roundWeightKg(lastWeekKg + incrementKg, unit);

  const fromSnapshot = suggestedTargetWeightKg(exerciseId, snapshot, incrementKg, unit);
  if (fromSnapshot != null) return fromSnapshot;

  const existing =
    prescription?.targetWeightKg ??
    prescription?.sets?.find((s) => (s.targetWeightKg ?? s.weightKg ?? 0) > 0)?.targetWeightKg ??
    prescription?.sets?.find((s) => (s.weightKg ?? 0) > 0)?.weightKg ??
    null;
  if (existing != null && existing > 0) return roundWeightKg(existing + incrementKg, unit);
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
  unit: BodyWeightUnit = "KG",
): Workout {
  return applyLoadsToWorkout(workout, (exerciseId) =>
    suggestedTargetWeightKg(exerciseId, snapshot, incrementKg, unit),
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
    weightLoadUnit?: BodyWeightUnit;
  },
): Workout {
  const unit = options.weightLoadUnit ?? "KG";
  const increment = progressionIncrementKg(options.profile?.progressionStyle, unit);
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
      unit,
    ),
  );
}
