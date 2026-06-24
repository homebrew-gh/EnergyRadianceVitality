/**
 * W6 — Athlete context bundle for external AI or future in-app AiContextBuilder.
 * Markdown (paste-friendly) + JSON (machine-readable dry run).
 */

import type { CardioCatalogActivity, StretchCatalogEntry } from "./catalog";
import type { CardioRoutine } from "./cardioTraining";
import {
  displayEquipmentTitle,
  equipmentSummaryLine,
  type BodyWeightUnit,
  type FitnessEquipmentPayload,
} from "./fitnessEquipment";
import type { StretchRoutine } from "./stretchTraining";
import {
  formatDaysSince,
  workingWeightSummary,
  type TrainingSnapshot,
  trainingSnapshotPayload,
} from "./trainingSnapshot";
import {
  avoidPatternLabel,
  CARDIO_BIAS_OPTIONS,
  EXPERIENCE_LEVEL_OPTIONS,
  isTrainingProfileBlank,
  PRIMARY_GOAL_OPTIONS,
  PROGRESSION_STYLE_OPTIONS,
  SPLIT_PREFERENCE_OPTIONS,
  stylePresetLabel,
  TRAINING_STYLE_PRESETS,
  type TrainingProfilePayload,
} from "./trainingProfile";
import type { WeightExercise, WeightRoutine } from "./weightTraining";
import { exerciseLabel } from "./weightTraining";
import type { Workout } from "./workoutTraining";

export const TRAINING_CONTEXT_VERSION = 1;

const MAX_BUILTIN_EXERCISE_LINES = 80;
const MAX_STRETCH_CATALOG_LINES = 40;

/** Curated hints when a style preset is selected (see ATHLETE_CONTEXT_WEB_PREP §7). */
export const STYLE_PRESET_AI_HINTS: Record<string, string[]> = {
  longevity_blueprint: [
    "Conservative load progression; prioritize recovery and full-body balance.",
    "Favor compound movements at moderate intensity over max-effort singles.",
    "Keep session density manageable; avoid excessive failure sets.",
  ],
  kot_durable: [
    "Emphasize knee and ankle durability: split squats, tibialis, controlled ROM.",
    "Progress range and control before adding load.",
    "Avoid aggressive jumping or deep loaded flexion early in blocks.",
  ],
  powerlifting: [
    "Center squat, bench, and deadlift variants with clear progression.",
    "Use percentage-style or rep-max based loading when history supports it.",
    "Accessories support the main lifts; avoid redundant volume.",
  ],
  hypertrophy: [
    "Target 8–15 reps for most working sets; higher set counts per muscle group.",
    "Include accessories for weak points; moderate rest periods.",
    "Volume progression is primary; load increases when reps are stable.",
  ],
  zone2_minimal: [
    "Prioritize zone 2 cardio base; keep strength blocks short and maintenance-focused.",
    "Full-body or upper/lower splits with low weekly strength volume.",
  ],
  general_athletic: [
    "Balance strength, cardio, and mobility across the week.",
    "Mixed modalities; avoid overspecializing a single movement pattern.",
  ],
};

export type TrainingContextBundleInput = {
  profile: TrainingProfilePayload;
  snapshot: TrainingSnapshot;
  equipment: FitnessEquipmentPayload;
  exercises: WeightExercise[];
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
  workouts: Workout[];
  weightRoutines: WeightRoutine[];
  cardioRoutines: CardioRoutine[];
  stretchRoutines: StretchRoutine[];
  equipmentUnit?: BodyWeightUnit;
};

function mdListSection(title: string, items: string[], emptyMessage: string): string {
  const lines = [`## ${title}`, ""];
  if (items.length === 0) {
    lines.push(emptyMessage, "");
  } else {
    for (const item of items) {
      lines.push(`- ${item}`);
    }
    lines.push("");
  }
  return lines.join("\n");
}

function labelForOption<T extends string>(
  value: T | null | undefined,
  options: { value: T; label: string }[],
): string {
  if (!value) return "Not set";
  return options.find((o) => o.value === value)?.label ?? value;
}

function profileSection(profile: TrainingProfilePayload): string {
  if (isTrainingProfileBlank(profile)) {
    return mdListSection(
      "Training Profile",
      [],
      "No training profile saved. Set goals and style on the Profile tab.",
    );
  }

  const items = [
    `Primary goal: ${labelForOption(profile.primaryGoal, PRIMARY_GOAL_OPTIONS)}`,
    `Experience: ${labelForOption(profile.experienceLevel, EXPERIENCE_LEVEL_OPTIONS)}`,
    profile.typicalSessionMinutes
      ? `Typical session: ${profile.typicalSessionMinutes} minutes`
      : null,
    profile.typicalTrainingDaysPerWeek
      ? `Training days per week: ${profile.typicalTrainingDaysPerWeek}`
      : null,
    `Preferred split: ${labelForOption(profile.preferredSplit, SPLIT_PREFERENCE_OPTIONS)}`,
    profile.progressionStyle
      ? `Progression: ${labelForOption(profile.progressionStyle, PROGRESSION_STYLE_OPTIONS)}`
      : null,
    profile.cardioBias
      ? `Cardio bias: ${labelForOption(profile.cardioBias, CARDIO_BIAS_OPTIONS)}`
      : null,
    profile.ageYears ? `Age: ${profile.ageYears}` : null,
    profile.influenceLabels.length > 0
      ? `Style influences: ${profile.influenceLabels.join(", ")}`
      : null,
    profile.styleNotes?.trim() ? `Style notes: ${profile.styleNotes.trim()}` : null,
    profile.customAvoidNotes?.trim()
      ? `Limitation notes: ${profile.customAvoidNotes.trim()}`
      : null,
  ].filter((s): s is string => Boolean(s));

  let out = mdListSection("Training Profile", items, "");

  if (profile.stylePresetIds.length > 0) {
    out += "## Training Style Presets\n\n";
    for (const id of profile.stylePresetIds) {
      out += `### ${stylePresetLabel(id)} (\`${id}\`)\n\n`;
      const hints = STYLE_PRESET_AI_HINTS[id];
      const fallback = TRAINING_STYLE_PRESETS.find((p) => p.id === id)?.description;
      if (hints) {
        for (const h of hints) out += `- ${h}\n`;
      } else if (fallback) {
        out += `- ${fallback}\n`;
      }
      out += "\n";
    }
  }

  if (profile.avoidMovementPatterns.length > 0) {
    out += mdListSection(
      "Movement Limits",
      profile.avoidMovementPatterns.map((id) => avoidPatternLabel(id)),
      "",
    );
  }

  if (profile.heartRateMaxBpm || profile.heartRateRestingBpm) {
    const hr = [
      profile.heartRateMaxBpm ? `Max HR: ${profile.heartRateMaxBpm} bpm` : null,
      profile.heartRateRestingBpm ? `Resting HR: ${profile.heartRateRestingBpm} bpm` : null,
      profile.heartRateZoneMethod ? `Zone method: ${profile.heartRateZoneMethod}` : null,
    ].filter(Boolean) as string[];
    out += mdListSection("Heart Rate Zones", hr, "");
  }

  return out;
}

function snapshotSection(snapshot: TrainingSnapshot, exercises: WeightExercise[]): string {
  const lines = [
    `Rolling window: ${snapshot.windowDays} days`,
    `Computed at epoch: ${snapshot.computedAtEpochSeconds}`,
    `Strength sessions: ${snapshot.strengthSessions}`,
    `Cardio sessions: ${snapshot.cardioSessions}`,
    snapshot.lastStrengthDate ? `Last strength date: ${snapshot.lastStrengthDate}` : null,
    snapshot.lastCardioDate ? `Last cardio date: ${snapshot.lastCardioDate}` : null,
    `Cardio minutes (${snapshot.windowDays}d): ${snapshot.cardioTotalMinutes}`,
  ].filter((s): s is string => Boolean(s));

  let out = mdListSection("Training Baseline (computed from logs)", lines, "");

  if (snapshot.workingWeights.length > 0) {
    out += mdListSection(
      "Recent Working Weights",
      snapshot.workingWeights.slice(0, 20).map((row) =>
        workingWeightSummary(row, exerciseLabel(row.exerciseId, exercises)),
      ),
      "",
    );
  }

  if (snapshot.muscleGroupRecency.length > 0) {
    out += mdListSection(
      "Muscle Group Recency",
      snapshot.muscleGroupRecency.slice(0, 12).map(
        (row) =>
          `${row.muscleGroup}: ${formatDaysSince(row.daysSince)} (${row.setCount} sets in window)`,
      ),
      "",
    );
  }

  if (snapshot.cardioByActivity.length > 0) {
    out += mdListSection(
      "Cardio By Activity",
      snapshot.cardioByActivity.map(
        (row) => `${row.activityLabel}: ${row.totalMinutes} min, ${row.sessions} sessions`,
      ),
      "",
    );
  }

  return out;
}

function equipmentSection(equipment: FitnessEquipmentPayload, unit: BodyWeightUnit): string {
  const items: string[] = [
    equipment.gymMembership ? "Commercial gym access: yes" : "Commercial gym access: no",
    ...equipment.equipment
      .slice()
      .sort((a, b) =>
        displayEquipmentTitle(a, unit).localeCompare(displayEquipmentTitle(b, unit)),
      )
      .map((item) => {
        const title = displayEquipmentTitle(item, unit);
        const detail = equipmentSummaryLine(item, unit);
        return detail ? `${title} — ${detail}` : title;
      }),
  ];
  if (equipment.enabledWeightExercisePackIds.length > 0) {
    items.push(
      `Enabled specialty packs: ${equipment.enabledWeightExercisePackIds.sort().join(", ")}`,
    );
  }
  return mdListSection("Equipment And Gym", items, "No equipment profile saved.");
}

export function buildTrainingContextMarkdown(input: TrainingContextBundleInput): string {
  const unit = input.equipmentUnit ?? "LB";
  const customExercises = input.exercises
    .filter((e) => !e.id.startsWith("erv-weight-exercise-"))
    .sort((a, b) => a.name.localeCompare(b.name));

  const builtinExerciseLines = input.exercises
    .filter((e) => e.id.startsWith("erv-weight-exercise-"))
    .sort((a, b) => a.name.localeCompare(b.name))
    .slice(0, MAX_BUILTIN_EXERCISE_LINES)
    .map((e) => `\`${e.id}\` — ${e.name} (${e.muscleGroup}, ${e.equipment})`);

  const stretchCatalogLines = input.stretchCatalog
    .slice()
    .sort((a, b) => a.name.localeCompare(b.name))
    .slice(0, MAX_STRETCH_CATALOG_LINES)
    .map((e) => `\`${e.id}\` — ${e.name}`);

  const cardioEnums = input.cardioCatalog.map((a) => `\`${a.id}\``);

  return [
    "# ERV Training Context Bundle",
    "",
    "Generated from the Start9 web companion for workout/plan generation.",
    "Prefer existing ids and equipment constraints below over inventing new ones.",
    "",
    `Exported at epoch: ${Math.floor(Date.now() / 1000)}`,
    "",
    "---",
    "",
    "## Planning Rules",
    "",
    "- Output must be valid ERV workout JSON (see workouts import schema) when generating workouts.",
    "- Use only exercise ids listed below or custom ids that already exist on this account.",
    "- Respect equipment limits — do not prescribe movements that require unavailable gear.",
    "- Respect movement limits and limitation notes in the training profile.",
    "- Use working weights and muscle recency as progression hints, not medical prescriptions.",
    "- Prefer referencing saved workout ids when reusing an existing template.",
    "",
    profileSection(input.profile),
    snapshotSection(input.snapshot, input.exercises),
    equipmentSection(input.equipment, unit),
    mdListSection(
      "Saved Workouts",
      input.workouts
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((w) => `\`workoutId: ${w.id}\` — ${w.name}`),
      "No saved workouts in library.",
    ),
    mdListSection(
      "Saved Weight Routines",
      input.weightRoutines
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((r) => `\`weightRoutineId: ${r.id}\` — ${r.name}`),
      "No saved weight routines.",
    ),
    mdListSection(
      "Saved Cardio Routines",
      input.cardioRoutines
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((r) => `\`cardioRoutineId: ${r.id}\` — ${r.name}`),
      "No saved cardio routines.",
    ),
    mdListSection(
      "Saved Stretch Routines",
      input.stretchRoutines
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((r) => `\`stretchRoutineId: ${r.id}\` — ${r.name}`),
      "No saved stretch routines.",
    ),
    mdListSection(
      "Custom Weight Exercise Ids",
      customExercises.map((e) => `\`${e.id}\` — ${e.name}`),
      "No custom weight exercises.",
    ),
    mdListSection(
      "Built-In Weight Exercise Ids (sample)",
      builtinExerciseLines,
      "Catalog not loaded.",
    ),
    mdListSection(
      "Built-In Stretch Catalog Ids (sample)",
      stretchCatalogLines,
      "Stretch catalog not loaded.",
    ),
    mdListSection("Allowed Cardio Activity Ids", cardioEnums, ""),
  ].join("\n");
}

export function buildTrainingContextJson(input: TrainingContextBundleInput): string {
  const customExercises = input.exercises.filter(
    (e) => !e.id.startsWith("erv-weight-exercise-"),
  );
  const body = {
    ervTrainingContextVersion: TRAINING_CONTEXT_VERSION,
    exportedAtEpochSeconds: Math.floor(Date.now() / 1000),
    profile: isTrainingProfileBlank(input.profile) ? null : input.profile,
    snapshot: JSON.parse(trainingSnapshotPayload(input.snapshot)),
    equipment: input.equipment,
    savedWorkoutIds: input.workouts.map((w) => ({ id: w.id, name: w.name })),
    savedWeightRoutineIds: input.weightRoutines.map((r) => ({ id: r.id, name: r.name })),
    savedCardioRoutineIds: input.cardioRoutines.map((r) => ({ id: r.id, name: r.name })),
    savedStretchRoutineIds: input.stretchRoutines.map((r) => ({ id: r.id, name: r.name })),
    customExerciseIds: customExercises.map((e) => ({ id: e.id, name: e.name })),
  };
  return JSON.stringify(body, null, 2);
}

export type ContextCompletenessItem = {
  id: string;
  label: string;
  ok: boolean;
  hint: string;
};

export function trainingContextCompleteness(
  input: TrainingContextBundleInput,
): ContextCompletenessItem[] {
  return [
    {
      id: "profile",
      label: "Training profile",
      ok: !isTrainingProfileBlank(input.profile),
      hint: "Set on Profile tab",
    },
    {
      id: "snapshot",
      label: "Training baseline (logs)",
      ok: input.snapshot.strengthSessions > 0 || input.snapshot.cardioSessions > 0,
      hint: "Log sessions on Android and reload Progress",
    },
    {
      id: "equipment",
      label: "Equipment profile",
      ok: input.equipment.gymMembership || input.equipment.equipment.length > 0,
      hint: "Set on Equipment tab",
    },
    {
      id: "workouts",
      label: "Saved workouts",
      ok: input.workouts.length > 0,
      hint: "Optional — build in Workout Builder",
    },
  ];
}
