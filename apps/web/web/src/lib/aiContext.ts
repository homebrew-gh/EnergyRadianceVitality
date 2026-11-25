import type { ProgressionGuardrailContext } from "./aiProgressionGuardrails";
import type { AiContextLevel } from "./api";
import { cardioActivityLabel, stretchLabel } from "./catalog";
import {
  displayEquipmentTitle,
  equipmentSummaryLine,
  kgToLb,
  type BodyWeightUnit,
  type FitnessEquipmentPayload,
} from "./fitnessEquipment";
import { buildLiftFacts, buildWeeklySetsByMuscle, buildWeeklyTraining } from "./coachFacts";
import {
  buildTrainingContextJson,
  STYLE_PRESET_AI_HINTS,
  type TrainingContextBundleInput,
} from "./trainingContextBundle";
import {
  addDays,
  formatIsoDate,
  summarizeCardioSession,
  summarizeWeightSession,
  type CardioDayLog,
  type WeightDayLog,
} from "./trainingHistory";
import {
  avoidPatternLabel,
  CARDIO_BIAS_OPTIONS,
  EXPERIENCE_LEVEL_OPTIONS,
  HEART_RATE_ZONE_METHOD_OPTIONS,
  isTrainingProfileBlank,
  PRIMARY_GOAL_OPTIONS,
  PROGRESSION_STYLE_OPTIONS,
  stylePresetLabel,
  TRAINING_STYLE_PRESETS,
  type TrainingProfilePayload,
} from "./trainingProfile";
import { exerciseLabel } from "./weightTraining";
import { segmentItems, type Workout, type WorkoutItem } from "./workoutTraining";

const RECENT_SESSION_LIMIT = 8;
const LIFT_LIMITS = { STANDARD: { lifts: 8, sessions: 3 }, FULL: { lifts: 25, sessions: 5 } } as const;

export type AnalysisContext = {
  context: Record<string, unknown>;
  json: string;
  hash: string;
  byteLength: number;
};

/**
 * Review context uses names only. Ids stay in the library for later workout edits.
 * Loads are converted to the athlete's unit (pounds by default) and rounded, so the model
 * never sees stored kilograms.
 */
export function buildAnalysisContext(
  input: TrainingContextBundleInput,
  options: { contextLevel: AiContextLevel; periodWeeks: number | null; todayIso?: string },
): AnalysisContext {
  const base = JSON.parse(buildTrainingContextJson(input)) as Record<string, unknown>;
  const level = options.contextLevel;
  const unit: BodyWeightUnit = input.equipmentUnit ?? "LB";
  const today = options.todayIso ?? todayFromSnapshot(input);
  const context: Record<string, unknown> = {
    ervTrainingContextVersion: base.ervTrainingContextVersion,
    contextLevel: level,
    units: { weight: unitSuffix(unit), distance: unit === "KG" ? "km" : "mi" },
    today,
    periodWeeks: options.periodWeeks,
    periodStart: periodStart(today, options.periodWeeks, input),
    periodEnd: today,
    profile: profileForReview(input.profile),
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
  };

  if (level === "MINIMAL") {
    context.snapshot = snapshotAggregates(base.snapshot);
  } else {
    const weightLogs = input.weightLogs ?? [];
    const cardioLogs = input.cardioLogs ?? [];
    const start = context.periodStart as string | null;
    const limits = LIFT_LIMITS[level];
    context.snapshot = snapshotForReview(base.snapshot);
    context.liftTrends = buildLiftFacts({
      weightLogs,
      exercises: input.exercises,
      workouts: input.workouts,
      unit,
      today,
      liftLimit: limits.lifts,
      sessionsPerLift: limits.sessions,
    });
    context.weeklyTraining = buildWeeklyTraining({
      weightLogs,
      cardioLogs,
      today,
      periodStart: start,
      plannedDaysPerWeek: input.profile.typicalTrainingDaysPerWeek ?? null,
    });
    context.weeklySetsByMuscle = buildWeeklySetsByMuscle({
      weightLogs,
      exercises: input.exercises,
      today,
      periodStart: start,
    });
    context.equipment = equipmentForReview(input.equipment, unit);
    context.progressionGuardrails = guardrailsForReview(
      base.progressionGuardrails as ProgressionGuardrailContext | undefined,
    );
    context.recentSessionSummaries = recentSessionSummaries(weightLogs, cardioLogs, input.workouts);
  }

  const json = JSON.stringify(context);
  return {
    context,
    json,
    hash: hashTrainingContext(context),
    byteLength: new TextEncoder().encode(json).length,
  };
}

/** Weight in the athlete's unit, rounded to the nearest 0.5. */
export function loadInUnit(kg: number, unit: BodyWeightUnit): number {
  const value = unit === "KG" ? kg : kgToLb(kg);
  const rounded = Math.round(value * 2) / 2;
  return Object.is(rounded, -0) ? 0 : rounded;
}

function unitSuffix(unit: BodyWeightUnit): "lb" | "kg" {
  return unit === "KG" ? "kg" : "lb";
}

function todayFromSnapshot(input: TrainingContextBundleInput): string {
  const seconds = input.snapshot.computedAtEpochSeconds;
  return formatIsoDate(new Date(seconds > 0 ? seconds * 1000 : Date.now()));
}

function periodStart(
  today: string,
  periodWeeks: number | null,
  input: TrainingContextBundleInput,
): string | null {
  if (periodWeeks != null && periodWeeks > 0) return addDays(today, -(periodWeeks * 7) + 1);
  const dates = [
    ...(input.weightLogs ?? []).map((log) => log.date),
    ...(input.cardioLogs ?? []).map((log) => log.date),
  ].sort();
  return dates[0] ?? null;
}

function optionLabel<T extends string>(
  value: T | null | undefined,
  options: { value: T; label: string }[],
): string | null {
  if (!value) return null;
  return options.find((option) => option.value === value)?.label ?? value;
}

/** Readable profile: labels instead of enum codes, plus the coaching hints for each style preset. */
export function profileForReview(
  profile: TrainingProfilePayload,
): Record<string, unknown> | null {
  if (isTrainingProfileBlank(profile)) return null;
  const out: Record<string, unknown> = {};
  const put = (key: string, value: unknown) => {
    if (value == null) return;
    if (typeof value === "string" && value.trim() === "") return;
    if (Array.isArray(value) && value.length === 0) return;
    out[key] = value;
  };
  put("primaryGoal", optionLabel(profile.primaryGoal, PRIMARY_GOAL_OPTIONS));
  put("experience", optionLabel(profile.experienceLevel, EXPERIENCE_LEVEL_OPTIONS));
  put("typicalSessionMinutes", profile.typicalSessionMinutes || null);
  put("trainingDaysPerWeek", profile.typicalTrainingDaysPerWeek || null);
  put("progressionStyle", optionLabel(profile.progressionStyle, PROGRESSION_STYLE_OPTIONS));
  put("cardioBias", optionLabel(profile.cardioBias, CARDIO_BIAS_OPTIONS));
  put("ageYears", profile.ageYears || null);
  put(
    "trainingStyles",
    profile.stylePresetIds.map((id) => ({
      name: stylePresetLabel(id),
      coachingHints:
        STYLE_PRESET_AI_HINTS[id] ??
        [TRAINING_STYLE_PRESETS.find((preset) => preset.id === id)?.description].filter(
          (hint): hint is string => Boolean(hint),
        ),
    })),
  );
  put("styleNotes", profile.styleNotes?.trim());
  put("movementsToAvoid", profile.avoidMovementPatterns.map((id) => avoidPatternLabel(id)));
  put("limitationNotes", profile.customAvoidNotes?.trim());
  put("maxHeartRateBpm", profile.heartRateMaxBpm || null);
  put("restingHeartRateBpm", profile.heartRateRestingBpm || null);
  put(
    "heartRateZoneMethod",
    optionLabel(profile.heartRateZoneMethod, HEART_RATE_ZONE_METHOD_OPTIONS),
  );
  return Object.keys(out).length > 0 ? out : null;
}

/** Equipment as readable lines in the athlete's unit, the same text the Equipment page shows. */
export function equipmentForReview(
  equipment: FitnessEquipmentPayload,
  unit: BodyWeightUnit,
): Record<string, unknown> {
  const items = equipment.equipment
    .slice()
    .sort((a, b) => displayEquipmentTitle(a, unit).localeCompare(displayEquipmentTitle(b, unit)))
    .map((item) => {
      const title = displayEquipmentTitle(item, unit);
      const detail = equipmentSummaryLine(item, unit);
      return detail ? `${title}: ${detail}` : title;
    });
  const out: Record<string, unknown> = { gymMembership: equipment.gymMembership, items };
  if (equipment.enabledWeightExercisePackIds.length > 0) {
    out.specialtyPacks = [...equipment.enabledWeightExercisePackIds].sort();
  }
  return out;
}

/** Heart-rate rules only matter when there is heart-rate data. */
function guardrailsForReview(
  guardrails: ProgressionGuardrailContext | undefined,
): Record<string, unknown> | null {
  if (!guardrails) return null;
  if (guardrails.heartRate.status === "missing") {
    return {
      progressionPolicy: guardrails.policyLabel,
      heartRate: "No heart-rate data in this period.",
    };
  }
  const hr = guardrails.heartRate;
  return {
    progressionPolicy: guardrails.policyLabel,
    heartRate: {
      status: hr.status,
      confidence: hr.confidence,
      sessionsWithHr: hr.sessionsWithHr,
      sessionsWithZoneLoad: hr.sessionsWithZoneLoad,
      currentWeekZ4Z5Minutes: Math.round(hr.currentWeekHighIntensitySeconds / 60),
      baselineWeeklyZ4Z5Minutes: Math.round(hr.baselineWeeklyHighIntensitySeconds / 60),
      highIntensityRatioVsBaseline:
        hr.highIntensityDeltaRatio != null ? Math.round(hr.highIntensityDeltaRatio * 10) / 10 : null,
      notes: hr.notes,
    },
    rules: guardrails.rules,
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

/** Period totals only. Per-lift detail lives in liftTrends and weekly sets in weeklySetsByMuscle. */
export function snapshotForReview(snapshot: unknown): unknown {
  if (!snapshot || typeof snapshot !== "object") return snapshot;
  const source = { ...(snapshot as Record<string, unknown>) };
  delete source.snapshotVersion;
  delete source.computedAtEpochSeconds;
  delete source.workingWeights;
  delete source.muscleGroupVolume;
  return source;
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

function recentSessionSummaries(
  weightLogs: WeightDayLog[],
  cardioLogs: CardioDayLog[],
  workouts: Workout[],
) {
  const workoutNames = new Map(workouts.map((workout) => [workout.id, workout.name]));
  const rows: {
    date: string;
    kind: "strength" | "cardio";
    summary: string;
    savedWorkout?: string;
    avgRpe?: number;
    minutes?: number;
  }[] = [];
  for (const day of weightLogs) {
    for (const session of day.workouts) {
      const row: (typeof rows)[number] = {
        date: day.date,
        kind: "strength",
        summary: summarizeWeightSession(session),
      };
      const saved = session.workoutLink ? workoutNames.get(session.workoutLink.workoutId) : undefined;
      if (saved) row.savedWorkout = saved;
      const rpes = session.entries
        .flatMap((entry) => entry.sets.map((set) => set.rpe))
        .filter((rpe): rpe is number => typeof rpe === "number" && rpe > 0);
      if (rpes.length > 0) {
        row.avgRpe = Math.round((rpes.reduce((sum, rpe) => sum + rpe, 0) / rpes.length) * 10) / 10;
      }
      if (session.durationSeconds && session.durationSeconds > 0) {
        row.minutes = Math.round(session.durationSeconds / 60);
      }
      rows.push(row);
    }
  }
  for (const day of cardioLogs) {
    for (const session of day.sessions) {
      const hr = session.heartRate?.avgBpm;
      const summary = hr ? `${summarizeCardioSession(session)} · avg ${hr} bpm` : summarizeCardioSession(session);
      const row: (typeof rows)[number] = { date: day.date, kind: "cardio", summary };
      const saved = session.workoutLink ? workoutNames.get(session.workoutLink.workoutId) : undefined;
      if (saved) row.savedWorkout = saved;
      rows.push(row);
    }
  }
  return rows
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, RECENT_SESSION_LIMIT);
}

/** Set-by-set text such as "185×8, 185×7, 185×6" in the athlete's unit. */
export function setDetail(
  sets: { reps: number; weightKg?: number | null; durationSeconds?: number | null }[],
  unit: BodyWeightUnit,
): string {
  if (sets.length === 0) return "no sets";
  const parts = sets.map((set) => {
    if (set.reps <= 0 && (set.durationSeconds ?? 0) > 0) return `${set.durationSeconds}s hold`;
    const kg = set.weightKg ?? 0;
    if (kg > 0) return `${loadInUnit(kg, unit)}×${set.reps}`;
    return `${set.reps} reps`;
  });
  const loaded = sets.some((set) => (set.weightKg ?? 0) > 0);
  return loaded ? `${parts.join(", ")} ${unitSuffix(unit)}` : parts.join(", ");
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
