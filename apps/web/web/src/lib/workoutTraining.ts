/** Matches Android WorkoutSync — `erv/workouts/library`. */

import type { WeightCatalogExercise } from "./catalog";
import {
  exerciseSupportsTargetWeight,
  exerciseSetLoggingStyle,
  type WeightSetLoggingStyle,
} from "./exerciseLogging";

export const WORKOUTS_LIBRARY_D_TAG = "erv/workouts/library";
export const WORKOUTS_LIBRARY_WORKOUT_PREFIX = "erv/workouts/library/workout/";

/** NIP-44 plaintext cap is 65535 bytes; stay under for encryption + relay headroom. */
export const MAX_WORKOUT_LIBRARY_PLAINTEXT_BYTES = 40_000;

export function workoutLibraryWorkoutShardTag(workoutId: string): string {
  return `${WORKOUTS_LIBRARY_WORKOUT_PREFIX}${workoutId}`;
}

export function workoutLibrarySegmentShardTag(
  workoutId: string,
  segmentId: string,
): string {
  return `${WORKOUTS_LIBRARY_WORKOUT_PREFIX}${workoutId}/segment/${segmentId}`;
}

export type { WeightSetLoggingStyle };
export { exerciseSetLoggingStyle, exerciseSupportsTargetWeight };

export type WorkoutSegmentKind =
  | "straight_sets"
  | "superset"
  | "circuit"
  | "composite"
  | "cardio"
  | "mobility"
  | "interval";

export type WorkoutCardioLogField = "INCLINE" | "SPEED" | "DISTANCE" | "NOTES";

export type WorkoutCardioIntervalLeg = {
  workSeconds: number;
  restSeconds?: number;
  label?: string | null;
  hrTargetBpm?: number | null;
};

export type WorkoutCardioPrescription = {
  activity: string;
  mode?: "steady" | "interval_template" | "sprint_intervals";
  targetMinutes?: number | null;
  hrTargetBpm?: number | null;
  hrTargetMinBpm?: number | null;
  hrTargetMaxBpm?: number | null;
  hrZoneLabel?: string | null;
  logFields?: WorkoutCardioLogField[];
  cardioRoutineId?: string | null;
  outerRounds?: number | null;
  legs?: WorkoutCardioIntervalLeg[];
  rounds?: number | null;
  workSeconds?: number | null;
  restSeconds?: number | null;
};

export type WorkoutPrescriptionSetSide = "left" | "right" | "each" | "alternating";

export type WorkoutPrescriptionSet = {
  reps?: number | null;
  repsPerSide?: number | null;
  side?: WorkoutPrescriptionSetSide | null;
  weightKg?: number | null;
  targetWeightKg?: number | null;
  rir?: number | null;
  rpe?: number | null;
  durationSeconds?: number | null;
  targetReps?: number | null;
  targetDurationSeconds?: number | null;
};

export type WorkoutWeightPrescription = {
  mode?: "straight" | "interval" | "time_based" | "max_reps";
  setCount?: number | null;
  targetReps?: number | null;
  targetWeightKg?: number | null;
  repRangeMin?: number | null;
  repRangeMax?: number | null;
  targetRir?: number | null;
  restBetweenSetsSeconds?: number | null;
  restAfterExerciseSeconds?: number | null;
  durationSeconds?: number | null;
  /** Get-ready countdown before each timed set starts. Null/0 = start immediately on tap. */
  timedPrepSeconds?: number | null;
  sets?: WorkoutPrescriptionSet[];
};

export type WorkoutMobilityPrescription = {
  catalogId: string;
  holdSeconds?: number | null;
  holdSecondsPerSide?: number | null;
};

export type WorkoutItem =
  | {
      type: "weight";
      id?: string;
      title?: string | null;
      exerciseId: string;
      alternativeExerciseIds?: string[];
      prescription?: WorkoutWeightPrescription;
    }
  | {
      type: "cardio";
      id?: string;
      title?: string | null;
      cardio: WorkoutCardioPrescription;
    }
  | {
      type: "mobility";
      id?: string;
      title?: string | null;
      mobility: WorkoutMobilityPrescription;
    }
  | {
      type: "note";
      id?: string;
      title?: string | null;
      text: string;
    }
  | {
      type: "rest";
      id?: string;
      title?: string | null;
      durationSeconds: number;
    };

export type WorkoutRestPolicy = {
  restBetweenItemsSeconds?: number;
  restAfterRoundSeconds?: number;
};

export type WorkoutSegment = {
  id?: string;
  kind: WorkoutSegmentKind;
  title?: string | null;
  notes?: string | null;
  items?: WorkoutItem[];
  rounds?: number;
  restPolicy?: WorkoutRestPolicy;
  restAfterSeconds?: number | null;
};

export type Workout = {
  id: string;
  name: string;
  description?: string | null;
  sourceLabel?: string | null;
  tags?: string[];
  segments: WorkoutSegment[];
  createdAtEpochSeconds?: number;
  lastModifiedEpochSeconds?: number;
};

export type WorkoutLibraryPayload = {
  workouts?: Workout[];
  libraryUpdatedAtEpochSeconds?: number;
  /** When true, full workouts live at `erv/workouts/library/workout/<id>`. */
  sharded?: boolean;
  workoutIds?: string[];
  /** Workout id to the epoch second it was deleted; readers drop workouts not edited since. */
  deletedWorkouts?: Record<string, number>;
};

/** Workout id to the epoch second it was deleted. */
export type WorkoutDeleteMarkers = Record<string, number>;

/** Delete markers older than this are dropped when the index is next published. */
export const WORKOUT_DELETE_MARKER_RETENTION_SECONDS = 365 * 24 * 60 * 60;

/** A workout survives a delete marker only when it was edited after the delete. */
export function withoutDeletedWorkouts(
  workouts: Workout[],
  deletedWorkouts: WorkoutDeleteMarkers,
): Workout[] {
  return workouts.filter((workout) => {
    const deletedAt = deletedWorkouts[workout.id];
    return deletedAt == null || (workout.lastModifiedEpochSeconds ?? 0) > deletedAt;
  });
}

/**
 * Markers to publish with the next index: ids that were in [previousIds] but are missing from
 * [nextWorkouts] are marked deleted at [now]; ids present again lose their marker; markers past
 * retention are pruned.
 */
export function nextWorkoutDeleteMarkers(
  previous: WorkoutDeleteMarkers,
  previousIds: Iterable<string>,
  nextWorkouts: Workout[],
  now: number,
): WorkoutDeleteMarkers {
  const nextIds = new Set(nextWorkouts.map((workout) => workout.id));
  const markers: WorkoutDeleteMarkers = { ...previous };
  for (const id of previousIds) {
    if (!nextIds.has(id)) markers[id] = now;
  }
  for (const id of nextIds) delete markers[id];
  const cutoff = now - WORKOUT_DELETE_MARKER_RETENTION_SECONDS;
  for (const [id, deletedAt] of Object.entries(markers)) {
    if (deletedAt < cutoff) delete markers[id];
  }
  return markers;
}

function parseDeleteMarkers(raw: unknown): WorkoutDeleteMarkers {
  if (raw == null || typeof raw !== "object" || Array.isArray(raw)) return {};
  const markers: WorkoutDeleteMarkers = {};
  for (const [id, deletedAt] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof deletedAt === "number" && Number.isFinite(deletedAt)) markers[id] = deletedAt;
  }
  return markers;
}

export type WorkoutLibraryShardPayload = {
  workout: Workout;
  /** When true, segment bodies live at `.../workout/<id>/segment/<segmentId>`. */
  segmentShards?: boolean;
  segmentIds?: string[];
};

export type WorkoutSegmentShardPayload = {
  segment: WorkoutSegment;
};

export type WorkoutLibraryPublishEntry = {
  d_tag: string;
  plaintext: string;
};

export class WorkoutLibraryPublishError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "WorkoutLibraryPublishError";
  }
}

function plaintextUtf8ByteLength(text: string): number {
  return new TextEncoder().encode(text).length;
}

export function parseWorkoutLibraryPayload(raw: string): Workout[] {
  const parsed = JSON.parse(raw) as WorkoutLibraryPayload;
  if (parsed.sharded) return [];
  return Array.isArray(parsed.workouts) ? parsed.workouts : [];
}

export function workoutLibraryPayload(
  workouts: Workout[],
  libraryUpdatedAtEpochSeconds: number,
): string {
  return JSON.stringify({
    workouts,
    libraryUpdatedAtEpochSeconds,
  } satisfies WorkoutLibraryPayload);
}

function workoutLibraryIndexPayload(
  workoutIds: string[],
  libraryUpdatedAtEpochSeconds: number,
  deletedWorkouts: WorkoutDeleteMarkers,
): string {
  return JSON.stringify({
    sharded: true,
    workoutIds,
    libraryUpdatedAtEpochSeconds,
    ...(Object.keys(deletedWorkouts).length > 0 ? { deletedWorkouts } : {}),
  } satisfies WorkoutLibraryPayload);
}

function workoutShardPayload(workout: Workout): string {
  return JSON.stringify({ workout } satisfies WorkoutLibraryShardPayload);
}

function workoutHeadShardPayload(workout: Workout, segmentIds: string[]): string {
  return JSON.stringify({
    workout: { ...workout, segments: [] },
    segmentShards: true,
    segmentIds,
  } satisfies WorkoutLibraryShardPayload);
}

function segmentShardPayload(segment: WorkoutSegment): string {
  return JSON.stringify({ segment } satisfies WorkoutSegmentShardPayload);
}

function segmentId(segment: WorkoutSegment, index: number): string {
  return segment.id?.trim() || `segment-${index}`;
}

/**
 * Publish rows for one cohesive workout (may use transparent segment shards).
 * Segment shards come before the head so the head never references segments the relay lacks.
 */
function buildWorkoutShardPublishEntries(workout: Workout): WorkoutLibraryPublishEntry[] {
  const fullPayload = workoutShardPayload(workout);
  if (plaintextUtf8ByteLength(fullPayload) <= MAX_WORKOUT_LIBRARY_PLAINTEXT_BYTES) {
    return [
      {
        d_tag: workoutLibraryWorkoutShardTag(workout.id),
        plaintext: fullPayload,
      },
    ];
  }

  const segmentIds = workout.segments.map((segment, index) => segmentId(segment, index));
  const entries: WorkoutLibraryPublishEntry[] = [];

  for (const [index, segment] of workout.segments.entries()) {
    const id = segmentIds[index];
    const segmentPayload = segmentShardPayload({ ...segment, id });
    const bytes = plaintextUtf8ByteLength(segmentPayload);
    if (bytes > MAX_WORKOUT_LIBRARY_PLAINTEXT_BYTES) {
      throw new WorkoutLibraryPublishError(
        `Segment "${segment.title ?? segment.kind}" in "${workout.name}" is too large to sync (${bytes.toLocaleString()} bytes). Shorten coach notes in that segment.`,
      );
    }
    entries.push({
      d_tag: workoutLibrarySegmentShardTag(workout.id, id),
      plaintext: segmentPayload,
    });
  }
  entries.push({
    d_tag: workoutLibraryWorkoutShardTag(workout.id),
    plaintext: workoutHeadShardPayload(workout, segmentIds),
  });
  return entries;
}

/**
 * Build kind-30078 publish rows for the workout library.
 * Each workout stays one logical storyboard; transport may use multiple relay events.
 * The index is last: readers drop index ids whose shard is missing, so shards must land first.
 *
 * When [changedWorkoutIds] is given, only those workouts get shard rows; the index is always
 * included. Relays like Haven's outbox allow ~10 events/hour, so resending every shard on each
 * save exhausts the budget and gets the index rejected.
 */
export function buildWorkoutLibraryPublishEntries(
  workouts: Workout[],
  libraryUpdatedAtEpochSeconds: number,
  changedWorkoutIds?: ReadonlySet<string>,
  deletedWorkouts: WorkoutDeleteMarkers = {},
): WorkoutLibraryPublishEntry[] {
  if (workouts.length === 0) {
    return [
      {
        d_tag: WORKOUTS_LIBRARY_D_TAG,
        plaintext: workoutLibraryIndexPayload([], libraryUpdatedAtEpochSeconds, deletedWorkouts),
      },
    ];
  }

  const entries: WorkoutLibraryPublishEntry[] = [];
  for (const workout of workouts) {
    if (changedWorkoutIds && !changedWorkoutIds.has(workout.id)) continue;
    entries.push(...buildWorkoutShardPublishEntries(workout));
  }
  entries.push({
    d_tag: WORKOUTS_LIBRARY_D_TAG,
    plaintext: workoutLibraryIndexPayload(
      workouts.map((w) => w.id),
      libraryUpdatedAtEpochSeconds,
      deletedWorkouts,
    ),
  });
  return entries;
}

function isWorkoutHeadShardTag(dTag: string): boolean {
  const prefix = WORKOUTS_LIBRARY_WORKOUT_PREFIX;
  if (!dTag.startsWith(prefix)) return false;
  const suffix = dTag.slice(prefix.length);
  return suffix.length > 0 && !suffix.includes("/segment/");
}

function parseWorkoutSegmentShardTag(dTag: string): { workoutId: string; segmentId: string } | null {
  const marker = "/segment/";
  const prefix = WORKOUTS_LIBRARY_WORKOUT_PREFIX;
  if (!dTag.startsWith(prefix) || !dTag.includes(marker)) return null;
  const rest = dTag.slice(prefix.length);
  const [workoutId, segmentId] = rest.split(marker);
  if (!workoutId || !segmentId) return null;
  return { workoutId, segmentId };
}

function assembleWorkoutFromShardRecords(
  records: AppDataRecordLike[],
  shardPlaintext: string,
): Workout | null {
  try {
    const parsed = JSON.parse(shardPlaintext) as WorkoutLibraryShardPayload;
    const workout = parsed.workout;
    if (!workout?.id) return null;
    if (!parsed.segmentShards) {
      return workout;
    }

    const segmentById = new Map<string, WorkoutSegment>();
    for (const record of records) {
      const dTag = record.d_tag?.trim();
      if (!dTag || !record.plaintext) continue;
      const ref = parseWorkoutSegmentShardTag(dTag);
      if (!ref || ref.workoutId !== workout.id) continue;
      try {
        const segmentPayload = JSON.parse(record.plaintext) as WorkoutSegmentShardPayload;
        if (segmentPayload.segment) {
          segmentById.set(ref.segmentId, segmentPayload.segment);
        }
      } catch {
        // ignore corrupt segment shard
      }
    }

    const orderedIds = parsed.segmentIds?.length ? parsed.segmentIds : [...segmentById.keys()];
    const segments = orderedIds
      .map((id) => segmentById.get(id))
      .filter((segment): segment is WorkoutSegment => segment != null);

    return { ...workout, segments };
  } catch {
    return null;
  }
}

export type AppDataRecordLike = {
  d_tag?: string | null;
  plaintext?: string | null;
  created_at?: number;
};

function listedWorkoutIds(masterPlaintext: string): string[] | null {
  try {
    const parsed = JSON.parse(masterPlaintext) as WorkoutLibraryPayload;
    if (parsed.sharded) return parsed.workoutIds ?? [];
    return (parsed.workouts ?? []).map((w) => w.id);
  } catch {
    return null;
  }
}

function parseDeleteMarkersFromMaster(masterPlaintext: string | null | undefined): WorkoutDeleteMarkers {
  if (!masterPlaintext) return {};
  try {
    return parseDeleteMarkers((JSON.parse(masterPlaintext) as WorkoutLibraryPayload).deletedWorkouts);
  } catch {
    return {};
  }
}

/** Workouts plus the index's delete markers, which a later save must carry forward. */
export function parseWorkoutLibraryFromAppDataRecords(records: AppDataRecordLike[]): {
  workouts: Workout[];
  deletedWorkouts: WorkoutDeleteMarkers;
} {
  const master = records.find((r) => r.d_tag === WORKOUTS_LIBRARY_D_TAG);
  const deletedWorkouts = parseDeleteMarkersFromMaster(master?.plaintext);
  return {
    workouts: withoutDeletedWorkouts(parseListedWorkouts(records), deletedWorkouts),
    deletedWorkouts,
  };
}

/**
 * Merge master + shard tags from relay app-data records into workouts, minus deleted ones.
 *
 * Index ids come first, in index order. A head shard missing from the index is still shown when
 * its event is newer than the index: that means the index update listing it never landed
 * (rejected or rate-limited), not a deletion — deleting always publishes a newer index.
 */
export function parseWorkoutsFromAppDataRecords(
  records: AppDataRecordLike[],
): Workout[] {
  return parseWorkoutLibraryFromAppDataRecords(records).workouts;
}

function parseListedWorkouts(records: AppDataRecordLike[]): Workout[] {
  const master = records.find((r) => r.d_tag === WORKOUTS_LIBRARY_D_TAG);
  const masterWorkouts = master?.plaintext
    ? parseWorkoutLibraryPayload(master.plaintext)
    : [];
  if (masterWorkouts.length > 0) {
    return masterWorkouts;
  }

  const shardById = new Map<string, Workout>();
  const shardCreatedAt = new Map<string, number>();
  for (const record of records) {
    const dTag = record.d_tag?.trim();
    if (!dTag || !isWorkoutHeadShardTag(dTag) || !record.plaintext) {
      continue;
    }
    const workout = assembleWorkoutFromShardRecords(records, record.plaintext);
    if (workout) {
      shardById.set(workout.id, workout);
      shardCreatedAt.set(workout.id, record.created_at ?? 0);
    }
  }

  if (shardById.size === 0) {
    return [];
  }

  const listed = master?.plaintext ? listedWorkoutIds(master.plaintext) : null;
  if (listed == null) {
    return [...shardById.keys()]
      .sort()
      .map((id) => shardById.get(id))
      .filter((workout): workout is Workout => workout != null);
  }

  const indexCreatedAt = master?.created_at;
  const listedSet = new Set(listed);
  const fromIndex = listed
    .map((id) => shardById.get(id))
    .filter((workout): workout is Workout => workout != null);
  const unlistedNewer =
    indexCreatedAt == null
      ? []
      : [...shardById.values()]
          .filter(
            (workout) =>
              !listedSet.has(workout.id) &&
              (shardCreatedAt.get(workout.id) ?? 0) > indexCreatedAt,
          )
          .sort((a, b) => a.name.localeCompare(b.name));
  return [...fromIndex, ...unlistedNewer];
}

export function upsertWorkout(workouts: Workout[], workout: Workout): Workout[] {
  const index = workouts.findIndex((w) => w.id === workout.id);
  if (index >= 0) {
    const next = [...workouts];
    next[index] = workout;
    return next;
  }
  return [...workouts, workout];
}

export const DEFAULT_TIMED_PREP_SECONDS = 10;

export function defaultWeightPrescription(
  segmentKind?: WorkoutSegmentKind,
  exercise?: WeightCatalogExercise | null,
): WorkoutWeightPrescription {
  const setCount =
    segmentKind === "circuit" || segmentKind === "superset" ? 1 : 3;
  const style = exercise ? exerciseSetLoggingStyle(exercise) : "reps";
  if (style === "time_only") {
    return {
      mode: "time_based",
      setCount,
      durationSeconds: 45,
      timedPrepSeconds: DEFAULT_TIMED_PREP_SECONDS,
    };
  }
  if (style === "reps_or_time") {
    return {
      mode: "straight",
      setCount,
      targetReps: 10,
    };
  }
  return {
    mode: "straight",
    setCount,
    repRangeMin: 8,
    repRangeMax: 12,
  };
}

export function defaultRestPolicy(): WorkoutRestPolicy {
  return {
    restBetweenItemsSeconds: 0,
    restAfterRoundSeconds: 90,
  };
}

export function newWeightItem(
  exerciseId: string,
  segmentKind?: WorkoutSegmentKind,
  exercise?: WeightCatalogExercise | null,
): Extract<WorkoutItem, { type: "weight" }> {
  return {
    type: "weight",
    id: crypto.randomUUID(),
    exerciseId,
    title: exercise?.name?.trim() || null,
    prescription: defaultWeightPrescription(segmentKind, exercise),
  };
}

export function newCardioItem(
  activityId: string,
  mode: WorkoutCardioPrescription["mode"] = "steady",
): Extract<WorkoutItem, { type: "cardio" }> {
  if (mode === "sprint_intervals") {
    return {
      type: "cardio",
      id: crypto.randomUUID(),
      cardio: {
        activity: activityId,
        mode: "sprint_intervals",
        rounds: 10,
        workSeconds: 60,
        restSeconds: 60,
      },
    };
  }
  if (mode === "interval_template") {
    return {
      type: "cardio",
      id: crypto.randomUUID(),
      cardio: {
        activity: activityId,
        mode: "interval_template",
        outerRounds: 3,
        legs: [{ workSeconds: 240, restSeconds: 240, label: "Work leg" }],
      },
    };
  }
  return {
    type: "cardio",
    id: crypto.randomUUID(),
    cardio: {
      activity: activityId,
      mode: "steady",
      targetMinutes: 10,
    },
  };
}

export function newMobilityItem(catalogId: string): Extract<WorkoutItem, { type: "mobility" }> {
  return {
    type: "mobility",
    id: crypto.randomUUID(),
    mobility: {
      catalogId,
      holdSeconds: 30,
    },
  };
}

export function newNoteItem(): Extract<WorkoutItem, { type: "note" }> {
  return { type: "note", id: crypto.randomUUID(), text: "" };
}

export function newRestItem(): Extract<WorkoutItem, { type: "rest" }> {
  return { type: "rest", id: crypto.randomUUID(), durationSeconds: 60 };
}

export type WorkoutLibraryKind = "weight" | "stretch" | "cardio";

export function segmentLibraryKinds(kind: WorkoutSegmentKind): WorkoutLibraryKind[] {
  switch (kind) {
    case "composite":
      return ["weight", "stretch", "cardio"];
    case "cardio":
    case "interval":
      return ["cardio"];
    case "mobility":
      return ["stretch"];
    default:
      return ["weight"];
  }
}

export function segmentAllowsInlineNotes(kind: WorkoutSegmentKind): boolean {
  return (
    kind === "composite" ||
    kind === "straight_sets" ||
    kind === "cardio" ||
    kind === "mobility" ||
    kind === "interval"
  );
}

export function defaultCardioModeForSegment(
  kind: WorkoutSegmentKind,
): WorkoutCardioPrescription["mode"] {
  return kind === "interval" ? "sprint_intervals" : "steady";
}

export function workoutItemKey(item: WorkoutItem): string {
  switch (item.type) {
    case "weight":
      return item.id ?? item.exerciseId;
    case "cardio":
      return item.id ?? item.cardio.activity;
    case "mobility":
      return item.id ?? item.mobility.catalogId;
    case "note":
    case "rest":
      return item.id ?? crypto.randomUUID();
  }
}

export function segmentItems(segment: WorkoutSegment): WorkoutItem[] {
  return segment.items ?? [];
}

export function segmentIsEmpty(segment: WorkoutSegment): boolean {
  return segmentItems(segment).length === 0;
}

export function cardioItemSummary(
  item: Extract<WorkoutItem, { type: "cardio" }>,
  activityLabel: string,
): string {
  const parts = [activityLabel];
  const mode = item.cardio.mode ?? "steady";
  if (mode === "sprint_intervals") {
    if (item.cardio.rounds != null && item.cardio.rounds > 0) {
      parts.push(`${item.cardio.rounds} rounds`);
    }
    if (item.cardio.workSeconds != null && item.cardio.workSeconds > 0) {
      const rest = item.cardio.restSeconds ?? 0;
      parts.push(`${item.cardio.workSeconds}s/${rest}s`);
    }
  } else if (mode === "interval_template") {
    if (item.cardio.outerRounds != null && item.cardio.outerRounds > 0) {
      parts.push(`${item.cardio.outerRounds} outer rounds`);
    }
    const leg = item.cardio.legs?.[0];
    if (leg != null) {
      parts.push(`${leg.workSeconds}s work`);
      if ((leg.restSeconds ?? 0) > 0) parts.push(`${leg.restSeconds}s rest`);
    }
  } else if (item.cardio.targetMinutes != null && item.cardio.targetMinutes > 0) {
    parts.push(`${item.cardio.targetMinutes} min`);
  }
  if (item.cardio.hrZoneLabel) {
    parts.push(item.cardio.hrZoneLabel);
  }
  if (
    item.cardio.hrTargetMinBpm != null &&
    item.cardio.hrTargetMaxBpm != null &&
    item.cardio.hrTargetMinBpm > 0 &&
    item.cardio.hrTargetMaxBpm > 0
  ) {
    parts.push(`${item.cardio.hrTargetMinBpm}–${item.cardio.hrTargetMaxBpm} bpm`);
  } else if (item.cardio.hrTargetBpm != null && item.cardio.hrTargetBpm > 0) {
    parts.push(`${item.cardio.hrTargetBpm} bpm`);
  }
  if (item.cardio.cardioRoutineId) {
    parts.push("saved routine");
  }
  if (item.cardio.logFields != null && item.cardio.logFields.length > 0) {
    parts.push(`log: ${item.cardio.logFields.join(", ").toLowerCase()}`);
  }
  return parts.join(" · ");
}

export function mobilityItemSummary(
  item: Extract<WorkoutItem, { type: "mobility" }>,
  stretchLabel: string,
): string {
  const parts = [stretchLabel];
  const hold = item.mobility.holdSecondsPerSide ?? item.mobility.holdSeconds;
  if (hold != null && hold > 0) {
    parts.push(item.mobility.holdSecondsPerSide != null ? `${hold}s/side` : `${hold}s hold`);
  }
  return parts.join(" · ");
}

export function updateWorkoutItem(
  segment: WorkoutSegment,
  itemKey: string,
  next: WorkoutItem,
): WorkoutSegment {
  return {
    ...segment,
    items: segmentItems(segment).map((item) =>
      workoutItemKey(item) === itemKey ? next : item,
    ),
  };
}

export function segmentKindLabel(kind: WorkoutSegmentKind): string {
  switch (kind) {
    case "straight_sets":
      return "Straight sets";
    case "superset":
      return "Superset";
    case "circuit":
      return "Circuit";
    case "composite":
      return "Flow block";
    case "cardio":
      return "Cardio";
    case "mobility":
      return "Mobility";
    case "interval":
      return "Intervals";
  }
}

/** Short hover hint for workout segment add buttons in the composer. */
export function segmentKindHint(kind: WorkoutSegmentKind): string {
  switch (kind) {
    case "composite":
      return "Mix cardio, stretches, lifts, and notes in order — ideal for warm-up or cooldown.";
    case "cardio":
      return "Steady-state cardio from your catalog (bike, row, walk, and similar).";
    case "interval":
      return "Timed work and rest rounds — HIIT, sprints, or custom interval templates.";
    case "mobility":
      return "Stretching and mobility only, from the stretch catalog.";
    case "straight_sets":
      return "One or more lifts with sets, reps, and rest between sets.";
    case "circuit":
      return "Rotate through exercises for multiple rounds; rest between rounds.";
    case "superset":
      return "Pair exercises back-to-back; rest after each pair or round.";
  }
}

export function defaultSegmentTitle(kind: WorkoutSegmentKind): string {
  switch (kind) {
    case "straight_sets":
      return "Main work";
    case "superset":
      return "Superset";
    case "circuit":
      return "Circuit";
    case "composite":
      return "Warm-up";
    case "cardio":
      return "Cardio";
    case "mobility":
      return "Mobility";
    case "interval":
      return "HIIT / intervals";
    default:
      return segmentKindLabel(kind);
  }
}

export function weightItems(segment: WorkoutSegment): Extract<WorkoutItem, { type: "weight" }>[] {
  return (segment.items ?? []).filter(
    (item): item is Extract<WorkoutItem, { type: "weight" }> => item.type === "weight",
  );
}

export function prescriptionSetCount(p: WorkoutWeightPrescription | undefined): number {
  if (p?.sets != null && p.sets.length > 0) return p.sets.length;
  return p?.setCount ?? 3;
}

export function ensurePrescriptionSets(p: WorkoutWeightPrescription): WorkoutPrescriptionSet[] {
  const count = Math.max(1, prescriptionSetCount(p));
  const existing = p.sets ?? [];
  return Array.from({ length: count }, (_, index) => ({ ...(existing[index] ?? {}) }));
}

export function syncPrescriptionSetCount(
  p: WorkoutWeightPrescription,
  setCount: number,
): WorkoutWeightPrescription {
  const count = Math.max(1, setCount);
  const sets = ensurePrescriptionSets({ ...p, setCount: count }).slice(0, count);
  while (sets.length < count) sets.push({});
  return { ...p, setCount: count, sets };
}

export function prescriptionUsesPerSide(p: WorkoutWeightPrescription | undefined): boolean {
  return (p?.sets ?? []).some((s) => (s.repsPerSide ?? 0) > 0);
}

export function prescriptionSummary(
  p: WorkoutWeightPrescription | undefined,
  formatWeight: (kg: number) => string = (kg) => `${kg} kg`,
): string {
  const sets = p?.sets ?? [];
  const setTotal = prescriptionSetCount(p);
  let repPart = "";
  if (p?.mode === "time_based" && p.durationSeconds != null && p.durationSeconds > 0) {
    repPart = `${p.durationSeconds}s hold`;
  } else if (p?.mode === "max_reps") {
    repPart = "max reps";
  } else if (prescriptionUsesPerSide(p)) {
    const first = sets.find((s) => (s.repsPerSide ?? 0) > 0);
    if (first?.repsPerSide != null) {
      repPart = `${first.repsPerSide}/side${first.side ? ` (${first.side})` : ""}`;
    }
  } else if (sets.length > 0 && sets.some((s) => (s.reps ?? 0) > 0 || (s.targetReps ?? 0) > 0)) {
    const reps = sets.map((s) => s.reps ?? s.targetReps).filter((v) => v != null && v > 0);
    if (reps.length === sets.length && new Set(reps).size === 1) {
      repPart = `${reps[0]} reps`;
    } else if (reps.length > 0) {
      repPart = "varied reps";
    }
  } else if (p?.targetReps != null && p.targetReps > 0) {
    repPart = `${p.targetReps} reps`;
  } else if (p?.repRangeMin != null && p?.repRangeMax != null && p.repRangeMin !== p.repRangeMax) {
    repPart = `${p.repRangeMin}–${p.repRangeMax} reps`;
  } else if (p?.repRangeMin != null) {
    repPart = `${p.repRangeMin} reps`;
  } else if (p?.repRangeMax != null) {
    repPart = `${p.repRangeMax} reps`;
  } else if (p?.durationSeconds != null && p.durationSeconds > 0) {
    repPart = `${p.durationSeconds}s hold`;
  }
  const parts = [repPart ? `${setTotal} sets · ${repPart}` : `${setTotal} sets`];
  const weightHint =
    p?.targetWeightKg ??
    p?.sets?.find((s) => (s.targetWeightKg ?? s.weightKg ?? 0) > 0)?.targetWeightKg ??
    p?.sets?.find((s) => (s.weightKg ?? 0) > 0)?.weightKg;
  if (weightHint != null && weightHint > 0) parts.push(formatWeight(weightHint));
  if (p?.targetRir != null) parts.push(`${p.targetRir} RIR`);
  if (p?.restBetweenSetsSeconds != null && p.restBetweenSetsSeconds > 0) {
    parts.push(`${p.restBetweenSetsSeconds}s between sets`);
  }
  if (p?.restAfterExerciseSeconds != null && p.restAfterExerciseSeconds > 0) {
    parts.push(`${p.restAfterExerciseSeconds}s after exercise`);
  }
  if (p?.timedPrepSeconds != null && p.timedPrepSeconds > 0) {
    parts.push(`${p.timedPrepSeconds}s get-ready`);
  }
  return parts.join(" · ");
}

export function duplicateWorkout(workout: Workout, nameSuffix = " (copy)"): Workout {
  const now = Math.floor(Date.now() / 1000);
  return {
    ...workout,
    id: crypto.randomUUID(),
    name: `${workout.name}${nameSuffix}`,
    segments: workout.segments.map((segment) => ({
      ...segment,
      id: crypto.randomUUID(),
      items: (segment.items ?? []).map((item) => {
        const id = crypto.randomUUID();
        switch (item.type) {
          case "weight":
            return {
              ...item,
              id,
              prescription: item.prescription
                ? {
                    ...item.prescription,
                    sets: item.prescription.sets?.map((s) => ({ ...s })),
                  }
                : undefined,
            };
          case "cardio":
            return {
              ...item,
              id,
              cardio: {
                ...item.cardio,
                legs: item.cardio.legs?.map((leg) => ({ ...leg })),
              },
            };
          case "mobility":
            return { ...item, id, mobility: { ...item.mobility } };
          case "note":
          case "rest":
            return { ...item, id };
        }
      }),
    })),
    lastModifiedEpochSeconds: now,
    createdAtEpochSeconds: now,
  };
}

export function parseNonNegativeInt(raw: string, fallback: number): number {
  const parsed = Number.parseInt(raw.trim(), 10);
  if (Number.isNaN(parsed)) return fallback;
  return Math.max(0, parsed);
}

export function parsePositiveInt(raw: string, fallback: number): number {
  const parsed = Number.parseInt(raw.trim(), 10);
  if (Number.isNaN(parsed)) return fallback;
  return Math.max(1, parsed);
}

/** Controlled numeric input: empty clears; invalid text keeps the current value. */
export function readOptionalNonNegativeInt(
  raw: string,
  current: number | null | undefined,
): number | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  const parsed = Number.parseInt(trimmed, 10);
  if (Number.isNaN(parsed)) return current ?? null;
  return Math.max(0, parsed);
}

/** Controlled numeric input: empty clears; invalid text keeps the current value. */
export function readOptionalPositiveInt(
  raw: string,
  current: number | null | undefined,
): number | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  const parsed = Number.parseInt(trimmed, 10);
  if (Number.isNaN(parsed)) return current ?? null;
  return Math.max(1, parsed);
}

export function updateWeightItemPrescription(
  segment: WorkoutSegment,
  itemId: string,
  prescription: WorkoutWeightPrescription,
): WorkoutSegment {
  return {
    ...segment,
    items: (segment.items ?? []).map((item) =>
      item.type === "weight" && (item.id ?? item.exerciseId) === itemId
        ? { ...item, prescription }
        : item,
    ),
  };
}
