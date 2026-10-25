import type { AiContextLevel } from "./api";
import { cardioActivityLabel, stretchLabel } from "./catalog";
import {
  buildTrainingContextJson,
  type TrainingContextBundleInput,
} from "./trainingContextBundle";
import {
  summarizeCardioSession,
  summarizeWeightSession,
  type CardioDayLog,
  type WeightDayLog,
} from "./trainingHistory";
import { exerciseLabel } from "./weightTraining";
import { segmentItems, type Workout, type WorkoutItem } from "./workoutTraining";

const RECENT_SESSION_LIMIT = 8;
const RECENT_EXERCISE_LIMIT = 24;

export type AnalysisContext = {
  context: Record<string, unknown>;
  json: string;
  hash: string;
  byteLength: number;
};

/** Review context uses names only. Ids stay in the library for later workout edits. */
export function buildAnalysisContext(
  input: TrainingContextBundleInput,
  options: { contextLevel: AiContextLevel; periodWeeks: number | null },
): AnalysisContext {
  const base = JSON.parse(buildTrainingContextJson(input)) as Record<string, unknown>;
  const level = options.contextLevel;
  const context: Record<string, unknown> = {
    ervTrainingContextVersion: base.ervTrainingContextVersion,
    contextLevel: level,
    periodWeeks: options.periodWeeks,
    profile: base.profile ?? null,
    savedWorkouts: input.workouts.map((workout) => ({
      name: workout.name,
      exercises: workoutExerciseNames(workout, input),
    })),
    savedWeightRoutines: input.weightRoutines.map((routine) => ({
      name: routine.name,
      exercises: routine.exerciseIds
        .map((id) => namedExercise(id, null, input.exercises))
        .filter((name) => name.length > 0),
    })),
    savedCardioRoutineNames: namesOnly(base.savedCardioRoutineIds),
    savedStretchRoutineNames: namesOnly(base.savedStretchRoutineIds),
    customExerciseNames: namesOnly(base.customExerciseIds),
  };

  if (level === "MINIMAL") {
    context.snapshot = snapshotAggregates(base.snapshot);
  } else {
    context.snapshot = snapshotForReview(base.snapshot, input.exercises);
    context.equipment = base.equipment;
    context.progressionGuardrails = base.progressionGuardrails;
    context.recentSessionSummaries = recentSessionSummaries(
      input.weightLogs ?? [],
      input.cardioLogs ?? [],
    );
  }

  if (level === "FULL") {
    context.recentExerciseRows = recentExerciseRows(input.weightLogs ?? [], input.exercises);
  }

  const json = JSON.stringify(context);
  return {
    context,
    json,
    hash: hashTrainingContext(context),
    byteLength: new TextEncoder().encode(json).length,
  };
}

export function hashTrainingContext(context: Record<string, unknown>): string {
  const stable = { ...context };
  delete stable.exportedAtEpochSeconds;
  return fnvPair(stableStringify(stable));
}

function workoutExerciseNames(
  workout: Workout,
  input: TrainingContextBundleInput,
): string[] {
  const names: string[] = [];
  for (const segment of workout.segments) {
    for (const item of segmentItems(segment)) {
      const name = itemName(item, input);
      if (name) names.push(name);
    }
  }
  return names;
}

function itemName(item: WorkoutItem, input: TrainingContextBundleInput): string {
  switch (item.type) {
    case "weight":
      return namedExercise(item.exerciseId, item.title, input.exercises);
    case "mobility":
      return namedCatalogEntry(item.mobility.catalogId, item.title, (id) =>
        stretchLabel(id, input.stretchCatalog),
      );
    case "cardio":
      return namedCatalogEntry(item.cardio.activity, item.title, (id) =>
        cardioActivityLabel(id, input.cardioCatalog),
      );
    case "note":
    case "rest":
      return "";
  }
}

function namedExercise(
  id: string,
  title: string | null | undefined,
  exercises: TrainingContextBundleInput["exercises"],
): string {
  return namedCatalogEntry(id, title, (exerciseId) => exerciseLabel(exerciseId, exercises));
}

function namedCatalogEntry(
  id: string,
  title: string | null | undefined,
  labelFor: (id: string) => string,
): string {
  const titled = title?.trim();
  if (titled && !isOpaqueId(titled)) return titled;
  const label = labelFor(id);
  if (!label || label === id && isOpaqueId(id)) return "";
  if (isOpaqueId(label)) return "";
  return label;
}

function isOpaqueId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.trim());
}

function namesOnly(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (typeof item === "string") return item;
      if (item && typeof item === "object" && "name" in item) {
        const name = (item as { name?: unknown }).name;
        return typeof name === "string" ? name : "";
      }
      return "";
    })
    .filter((name) => name.length > 0);
}

function snapshotForReview(
  snapshot: unknown,
  exercises: TrainingContextBundleInput["exercises"],
): unknown {
  if (!snapshot || typeof snapshot !== "object") return snapshot;
  const source = snapshot as Record<string, unknown>;
  if (!Array.isArray(source.workingWeights)) return snapshot;
  return {
    ...source,
    workingWeights: source.workingWeights.map((row) => {
      if (!row || typeof row !== "object") return row;
      const entry = row as Record<string, unknown>;
      const exerciseId = typeof entry.exerciseId === "string" ? entry.exerciseId : "";
      const rest = { ...entry };
      delete rest.exerciseId;
      return { name: exerciseLabel(exerciseId, exercises), ...rest };
    }),
  };
}

function snapshotAggregates(snapshot: unknown): Record<string, unknown> | null {
  if (!snapshot || typeof snapshot !== "object") return null;
  const source = snapshot as Record<string, unknown>;
  return {
    strengthSessions: source.strengthSessions ?? 0,
    cardioSessions: source.cardioSessions ?? 0,
    lastStrengthDate: source.lastStrengthDate ?? null,
    lastCardioDate: source.lastCardioDate ?? null,
    cardioTotalMinutes: source.cardioTotalMinutes ?? 0,
  };
}

function recentSessionSummaries(weightLogs: WeightDayLog[], cardioLogs: CardioDayLog[]) {
  const rows: { date: string; kind: "strength" | "cardio"; summary: string }[] = [];
  for (const day of weightLogs) {
    for (const session of day.workouts) {
      rows.push({ date: day.date, kind: "strength", summary: summarizeWeightSession(session) });
    }
  }
  for (const day of cardioLogs) {
    for (const session of day.sessions) {
      const hr = session.heartRate?.avgBpm;
      const summary = hr ? `${summarizeCardioSession(session)} · avg ${hr} bpm` : summarizeCardioSession(session);
      rows.push({ date: day.date, kind: "cardio", summary });
    }
  }
  return rows
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, RECENT_SESSION_LIMIT);
}

function recentExerciseRows(
  weightLogs: WeightDayLog[],
  exercises: TrainingContextBundleInput["exercises"],
) {
  const rows: { date: string; name: string; detail: string }[] = [];
  const days = [...weightLogs].sort((a, b) => b.date.localeCompare(a.date));
  for (const day of days) {
    for (const session of day.workouts) {
      for (const entry of session.entries) {
        rows.push({
          date: day.date,
          name: exerciseLabel(entry.exerciseId, exercises),
          detail: setDetail(entry.sets),
        });
        if (rows.length >= RECENT_EXERCISE_LIMIT) return rows;
      }
    }
  }
  return rows;
}

function setDetail(sets: { reps: number; weightKg?: number | null }[]): string {
  if (sets.length === 0) return "no sets";
  const reps = sets.map((set) => set.reps).filter((reps) => reps > 0);
  const loads = sets.map((set) => set.weightKg ?? 0).filter((kg) => kg > 0);
  const best = loads.length > 0 ? Math.max(...loads) : 0;
  const repText = reps.length > 0 ? reps.join(",") : `${sets.length} sets`;
  return best > 0 ? `${sets.length} sets · ${repText} · best ${best} kg` : `${sets.length} sets · ${repText}`;
}

function stableStringify(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  if (value && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>).sort(([a], [b]) =>
      a.localeCompare(b),
    );
    return `{${entries.map(([key, child]) => `${JSON.stringify(key)}:${stableStringify(child)}`).join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

function fnvPair(text: string): string {
  return `${fnv1a(text, 0x811c9dc5)}-${fnv1a(text, 0x01000193)}`;
}

function fnv1a(text: string, seed: number): string {
  let hash = seed >>> 0;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}
