export const PROGRAMS_MASTER_D_TAG = "erv/programs/master";

export type ProgramBlockKind =
  | "workout"
  | "weight"
  | "cardio"
  | "unified_routine"
  | "flex_training"
  | "stretch_routine"
  | "stretch_catalog"
  | "heat_cold"
  | "rest"
  | "custom"
  | "other";

export type ProgramDayBlock = {
  id: string;
  kind: ProgramBlockKind;
  title?: string | null;
  notes?: string | null;
  workoutId?: string | null;
  weightExerciseIds?: string[];
  weightRoutineId?: string | null;
  cardioActivity?: string | null;
  cardioRoutineId?: string | null;
  unifiedRoutineId?: string | null;
  stretchRoutineId?: string | null;
  stretchCatalogIds?: string[];
  stretchHoldSecondsPerStretch?: number | null;
  heatColdMode?: string | null;
  targetMinutes?: number | null;
  checklistItems?: string[];
};

export type ProgramWeekDay = {
  dayOfWeek: number;
  blocks: ProgramDayBlock[];
};

export type FitnessProgram = {
  id: string;
  name: string;
  description?: string | null;
  sourceLabel?: string | null;
  weeklySchedule: ProgramWeekDay[];
  createdAtEpochSeconds: number;
  lastModifiedEpochSeconds: number;
};

export type ProgramStrategy = {
  mode?: string;
  repeatProgramId?: string | null;
  rotationEntries?: unknown[];
  rotationStartDate?: string | null;
  challengeProgramId?: string | null;
  challengeStartDate?: string | null;
  challengeLengthDays?: number;
};

export type ProgramMasterPayload = {
  programs: FitnessProgram[];
  activeProgramId?: string | null;
  strategy: ProgramStrategy;
  masterUpdatedAtEpochSeconds: number;
};

export const ISO_WEEK_DAYS = [
  { value: 1, label: "Monday", shortLabel: "Mon" },
  { value: 2, label: "Tuesday", shortLabel: "Tue" },
  { value: 3, label: "Wednesday", shortLabel: "Wed" },
  { value: 4, label: "Thursday", shortLabel: "Thu" },
  { value: 5, label: "Friday", shortLabel: "Fri" },
  { value: 6, label: "Saturday", shortLabel: "Sat" },
  { value: 7, label: "Sunday", shortLabel: "Sun" },
] as const;

export function emptyProgramMaster(): ProgramMasterPayload {
  return {
    programs: [],
    activeProgramId: null,
    strategy: { mode: "MANUAL" },
    masterUpdatedAtEpochSeconds: 0,
  };
}

function optionalString(raw: unknown): string | null {
  return typeof raw === "string" && raw.trim() ? raw : null;
}

function optionalInt(raw: unknown): number | null {
  if (typeof raw !== "number" || !Number.isFinite(raw)) return null;
  return Math.round(raw);
}

function stringList(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return raw.filter((v): v is string => typeof v === "string" && v.trim().length > 0);
}

function parseProgramDayBlock(raw: unknown): ProgramDayBlock | null {
  if (!raw || typeof raw !== "object") return null;
  const data = raw as Record<string, unknown>;
  const kind = optionalString(data.kind) as ProgramBlockKind | null;
  if (!kind) return null;
  return {
    id: optionalString(data.id) ?? crypto.randomUUID(),
    kind,
    title: optionalString(data.title),
    notes: optionalString(data.notes),
    workoutId: optionalString(data.workoutId),
    weightExerciseIds: stringList(data.weightExerciseIds),
    weightRoutineId: optionalString(data.weightRoutineId),
    cardioActivity: optionalString(data.cardioActivity),
    cardioRoutineId: optionalString(data.cardioRoutineId),
    unifiedRoutineId: optionalString(data.unifiedRoutineId),
    stretchRoutineId: optionalString(data.stretchRoutineId),
    stretchCatalogIds: stringList(data.stretchCatalogIds),
    stretchHoldSecondsPerStretch: optionalInt(data.stretchHoldSecondsPerStretch),
    heatColdMode: optionalString(data.heatColdMode),
    targetMinutes: optionalInt(data.targetMinutes),
    checklistItems: stringList(data.checklistItems),
  };
}

function parseProgramWeekDay(raw: unknown): ProgramWeekDay | null {
  if (!raw || typeof raw !== "object") return null;
  const data = raw as Record<string, unknown>;
  const dayOfWeek = optionalInt(data.dayOfWeek);
  if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) return null;
  const blocks = Array.isArray(data.blocks)
    ? data.blocks.map(parseProgramDayBlock).filter((b): b is ProgramDayBlock => b != null)
    : [];
  return { dayOfWeek, blocks };
}

function normalizeWeek(days: ProgramWeekDay[]): ProgramWeekDay[] {
  const byDay = new Map<number, ProgramDayBlock[]>();
  for (const day of days) {
    if (day.dayOfWeek < 1 || day.dayOfWeek > 7) continue;
    byDay.set(day.dayOfWeek, [...(byDay.get(day.dayOfWeek) ?? []), ...day.blocks]);
  }
  return ISO_WEEK_DAYS.map((day) => ({
    dayOfWeek: day.value,
    blocks: byDay.get(day.value) ?? [],
  }));
}

function parseFitnessProgram(raw: unknown): FitnessProgram | null {
  if (!raw || typeof raw !== "object") return null;
  const data = raw as Record<string, unknown>;
  const name = optionalString(data.name);
  if (!name) return null;
  const now = Math.floor(Date.now() / 1000);
  const week = Array.isArray(data.weeklySchedule)
    ? data.weeklySchedule.map(parseProgramWeekDay).filter((d): d is ProgramWeekDay => d != null)
    : [];
  return {
    id: optionalString(data.id) ?? crypto.randomUUID(),
    name,
    description: optionalString(data.description),
    sourceLabel: optionalString(data.sourceLabel),
    weeklySchedule: normalizeWeek(week),
    createdAtEpochSeconds: optionalInt(data.createdAtEpochSeconds) ?? now,
    lastModifiedEpochSeconds: optionalInt(data.lastModifiedEpochSeconds) ?? now,
  };
}

export function parseProgramMasterPayload(raw: string): ProgramMasterPayload {
  try {
    const data = JSON.parse(raw) as Record<string, unknown>;
    const programs = Array.isArray(data.programs)
      ? data.programs.map(parseFitnessProgram).filter((p): p is FitnessProgram => p != null)
      : [];
    const activeProgramId = optionalString(data.activeProgramId);
    return {
      programs,
      activeProgramId: activeProgramId && programs.some((p) => p.id === activeProgramId) ? activeProgramId : null,
      strategy:
        data.strategy && typeof data.strategy === "object"
          ? (data.strategy as ProgramStrategy)
          : { mode: "MANUAL" },
      masterUpdatedAtEpochSeconds: optionalInt(data.masterUpdatedAtEpochSeconds) ?? 0,
    };
  } catch {
    return emptyProgramMaster();
  }
}

function cleanBlock(block: ProgramDayBlock): ProgramDayBlock {
  const body: ProgramDayBlock = {
    id: block.id,
    kind: block.kind,
  };
  if (block.title?.trim()) body.title = block.title.trim();
  if (block.notes?.trim()) body.notes = block.notes.trim();
  if (block.kind === "workout" && block.workoutId?.trim()) body.workoutId = block.workoutId.trim();
  return body;
}

export function programMasterPayload(payload: ProgramMasterPayload): string {
  const programs = payload.programs.map((program) => ({
    ...program,
    weeklySchedule: normalizeWeek(program.weeklySchedule).map((day) => ({
      dayOfWeek: day.dayOfWeek,
      blocks: day.blocks.map(cleanBlock).filter((block) => block.kind !== "workout" || block.workoutId),
    })),
  }));
  const body: ProgramMasterPayload = {
    programs,
    activeProgramId: payload.activeProgramId ?? null,
    strategy: payload.strategy ?? { mode: "MANUAL" },
    masterUpdatedAtEpochSeconds: payload.masterUpdatedAtEpochSeconds,
  };
  return JSON.stringify(body);
}

export function createEmptyFitnessProgram(name = "Weekly Plan"): FitnessProgram {
  const now = Math.floor(Date.now() / 1000);
  return {
    id: crypto.randomUUID(),
    name,
    description: null,
    sourceLabel: "Web Planner",
    weeklySchedule: normalizeWeek([]),
    createdAtEpochSeconds: now,
    lastModifiedEpochSeconds: now,
  };
}
