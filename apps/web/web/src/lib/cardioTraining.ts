/** Matches Android CardioSync master — `erv/cardio/routines`. */

export const CARDIO_ROUTINES_D_TAG = "erv/cardio/routines";

export type CardioModality = "OUTDOOR" | "INDOOR_TREADMILL";

export type CardioActivitySnapshot = {
  builtin?: string | null;
  customTypeId?: string | null;
  customName?: string | null;
  displayLabel: string;
};

export type CardioRoutineStep = {
  id: string;
  activity: CardioActivitySnapshot;
  modality: CardioModality;
  treadmill?: unknown | null;
  targetDurationMinutes?: number | null;
  orderIndex: number;
};

export type CardioRoutine = {
  id: string;
  name: string;
  steps: CardioRoutineStep[];
  activity: CardioActivitySnapshot;
  modality: CardioModality;
  treadmill?: unknown | null;
  targetDurationMinutes?: number | null;
  repeatDays: string[];
  notes: string;
};

export type CardioCustomActivityType = {
  id: string;
  name: string;
  optionalMet?: number | null;
};

export type CardioQuickLaunch = {
  id: string;
  name: string;
  activity: CardioActivitySnapshot;
  modality: CardioModality;
  [key: string]: unknown;
};

/** Full cardio master payload (routines + custom types + quick launches). */
export type CardioMasterPayload = {
  routines: CardioRoutine[];
  customActivityTypes: CardioCustomActivityType[];
  quickLaunches: CardioQuickLaunch[];
};

export function parseCardioMasterPayload(raw: string): CardioMasterPayload {
  const parsed = JSON.parse(raw) as Partial<CardioMasterPayload>;
  return {
    routines: Array.isArray(parsed.routines) ? parsed.routines : [],
    customActivityTypes: Array.isArray(parsed.customActivityTypes)
      ? parsed.customActivityTypes
      : [],
    quickLaunches: Array.isArray(parsed.quickLaunches) ? parsed.quickLaunches : [],
  };
}

export function cardioMasterPayload(payload: CardioMasterPayload): string {
  return JSON.stringify(payload satisfies CardioMasterPayload);
}

export function activityFromCatalogId(
  activityId: string,
  catalog: { id: string; displayName: string }[],
): CardioActivitySnapshot {
  const entry = catalog.find((a) => a.id === activityId);
  return {
    builtin: activityId,
    displayLabel: entry?.displayName ?? activityId,
  };
}

export function cardioRoutineSummary(routine: CardioRoutine): string {
  const steps = routine.steps?.length
    ? [...routine.steps].sort((a, b) => a.orderIndex - b.orderIndex)
    : null;
  if (steps?.length) {
    return steps.map((s) => s.activity.displayLabel).join(" → ");
  }
  return routine.activity.displayLabel;
}

export function newCardioRoutine(
  activity: CardioActivitySnapshot,
  modality: CardioModality = "OUTDOOR",
): CardioRoutine {
  return {
    id: crypto.randomUUID(),
    name: "",
    steps: [],
    activity,
    modality,
    targetDurationMinutes: null,
    repeatDays: [],
    notes: "",
  };
}

/** Local editor row for a cardio routine leg. */
export type CardioStepDraft = {
  id: string;
  activityId: string;
  modality: CardioModality;
  targetDurationMinutes: number | "";
};

export function newCardioStepDraft(
  activityId: string,
  modality: CardioModality = "OUTDOOR",
): CardioStepDraft {
  return {
    id: crypto.randomUUID(),
    activityId,
    modality,
    targetDurationMinutes: "",
  };
}

export function activityIdFromSnapshot(snapshot: CardioActivitySnapshot): string {
  return snapshot.builtin ?? snapshot.customTypeId ?? snapshot.displayLabel;
}

export function routineToStepDrafts(routine: CardioRoutine): CardioStepDraft[] {
  if (routine.steps?.length) {
    return [...routine.steps]
      .sort((a, b) => a.orderIndex - b.orderIndex)
      .map((step) => ({
        id: step.id,
        activityId: activityIdFromSnapshot(step.activity),
        modality: step.modality,
        targetDurationMinutes: step.targetDurationMinutes ?? "",
      }));
  }
  return [
    {
      id: crypto.randomUUID(),
      activityId: activityIdFromSnapshot(routine.activity),
      modality: routine.modality,
      targetDurationMinutes: routine.targetDurationMinutes ?? "",
    },
  ];
}

export function buildCardioRoutineFromDrafts(
  input: {
    editingId: string | null;
    name: string;
    notes: string;
    steps: CardioStepDraft[];
    catalog: { id: string; displayName: string }[];
    existing?: CardioRoutine;
  },
): CardioRoutine {
  if (input.steps.length === 0) {
    throw new Error("Add at least one activity leg.");
  }

  const builtSteps: CardioRoutineStep[] = input.steps.map((draft, orderIndex) => ({
    id: draft.id || crypto.randomUUID(),
    activity: activityFromCatalogId(draft.activityId, input.catalog),
    modality: draft.modality,
    targetDurationMinutes:
      draft.targetDurationMinutes === ""
        ? null
        : Math.max(1, Number(draft.targetDurationMinutes)),
    orderIndex,
  }));

  const first = builtSteps[0]!;
  const multiLeg = builtSteps.length > 1;

  return {
    id: input.editingId ?? crypto.randomUUID(),
    name: input.name,
    steps: multiLeg ? builtSteps : [],
    activity: first.activity,
    modality: first.modality,
    targetDurationMinutes: first.targetDurationMinutes,
    repeatDays: input.existing?.repeatDays ?? [],
    notes: input.notes,
  };
}

export function upsertRoutine<T extends { id: string }>(
  routines: T[],
  routine: T,
): T[] {
  const next = routines.filter((r) => r.id !== routine.id);
  next.push(routine);
  return next.sort((a, b) =>
    ("name" in a && "name" in b
      ? String(a.name).localeCompare(String(b.name))
      : 0),
  );
}
