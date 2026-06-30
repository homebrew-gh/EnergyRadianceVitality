/** Weight and cardio day logs from relay (`erv/weight/YYYY-MM-DD`, `erv/cardio/YYYY-MM-DD`). */

import type { AppDataRecord } from "./api";
import { CARDIO_ROUTINES_D_TAG } from "./cardioTraining";
import type { WeightExercise } from "./weightTraining";
import { WEIGHT_EXERCISES_D_TAG, WEIGHT_ROUTINES_D_TAG } from "./weightTraining";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export const WEIGHT_DAY_LOG_PREFIX = "erv/weight/";
export const CARDIO_DAY_LOG_PREFIX = "erv/cardio/";

export type WeightSet = {
  reps: number;
  weightKg?: number | null;
  rpe?: number | null;
  rir?: number | null;
  durationSeconds?: number | null;
};

export type WeightHiitBlockLog = {
  intervals: number;
  workSeconds: number;
  restSeconds: number;
  weightKg?: number | null;
  rpe?: number | null;
};

export type WeightWorkoutEntry = {
  exerciseId: string;
  sets: WeightSet[];
  hiitBlock?: WeightHiitBlockLog | null;
};

export type WeightWorkoutSource = "LIVE" | "MANUAL" | "IMPORTED";

export type WeightWorkoutSession = {
  id: string;
  source: WeightWorkoutSource;
  startedAtEpochSeconds?: number | null;
  finishedAtEpochSeconds?: number | null;
  durationSeconds?: number | null;
  routineId?: string | null;
  routineName?: string | null;
  entries: WeightWorkoutEntry[];
  estimatedKcal?: number | null;
  heartRate?: CardioHeartRateSummary | null;
  workoutLink?: WorkoutSessionLink | null;
};

export type WeightDayLog = {
  date: string;
  workouts: WeightWorkoutSession[];
};

export type CardioActivitySnapshot = {
  builtin?: string | null;
  customTypeId?: string | null;
  customName?: string | null;
  displayLabel: string;
};

export type CardioSession = {
  id: string;
  activity: CardioActivitySnapshot;
  durationMinutes: number;
  distanceMeters?: number | null;
  estimatedKcal?: number | null;
  routineId?: string | null;
  routineName?: string | null;
  source?: string | null;
  startEpochSeconds?: number | null;
  endEpochSeconds?: number | null;
  loggedAtEpochSeconds?: number | null;
  heartRate?: CardioHeartRateSummary | null;
  routeImageUrl?: string | null;
  workoutLink?: WorkoutSessionLink | null;
};

export type HeartRateLoadSummary = {
  sampleCount: number;
  durationSeconds?: number | null;
  /** Seconds in Z1..Z5. */
  zoneSeconds: number[];
  zoneMethod?: string | null;
  maxBpm?: number | null;
  restingBpm?: number | null;
};

export type CardioHeartRateSummary = {
  avgBpm?: number | null;
  maxBpm?: number | null;
  minBpm?: number | null;
  load?: HeartRateLoadSummary | null;
};

export type WorkoutSessionLink = {
  sessionId: string;
  workoutId: string;
  segmentId: string;
  itemId: string;
  displayRef: string;
  sessionHeartRate?: CardioHeartRateSummary | null;
};

export type CardioDayLog = {
  date: string;
  sessions: CardioSession[];
};

export type HistoryTimelineItem =
  | { kind: "weight"; date: string; session: WeightWorkoutSession }
  | { kind: "cardio"; date: string; session: CardioSession };

export type RecentWorkoutItem = HistoryTimelineItem & {
  contextKey: string;
};

export type WeeklyCountBucket = {
  weekStart: string;
  label: string;
  weightSessions: number;
  cardioSessions: number;
};

export type MuscleGroupVolume = {
  muscleGroup: string;
  setCount: number;
};

export type ExerciseHistoryRow = {
  date: string;
  workoutId: string;
  routineName?: string | null;
  entry: WeightWorkoutEntry;
  sessionEpoch: number;
};

export type RepBucketMax = {
  label: string;
  maxKg: number | null;
};

export type ParsedTrainingLogs = {
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  decryptErrors: string[];
  /** Decrypted day-log tags whose JSON did not yield any sessions/workouts. */
  emptyDayLogs: string[];
};

function parseWeightSet(raw: unknown): WeightSet | null {
  if (!raw || typeof raw !== "object") return null;
  const s = raw as Record<string, unknown>;
  return {
    reps: typeof s.reps === "number" ? s.reps : 0,
    weightKg: typeof s.weightKg === "number" ? s.weightKg : null,
    rpe: typeof s.rpe === "number" ? s.rpe : null,
    rir: typeof s.rir === "number" ? s.rir : null,
    durationSeconds: typeof s.durationSeconds === "number" ? s.durationSeconds : null,
  };
}

function parseWeightEntry(raw: unknown): WeightWorkoutEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const e = raw as Record<string, unknown>;
  if (typeof e.exerciseId !== "string") return null;
  const sets = Array.isArray(e.sets)
    ? e.sets.map(parseWeightSet).filter((s): s is WeightSet => s != null)
    : [];
  let hiitBlock: WeightHiitBlockLog | null = null;
  if (e.hiitBlock && typeof e.hiitBlock === "object") {
    const h = e.hiitBlock as Record<string, unknown>;
    hiitBlock = {
      intervals: typeof h.intervals === "number" ? h.intervals : 0,
      workSeconds: typeof h.workSeconds === "number" ? h.workSeconds : 0,
      restSeconds: typeof h.restSeconds === "number" ? h.restSeconds : 0,
      weightKg: typeof h.weightKg === "number" ? h.weightKg : null,
      rpe: typeof h.rpe === "number" ? h.rpe : null,
    };
  }
  return { exerciseId: e.exerciseId, sets, hiitBlock };
}

function parseWeightWorkout(raw: unknown): WeightWorkoutSession | null {
  if (!raw || typeof raw !== "object") return null;
  const w = raw as Record<string, unknown>;
  const sourceRaw = w.source;
  const source: WeightWorkoutSource =
    sourceRaw === "LIVE" || sourceRaw === "MANUAL" || sourceRaw === "IMPORTED"
      ? sourceRaw
      : "MANUAL";
  const entries = Array.isArray(w.entries)
    ? w.entries.map(parseWeightEntry).filter((e): e is WeightWorkoutEntry => e != null)
    : [];
  if (entries.length === 0) return null;
  const heartRate =
    w.heartRate && typeof w.heartRate === "object"
      ? parseCardioHeartRate(w.heartRate)
      : null;
  return {
    id: typeof w.id === "string" ? w.id : crypto.randomUUID(),
    source,
    startedAtEpochSeconds:
      typeof w.startedAtEpochSeconds === "number" ? w.startedAtEpochSeconds : null,
    finishedAtEpochSeconds:
      typeof w.finishedAtEpochSeconds === "number" ? w.finishedAtEpochSeconds : null,
    durationSeconds: typeof w.durationSeconds === "number" ? w.durationSeconds : null,
    routineId: typeof w.routineId === "string" ? w.routineId : null,
    routineName: typeof w.routineName === "string" ? w.routineName : null,
    entries,
    estimatedKcal: typeof w.estimatedKcal === "number" ? w.estimatedKcal : null,
    heartRate,
    workoutLink:
      w.workoutLink && typeof w.workoutLink === "object"
        ? parseWorkoutSessionLink(w.workoutLink)
        : null,
  };
}

export function parseWeightDayLog(raw: string, dateFallback: string): WeightDayLog | null {
  try {
    const data = JSON.parse(raw) as Record<string, unknown>;
    const date =
      typeof data.date === "string" && ISO_DATE.test(data.date) ? data.date : dateFallback;
    const workouts = Array.isArray(data.workouts)
      ? data.workouts.map(parseWeightWorkout).filter((w): w is WeightWorkoutSession => w != null)
      : [];
    if (workouts.length === 0) return null;
    return { date, workouts };
  } catch {
    return null;
  }
}

function parseCardioActivity(raw: unknown): CardioActivitySnapshot | null {
  if (!raw || typeof raw !== "object") return null;
  const a = raw as Record<string, unknown>;
  const displayLabel =
    typeof a.displayLabel === "string" && a.displayLabel.trim()
      ? a.displayLabel.trim()
      : "Cardio";
  return {
    builtin: typeof a.builtin === "string" ? a.builtin : null,
    customTypeId: typeof a.customTypeId === "string" ? a.customTypeId : null,
    customName: typeof a.customName === "string" ? a.customName : null,
    displayLabel,
  };
}

function cardioDurationMinutes(raw: Record<string, unknown>): number {
  if (typeof raw.durationMinutes === "number" && raw.durationMinutes > 0) {
    return raw.durationMinutes;
  }
  if (Array.isArray(raw.segments)) {
    let sum = 0;
    for (const seg of raw.segments) {
      if (seg && typeof seg === "object" && typeof (seg as Record<string, unknown>).durationMinutes === "number") {
        sum += Math.max(0, (seg as Record<string, unknown>).durationMinutes as number);
      }
    }
    if (sum > 0) return sum;
  }
  const start = raw.startEpochSeconds;
  const end = raw.endEpochSeconds;
  if (typeof start === "number" && typeof end === "number" && end > start) {
    return Math.max(1, Math.round((end - start) / 60));
  }
  return typeof raw.durationMinutes === "number" ? Math.max(0, raw.durationMinutes) : 0;
}

function parseCardioSession(raw: unknown): CardioSession | null {
  if (!raw || typeof raw !== "object") return null;
  const s = raw as Record<string, unknown>;
  const activity = parseCardioActivity(s.activity);
  if (!activity) return null;
  const durationMinutes = cardioDurationMinutes(s);
  const hasSegments = Array.isArray(s.segments) && s.segments.length > 0;
  if (durationMinutes <= 0 && !hasSegments) return null;
  const heartRate =
    s.heartRate && typeof s.heartRate === "object"
      ? parseCardioHeartRate(s.heartRate)
      : null;
  return {
    id: typeof s.id === "string" ? s.id : crypto.randomUUID(),
    activity,
    durationMinutes,
    distanceMeters: typeof s.distanceMeters === "number" ? s.distanceMeters : null,
    estimatedKcal: typeof s.estimatedKcal === "number" ? s.estimatedKcal : null,
    routineId: typeof s.routineId === "string" ? s.routineId : null,
    routineName: typeof s.routineName === "string" ? s.routineName : null,
    source: typeof s.source === "string" ? s.source : null,
    startEpochSeconds: typeof s.startEpochSeconds === "number" ? s.startEpochSeconds : null,
    endEpochSeconds: typeof s.endEpochSeconds === "number" ? s.endEpochSeconds : null,
    loggedAtEpochSeconds:
      typeof s.loggedAtEpochSeconds === "number" ? s.loggedAtEpochSeconds : null,
    heartRate,
    routeImageUrl: typeof s.routeImageUrl === "string" ? s.routeImageUrl : null,
    workoutLink:
      s.workoutLink && typeof s.workoutLink === "object"
        ? parseWorkoutSessionLink(s.workoutLink)
        : null,
  };
}

function parseHeartRateLoadSummary(raw: unknown): HeartRateLoadSummary | null {
  if (!raw || typeof raw !== "object") return null;
  const data = raw as Record<string, unknown>;
  const zoneSeconds = Array.isArray(data.zoneSeconds)
    ? data.zoneSeconds
        .map((v) => (typeof v === "number" && Number.isFinite(v) ? Math.max(0, Math.round(v)) : null))
        .filter((v): v is number => v != null)
        .slice(0, 5)
    : [];
  if (zoneSeconds.length === 0 && typeof data.sampleCount !== "number") return null;
  return {
    sampleCount:
      typeof data.sampleCount === "number" && Number.isFinite(data.sampleCount)
        ? Math.max(0, Math.round(data.sampleCount))
        : 0,
    durationSeconds:
      typeof data.durationSeconds === "number" && Number.isFinite(data.durationSeconds)
        ? Math.max(0, Math.round(data.durationSeconds))
        : null,
    zoneSeconds,
    zoneMethod: typeof data.zoneMethod === "string" ? data.zoneMethod : null,
    maxBpm: typeof data.maxBpm === "number" ? data.maxBpm : null,
    restingBpm: typeof data.restingBpm === "number" ? data.restingBpm : null,
  };
}

function parseCardioHeartRate(raw: object): CardioHeartRateSummary | null {
  const hr = raw as Record<string, unknown>;
  const load = parseHeartRateLoadSummary(hr.load);
  const parsed = {
    avgBpm: typeof hr.avgBpm === "number" ? hr.avgBpm : null,
    maxBpm: typeof hr.maxBpm === "number" ? hr.maxBpm : null,
    minBpm: typeof hr.minBpm === "number" ? hr.minBpm : null,
    load,
  };
  return parsed.avgBpm != null || parsed.maxBpm != null || parsed.minBpm != null || load
    ? parsed
    : null;
}

function parseWorkoutSessionLink(raw: unknown): WorkoutSessionLink | null {
  if (!raw || typeof raw !== "object") return null;
  const link = raw as Record<string, unknown>;
  if (
    typeof link.sessionId !== "string" ||
    typeof link.workoutId !== "string" ||
    typeof link.segmentId !== "string" ||
    typeof link.itemId !== "string"
  ) {
    return null;
  }
  return {
    sessionId: link.sessionId,
    workoutId: link.workoutId,
    segmentId: link.segmentId,
    itemId: link.itemId,
    displayRef: typeof link.displayRef === "string" ? link.displayRef : "",
    sessionHeartRate:
      link.sessionHeartRate && typeof link.sessionHeartRate === "object"
        ? parseCardioHeartRate(link.sessionHeartRate)
        : null,
  };
}

export function parseCardioDayLog(raw: string, dateFallback: string): CardioDayLog | null {
  try {
    const data = JSON.parse(raw) as Record<string, unknown>;
    const date =
      typeof data.date === "string" && ISO_DATE.test(data.date) ? data.date : dateFallback;
    const sessions = Array.isArray(data.sessions)
      ? data.sessions.map(parseCardioSession).filter((s): s is CardioSession => s != null)
      : [];
    if (sessions.length === 0) return null;
    return { date, sessions };
  } catch {
    return null;
  }
}

export function weightDayLogDateFromTag(dTag: string): string | null {
  if (!dTag.startsWith(WEIGHT_DAY_LOG_PREFIX)) return null;
  const suffix = dTag.slice(WEIGHT_DAY_LOG_PREFIX.length);
  if (suffix === "exercises" || suffix === "routines") return null;
  const date = suffix.split("/session/")[0];
  return ISO_DATE.test(date) ? date : null;
}

export function cardioDayLogDateFromTag(dTag: string): string | null {
  if (dTag === CARDIO_ROUTINES_D_TAG) return null;
  if (!dTag.startsWith(CARDIO_DAY_LOG_PREFIX)) return null;
  const suffix = dTag.slice(CARDIO_DAY_LOG_PREFIX.length);
  if (suffix === "routines") return null;
  const date = suffix.split("/session/")[0];
  return ISO_DATE.test(date) ? date : null;
}

/** Weight or cardio day log d-tags shown on the Progress tab. */
export function isTrainingDayLogTag(dTag: string): boolean {
  return weightDayLogDateFromTag(dTag) != null || cardioDayLogDateFromTag(dTag) != null;
}

/** Extract day logs from decrypted relay app-data records. */
export type TrainingDaySideStatus =
  | "parsed_ok"
  | "missing"
  | "tombstone_on_relay"
  | "decrypt_failed"
  | "empty_on_relay"
  | "parse_rejected";

export type TrainingDaySideDiagnostic = {
  dTag: string;
  kind: "weight" | "cardio";
  date: string;
  status: TrainingDaySideStatus;
  decryptError?: string;
  rawCount: number;
  parsedCount: number;
  parseNotes: string[];
  plaintext?: string;
};

export type TrainingDayDiagnostic = {
  date: string;
  weight: TrainingDaySideDiagnostic | null;
  cardio: TrainingDaySideDiagnostic | null;
  /** Human-readable hint when strength/cardio sides disagree on the relay. */
  issue?: string;
};

function rawWeightWorkoutCount(plain: string): number {
  try {
    const data = JSON.parse(plain) as Record<string, unknown>;
    return Array.isArray(data.workouts) ? data.workouts.length : 0;
  } catch {
    return 0;
  }
}

function rawCardioSessionCount(plain: string): number {
  try {
    const data = JSON.parse(plain) as Record<string, unknown>;
    return Array.isArray(data.sessions) ? data.sessions.length : 0;
  } catch {
    return 0;
  }
}

function explainCardioSessionSkip(raw: unknown, index: number): string[] {
  if (!raw || typeof raw !== "object") {
    return [`session ${index + 1}: not an object`];
  }
  const s = raw as Record<string, unknown>;
  const notes: string[] = [];
  if (!s.activity || typeof s.activity !== "object") {
    notes.push(`session ${index + 1}: missing activity object`);
  }
  const durationMinutes = cardioDurationMinutes(s);
  const hasSegments = Array.isArray(s.segments) && s.segments.length > 0;
  if (durationMinutes <= 0 && !hasSegments) {
    const segmentCount = hasSegments ? (s.segments as unknown[]).length : 0;
    notes.push(
      `session ${index + 1}: no duration (durationMinutes=${String(s.durationMinutes)}, segments=${segmentCount})`,
    );
  }
  return notes;
}

function explainWeightWorkoutSkip(raw: unknown, index: number): string[] {
  if (!raw || typeof raw !== "object") {
    return [`workout ${index + 1}: not an object`];
  }
  const w = raw as Record<string, unknown>;
  const entries = Array.isArray(w.entries) ? w.entries : [];
  if (entries.length === 0) {
    return [`workout ${index + 1}: entries array is empty`];
  }
  return [];
}

/** Inspect one relay record for a weight or cardio day log (Progress diagnostics). */
export function diagnoseTrainingDayRecord(record: AppDataRecord): TrainingDaySideDiagnostic | null {
  const dTag = record.d_tag?.trim();
  if (!dTag) return null;
  const weightDate = weightDayLogDateFromTag(dTag);
  const cardioDate = cardioDayLogDateFromTag(dTag);
  if (!weightDate && !cardioDate) return null;

  const kind = weightDate ? "weight" : "cardio";
  const date = weightDate ?? cardioDate!;

  if (record.decrypt_error) {
    return {
      dTag,
      kind,
      date,
      status: "decrypt_failed",
      decryptError: record.decrypt_error,
      rawCount: 0,
      parsedCount: 0,
      parseNotes: [],
    };
  }

  if (!record.plaintext) {
    const tombstone = Boolean(record.event_id || record.ciphertext);
    return {
      dTag,
      kind,
      date,
      status: tombstone ? "tombstone_on_relay" : "missing",
      rawCount: 0,
      parsedCount: 0,
      parseNotes: tombstone
        ? [
            "Relay has a cleared/tombstone event for this tag (empty ciphertext or empty decrypt). Force resync on Android should republish local data.",
          ]
        : ["No decrypted plaintext on relay."],
    };
  }

  const plain = record.plaintext;
  const rawCount =
    kind === "weight" ? rawWeightWorkoutCount(plain) : rawCardioSessionCount(plain);

  if (rawCount === 0) {
    return {
      dTag,
      kind,
      date,
      status: "empty_on_relay",
      rawCount: 0,
      parsedCount: 0,
      parseNotes: [
        kind === "weight"
          ? "Relay JSON has workouts:[] (cleared or never synced)."
          : "Relay JSON has sessions:[] (cleared or never synced).",
      ],
      plaintext: plain,
    };
  }

  const parsed =
    kind === "weight"
      ? parseWeightDayLog(plain, date)
      : parseCardioDayLog(plain, date);
  const parsedCount =
    kind === "weight"
      ? (parsed && "workouts" in parsed ? parsed.workouts.length : 0)
      : (parsed && "sessions" in parsed ? parsed.sessions.length : 0);

  if (parsedCount > 0) {
    return {
      dTag,
      kind,
      date,
      status: "parsed_ok",
      rawCount,
      parsedCount,
      parseNotes: [],
      plaintext: plain,
    };
  }

  const parseNotes: string[] = [];
  try {
    const data = JSON.parse(plain) as Record<string, unknown>;
    if (kind === "weight" && Array.isArray(data.workouts)) {
      data.workouts.forEach((w, i) => parseNotes.push(...explainWeightWorkoutSkip(w, i)));
    }
    if (kind === "cardio" && Array.isArray(data.sessions)) {
      data.sessions.forEach((s, i) => parseNotes.push(...explainCardioSessionSkip(s, i)));
    }
  } catch {
    parseNotes.push("JSON parse failed.");
  }
  if (parseNotes.length === 0) {
    parseNotes.push("Payload did not yield readable sessions after filtering.");
  }

  return {
    dTag,
    kind,
    date,
    status: "parse_rejected",
    rawCount,
    parsedCount: 0,
    parseNotes,
    plaintext: plain,
  };
}

/** Per-calendar-day relay status for weight + cardio (Progress diagnostics). */
export function buildTrainingDayDiagnostics(records: AppDataRecord[]): TrainingDayDiagnostic[] {
  const byDate = new Map<string, TrainingDayDiagnostic>();

  for (const record of records) {
    const side = diagnoseTrainingDayRecord(record);
    if (!side) continue;
    let row = byDate.get(side.date);
    if (!row) {
      row = { date: side.date, weight: null, cardio: null };
      byDate.set(side.date, row);
    }
    if (side.kind === "weight") row.weight = side;
    else row.cardio = side;
  }

  const rows = [...byDate.values()].sort((a, b) => b.date.localeCompare(a.date));

  for (const row of rows) {
    const wOk = row.weight?.status === "parsed_ok";
    const cOk = row.cardio?.status === "parsed_ok";
    if (wOk && row.cardio?.status === "tombstone_on_relay") {
      row.issue =
        "Cardio tag is cleared on the relay. If you logged cardio this day on Android, force resync.";
    } else if (wOk && row.cardio?.status === "empty_on_relay") {
      row.issue = "Cardio tag on relay is empty (sessions:[]). Force resync if Android has a session.";
    } else if (wOk && row.cardio?.status === "parse_rejected") {
      row.issue = "Cardio JSON on relay but Progress could not read sessions.";
    } else if (cOk && row.weight?.status === "tombstone_on_relay") {
      row.issue =
        "Strength tag is cleared on the relay. If you logged lifting this day on Android, force resync.";
    } else if (cOk && row.weight?.status === "empty_on_relay") {
      row.issue = "Strength tag on relay is empty (workouts:[]). Force resync if Android has a workout.";
    } else if (cOk && row.weight?.status === "parse_rejected") {
      row.issue = "Strength JSON on relay but Progress could not read workouts.";
    } else if (row.weight?.status === "parse_rejected") {
      row.issue = "Strength day log on relay but web could not read any workouts.";
    } else if (row.cardio?.status === "parse_rejected") {
      row.issue = "Cardio day log on relay but web could not read any sessions.";
    }
  }

  return rows;
}

export function trainingDayDiagnosticLabel(status: TrainingDaySideStatus): string {
  switch (status) {
    case "parsed_ok":
      return "OK";
    case "missing":
      return "Missing";
    case "tombstone_on_relay":
      return "Cleared on relay";
    case "decrypt_failed":
      return "Decrypt failed";
    case "empty_on_relay":
      return "Empty on relay";
    case "parse_rejected":
      return "Parse rejected";
  }
}

function mergeWeightLogs(logs: WeightDayLog[]): WeightDayLog[] {
  const byDate = new Map<string, WeightWorkoutSession[]>();
  for (const log of logs) {
    byDate.set(log.date, [...(byDate.get(log.date) ?? []), ...log.workouts]);
  }
  return [...byDate.entries()]
    .map(([date, workouts]) => ({ date, workouts: uniqueById(workouts) }))
    .sort((a, b) => a.date.localeCompare(b.date));
}

function mergeCardioLogs(logs: CardioDayLog[]): CardioDayLog[] {
  const byDate = new Map<string, CardioSession[]>();
  for (const log of logs) {
    byDate.set(log.date, [...(byDate.get(log.date) ?? []), ...log.sessions]);
  }
  return [...byDate.entries()]
    .map(([date, sessions]) => ({ date, sessions: uniqueById(sessions) }))
    .sort((a, b) => a.date.localeCompare(b.date));
}

function uniqueById<T extends { id: string }>(items: T[]): T[] {
  const byId = new Map<string, T>();
  for (const item of items) {
    byId.set(item.id, item);
  }
  return [...byId.values()];
}

export function parseLogsFromAppData(records: AppDataRecord[]): ParsedTrainingLogs {
  const weightLogs: WeightDayLog[] = [];
  const cardioLogs: CardioDayLog[] = [];
  const decryptErrors: string[] = [];
  const emptyDayLogs: string[] = [];

  for (const record of records) {
    const dTag = record.d_tag?.trim();
    if (!dTag) continue;
    if (record.decrypt_error) {
      // Tombstones (NIP-33 delete / empty NIP-44 payload) mean "cleared on relay" — not a key mismatch.
      if (
        isTrainingDayLogTag(dTag) &&
        !record.decrypt_error.includes("message empty") &&
        record.decrypt_error !== "tombstone"
      ) {
        decryptErrors.push(`${dTag}: ${record.decrypt_error}`);
      }
      continue;
    }
    if (!record.plaintext) continue;

    const weightDate = weightDayLogDateFromTag(dTag);
    if (weightDate) {
      const log = parseWeightDayLog(record.plaintext, weightDate);
      if (log) weightLogs.push(log);
      else emptyDayLogs.push(dTag);
      continue;
    }

    const cardioDate = cardioDayLogDateFromTag(dTag);
    if (cardioDate) {
      const log = parseCardioDayLog(record.plaintext, cardioDate);
      if (log) cardioLogs.push(log);
      else emptyDayLogs.push(dTag);
    }
  }

  return {
    weightLogs: mergeWeightLogs(weightLogs),
    cardioLogs: mergeCardioLogs(cardioLogs),
    decryptErrors,
    emptyDayLogs,
  };
}

export function isMasterTrainingTag(dTag: string): boolean {
  return (
    dTag === WEIGHT_EXERCISES_D_TAG ||
    dTag === WEIGHT_ROUTINES_D_TAG ||
    dTag === CARDIO_ROUTINES_D_TAG
  );
}

function sessionEpoch(session: WeightWorkoutSession): number {
  return session.startedAtEpochSeconds ?? session.finishedAtEpochSeconds ?? 0;
}

function cardioSessionEpoch(session: CardioSession): number {
  return (
    session.startEpochSeconds ??
    session.endEpochSeconds ??
    session.loggedAtEpochSeconds ??
    0
  );
}

export function buildTimeline(
  weightLogs: WeightDayLog[],
  cardioLogs: CardioDayLog[],
): HistoryTimelineItem[] {
  const items: HistoryTimelineItem[] = [];
  for (const log of weightLogs) {
    for (const session of log.workouts) {
      items.push({ kind: "weight", date: log.date, session });
    }
  }
  for (const log of cardioLogs) {
    for (const session of log.sessions) {
      items.push({ kind: "cardio", date: log.date, session });
    }
  }
  return items.sort((a, b) => {
    const dateCmp = b.date.localeCompare(a.date);
    if (dateCmp !== 0) return dateCmp;
    const ea =
      a.kind === "weight" ? sessionEpoch(a.session) : cardioSessionEpoch(a.session);
    const eb =
      b.kind === "weight" ? sessionEpoch(b.session) : cardioSessionEpoch(b.session);
    return eb - ea;
  });
}

export function buildRecentWorkouts(
  weightLogs: WeightDayLog[],
  cardioLogs: CardioDayLog[],
  limit = 5,
): RecentWorkoutItem[] {
  return buildTimeline(weightLogs, cardioLogs)
    .slice(0, limit)
    .map((item) => ({
      ...item,
      contextKey: `${item.kind}:${item.date}:${item.session.id}`,
    }));
}

export function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function formatIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function addDays(iso: string, days: number): string {
  const d = parseIsoDate(iso);
  d.setDate(d.getDate() + days);
  return formatIsoDate(d);
}

/** Monday-start week containing [isoDate]. */
export function weekStartMonday(isoDate: string): string {
  const d = parseIsoDate(isoDate);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  return formatIsoDate(d);
}

export function filterLogsByPeriod(
  weightLogs: WeightDayLog[],
  cardioLogs: CardioDayLog[],
  weeks: number | null,
): { weightLogs: WeightDayLog[]; cardioLogs: CardioDayLog[] } {
  if (weeks == null) return { weightLogs, cardioLogs };
  const today = formatIsoDate(new Date());
  const start = addDays(today, -(weeks * 7 - 1));
  const inRange = (date: string) => date >= start && date <= today;
  return {
    weightLogs: weightLogs
      .filter((l) => inRange(l.date))
      .map((l) => ({ ...l, workouts: l.workouts }))
      .filter((l) => l.workouts.length > 0),
    cardioLogs: cardioLogs
      .filter((l) => inRange(l.date))
      .filter((l) => l.sessions.length > 0),
  };
}

function weekLabel(weekStartIso: string): string {
  const d = parseIsoDate(weekStartIso);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export function weeklySessionCounts(
  weightLogs: WeightDayLog[],
  cardioLogs: CardioDayLog[],
): WeeklyCountBucket[] {
  const map = new Map<string, WeeklyCountBucket>();

  const ensure = (weekStart: string) => {
    let b = map.get(weekStart);
    if (!b) {
      b = {
        weekStart,
        label: weekLabel(weekStart),
        weightSessions: 0,
        cardioSessions: 0,
      };
      map.set(weekStart, b);
    }
    return b;
  };

  for (const log of weightLogs) {
    const ws = weekStartMonday(log.date);
    ensure(ws).weightSessions += log.workouts.length;
  }
  for (const log of cardioLogs) {
    const ws = weekStartMonday(log.date);
    ensure(ws).cardioSessions += log.sessions.length;
  }

  return [...map.values()].sort((a, b) => a.weekStart.localeCompare(b.weekStart));
}

function isWorkingSet(set: WeightSet): boolean {
  return set.reps > 0 || (set.durationSeconds ?? 0) > 0;
}

export function volumeByMuscleGroup(
  weightLogs: WeightDayLog[],
  exercises: WeightExercise[],
): MuscleGroupVolume[] {
  const byId = new Map(exercises.map((e) => [e.id, e]));
  const counts = new Map<string, number>();

  for (const log of weightLogs) {
    for (const workout of log.workouts) {
      for (const entry of workout.entries) {
        const ex = byId.get(entry.exerciseId);
        const group = ex?.muscleGroup?.trim().toLowerCase() || "unknown";
        const working = entry.sets.filter(isWorkingSet).length;
        if (entry.hiitBlock) {
          counts.set(group, (counts.get(group) ?? 0) + 1);
        } else {
          counts.set(group, (counts.get(group) ?? 0) + working);
        }
      }
    }
  }

  return [...counts.entries()]
    .map(([muscleGroup, setCount]) => ({
      muscleGroup: muscleGroup.replace(/^\w/, (c) => c.toUpperCase()),
      setCount,
    }))
    .sort((a, b) => b.setCount - a.setCount);
}

export function exerciseIdsInLogs(weightLogs: WeightDayLog[]): string[] {
  const ids = new Set<string>();
  for (const log of weightLogs) {
    for (const workout of log.workouts) {
      for (const entry of workout.entries) {
        ids.add(entry.exerciseId);
      }
    }
  }
  return [...ids].sort();
}

export function historyForExercise(
  weightLogs: WeightDayLog[],
  exerciseId: string,
): ExerciseHistoryRow[] {
  const rows: ExerciseHistoryRow[] = [];
  for (const log of weightLogs) {
    for (const workout of log.workouts) {
      const entry = workout.entries.find((e) => e.exerciseId === exerciseId);
      if (!entry) continue;
      rows.push({
        date: log.date,
        workoutId: workout.id,
        routineName: workout.routineName,
        entry,
        sessionEpoch: sessionEpoch(workout),
      });
    }
  }
  return rows.sort((a, b) => {
    const d = b.date.localeCompare(a.date);
    if (d !== 0) return d;
    return b.sessionEpoch - a.sessionEpoch;
  });
}

const REP_BUCKET_LABELS = [
  ...Array.from({ length: 10 }, (_, i) => (i === 0 ? "1 rep" : `${i + 1} reps`)),
  "10+ reps",
];

export function maxWeightByRepBucketKg(
  weightLogs: WeightDayLog[],
  exerciseId: string,
): RepBucketMax[] {
  const max: (number | null)[] = Array(11).fill(null);

  for (const log of weightLogs) {
    for (const workout of log.workouts) {
      const entry = workout.entries.find((e) => e.exerciseId === exerciseId);
      if (!entry) continue;
      for (const set of entry.sets) {
        const reps = set.reps;
        const w = set.weightKg;
        if (reps <= 0 || w == null || w <= 0) continue;
        const idx = reps <= 10 ? reps - 1 : 10;
        const prev = max[idx];
        if (prev == null || w > prev) max[idx] = w;
      }
    }
  }

  return REP_BUCKET_LABELS.map((label, i) => ({ label, maxKg: max[i] }));
}

export function formatWeightKg(kg: number, unit: "KG" | "LB" = "LB"): string {
  if (unit === "KG") {
    return kg % 1 === 0 ? `${kg} kg` : `${kg.toFixed(1)} kg`;
  }
  const lb = kg * 2.2046226218;
  return lb % 1 < 0.05 ? `${Math.round(lb)} lb` : `${lb.toFixed(1)} lb`;
}

export function formatDistanceMeters(meters: number, unit: "MI" | "KM" = "MI"): string {
  if (unit === "KM") {
    const km = meters / 1000;
    return `${km.toFixed(2)} km`;
  }
  const miles = meters / 1609.344;
  return `${miles.toFixed(2)} mi`;
}

export function weightSourceLabel(source: WeightWorkoutSource): string {
  switch (source) {
    case "LIVE":
      return "Live";
    case "IMPORTED":
      return "Imported";
    default:
      return "Manual";
  }
}

export function summarizeWeightSession(session: WeightWorkoutSession): string {
  const names = session.entries.length;
  const sets = session.entries.reduce(
    (n, e) => n + e.sets.filter(isWorkingSet).length + (e.hiitBlock ? 1 : 0),
    0,
  );
  const title = session.routineName?.trim() || "Weight workout";
  return `${title} · ${names} exercise${names === 1 ? "" : "s"} · ${sets} set${sets === 1 ? "" : "s"}`;
}

export function summarizeCardioSession(session: CardioSession): string {
  const title = session.routineName?.trim() || session.activity.displayLabel;
  let line = `${title} · ${session.durationMinutes} min`;
  if (session.distanceMeters != null && session.distanceMeters > 0) {
    line += ` · ${formatDistanceMeters(session.distanceMeters)}`;
  }
  return line;
}

export function formatSetLine(set: WeightSet, unit: "KG" | "LB" = "LB"): string {
  if ((set.durationSeconds ?? 0) > 0 && set.reps <= 0) {
    return `${set.durationSeconds}s hold`;
  }
  const reps = set.reps > 0 ? `${set.reps} reps` : "—";
  if (set.weightKg != null && set.weightKg > 0) {
    return `${reps} @ ${formatWeightKg(set.weightKg, unit)}`;
  }
  return reps;
}
