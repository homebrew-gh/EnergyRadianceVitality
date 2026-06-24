/**
 * Computed training baseline from relay logs — future AI context (W3).
 * v1: computed locally on web; optional `erv/training-snapshot` relay publish is deferred.
 */

import type { WeightExercise } from "./weightTraining";
import {
  addDays,
  formatIsoDate,
  formatWeightKg,
  parseIsoDate,
  volumeByMuscleGroup,
  type CardioDayLog,
  type MuscleGroupVolume,
  type WeightDayLog,
  type WeightSet,
} from "./trainingHistory";

export const TRAINING_SNAPSHOT_D_TAG = "erv/training-snapshot";
export const SNAPSHOT_VERSION = 1;
export const DEFAULT_SNAPSHOT_WINDOW_DAYS = 28;

export type WorkingWeightEntry = {
  exerciseId: string;
  lastDate: string;
  reps: number;
  weightKg: number;
  estimatedOneRepMaxKg: number | null;
  sessionCount: number;
};

export type MuscleGroupRecency = {
  muscleGroup: string;
  lastDate: string | null;
  daysSince: number | null;
  setCount: number;
};

export type CardioActivityLoad = {
  activityLabel: string;
  sessions: number;
  totalMinutes: number;
};

export type TrainingSnapshot = {
  snapshotVersion: number;
  windowDays: number;
  computedAtEpochSeconds: number;
  strengthSessions: number;
  cardioSessions: number;
  lastStrengthDate: string | null;
  lastCardioDate: string | null;
  workingWeights: WorkingWeightEntry[];
  muscleGroupRecency: MuscleGroupRecency[];
  muscleGroupVolume: MuscleGroupVolume[];
  cardioTotalMinutes: number;
  cardioByActivity: CardioActivityLoad[];
};

export type BuildTrainingSnapshotInput = {
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  exercises: WeightExercise[];
  windowDays?: number;
  computedAtMs?: number;
};

function isWorkingSet(set: WeightSet): boolean {
  return set.reps > 0 || (set.durationSeconds ?? 0) > 0;
}

function daysBetween(fromIso: string, toIso: string): number {
  const from = parseIsoDate(fromIso);
  const to = parseIsoDate(toIso);
  const ms = to.getTime() - from.getTime();
  return Math.max(0, Math.round(ms / (24 * 60 * 60 * 1000)));
}

function estimateEpleyOneRepMaxKg(weightKg: number, reps: number): number | null {
  if (weightKg <= 0 || reps <= 0) return null;
  if (reps === 1) return weightKg;
  return weightKg * (1 + reps / 30);
}

function pickRepresentativeSet(sets: WeightSet[]): WeightSet | null {
  const working = sets.filter((s) => isWorkingSet(s) && (s.weightKg ?? 0) > 0 && s.reps > 0);
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

function filterLogsToWindow<T extends { date: string }>(
  logs: T[],
  startIso: string,
  endIso: string,
): T[] {
  return logs.filter((l) => l.date >= startIso && l.date <= endIso);
}

function computeWorkingWeights(
  weightLogs: WeightDayLog[],
  exercises: WeightExercise[],
): WorkingWeightEntry[] {
  const byExercise = new Map<
    string,
    { lastDate: string; set: WeightSet; sessionCount: number }
  >();

  for (const log of weightLogs) {
    for (const workout of log.workouts) {
      for (const entry of workout.entries) {
        const repSet = pickRepresentativeSet(entry.sets);
        if (!repSet && !entry.hiitBlock?.weightKg) continue;

        const existing = byExercise.get(entry.exerciseId);
        const sessionCount = (existing?.sessionCount ?? 0) + 1;

        if (entry.hiitBlock?.weightKg && !repSet) {
          const hiitSet: WeightSet = {
            reps: entry.hiitBlock.intervals,
            weightKg: entry.hiitBlock.weightKg,
          };
          if (!existing || log.date >= existing.lastDate) {
            byExercise.set(entry.exerciseId, {
              lastDate: log.date,
              set: hiitSet,
              sessionCount,
            });
          } else {
            byExercise.set(entry.exerciseId, { ...existing, sessionCount });
          }
          continue;
        }

        if (!repSet) continue;
        if (!existing || log.date > existing.lastDate) {
          byExercise.set(entry.exerciseId, {
            lastDate: log.date,
            set: repSet,
            sessionCount,
          });
        } else {
          byExercise.set(entry.exerciseId, { ...existing, sessionCount });
        }
      }
    }
  }

  const nameOrder = new Map(exercises.map((e, i) => [e.id, i]));

  return [...byExercise.entries()]
    .map(([exerciseId, row]) => ({
      exerciseId,
      lastDate: row.lastDate,
      reps: row.set.reps,
      weightKg: row.set.weightKg ?? 0,
      estimatedOneRepMaxKg: estimateEpleyOneRepMaxKg(row.set.weightKg ?? 0, row.set.reps),
      sessionCount: row.sessionCount,
    }))
    .filter((r) => r.weightKg > 0)
    .sort((a, b) => {
      const dateCmp = b.lastDate.localeCompare(a.lastDate);
      if (dateCmp !== 0) return dateCmp;
      return (nameOrder.get(a.exerciseId) ?? 9999) - (nameOrder.get(b.exerciseId) ?? 9999);
    });
}

function computeMuscleGroupRecency(
  weightLogs: WeightDayLog[],
  exercises: WeightExercise[],
  todayIso: string,
): MuscleGroupRecency[] {
  const byId = new Map(exercises.map((e) => [e.id, e]));
  const lastDate = new Map<string, string>();
  const setCounts = new Map<string, number>();

  for (const log of weightLogs) {
    for (const workout of log.workouts) {
      for (const entry of workout.entries) {
        const ex = byId.get(entry.exerciseId);
        const group = ex?.muscleGroup?.trim().toLowerCase() || "unknown";
        const working = entry.sets.filter(isWorkingSet).length + (entry.hiitBlock ? 1 : 0);
        if (working <= 0) continue;
        setCounts.set(group, (setCounts.get(group) ?? 0) + working);
        const prev = lastDate.get(group);
        if (!prev || log.date > prev) lastDate.set(group, log.date);
      }
    }
  }

  return [...setCounts.entries()]
    .map(([muscleGroup, setCount]) => {
      const last = lastDate.get(muscleGroup) ?? null;
      return {
        muscleGroup: muscleGroup.replace(/^\w/, (c) => c.toUpperCase()),
        lastDate: last,
        daysSince: last ? daysBetween(last, todayIso) : null,
        setCount,
      };
    })
    .sort((a, b) => {
      const da = a.daysSince ?? 9999;
      const db = b.daysSince ?? 9999;
      if (da !== db) return da - db;
      return b.setCount - a.setCount;
    });
}

function computeCardioLoad(cardioLogs: CardioDayLog[]): {
  totalMinutes: number;
  byActivity: CardioActivityLoad[];
} {
  const byLabel = new Map<string, CardioActivityLoad>();
  let totalMinutes = 0;

  for (const log of cardioLogs) {
    for (const session of log.sessions) {
      totalMinutes += session.durationMinutes;
      const label = session.activity.displayLabel.trim() || "Cardio";
      const row = byLabel.get(label) ?? { activityLabel: label, sessions: 0, totalMinutes: 0 };
      row.sessions += 1;
      row.totalMinutes += session.durationMinutes;
      byLabel.set(label, row);
    }
  }

  return {
    totalMinutes,
    byActivity: [...byLabel.values()].sort((a, b) => b.totalMinutes - a.totalMinutes),
  };
}

function lastLogDate(logs: { date: string }[]): string | null {
  if (logs.length === 0) return null;
  return logs.reduce((max, l) => (l.date > max ? l.date : max), logs[0].date);
}

export function buildTrainingSnapshot(input: BuildTrainingSnapshotInput): TrainingSnapshot {
  const windowDays = input.windowDays ?? DEFAULT_SNAPSHOT_WINDOW_DAYS;
  const computedAtMs = input.computedAtMs ?? Date.now();
  const todayIso = formatIsoDate(new Date(computedAtMs));
  const startIso = addDays(todayIso, -(windowDays - 1));

  const weightWindow = filterLogsToWindow(input.weightLogs, startIso, todayIso);
  const cardioWindow = filterLogsToWindow(input.cardioLogs, startIso, todayIso);

  const strengthSessions = weightWindow.reduce((n, l) => n + l.workouts.length, 0);
  const cardioSessions = cardioWindow.reduce((n, l) => n + l.sessions.length, 0);
  const cardioLoad = computeCardioLoad(cardioWindow);

  return {
    snapshotVersion: SNAPSHOT_VERSION,
    windowDays,
    computedAtEpochSeconds: Math.floor(computedAtMs / 1000),
    strengthSessions,
    cardioSessions,
    lastStrengthDate: lastLogDate(weightWindow),
    lastCardioDate: lastLogDate(cardioWindow),
    workingWeights: computeWorkingWeights(weightWindow, input.exercises),
    muscleGroupRecency: computeMuscleGroupRecency(weightWindow, input.exercises, todayIso),
    muscleGroupVolume: volumeByMuscleGroup(weightWindow, input.exercises),
    cardioTotalMinutes: cardioLoad.totalMinutes,
    cardioByActivity: cardioLoad.byActivity,
  };
}

export function snapshotHasData(snapshot: TrainingSnapshot): boolean {
  return (
    snapshot.strengthSessions > 0 ||
    snapshot.cardioSessions > 0 ||
    snapshot.workingWeights.length > 0
  );
}

export function formatRelativeTime(epochSeconds: number, nowMs = Date.now()): string {
  const diffSec = Math.max(0, Math.floor(nowMs / 1000) - epochSeconds);
  if (diffSec < 60) return "just now";
  if (diffSec < 3600) {
    const m = Math.floor(diffSec / 60);
    return `${m} minute${m === 1 ? "" : "s"} ago`;
  }
  if (diffSec < 86400) {
    const h = Math.floor(diffSec / 3600);
    return `${h} hour${h === 1 ? "" : "s"} ago`;
  }
  const d = Math.floor(diffSec / 86400);
  return `${d} day${d === 1 ? "" : "s"} ago`;
}

export function formatDaysSince(days: number | null): string {
  if (days == null) return "Never";
  if (days === 0) return "Today";
  if (days === 1) return "1 day ago";
  return `${days} days ago`;
}

export function workingWeightSummary(
  entry: WorkingWeightEntry,
  exerciseName: string,
  unit: "KG" | "LB" = "LB",
): string {
  const load = formatWeightKg(entry.weightKg, unit);
  const e1rm =
    entry.estimatedOneRepMaxKg != null
      ? ` · est. 1RM ${formatWeightKg(entry.estimatedOneRepMaxKg, unit)}`
      : "";
  return `${exerciseName}: ${entry.reps} @ ${load}${e1rm}`;
}

export type TrainingSnapshotPayload = {
  snapshotVersion: number;
  windowDays: number;
  computedAtEpochSeconds: number;
  strengthSessions: number;
  cardioSessions: number;
  lastStrengthDate: string | null;
  lastCardioDate: string | null;
  workingWeights: WorkingWeightEntry[];
  muscleGroupRecency: MuscleGroupRecency[];
  muscleGroupVolume: MuscleGroupVolume[];
  cardioTotalMinutes: number;
  cardioByActivity: CardioActivityLoad[];
};

export function trainingSnapshotPayload(snapshot: TrainingSnapshot): string {
  const body: TrainingSnapshotPayload = {
    snapshotVersion: snapshot.snapshotVersion,
    windowDays: snapshot.windowDays,
    computedAtEpochSeconds: snapshot.computedAtEpochSeconds,
    strengthSessions: snapshot.strengthSessions,
    cardioSessions: snapshot.cardioSessions,
    lastStrengthDate: snapshot.lastStrengthDate,
    lastCardioDate: snapshot.lastCardioDate,
    workingWeights: snapshot.workingWeights,
    muscleGroupRecency: snapshot.muscleGroupRecency,
    muscleGroupVolume: snapshot.muscleGroupVolume,
    cardioTotalMinutes: snapshot.cardioTotalMinutes,
    cardioByActivity: snapshot.cardioByActivity,
  };
  return JSON.stringify(body);
}

export function parseTrainingSnapshotPayload(raw: string): TrainingSnapshot | null {
  try {
    const data = JSON.parse(raw) as TrainingSnapshotPayload;
    if (typeof data.computedAtEpochSeconds !== "number") return null;
    return {
      snapshotVersion: data.snapshotVersion ?? SNAPSHOT_VERSION,
      windowDays: data.windowDays ?? DEFAULT_SNAPSHOT_WINDOW_DAYS,
      computedAtEpochSeconds: data.computedAtEpochSeconds,
      strengthSessions: data.strengthSessions ?? 0,
      cardioSessions: data.cardioSessions ?? 0,
      lastStrengthDate: data.lastStrengthDate ?? null,
      lastCardioDate: data.lastCardioDate ?? null,
      workingWeights: Array.isArray(data.workingWeights) ? data.workingWeights : [],
      muscleGroupRecency: Array.isArray(data.muscleGroupRecency) ? data.muscleGroupRecency : [],
      muscleGroupVolume: Array.isArray(data.muscleGroupVolume) ? data.muscleGroupVolume : [],
      cardioTotalMinutes: data.cardioTotalMinutes ?? 0,
      cardioByActivity: Array.isArray(data.cardioByActivity) ? data.cardioByActivity : [],
    };
  } catch {
    return null;
  }
}
