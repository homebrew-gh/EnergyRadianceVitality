/**
 * Phone check: places where the ERV phone app runs a workout differently from what the builder
 * shows. Each warning mirrors a specific behaviour in the Android live run, so delete a rule here
 * once the phone handles that case (see the PC Workout Builder Review, H1 to H6).
 *
 * Android references:
 * - Supersets and circuits run only their lifts, one set per round, using the first set's
 *   targets: `WorkoutCircuitLive.kt` (`toCircuitSlots`, `buildCircuitSetsSeed`).
 * - Rest inside supersets and circuits comes from the section's rest policy only:
 *   `WorkoutRunEngine.pendingRestBeforeAdvance`, and no per-lift rest is passed to the session.
 * - Interval legs are timed in whole minutes, rounded up: `secondsToGuidedMinutes` in
 *   `WorkoutLaunchResolvers.kt`.
 */
import { prescriptionSetTargets } from "./workoutTraining";
import type { WorkoutItem, WorkoutPrescriptionSet, WorkoutSegment } from "./workoutTraining";

export type PhoneWarning = {
  /** Stable key for React lists. */
  key: string;
  segmentIndex: number;
  /** Index into the segment's items, when the warning is about one item. */
  itemIndex?: number;
  message: string;
};

export type PhoneCheckOptions = {
  /** Display name for a weight exercise id; falls back to the id. */
  exerciseName?: (exerciseId: string) => string | undefined;
};

function isRoundBased(segment: WorkoutSegment): boolean {
  return segment.kind === "superset" || segment.kind === "circuit";
}

function itemLabel(item: WorkoutItem, options: PhoneCheckOptions): string {
  if (item.title?.trim()) return item.title.trim();
  switch (item.type) {
    case "weight":
      return options.exerciseName?.(item.exerciseId) ?? item.exerciseId;
    case "cardio":
      return item.cardio.activity || "Cardio";
    case "mobility":
      return "Mobility";
    case "note":
      return "Note";
    case "rest":
      return "Rest";
  }
}

function sectionLabel(segment: WorkoutSegment, index: number): string {
  return segment.title?.trim() || `Section ${index + 1}`;
}

/** Whole minutes the phone times a leg of [seconds] for. */
export function phoneIntervalMinutes(seconds: number): number {
  return Math.max(1, Math.ceil(seconds / 60));
}

function formatSeconds(seconds: number): string {
  return seconds % 60 === 0 ? `${seconds / 60} min` : `${seconds} s`;
}

function sameTargets(a: WorkoutPrescriptionSet, b: WorkoutPrescriptionSet): boolean {
  const left = prescriptionSetTargets(a);
  const right = prescriptionSetTargets(b);
  return (
    left.reps === right.reps &&
    left.weightKg === right.weightKg &&
    left.durationSeconds === right.durationSeconds
  );
}

function roundBasedWarnings(
  segment: WorkoutSegment,
  segmentIndex: number,
  options: PhoneCheckOptions,
): PhoneWarning[] {
  const warnings: PhoneWarning[] = [];
  const items = segment.items ?? [];
  const rounds = Math.max(1, segment.rounds ?? 1);
  const kindName = segment.kind === "superset" ? "superset" : "circuit";
  const lifts = items.filter((item) => item.type === "weight");

  if (items.length > 0 && lifts.length === 0) {
    warnings.push({
      key: `${segmentIndex}:no-lifts`,
      segmentIndex,
      message: `${sectionLabel(segment, segmentIndex)} has no lifts, so the phone can't start this ${kindName}.`,
    });
  }

  items.forEach((item, itemIndex) => {
    const key = `${segmentIndex}:${itemIndex}`;
    if (item.type !== "weight") {
      warnings.push({
        key: `${key}:skipped`,
        segmentIndex,
        itemIndex,
        message: `${itemLabel(item, options)} is skipped on the phone: a ${kindName} only runs its lifts. Use the section's Rest Between Exercises or Rest After Round, or move it to its own section.`,
      });
      return;
    }
    const name = itemLabel(item, options);
    const prescription = item.prescription ?? {};
    const sets = prescription.sets ?? [];
    const setCount = sets.length > 0 ? sets.length : prescription.setCount ?? 1;
    if (setCount > 1 && setCount !== rounds) {
      warnings.push({
        key: `${key}:sets`,
        segmentIndex,
        itemIndex,
        message: `${name} has ${setCount} sets, but the phone runs this ${kindName} as ${rounds} round${rounds === 1 ? "" : "s"} with one set each.`,
      });
    } else if (sets.length > 1 && !sets.every((set) => sameTargets(set, sets[0]))) {
      warnings.push({
        key: `${key}:targets`,
        segmentIndex,
        itemIndex,
        message: `${name} has different targets per set, but the phone uses the first set's targets for every round.`,
      });
    }
    const liftRest =
      (prescription.restBetweenSetsSeconds ?? 0) > 0 || (prescription.restAfterExerciseSeconds ?? 0) > 0;
    if (liftRest) {
      warnings.push({
        key: `${key}:rest`,
        segmentIndex,
        itemIndex,
        message: `${name}'s own rest isn't used in a ${kindName}. The phone rests only by the section's Rest Between Exercises and Rest After Round.`,
      });
    }
  });
  return warnings;
}

function intervalWarnings(
  item: Extract<WorkoutItem, { type: "cardio" }>,
  segmentIndex: number,
  itemIndex: number,
  options: PhoneCheckOptions,
): PhoneWarning[] {
  const cardio = item.cardio;
  const legSeconds: number[] = [];
  if (cardio.mode === "sprint_intervals") {
    if ((cardio.workSeconds ?? 0) > 0) legSeconds.push(cardio.workSeconds ?? 0);
    if ((cardio.restSeconds ?? 0) > 0) legSeconds.push(cardio.restSeconds ?? 0);
  } else if (cardio.mode === "interval_template") {
    for (const leg of cardio.legs ?? []) {
      if (leg.workSeconds > 0) legSeconds.push(leg.workSeconds);
      if ((leg.restSeconds ?? 0) > 0) legSeconds.push(leg.restSeconds ?? 0);
    }
  }
  const rounded = Array.from(new Set(legSeconds.filter((seconds) => seconds % 60 !== 0)));
  if (rounded.length === 0) return [];
  const changes = rounded
    .map((seconds) => `${formatSeconds(seconds)} becomes ${phoneIntervalMinutes(seconds)} min`)
    .join(", ");
  return [
    {
      key: `${segmentIndex}:${itemIndex}:interval-minutes`,
      segmentIndex,
      itemIndex,
      message: `${itemLabel(item, options)}: the phone times interval legs in whole minutes, rounded up (${changes}).`,
    },
  ];
}

/** Every phone-check warning for a workout's segments, in section order. */
export function phoneCompatibilityWarnings(
  segments: WorkoutSegment[],
  options: PhoneCheckOptions = {},
): PhoneWarning[] {
  const warnings: PhoneWarning[] = [];
  segments.forEach((segment, segmentIndex) => {
    if (isRoundBased(segment)) {
      warnings.push(...roundBasedWarnings(segment, segmentIndex, options));
    }
    (segment.items ?? []).forEach((item, itemIndex) => {
      if (item.type === "cardio" && !isRoundBased(segment)) {
        warnings.push(...intervalWarnings(item, segmentIndex, itemIndex, options));
      }
    });
  });
  return warnings;
}
