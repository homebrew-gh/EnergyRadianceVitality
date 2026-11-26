/**
 * What the exercise page and list show for one lift: trend, record values, set lines and list
 * stats. Same rules as Android's `WeightExerciseStats.kt`; records come from `liftRecords.ts`.
 */
import type { BodyWeightUnit } from "./fitnessEquipment";
import { kgToLb } from "./fitnessEquipment";
import {
  beatsRecord,
  liftRecordsOf,
  type LiftRecord,
  type LiftRecordKind,
} from "./liftRecords";
import {
  entryForExercise,
  sessionEpoch,
  type WeightDayLog,
  type WeightSet,
  type WeightWorkoutEntry,
  type WeightWorkoutSession,
} from "./trainingHistory";
import { formatWeightLoadNumber, weightLoadUnitSuffix } from "./weightLoadUnit";

export type LiftTrendMetric = "EST_ONE_RM" | "MOST_REPS" | "LONGEST_HOLD";

export const LIFT_TREND_TITLES: Record<LiftTrendMetric, string> = {
  EST_ONE_RM: "Est. 1RM Trend",
  MOST_REPS: "Most Reps Trend",
  LONGEST_HOLD: "Longest Hold Trend",
};

export type LiftTrendPoint = { date: string; value: number; workoutId: string };

type DatedWorkout = { date: string; workout: WeightWorkoutSession };

/** Every workout, oldest day and earliest session first. */
function datedWorkouts(weightLogs: WeightDayLog[]): DatedWorkout[] {
  return weightLogs
    .flatMap((log) => log.workouts.map((workout) => ({ date: log.date, workout })))
    .sort((a, b) => a.date.localeCompare(b.date) || sessionEpoch(a.workout) - sessionEpoch(b.workout));
}

/**
 * The trend for [exerciseId], oldest first: each workout's best est. 1RM when the lift has ever
 * been loaded, else its most reps in a set, else its longest hold. Warm-ups never count.
 */
export function liftTrend(
  weightLogs: WeightDayLog[],
  exerciseId: string,
): { metric: LiftTrendMetric; points: LiftTrendPoint[] } | null {
  const perWorkout = datedWorkouts(weightLogs)
    .map((d) => ({ d, records: new Map(liftRecordsOf(d.workout, d.date, exerciseId).map((r) => [r.kind, r])) }))
    .filter((x) => x.records.size > 0);
  if (perWorkout.length === 0) return null;
  const metric: LiftTrendMetric | null = perWorkout.some((x) => x.records.has("EST_ONE_RM"))
    ? "EST_ONE_RM"
    : perWorkout.some((x) => x.records.has("MOST_REPS"))
      ? "MOST_REPS"
      : perWorkout.some((x) => x.records.has("LONGEST_HOLD"))
        ? "LONGEST_HOLD"
        : null;
  if (!metric) return null;
  const points = perWorkout.flatMap(({ d, records }) => {
    const r = records.get(metric);
    return r ? [{ date: d.date, value: r.value, workoutId: d.workout.id }] : [];
  });
  return { metric, points };
}

/** "1:30" for a hold, from seconds. */
export function formatHoldClock(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

/** The record's headline value, e.g. "268 lb", "245 lb × 2", "15 @ 135 lb", "1:30". */
export function formatLiftRecordValue(record: LiftRecord, unit: BodyWeightUnit): string {
  const suffix = weightLoadUnitSuffix(unit);
  const load = (kg: number) => `${formatWeightLoadNumber(kg, unit)} ${suffix}`;
  switch (record.kind) {
    case "EST_ONE_RM": {
      const v = unit === "KG" ? record.value : kgToLb(record.value);
      return `${v >= 20 ? Math.round(v) : formatWeightLoadNumber(record.value, unit)} ${suffix}`;
    }
    case "TEN_REP_MAX":
      return (record.reps ?? 10) > 10 ? `${load(record.value)} × ${record.reps}` : load(record.value);
    case "HEAVIEST":
      return record.reps ? `${load(record.value)} × ${record.reps}` : load(record.value);
    case "BEST_VOLUME": {
      const v = unit === "KG" ? record.value : kgToLb(record.value);
      return `${Math.round(v).toLocaleString("en-US")} ${suffix}`;
    }
    case "MOST_REPS":
      return record.weightKg ? `${record.value} @ ${load(record.weightKg)}` : `${record.value} reps`;
    case "LONGEST_HOLD":
      return formatHoldClock(record.value);
  }
}

function isLogged(set: WeightSet): boolean {
  return set.reps > 0 || (set.durationSeconds ?? 0) > 0;
}

/**
 * Sets as one short line, consecutive sets at the same weight joined:
 * "225 lb × 5, 5, 5 · 215 lb × 8", "12, 10, 8 reps", "1:30, 1:15 hold".
 * [warmups] true lists only the warm-ups, false only the working sets.
 */
export function compactSetsLine(
  entry: WeightWorkoutEntry,
  unit: BodyWeightUnit,
  warmups: boolean,
): string | null {
  const suffix = weightLoadUnitSuffix(unit);
  if (entry.hiitBlock) {
    if (warmups) return null;
    const b = entry.hiitBlock;
    const w = b.weightKg ? ` @ ${formatWeightLoadNumber(b.weightKg, unit)} ${suffix}` : "";
    return `${b.intervals} intervals × ${b.workSeconds}s${w}`;
  }
  const chosen = entry.sets.filter((s) => isLogged(s) && (s.warmup === true) === warmups);
  if (chosen.length === 0) return null;
  const groups: { kg: number | null; sets: WeightSet[] }[] = [];
  for (const set of chosen) {
    const kg = (set.weightKg ?? 0) > 0 ? (set.weightKg as number) : null;
    const last = groups[groups.length - 1];
    if (last && last.kg === kg) last.sets.push(set);
    else groups.push({ kg, sets: [set] });
  }
  return groups
    .map(({ kg, sets }) => {
      const timed = sets.every((s) => (s.durationSeconds ?? 0) > 0 && s.reps <= 0);
      const amounts = sets
        .map((s) => (timed ? formatHoldClock(s.durationSeconds ?? 0) : String(s.reps)))
        .join(", ");
      if (kg != null) return `${formatWeightLoadNumber(kg, unit)} ${suffix} × ${amounts}`;
      return timed ? `${amounts} hold` : `${amounts} reps`;
    })
    .join(" · ");
}

/** What the exercise list shows under one exercise. */
export type LiftListStat = {
  /** yyyy-mm-dd it was last done, and that session's entry. */
  lastDate: string;
  lastEntry: WeightWorkoutEntry;
  /** Best est. 1RM ever, in kg; null for bodyweight and timed lifts. */
  bestEstOneRmKg: number | null;
  /** The last day a record was beaten; a first time doing the lift doesn't count. */
  lastRecordDate: string | null;
};

/** List stats for every logged exercise, in one pass over the log. */
export function liftListStats(weightLogs: WeightDayLog[]): Map<string, LiftListStat> {
  const best = new Map<string, Map<LiftRecordKind, LiftRecord>>();
  const last = new Map<string, { date: string; entry: WeightWorkoutEntry }>();
  const lastRecord = new Map<string, string>();
  for (const { date, workout } of datedWorkouts(weightLogs)) {
    for (const exerciseId of new Set(workout.entries.map((e) => e.exerciseId))) {
      const entry = entryForExercise(workout, exerciseId);
      if (!entry) continue;
      if (entry.sets.some(isLogged) || entry.hiitBlock) last.set(exerciseId, { date, entry });
      let kinds = best.get(exerciseId);
      if (!kinds) best.set(exerciseId, (kinds = new Map()));
      for (const record of liftRecordsOf(workout, date, exerciseId)) {
        const previous = kinds.get(record.kind);
        if (beatsRecord(record, previous)) {
          if (previous) lastRecord.set(exerciseId, date);
          kinds.set(record.kind, record);
        }
      }
    }
  }
  const out = new Map<string, LiftListStat>();
  for (const [exerciseId, { date, entry }] of last) {
    out.set(exerciseId, {
      lastDate: date,
      lastEntry: entry,
      bestEstOneRmKg: best.get(exerciseId)?.get("EST_ONE_RM")?.value ?? null,
      lastRecordDate: lastRecord.get(exerciseId) ?? null,
    });
  }
  return out;
}

/** "Nov 24", with the year when it isn't this year; "Today" for today. */
export function liftDateLabel(iso: string, todayIso: string): string {
  if (iso === todayIso) return "Today";
  const [y, m, d] = iso.split("-").map(Number);
  const date = new Date(y!, (m ?? 1) - 1, d ?? 1);
  const sameYear = iso.slice(0, 4) === todayIso.slice(0, 4);
  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    ...(sameYear ? {} : { year: "numeric" }),
  });
}

/** Days from [fromIso] to [toIso]. */
export function daysBetween(fromIso: string, toIso: string): number {
  const toUtc = (iso: string) => {
    const [y, m, d] = iso.split("-").map(Number);
    return Date.UTC(y!, (m ?? 1) - 1, d ?? 1);
  };
  return Math.round((toUtc(toIso) - toUtc(fromIso)) / 86_400_000);
}
