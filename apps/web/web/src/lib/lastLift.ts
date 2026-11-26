/**
 * What you did with each lift last time, for the "Last: …" line in the workout editor.
 */
import type { BodyWeightUnit } from "./fitnessEquipment";
import { formatClock } from "./liftRowFields";
import { beatsRecord, liftRecordsOf, type LiftRecord } from "./liftRecords";
import type { WeightDayLog, WeightSet } from "./trainingHistory";
import { formatWeightLoadNumber, weightLoadUnitSuffix } from "./weightLoadUnit";

export type LastLift = {
  /** yyyy-mm-dd of the session. */
  date: string;
  sets: WeightSet[];
  /** Best est. 1RM ever for the lift, in kg; null for bodyweight and timed lifts. */
  bestEstOneRmKg?: number | null;
  /** Heaviest weight ever lifted for 10 or more reps, in kg. */
  tenRepMaxKg?: number | null;
};

/** The most recent logged sets for each exercise, newest day and latest session first. */
export function lastLiftByExercise(weightLogs: WeightDayLog[]): Map<string, LastLift> {
  const out = new Map<string, LastLift>();
  const days = [...weightLogs].sort((a, b) => b.date.localeCompare(a.date));
  for (const day of days) {
    const sessions = [...day.workouts].sort(
      (a, b) =>
        (b.finishedAtEpochSeconds ?? b.startedAtEpochSeconds ?? 0) -
        (a.finishedAtEpochSeconds ?? a.startedAtEpochSeconds ?? 0),
    );
    for (const session of sessions) {
      for (const entry of session.entries) {
        if (out.has(entry.exerciseId)) continue;
        // Warm-ups are left out: the line is about the working sets to match or beat.
        const sets = entry.sets.filter(
          (s) => s.warmup !== true && (s.reps > 0 || (s.durationSeconds ?? 0) > 0),
        );
        if (sets.length > 0) out.set(entry.exerciseId, { date: day.date, sets });
      }
    }
  }
  addBests(out, weightLogs);
  return out;
}

/** Fills in each lift's best est. 1RM and 10 rep max, from the same records as the exercise page. */
function addBests(out: Map<string, LastLift>, weightLogs: WeightDayLog[]): void {
  const oneRm = new Map<string, LiftRecord>();
  const tenRm = new Map<string, LiftRecord>();
  for (const day of weightLogs) {
    for (const session of day.workouts) {
      for (const exerciseId of new Set(session.entries.map((e) => e.exerciseId))) {
        if (!out.has(exerciseId)) continue;
        for (const record of liftRecordsOf(session, day.date, exerciseId)) {
          const map = record.kind === "EST_ONE_RM" ? oneRm : record.kind === "TEN_REP_MAX" ? tenRm : null;
          if (map && beatsRecord(record, map.get(exerciseId))) map.set(exerciseId, record);
        }
      }
    }
  }
  for (const [exerciseId, last] of out) {
    last.bestEstOneRmKg = oneRm.get(exerciseId)?.value ?? null;
    last.tenRepMaxKg = tenRm.get(exerciseId)?.value ?? null;
  }
}

/** "Best: 1RM 268 lb · 10RM 185 lb", or null when the lift has neither. */
export function formatLiftBests(last: LastLift, unit: BodyWeightUnit): string | null {
  const suffix = weightLoadUnitSuffix(unit);
  const parts: string[] = [];
  if (last.bestEstOneRmKg != null) {
    parts.push(`1RM ${Math.round(Number(formatWeightLoadNumber(last.bestEstOneRmKg, unit)))} ${suffix}`);
  }
  if (last.tenRepMaxKg != null) parts.push(`10RM ${formatWeightLoadNumber(last.tenRepMaxKg, unit)} ${suffix}`);
  return parts.length > 0 ? `Best: ${parts.join(" · ")}` : null;
}

/** Heaviest weight in the session, in kg, or null for bodyweight work. */
export function lastLiftTopWeightKg(last: LastLift): number | null {
  const weights = last.sets.map((s) => s.weightKg ?? 0).filter((w) => w > 0);
  return weights.length > 0 ? Math.max(...weights) : null;
}

function setText(set: WeightSet): string {
  return set.reps > 0 ? String(set.reps) : formatClock(set.durationSeconds ?? 0);
}

function shortDate(iso: string, todayIso: string): string {
  if (iso === todayIso) return "today";
  const [y, m, d] = iso.split("-").map(Number);
  const date = new Date(y!, (m ?? 1) - 1, d ?? 1);
  const sameYear = iso.slice(0, 4) === todayIso.slice(0, 4);
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric", ...(sameYear ? {} : { year: "numeric" }) });
}

/**
 * "3×8 @ 185 lb · Nov 20" when every set matched, else each set: "185×8, 185×7, 175×8 · Nov 20".
 * Bodyweight sets leave out the weight, and holds show their time.
 */
export function formatLastLift(last: LastLift, unit: BodyWeightUnit, todayIso: string): string {
  const suffix = weightLoadUnitSuffix(unit);
  const weightOf = (s: WeightSet) => ((s.weightKg ?? 0) > 0 ? formatWeightLoadNumber(s.weightKg!, unit) : null);
  const first = last.sets[0]!;
  const allSame = last.sets.every((s) => setText(s) === setText(first) && weightOf(s) === weightOf(first));
  let body: string;
  if (allSame) {
    const w = weightOf(first);
    body = `${last.sets.length}×${setText(first)}${w ? ` @ ${w} ${suffix}` : ""}`;
  } else {
    const shown = last.sets.slice(0, 5).map((s) => {
      const w = weightOf(s);
      return w ? `${w}×${setText(s)}` : setText(s);
    });
    const anyWeight = last.sets.some((s) => weightOf(s));
    body = `${shown.join(", ")}${last.sets.length > 5 ? ", …" : ""}${anyWeight ? ` ${suffix}` : ""}`;
  }
  return `${body} · ${shortDate(last.date, todayIso)}`;
}
