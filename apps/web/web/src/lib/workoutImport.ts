/**
 * Web workout import envelope (`ervWorkoutImportVersion: 1`).
 *
 * Paste-import merges each workout by id, matching Android `WorkoutImport.kt`.
 * The preview is a read-only side-by-side diff.
 *
 * Later AI suggest-changes reuses this envelope, then lets the user choose which
 * current exercises to remove and which suggested exercises to add. That picker
 * is specified in `docs/architecture/AI_COACH_MAPLE_INTEGRATION.md` §7.3 and is
 * not part of paste-import.
 */

import {
  cardioActivityLabel,
  stretchLabel,
  type CardioCatalogActivity,
  type StretchCatalogEntry,
  type WeightCatalogExercise,
} from "./catalog";
import { exerciseLabel } from "./weightTraining";
import {
  cardioItemSummary,
  mobilityItemSummary,
  prescriptionSummary,
  segmentItems,
  segmentKindLabel,
  upsertWorkout,
  type Workout,
  type WorkoutCardioLogField,
  type WorkoutCardioPrescription,
  type WorkoutItem,
  type WorkoutMobilityPrescription,
  type WorkoutPrescriptionSet,
  type WorkoutPrescriptionSetSide,
  type WorkoutRestPolicy,
  type WorkoutSegment,
  type WorkoutSegmentKind,
  type WorkoutWeightPrescription,
} from "./workoutTraining";

const IMPORT_VERSION = 1;

const SEGMENT_KINDS = [
  "straight_sets",
  "superset",
  "circuit",
  "composite",
  "cardio",
  "mobility",
  "interval",
] as const satisfies readonly WorkoutSegmentKind[];

const ITEM_TYPES = ["weight", "cardio", "mobility", "note", "rest"] as const;

const WEIGHT_MODES = ["straight", "interval", "time_based", "max_reps"] as const;

const CARDIO_MODES = ["steady", "interval_template", "sprint_intervals"] as const;

const SET_SIDES = ["left", "right", "each", "alternating"] as const;

const CARDIO_LOG_FIELDS = ["INCLINE", "SPEED", "DISTANCE", "NOTES"] as const;

const SESSION_LOG_KEYS = new Set([
  "finishedAtEpochSeconds",
  "completedAtEpochSeconds",
  "heartRateSamples",
  "hrSamples",
]);

export type WorkoutImportIssue = {
  severity: "error" | "warning";
  path: string;
  message: string;
};

export type WorkoutImportCatalogs = {
  exercises: WeightCatalogExercise[];
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
};

export type WorkoutImportParseResult = {
  /** Empty when any issue has severity `error`. */
  workouts: Workout[];
  issues: WorkoutImportIssue[];
};

export type WorkoutImportChange = "same" | "changed" | "removed" | "added";

export type WorkoutImportExerciseRow = {
  change: WorkoutImportChange;
  /** Stable item id on the current workout, when this row has a current exercise. */
  currentKey: string | null;
  incomingKey: string | null;
  currentLabel: string | null;
  currentDetail: string | null;
  incomingLabel: string | null;
  incomingDetail: string | null;
};

export type WorkoutImportSegmentPreview = {
  change: WorkoutImportChange;
  currentKey: string | null;
  incomingKey: string | null;
  heading: string;
  rows: WorkoutImportExerciseRow[];
};

export type WorkoutImportWorkoutPreview = {
  id: string;
  name: string;
  action: "add" | "replace";
  existingName: string | null;
  segments: WorkoutImportSegmentPreview[];
};

type ParseOptions = {
  nowEpochSeconds?: number;
  newId?: () => string;
};

type LabelContext = WorkoutImportCatalogs & {
  formatWeight: (kg: number) => string;
};

export function workoutImportHasErrors(issues: WorkoutImportIssue[]): boolean {
  return issues.some((issue) => issue.severity === "error");
}

export function parseWorkoutImportEnvelope(
  raw: string,
  catalogs: WorkoutImportCatalogs,
  options: ParseOptions = {},
): WorkoutImportParseResult {
  const issues: WorkoutImportIssue[] = [];
  const newId = options.newId ?? (() => crypto.randomUUID());
  const now = options.nowEpochSeconds ?? Math.floor(Date.now() / 1000);
  const text = stripCodeFence(raw);
  if (!text) {
    issues.push({
      severity: "error",
      path: "",
      message: "Paste a workout import envelope.",
    });
    return { workouts: [], issues };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    issues.push({
      severity: "error",
      path: "",
      message: "That text is not valid JSON.",
    });
    return { workouts: [], issues };
  }

  if (!isRecord(parsed)) {
    issues.push({
      severity: "error",
      path: "",
      message: "The envelope must be a JSON object.",
    });
    return { workouts: [], issues };
  }

  if (parsed.ervWorkoutImportVersion !== IMPORT_VERSION) {
    issues.push({
      severity: "error",
      path: "ervWorkoutImportVersion",
      message: "Expected ervWorkoutImportVersion: 1.",
    });
  }

  const customExercises = readCustomExercises(parsed.customExercises, issues);
  const libraryExerciseIds = new Set(catalogs.exercises.map((exercise) => exercise.id));
  const exerciseIds = new Set(libraryExerciseIds);
  for (const id of customExercises.keys()) exerciseIds.add(id);
  const stretchIds = new Set(catalogs.stretchCatalog.map((entry) => entry.id));
  const cardioIds = new Set(catalogs.cardioCatalog.map((activity) => activity.id));

  if (!Array.isArray(parsed.workouts)) {
    issues.push({
      severity: "error",
      path: "workouts",
      message: "workouts must be an array.",
    });
    return { workouts: [], issues };
  }
  if (parsed.workouts.length === 0) {
    issues.push({
      severity: "error",
      path: "workouts",
      message: "Add at least one workout.",
    });
    return { workouts: [], issues };
  }

  const seenIds = new Set<string>();
  const workouts: Workout[] = [];
  parsed.workouts.forEach((entry, index) => {
    const path = `workouts[${index}]`;
    const workout = readWorkout(entry, path, {
      issues,
      newId,
      now,
      exerciseIds,
      libraryExerciseIds,
      customExercises,
      stretchIds,
      cardioIds,
      stretchCatalogEmpty: catalogs.stretchCatalog.length === 0,
      stretchCatalogWarned: false,
    });
    if (!workout) return;
    if (seenIds.has(workout.id)) {
      issues.push({
        severity: "error",
        path: `${path}.id`,
        message: `Duplicate workout id ${workout.id}.`,
      });
      return;
    }
    seenIds.add(workout.id);
    workouts.push(workout);
  });

  if (workoutImportHasErrors(issues)) return { workouts: [], issues };
  return { workouts, issues };
}

export function mergeImportedWorkouts(
  current: Workout[],
  imported: Workout[],
  nowEpochSeconds = Math.floor(Date.now() / 1000),
): Workout[] {
  return imported.reduce((library, workout) => {
    const existing = library.find((entry) => entry.id === workout.id);
    return upsertWorkout(library, {
      ...workout,
      createdAtEpochSeconds:
        workout.createdAtEpochSeconds ?? existing?.createdAtEpochSeconds ?? nowEpochSeconds,
      lastModifiedEpochSeconds: nowEpochSeconds,
    });
  }, current);
}

export function previewWorkoutImport(
  current: Workout[],
  imported: Workout[],
  catalogs: WorkoutImportCatalogs,
  formatWeight: (kg: number) => string = (kg) => `${kg} kg`,
): WorkoutImportWorkoutPreview[] {
  const labels: LabelContext = { ...catalogs, formatWeight };
  return imported.map((workout) => {
    const existing = current.find((entry) => entry.id === workout.id) ?? null;
    return {
      id: workout.id,
      name: workout.name,
      action: existing ? "replace" : "add",
      existingName: existing?.name ?? null,
      segments: existing
        ? diffSegments(existing.segments, workout.segments, labels)
        : workout.segments.map((segment) => segmentPreview(segment, "added", labels)),
    };
  });
}

function diffSegments(
  current: WorkoutSegment[],
  incoming: WorkoutSegment[],
  labels: LabelContext,
): WorkoutImportSegmentPreview[] {
  const used = new Set<number>();
  const previews: WorkoutImportSegmentPreview[] = [];
  current.forEach((segment, index) => {
    const match = matchIndex(
      incoming,
      used,
      (candidate) => Boolean(segment.id && candidate.id && segment.id === candidate.id),
      (candidate) => segmentMatchKey(segment) === segmentMatchKey(candidate),
    );
    if (match < 0) {
      previews.push(segmentPreview(segment, "removed", labels, index));
      return;
    }
    used.add(match);
    const next = incoming[match]!;
    previews.push(diffSegment(segment, next, labels));
  });
  incoming.forEach((segment, index) => {
    if (!used.has(index)) previews.push(segmentPreview(segment, "added", labels));
  });
  return previews;
}

function diffSegment(
  current: WorkoutSegment,
  incoming: WorkoutSegment,
  labels: LabelContext,
): WorkoutImportSegmentPreview {
  const rows = diffItems(segmentItems(current), segmentItems(incoming), labels);
  const structuralChange =
    segmentFingerprint(current) !== segmentFingerprint(incoming) ||
    rows.some((row) => row.change !== "same");
  return {
    change: structuralChange ? "changed" : "same",
    currentKey: current.id ?? null,
    incomingKey: incoming.id ?? null,
    heading: segmentHeading(incoming.title, incoming.kind) || segmentHeading(current.title, current.kind),
    rows,
  };
}

function segmentPreview(
  segment: WorkoutSegment,
  change: "added" | "removed",
  labels: LabelContext,
  index = 0,
): WorkoutImportSegmentPreview {
  const rows = segmentItems(segment).map((item) => exerciseRow(item, change, labels));
  return {
    change,
    currentKey: change === "removed" ? segment.id ?? `segment-${index}` : null,
    incomingKey: change === "added" ? segment.id ?? null : null,
    heading: segmentHeading(segment.title, segment.kind),
    rows,
  };
}

function diffItems(
  current: WorkoutItem[],
  incoming: WorkoutItem[],
  labels: LabelContext,
): WorkoutImportExerciseRow[] {
  const used = new Set<number>();
  const rows: WorkoutImportExerciseRow[] = [];
  for (const item of current) {
    const match = matchIndex(
      incoming,
      used,
      (candidate) => Boolean(item.id && candidate.id && item.id === candidate.id),
      (candidate) => itemFingerprint(item) === itemFingerprint(candidate),
      (candidate) => itemMatchKey(item) === itemMatchKey(candidate),
    );
    if (match < 0) {
      rows.push(exerciseRow(item, "removed", labels));
      continue;
    }
    used.add(match);
    const next = incoming[match]!;
    if (itemFingerprint(item) === itemFingerprint(next)) {
      const described = describeItem(item, labels);
      rows.push({
        change: "same",
        currentKey: item.id ?? itemMatchKey(item),
        incomingKey: next.id ?? itemMatchKey(next),
        currentLabel: described.label,
        currentDetail: described.detail,
        incomingLabel: described.label,
        incomingDetail: described.detail,
      });
    } else {
      const left = describeItem(item, labels);
      const right = describeItem(next, labels);
      rows.push({
        change: "changed",
        currentKey: item.id ?? itemMatchKey(item),
        incomingKey: next.id ?? itemMatchKey(next),
        currentLabel: left.label,
        currentDetail: left.detail,
        incomingLabel: right.label,
        incomingDetail: right.detail,
      });
    }
  }
  incoming.forEach((item, index) => {
    if (!used.has(index)) rows.push(exerciseRow(item, "added", labels));
  });
  return rows;
}

function exerciseRow(
  item: WorkoutItem,
  change: "added" | "removed",
  labels: LabelContext,
): WorkoutImportExerciseRow {
  const described = describeItem(item, labels);
  const key = item.id ?? itemMatchKey(item);
  if (change === "removed") {
    return {
      change,
      currentKey: key,
      incomingKey: null,
      currentLabel: described.label,
      currentDetail: described.detail,
      incomingLabel: null,
      incomingDetail: null,
    };
  }
  return {
    change,
    currentKey: null,
    incomingKey: key,
    currentLabel: null,
    currentDetail: null,
    incomingLabel: described.label,
    incomingDetail: described.detail,
  };
}

function describeItem(
  item: WorkoutItem,
  labels: LabelContext,
): { label: string; detail: string } {
  switch (item.type) {
    case "weight": {
      const label = item.title?.trim() || exerciseLabel(item.exerciseId, labels.exercises);
      const summary = prescriptionSummary(item.prescription, labels.formatWeight);
      return { label, detail: summary };
    }
    case "cardio": {
      const label =
        item.title?.trim() || cardioActivityLabel(item.cardio.activity, labels.cardioCatalog);
      return {
        label,
        detail: detailAfterLabel(cardioItemSummary(item, label), label),
      };
    }
    case "mobility": {
      const label =
        item.title?.trim() || stretchLabel(item.mobility.catalogId, labels.stretchCatalog);
      return {
        label,
        detail: detailAfterLabel(mobilityItemSummary(item, label), label),
      };
    }
    case "note":
      return { label: item.title?.trim() || "Note", detail: item.text };
    case "rest":
      return {
        label: item.title?.trim() || "Rest",
        detail: `${item.durationSeconds}s`,
      };
  }
}

type ReadWorkoutContext = {
  issues: WorkoutImportIssue[];
  newId: () => string;
  now: number;
  exerciseIds: Set<string>;
  libraryExerciseIds: Set<string>;
  customExercises: Map<string, string>;
  stretchIds: Set<string>;
  cardioIds: Set<string>;
  stretchCatalogEmpty: boolean;
  stretchCatalogWarned: boolean;
};

function readWorkout(
  raw: unknown,
  path: string,
  ctx: ReadWorkoutContext,
): Workout | null {
  if (!isRecord(raw)) {
    ctx.issues.push({ severity: "error", path, message: "Each workout must be an object." });
    return null;
  }
  findSessionLogKeys(raw, path, ctx.issues);
  const name = readRequiredString(raw.name, `${path}.name`, "Workout name is required.", ctx.issues);
  const id = readOptionalId(raw.id, `${path}.id`, ctx) ?? ctx.newId();
  const segments = readSegments(raw.segments, `${path}.segments`, ctx);
  if (!name || !segments) return null;
  if (segments.length === 0) {
    ctx.issues.push({
      severity: "warning",
      path: `${path}.segments`,
      message: `"${name}" has no segments, so it is not runnable until you add some.`,
    });
  }
  const workout: Workout = { id, name, segments };
  const description = readOptionalString(raw.description, `${path}.description`, ctx.issues);
  const sourceLabel = readOptionalString(raw.sourceLabel, `${path}.sourceLabel`, ctx.issues);
  if (description) workout.description = description;
  if (sourceLabel) workout.sourceLabel = sourceLabel;
  if (raw.tags != null) {
    const tags = readStringArray(raw.tags, `${path}.tags`, ctx.issues);
    if (tags) workout.tags = tags;
  }
  if (typeof raw.createdAtEpochSeconds === "number" && Number.isFinite(raw.createdAtEpochSeconds)) {
    workout.createdAtEpochSeconds = raw.createdAtEpochSeconds;
  }
  return workout;
}

function readSegments(
  raw: unknown,
  path: string,
  ctx: ReadWorkoutContext,
): WorkoutSegment[] | null {
  if (!Array.isArray(raw)) {
    ctx.issues.push({ severity: "error", path, message: "segments must be an array." });
    return null;
  }
  const segments: WorkoutSegment[] = [];
  raw.forEach((entry, index) => {
    const segment = readSegment(entry, `${path}[${index}]`, ctx);
    if (segment) segments.push(segment);
  });
  return segments;
}

function readSegment(
  raw: unknown,
  path: string,
  ctx: ReadWorkoutContext,
): WorkoutSegment | null {
  if (!isRecord(raw)) {
    ctx.issues.push({ severity: "error", path, message: "Each segment must be an object." });
    return null;
  }
  if (typeof raw.kind !== "string" || !isSegmentKind(raw.kind)) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.kind`,
      message: `Unsupported segment kind. Use ${SEGMENT_KINDS.join(", ")}.`,
    });
    return null;
  }
  const kind = raw.kind;
  const items = readItems(raw.items, `${path}.items`, kind, ctx);
  if (!items) return null;
  if ((kind === "circuit" || kind === "superset") && items.length === 1) {
    ctx.issues.push({
      severity: "warning",
      path: `${path}.items`,
      message: `${segmentKindLabel(kind)} segments usually include at least two exercises.`,
    });
  }
  const segment: WorkoutSegment = {
    id: readOptionalId(raw.id, `${path}.id`, ctx) ?? ctx.newId(),
    kind,
    items,
  };
  const title = readOptionalString(raw.title, `${path}.title`, ctx.issues);
  const notes = readOptionalString(raw.notes, `${path}.notes`, ctx.issues);
  if (title) segment.title = title;
  if (notes) segment.notes = notes;
  if (raw.rounds != null) {
    const rounds = readPositiveInt(raw.rounds, `${path}.rounds`, ctx.issues);
    if (rounds != null) segment.rounds = rounds;
  }
  if (raw.restAfterSeconds != null) {
    const restAfter = readNonNegativeInt(raw.restAfterSeconds, `${path}.restAfterSeconds`, ctx.issues);
    if (restAfter != null) segment.restAfterSeconds = restAfter;
  }
  if (raw.restPolicy != null) {
    const restPolicy = readRestPolicy(raw.restPolicy, `${path}.restPolicy`, ctx.issues);
    if (restPolicy) segment.restPolicy = restPolicy;
  }
  return segment;
}

function readItems(
  raw: unknown,
  path: string,
  kind: WorkoutSegmentKind,
  ctx: ReadWorkoutContext,
): WorkoutItem[] | null {
  if (raw == null) {
    ctx.issues.push({ severity: "error", path, message: "items is required." });
    return null;
  }
  if (!Array.isArray(raw)) {
    ctx.issues.push({ severity: "error", path, message: "items must be an array." });
    return null;
  }
  const items: WorkoutItem[] = [];
  raw.forEach((entry, index) => {
    const item = readItem(entry, `${path}[${index}]`, ctx);
    if (item) items.push(item);
  });
  if (kind === "cardio" && items.length === 0) {
    ctx.issues.push({
      severity: "warning",
      path,
      message: "Cardio segment has no items.",
    });
  }
  return items;
}

function readItem(raw: unknown, path: string, ctx: ReadWorkoutContext): WorkoutItem | null {
  if (!isRecord(raw)) {
    ctx.issues.push({ severity: "error", path, message: "Each item must be an object." });
    return null;
  }
  if (typeof raw.type !== "string" || !isItemType(raw.type)) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.type`,
      message: `Unsupported item type. Use ${ITEM_TYPES.join(", ")}.`,
    });
    return null;
  }
  const id = readOptionalId(raw.id, `${path}.id`, ctx) ?? ctx.newId();
  const title = readOptionalString(raw.title, `${path}.title`, ctx.issues);
  switch (raw.type) {
    case "weight":
      return readWeightItem(raw, path, id, title, ctx);
    case "cardio":
      return readCardioItem(raw, path, id, title, ctx);
    case "mobility":
      return readMobilityItem(raw, path, id, title, ctx);
    case "note":
      return readNoteItem(raw, path, id, title, ctx);
    case "rest":
      return readRestItem(raw, path, id, title, ctx);
  }
}

function readWeightItem(
  raw: Record<string, unknown>,
  path: string,
  id: string,
  title: string | undefined,
  ctx: ReadWorkoutContext,
): WorkoutItem | null {
  const exerciseId = readRequiredString(
    raw.exerciseId,
    `${path}.exerciseId`,
    "Weight items need an exerciseId.",
    ctx.issues,
  );
  if (!exerciseId) return null;
  if (!ctx.exerciseIds.has(exerciseId)) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.exerciseId`,
      message: `Unknown exercise id ${exerciseId}.`,
    });
  } else if (
    ctx.customExercises.has(exerciseId) &&
    !ctx.libraryExerciseIds.has(exerciseId)
  ) {
    ctx.issues.push({
      severity: "warning",
      path: `${path}.exerciseId`,
      message: `Custom exercise "${ctx.customExercises.get(exerciseId)}" is referenced but not added to the catalog.`,
    });
  }
  let alternativeExerciseIds: string[] | undefined;
  if (raw.alternativeExerciseIds != null) {
    const alternatives = readStringArray(
      raw.alternativeExerciseIds,
      `${path}.alternativeExerciseIds`,
      ctx.issues,
    );
    if (alternatives) {
      for (const alternativeId of alternatives) {
        if (!ctx.exerciseIds.has(alternativeId)) {
          ctx.issues.push({
            severity: "error",
            path: `${path}.alternativeExerciseIds`,
            message: `Unknown alternative exercise id ${alternativeId}.`,
          });
        }
      }
      alternativeExerciseIds = alternatives;
    }
  }
  if (!isRecord(raw.prescription)) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.prescription`,
      message: "Weight items need a prescription object.",
    });
    return null;
  }
  const prescription = readWeightPrescription(raw.prescription, `${path}.prescription`, ctx.issues);
  if (!prescription) return null;
  return {
    type: "weight",
    id,
    ...(title ? { title } : {}),
    exerciseId,
    ...(alternativeExerciseIds ? { alternativeExerciseIds } : {}),
    prescription,
  };
}

function readWeightPrescription(
  raw: Record<string, unknown>,
  path: string,
  issues: WorkoutImportIssue[],
): WorkoutWeightPrescription | null {
  const prescription: WorkoutWeightPrescription = {};
  if (raw.mode != null) {
    if (typeof raw.mode !== "string" || !isWeightMode(raw.mode)) {
      issues.push({
        severity: "error",
        path: `${path}.mode`,
        message: `Unsupported prescription mode. Use ${WEIGHT_MODES.join(", ")}.`,
      });
    } else {
      prescription.mode = raw.mode;
    }
  }
  assignOptionalInt(prescription, "setCount", raw.setCount, `${path}.setCount`, issues, 1);
  assignOptionalInt(prescription, "targetReps", raw.targetReps, `${path}.targetReps`, issues, 1);
  assignOptionalNumber(prescription, "targetWeightKg", raw.targetWeightKg, `${path}.targetWeightKg`, issues);
  assignOptionalInt(prescription, "repRangeMin", raw.repRangeMin, `${path}.repRangeMin`, issues, 1);
  assignOptionalInt(prescription, "repRangeMax", raw.repRangeMax, `${path}.repRangeMax`, issues, 1);
  assignOptionalInt(prescription, "targetRir", raw.targetRir, `${path}.targetRir`, issues, 0);
  assignOptionalInt(
    prescription,
    "restBetweenSetsSeconds",
    raw.restBetweenSetsSeconds,
    `${path}.restBetweenSetsSeconds`,
    issues,
    0,
  );
  assignOptionalInt(
    prescription,
    "restAfterExerciseSeconds",
    raw.restAfterExerciseSeconds,
    `${path}.restAfterExerciseSeconds`,
    issues,
    0,
  );
  assignOptionalInt(
    prescription,
    "durationSeconds",
    raw.durationSeconds,
    `${path}.durationSeconds`,
    issues,
    1,
  );
  assignOptionalInt(
    prescription,
    "timedPrepSeconds",
    raw.timedPrepSeconds,
    `${path}.timedPrepSeconds`,
    issues,
    0,
  );
  if (raw.sets != null) {
    if (!Array.isArray(raw.sets)) {
      issues.push({ severity: "error", path: `${path}.sets`, message: "sets must be an array." });
    } else {
      const sets: WorkoutPrescriptionSet[] = [];
      raw.sets.forEach((entry, index) => {
        const set = readPrescriptionSet(entry, `${path}.sets[${index}]`, issues);
        if (set) sets.push(set);
      });
      prescription.sets = sets;
    }
  }
  return prescription;
}

function readPrescriptionSet(
  raw: unknown,
  path: string,
  issues: WorkoutImportIssue[],
): WorkoutPrescriptionSet | null {
  if (!isRecord(raw)) {
    issues.push({ severity: "error", path, message: "Each set must be an object." });
    return null;
  }
  const set: WorkoutPrescriptionSet = {};
  assignOptionalInt(set, "reps", raw.reps, `${path}.reps`, issues, 1);
  assignOptionalInt(set, "repsPerSide", raw.repsPerSide, `${path}.repsPerSide`, issues, 1);
  assignOptionalNumber(set, "weightKg", raw.weightKg, `${path}.weightKg`, issues);
  assignOptionalNumber(set, "targetWeightKg", raw.targetWeightKg, `${path}.targetWeightKg`, issues);
  assignOptionalInt(set, "rir", raw.rir, `${path}.rir`, issues, 0);
  assignOptionalNumber(set, "rpe", raw.rpe, `${path}.rpe`, issues);
  assignOptionalInt(set, "durationSeconds", raw.durationSeconds, `${path}.durationSeconds`, issues, 1);
  assignOptionalInt(set, "targetReps", raw.targetReps, `${path}.targetReps`, issues, 1);
  assignOptionalInt(
    set,
    "targetDurationSeconds",
    raw.targetDurationSeconds,
    `${path}.targetDurationSeconds`,
    issues,
    1,
  );
  if (raw.side != null) {
    if (typeof raw.side !== "string" || !isSetSide(raw.side)) {
      issues.push({
        severity: "error",
        path: `${path}.side`,
        message: `Unsupported side. Use ${SET_SIDES.join(", ")}.`,
      });
    } else {
      set.side = raw.side;
    }
  }
  return set;
}

function readCardioItem(
  raw: Record<string, unknown>,
  path: string,
  id: string,
  title: string | undefined,
  ctx: ReadWorkoutContext,
): WorkoutItem | null {
  if (!isRecord(raw.cardio)) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.cardio`,
      message: "Cardio items need a cardio object.",
    });
    return null;
  }
  const activity = readRequiredString(
    raw.cardio.activity,
    `${path}.cardio.activity`,
    "Cardio items need an activity id.",
    ctx.issues,
  );
  if (!activity) return null;
  if (!ctx.cardioIds.has(activity)) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.cardio.activity`,
      message: `Unknown cardio activity ${activity}. Use the catalog id, such as RUN or ROWING.`,
    });
  }
  const cardio: WorkoutCardioPrescription = { activity };
  if (raw.cardio.mode != null) {
    if (typeof raw.cardio.mode !== "string" || !isCardioMode(raw.cardio.mode)) {
      ctx.issues.push({
        severity: "error",
        path: `${path}.cardio.mode`,
        message: `Unsupported cardio mode. Use ${CARDIO_MODES.join(", ")}.`,
      });
    } else {
      cardio.mode = raw.cardio.mode;
    }
  }
  assignOptionalInt(cardio, "targetMinutes", raw.cardio.targetMinutes, `${path}.cardio.targetMinutes`, ctx.issues, 1);
  assignOptionalInt(cardio, "hrTargetBpm", raw.cardio.hrTargetBpm, `${path}.cardio.hrTargetBpm`, ctx.issues, 1);
  assignOptionalInt(cardio, "hrTargetMinBpm", raw.cardio.hrTargetMinBpm, `${path}.cardio.hrTargetMinBpm`, ctx.issues, 1);
  assignOptionalInt(cardio, "hrTargetMaxBpm", raw.cardio.hrTargetMaxBpm, `${path}.cardio.hrTargetMaxBpm`, ctx.issues, 1);
  assignOptionalInt(cardio, "outerRounds", raw.cardio.outerRounds, `${path}.cardio.outerRounds`, ctx.issues, 1);
  assignOptionalInt(cardio, "rounds", raw.cardio.rounds, `${path}.cardio.rounds`, ctx.issues, 1);
  assignOptionalInt(cardio, "workSeconds", raw.cardio.workSeconds, `${path}.cardio.workSeconds`, ctx.issues, 1);
  assignOptionalInt(cardio, "restSeconds", raw.cardio.restSeconds, `${path}.cardio.restSeconds`, ctx.issues, 0);
  const zone = readOptionalString(raw.cardio.hrZoneLabel, `${path}.cardio.hrZoneLabel`, ctx.issues);
  const routineId = readOptionalString(raw.cardio.cardioRoutineId, `${path}.cardio.cardioRoutineId`, ctx.issues);
  if (zone) cardio.hrZoneLabel = zone;
  if (routineId) cardio.cardioRoutineId = routineId;
  if (raw.cardio.logFields != null) {
    const fields = readStringArray(raw.cardio.logFields, `${path}.cardio.logFields`, ctx.issues);
    if (fields) {
      const logFields: WorkoutCardioLogField[] = [];
      for (const field of fields) {
        if (!isCardioLogField(field)) {
          ctx.issues.push({
            severity: "error",
            path: `${path}.cardio.logFields`,
            message: `Unsupported log field ${field}. Use ${CARDIO_LOG_FIELDS.join(", ")}.`,
          });
        } else {
          logFields.push(field);
        }
      }
      cardio.logFields = logFields;
    }
  }
  if (raw.cardio.legs != null) {
    if (!Array.isArray(raw.cardio.legs)) {
      ctx.issues.push({
        severity: "error",
        path: `${path}.cardio.legs`,
        message: "legs must be an array.",
      });
    } else {
      cardio.legs = [];
      raw.cardio.legs.forEach((entry, index) => {
        if (!isRecord(entry)) {
          ctx.issues.push({
            severity: "error",
            path: `${path}.cardio.legs[${index}]`,
            message: "Each interval leg must be an object.",
          });
          return;
        }
        const workSeconds = readPositiveInt(
          entry.workSeconds,
          `${path}.cardio.legs[${index}].workSeconds`,
          ctx.issues,
        );
        if (workSeconds == null) return;
        const leg: NonNullable<WorkoutCardioPrescription["legs"]>[number] = { workSeconds };
        if (entry.restSeconds != null) {
          const restSeconds = readNonNegativeInt(
            entry.restSeconds,
            `${path}.cardio.legs[${index}].restSeconds`,
            ctx.issues,
          );
          if (restSeconds != null) leg.restSeconds = restSeconds;
        }
        const label = readOptionalString(entry.label, `${path}.cardio.legs[${index}].label`, ctx.issues);
        if (label) leg.label = label;
        if (entry.hrTargetBpm != null) {
          const hr = readPositiveInt(
            entry.hrTargetBpm,
            `${path}.cardio.legs[${index}].hrTargetBpm`,
            ctx.issues,
          );
          if (hr != null) leg.hrTargetBpm = hr;
        }
        cardio.legs?.push(leg);
      });
    }
  }
  return {
    type: "cardio",
    id,
    ...(title ? { title } : {}),
    cardio,
  };
}

function readMobilityItem(
  raw: Record<string, unknown>,
  path: string,
  id: string,
  title: string | undefined,
  ctx: ReadWorkoutContext,
): WorkoutItem | null {
  if (!isRecord(raw.mobility)) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.mobility`,
      message: "Mobility items need a mobility object.",
    });
    return null;
  }
  const catalogId = readRequiredString(
    raw.mobility.catalogId,
    `${path}.mobility.catalogId`,
    "Mobility items need a catalogId.",
    ctx.issues,
  );
  if (!catalogId) return null;
  if (ctx.stretchCatalogEmpty) {
    if (!ctx.stretchCatalogWarned) {
      ctx.stretchCatalogWarned = true;
      ctx.issues.push({
        severity: "warning",
        path: `${path}.mobility.catalogId`,
        message:
          "Stretch catalog is empty, so mobility ids were not checked. Sync from the phone before relying on them.",
      });
    }
  } else if (!ctx.stretchIds.has(catalogId)) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.mobility.catalogId`,
      message: `Unknown stretch id ${catalogId}.`,
    });
  }
  const mobility: WorkoutMobilityPrescription = { catalogId };
  if (raw.mobility.holdSeconds != null) {
    const hold = readPositiveInt(raw.mobility.holdSeconds, `${path}.mobility.holdSeconds`, ctx.issues);
    if (hold != null) mobility.holdSeconds = hold;
  }
  if (raw.mobility.holdSecondsPerSide != null) {
    const hold = readPositiveInt(
      raw.mobility.holdSecondsPerSide,
      `${path}.mobility.holdSecondsPerSide`,
      ctx.issues,
    );
    if (hold != null) mobility.holdSecondsPerSide = hold;
  }
  return {
    type: "mobility",
    id,
    ...(title ? { title } : {}),
    mobility,
  };
}

function readNoteItem(
  raw: Record<string, unknown>,
  path: string,
  id: string,
  title: string | undefined,
  ctx: ReadWorkoutContext,
): WorkoutItem | null {
  if (typeof raw.text !== "string") {
    ctx.issues.push({ severity: "error", path: `${path}.text`, message: "Note items need text." });
    return null;
  }
  return {
    type: "note",
    id,
    ...(title ? { title } : {}),
    text: raw.text,
  };
}

function readRestItem(
  raw: Record<string, unknown>,
  path: string,
  id: string,
  title: string | undefined,
  ctx: ReadWorkoutContext,
): WorkoutItem | null {
  if (raw.durationSeconds == null) {
    ctx.issues.push({
      severity: "error",
      path: `${path}.durationSeconds`,
      message: "Rest items need durationSeconds.",
    });
    return null;
  }
  const durationSeconds = readNonNegativeInt(
    raw.durationSeconds,
    `${path}.durationSeconds`,
    ctx.issues,
  );
  if (durationSeconds == null) return null;
  return {
    type: "rest",
    id,
    ...(title ? { title } : {}),
    durationSeconds,
  };
}

function readRestPolicy(
  raw: unknown,
  path: string,
  issues: WorkoutImportIssue[],
): WorkoutRestPolicy | null {
  if (!isRecord(raw)) {
    issues.push({ severity: "error", path, message: "restPolicy must be an object." });
    return null;
  }
  const policy: WorkoutRestPolicy = {};
  if (raw.restBetweenItemsSeconds != null) {
    const seconds = readNonNegativeInt(raw.restBetweenItemsSeconds, `${path}.restBetweenItemsSeconds`, issues);
    if (seconds != null) policy.restBetweenItemsSeconds = seconds;
  }
  if (raw.restAfterRoundSeconds != null) {
    const seconds = readNonNegativeInt(raw.restAfterRoundSeconds, `${path}.restAfterRoundSeconds`, issues);
    if (seconds != null) policy.restAfterRoundSeconds = seconds;
  }
  return policy;
}

function readCustomExercises(
  raw: unknown,
  issues: WorkoutImportIssue[],
): Map<string, string> {
  const custom = new Map<string, string>();
  if (raw == null) return custom;
  if (!Array.isArray(raw)) {
    issues.push({
      severity: "error",
      path: "customExercises",
      message: "customExercises must be an array.",
    });
    return custom;
  }
  raw.forEach((entry, index) => {
    const path = `customExercises[${index}]`;
    if (!isRecord(entry)) {
      issues.push({ severity: "error", path, message: "Each custom exercise must be an object." });
      return;
    }
    const id = readRequiredString(entry.id, `${path}.id`, "Custom exercises need an id.", issues);
    if (!id) return;
    const name = readOptionalString(entry.name, `${path}.name`, issues) ?? id;
    custom.set(id, name);
  });
  return custom;
}

function findSessionLogKeys(value: unknown, path: string, issues: WorkoutImportIssue[]) {
  if (Array.isArray(value)) {
    value.forEach((entry, index) => findSessionLogKeys(entry, `${path}[${index}]`, issues));
    return;
  }
  if (!isRecord(value)) return;
  for (const [key, child] of Object.entries(value)) {
    const childPath = `${path}.${key}`;
    if (SESSION_LOG_KEYS.has(key)) {
      issues.push({
        severity: "error",
        path: childPath,
        message: "Workout templates cannot include logged session data.",
      });
    }
    findSessionLogKeys(child, childPath, issues);
  }
}

function readRequiredString(
  value: unknown,
  path: string,
  message: string,
  issues: WorkoutImportIssue[],
): string | null {
  if (typeof value !== "string" || !value.trim()) {
    issues.push({ severity: "error", path, message });
    return null;
  }
  return value.trim();
}

function readOptionalString(
  value: unknown,
  path: string,
  issues: WorkoutImportIssue[],
): string | undefined {
  if (value == null) return undefined;
  if (typeof value !== "string") {
    issues.push({ severity: "error", path, message: "Expected text." });
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed || undefined;
}

function readOptionalId(
  value: unknown,
  path: string,
  ctx: ReadWorkoutContext,
): string | undefined {
  if (value == null) return undefined;
  if (typeof value !== "string" || !value.trim()) {
    ctx.issues.push({ severity: "error", path, message: "id must be text." });
    return undefined;
  }
  return value.trim();
}

function readStringArray(
  value: unknown,
  path: string,
  issues: WorkoutImportIssue[],
): string[] | null {
  if (!Array.isArray(value) || value.some((entry) => typeof entry !== "string")) {
    issues.push({ severity: "error", path, message: "Expected an array of strings." });
    return null;
  }
  return value.map((entry) => entry.trim()).filter((entry) => entry.length > 0);
}

function readPositiveInt(value: unknown, path: string, issues: WorkoutImportIssue[]): number | null {
  return readBoundedInt(value, path, issues, 1);
}

function readNonNegativeInt(
  value: unknown,
  path: string,
  issues: WorkoutImportIssue[],
): number | null {
  return readBoundedInt(value, path, issues, 0);
}

function readBoundedInt(
  value: unknown,
  path: string,
  issues: WorkoutImportIssue[],
  min: number,
): number | null {
  if (typeof value !== "number" || !Number.isInteger(value) || value < min) {
    issues.push({
      severity: "error",
      path,
      message: min === 0 ? "Expected a whole number of 0 or more." : "Expected a whole number of 1 or more.",
    });
    return null;
  }
  return value;
}

function assignOptionalInt<T extends object>(
  target: T,
  key: keyof T,
  value: unknown,
  path: string,
  issues: WorkoutImportIssue[],
  min: number,
) {
  if (value == null) return;
  const parsed = readBoundedInt(value, path, issues, min);
  if (parsed != null) target[key] = parsed as T[keyof T];
}

function assignOptionalNumber<T extends object>(
  target: T,
  key: keyof T,
  value: unknown,
  path: string,
  issues: WorkoutImportIssue[],
) {
  if (value == null) return;
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
    issues.push({ severity: "error", path, message: "Expected a number of 0 or more." });
    return;
  }
  target[key] = value as T[keyof T];
}

function matchIndex<T>(
  items: T[],
  used: Set<number>,
  ...predicates: Array<(candidate: T) => boolean>
): number {
  for (const predicate of predicates) {
    const index = items.findIndex((candidate, itemIndex) => !used.has(itemIndex) && predicate(candidate));
    if (index >= 0) return index;
  }
  return -1;
}

function segmentMatchKey(segment: WorkoutSegment): string {
  return `${segment.kind}:${segment.title?.trim() ?? ""}`;
}

function itemMatchKey(item: WorkoutItem): string {
  switch (item.type) {
    case "weight":
      return `weight:${item.exerciseId}`;
    case "cardio":
      return `cardio:${item.cardio.activity}:${item.cardio.cardioRoutineId ?? ""}`;
    case "mobility":
      return `mobility:${item.mobility.catalogId}`;
    case "note":
      return `note:${item.text}`;
    case "rest":
      return `rest:${item.durationSeconds}:${item.title ?? ""}`;
  }
}

function itemFingerprint(item: WorkoutItem): string {
  const { id: _id, ...rest } = item;
  return JSON.stringify(rest);
}

function segmentFingerprint(segment: WorkoutSegment): string {
  const { id: _id, items, ...rest } = segment;
  return JSON.stringify({
    ...rest,
    items: (items ?? []).map((item) => itemFingerprint(item)),
  });
}

function segmentHeading(title: string | null | undefined, kind: WorkoutSegmentKind): string {
  const label = segmentKindLabel(kind);
  const trimmed = title?.trim();
  return trimmed ? `${trimmed} (${label})` : label;
}

function detailAfterLabel(summary: string, label: string): string {
  if (summary === label) return "";
  const prefix = `${label} · `;
  return summary.startsWith(prefix) ? summary.slice(prefix.length) : summary;
}

function stripCodeFence(raw: string): string {
  const trimmed = raw.trim().replace(/^\uFEFF/, "");
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  return (fenced?.[1] ?? trimmed).trim();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isSegmentKind(value: string): value is WorkoutSegmentKind {
  return (SEGMENT_KINDS as readonly string[]).includes(value);
}

function isItemType(value: string): value is (typeof ITEM_TYPES)[number] {
  return (ITEM_TYPES as readonly string[]).includes(value);
}

function isWeightMode(value: string): value is NonNullable<WorkoutWeightPrescription["mode"]> {
  return (WEIGHT_MODES as readonly string[]).includes(value);
}

function isCardioMode(value: string): value is NonNullable<WorkoutCardioPrescription["mode"]> {
  return (CARDIO_MODES as readonly string[]).includes(value);
}

function isSetSide(value: string): value is WorkoutPrescriptionSetSide {
  return (SET_SIDES as readonly string[]).includes(value);
}

function isCardioLogField(value: string): value is WorkoutCardioLogField {
  return (CARDIO_LOG_FIELDS as readonly string[]).includes(value);
}
