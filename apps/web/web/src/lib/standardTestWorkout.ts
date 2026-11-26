/**
 * Turns a fitness standard into a test-day workout: one segment per event, in the standard's
 * order, with the exercise, the test (top set, max reps, hold, timed distance) and its targets
 * as a note. It opens in the builder as a draft; nothing is published until the athlete does.
 */
import type { BodyWeightUnit } from "./fitnessEquipment";
import type { CardioRecordGroupId } from "./cardioProgress";
import type { FitnessStandard, StandardEvent, StandardProfile } from "./standards";
import { tiersFor } from "./standards";
import { formatStandardValue } from "./standardsFormat";
import type { WorkoutItem, WorkoutSegment } from "./workoutTraining";

const GROUP_ACTIVITY: Record<CardioRecordGroupId, string> = {
  RUN: "RUN",
  RIDE: "BIKE",
  INDOOR_RIDE: "STATIONARY_BIKE",
  RUCK: "RUCK",
  WALK: "WALK",
  HIKE: "HIKE",
  ROW: "ROWING",
};

/** Rest after each event, so every test starts fresh. */
const REST_BETWEEN_EVENTS_SECONDS = 240;

export type TestWorkoutOptions = {
  exercises: { id: string; name: string }[];
  profile: StandardProfile;
  unit: BodyWeightUnit;
  /** Latest weigh-in, for "bodyweight on the bar" events. */
  latestBodyWeightKg: number | null;
  newId?: () => string;
};

export type TestWorkout = {
  name: string;
  segments: WorkoutSegment[];
};

/** The exercise to test [event] with: a listed id, then a name match, then the fallback. */
export function testExerciseFor(event: StandardEvent, exercises: { id: string; name: string }[]): string | null {
  const known = new Set(exercises.map((e) => e.id));
  const listed = (event.exerciseIds ?? []).find((id) => known.has(id));
  if (listed) return listed;
  if (event.exerciseNamePattern) {
    const re = new RegExp(event.exerciseNamePattern, "i");
    const byName = exercises.find((e) => re.test(e.name));
    if (byName) return byName.id;
  }
  const fallback = (event.fallbackExerciseIds ?? []).find((id) => known.has(id));
  if (fallback) return fallback;
  // A built-in catalog id the list did not include (an equipment filter, say) still works in the builder.
  return (event.exerciseIds ?? []).find((id) => id.startsWith("erv-weight-exercise-")) ?? null;
}

function targetsLine(event: StandardEvent, options: TestWorkoutOptions): string {
  const tiers = tiersFor(event, options.profile);
  return `Base ${formatStandardValue(event, tiers.BASE, options.unit)} · Strong ${formatStandardValue(
    event,
    tiers.STRONG,
    options.unit,
  )} · Elite ${formatStandardValue(event, tiers.ELITE, options.unit)}`;
}

function roundLoadKg(kg: number, unit: BodyWeightUnit): number {
  if (unit === "KG") return Math.round(kg / 2.5) * 2.5;
  const lb = Math.round(kg / 0.45359237 / 5) * 5;
  return lb * 0.45359237;
}

function eventSegment(event: StandardEvent, options: TestWorkoutOptions): WorkoutSegment {
  const id = options.newId ?? (() => crypto.randomUUID());
  const m = event.measure;
  const how = event.detail ? `${event.detail}. ` : "";
  const note = (text: string): WorkoutItem => ({ type: "note", id: id(), text });
  const targets = targetsLine(event, options);
  const segment = (kind: WorkoutSegment["kind"], items: WorkoutItem[]): WorkoutSegment => ({
    id: id(),
    kind,
    title: event.name,
    items,
    restAfterSeconds: REST_BETWEEN_EVENTS_SECONDS,
  });

  if (m.kind === "FASTEST") {
    const group = m.cardioGroups[0] ?? "RUN";
    const pack = m.minPackKg
      ? ` Carry at least ${options.unit === "KG" ? `${Math.round(m.minPackKg)} kg` : `${Math.round(m.minPackKg / 0.45359237)} lb`}.`
      : "";
    return segment("cardio", [
      note(`${how}Go for your best time.${pack} ${targets}`),
      {
        type: "cardio",
        id: id(),
        cardio: {
          activity: GROUP_ACTIVITY[group],
          mode: "steady",
          targetDistanceMeters: m.meters,
          logFields: ["DISTANCE"],
        },
      },
    ]);
  }

  if (m.kind === "MANUAL") {
    return segment("composite", [note(`${how}Log the result on Progress afterwards. ${targets}`)]);
  }

  const exerciseId = testExerciseFor(event, options.exercises);
  if (!exerciseId) {
    return segment("composite", [
      note(`${how}No matching exercise in your library, so add one here or test it on its own. ${targets}`),
    ]);
  }

  let prescription: Extract<WorkoutItem, { type: "weight" }>["prescription"];
  let cue: string;
  switch (m.kind) {
    case "LIFT_MAX_BW":
    case "LIFT_MAX_LOAD":
      prescription = { mode: "straight", setCount: 1, targetReps: m.reps, restBetweenSetsSeconds: 180 };
      cue =
        m.reps === 1
          ? "Warm up in a few sets, then take your best single."
          : `Warm up in a few sets, then take your best set of ${m.reps}.`;
      break;
    case "REPS_AT_BW":
      prescription = {
        mode: "max_reps",
        setCount: 1,
        ...(options.latestBodyWeightKg
          ? { targetWeightKg: roundLoadKg(options.latestBodyWeightKg, options.unit) }
          : {}),
      };
      cue = "Bodyweight on the bar, as many reps as you can.";
      break;
    case "MAX_REPS":
      prescription = { mode: "max_reps", setCount: 1 };
      cue = "As many reps as you can in one set.";
      break;
    case "LONGEST_HOLD":
      prescription = { mode: "time_based", durationSeconds: Math.round(tiersFor(event, options.profile).ELITE) };
      cue = "Hold as long as you can; stop when your form breaks.";
      break;
  }
  return segment("straight_sets", [
    note(`${cue} ${targets}`),
    { type: "weight", id: id(), exerciseId, prescription },
  ]);
}

export function buildStandardTestWorkout(standard: FitnessStandard, options: TestWorkoutOptions): TestWorkout {
  const segments = standard.events.map((e) => eventSegment(e, options));
  // No rest after the last event.
  const last = segments[segments.length - 1];
  if (last) last.restAfterSeconds = null;
  return { name: `${standard.name} Test`, segments };
}
