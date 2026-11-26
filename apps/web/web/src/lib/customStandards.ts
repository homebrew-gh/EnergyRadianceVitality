/**
 * Custom fitness standards the user builds on the web, kind 30078 `erv/standards/custom`.
 * Stored in the same shape as built-ins (`FitnessStandard`, canonical units), so scoring and
 * results work the same. The editor works on a draft of strings in the athlete's units.
 */
import type { CardioRecordGroupId } from "./cardioProgress";
import { formatClock } from "./cardioProgress";
import { kgToLb, type BodyWeightUnit } from "./fitnessEquipment";
import { parseClockInput } from "./standardResults";
import type { FitnessStandard, ManualUnit, StandardEvent, StandardMeasure, StandardTierValues } from "./standards";

export const CUSTOM_STANDARDS_D_TAG = "erv/standards/custom";
export const CUSTOM_STANDARD_ID_PREFIX = "custom-";

export type StoredCustomStandard = FitnessStandard & { lastModifiedEpochSeconds: number };

export type CustomStandardsPayload = {
  ervCustomStandardsVersion: 1;
  standards: StoredCustomStandard[];
  deletedStandards: Record<string, number>;
  lastModifiedEpochSeconds: number;
};

export function emptyCustomStandards(): CustomStandardsPayload {
  return { ervCustomStandardsVersion: 1, standards: [], deletedStandards: {}, lastModifiedEpochSeconds: 0 };
}

const MILE = 1609.344;
const LB = 0.45359237;
const FOOT_M = 0.3048;
const INCH_CM = 2.54;

export type CustomEventType =
  | "LIFT_BW"
  | "LIFT_LOAD"
  | "MAX_REPS"
  | "REPS_AT_BW"
  | "HOLD"
  | "CARDIO"
  | "TEST_TIME_FASTER"
  | "TEST_TIME_LONGER"
  | "TEST_REPS"
  | "TEST_DISTANCE"
  | "TEST_JUMP";

export const CUSTOM_EVENT_TYPES: { id: CustomEventType; label: string; group: "Lift" | "Cardio" | "Test" }[] = [
  { id: "LIFT_BW", label: "Lift: weight × bodyweight", group: "Lift" },
  { id: "LIFT_LOAD", label: "Lift: weight", group: "Lift" },
  { id: "REPS_AT_BW", label: "Lift: reps with bodyweight on the bar", group: "Lift" },
  { id: "MAX_REPS", label: "Most reps in one set", group: "Lift" },
  { id: "HOLD", label: "Longest hold", group: "Lift" },
  { id: "CARDIO", label: "Fastest distance (run, ruck, row, ride)", group: "Cardio" },
  { id: "TEST_TIME_FASTER", label: "Test: time, faster is better", group: "Test" },
  { id: "TEST_TIME_LONGER", label: "Test: time, longer is better", group: "Test" },
  { id: "TEST_REPS", label: "Test: reps", group: "Test" },
  { id: "TEST_DISTANCE", label: "Test: distance (carry, throw)", group: "Test" },
  { id: "TEST_JUMP", label: "Test: jump against your height", group: "Test" },
];

export const CUSTOM_CARDIO_GROUPS: { id: CardioRecordGroupId; label: string }[] = [
  { id: "RUN", label: "Run" },
  { id: "RUCK", label: "Ruck" },
  { id: "ROW", label: "Row" },
  { id: "RIDE", label: "Ride" },
  { id: "INDOOR_RIDE", label: "Indoor ride" },
  { id: "WALK", label: "Walk" },
];

export const CUSTOM_CARDIO_DISTANCES: { key: string; label: string; meters: number }[] = [
  { key: "400m", label: "400 m", meters: 400 },
  { key: "500m", label: "500 m", meters: 500 },
  { key: "800m", label: "800 m", meters: 800 },
  { key: "1mi", label: "1 mile", meters: MILE },
  { key: "1.5mi", label: "1.5 miles", meters: 1.5 * MILE },
  { key: "2k", label: "2K", meters: 2000 },
  { key: "2mi", label: "2 miles", meters: 2 * MILE },
  { key: "3mi", label: "3 miles", meters: 3 * MILE },
  { key: "5k", label: "5K", meters: 5000 },
  { key: "10k", label: "10K", meters: 10000 },
  { key: "10mi", label: "10 miles", meters: 10 * MILE },
  { key: "12mi", label: "12 miles", meters: 12 * MILE },
  { key: "half", label: "Half marathon", meters: 21097.5 },
];

export type CustomEventDraft = {
  key: string;
  /** The stored event id when editing, so logged results stay attached. */
  eventId: string | null;
  name: string;
  type: CustomEventType;
  exerciseId: string;
  reps: string;
  cardioGroup: CardioRecordGroupId;
  distanceKey: string;
  minPack: string;
  base: string;
  strong: string;
  elite: string;
};

export type CustomStandardDraft = {
  id: string | null;
  name: string;
  description: string;
  events: CustomEventDraft[];
};

let draftKey = 0;
export function newEventDraft(): CustomEventDraft {
  draftKey += 1;
  return {
    key: `e${Date.now()}-${draftKey}`,
    eventId: null,
    name: "",
    type: "LIFT_BW",
    exerciseId: "",
    reps: "1",
    cardioGroup: "RUN",
    distanceKey: "1mi",
    minPack: "",
    base: "",
    strong: "",
    elite: "",
  };
}

export function newStandardDraft(): CustomStandardDraft {
  return { id: null, name: "", description: "", events: [newEventDraft()] };
}

export function usesExercise(type: CustomEventType): boolean {
  return type === "LIFT_BW" || type === "LIFT_LOAD" || type === "MAX_REPS" || type === "REPS_AT_BW" || type === "HOLD";
}

export function lowerIsBetterFor(type: CustomEventType): boolean {
  return type === "CARDIO" || type === "TEST_TIME_FASTER";
}

/** What one tier target is typed in, e.g. "x BW", "lb", "reps", "m:ss", "ft", "in over height". */
export function tierUnitLabel(type: CustomEventType, unit: BodyWeightUnit): string {
  switch (type) {
    case "LIFT_BW":
      return "x BW";
    case "LIFT_LOAD":
      return unit === "KG" ? "kg" : "lb";
    case "MAX_REPS":
    case "REPS_AT_BW":
    case "TEST_REPS":
      return "reps";
    case "HOLD":
    case "CARDIO":
    case "TEST_TIME_FASTER":
    case "TEST_TIME_LONGER":
      return "m:ss";
    case "TEST_DISTANCE":
      return unit === "KG" ? "m" : "ft";
    case "TEST_JUMP":
      return unit === "KG" ? "cm over height" : "in over height";
  }
}

function parseNumber(raw: string): number | null {
  const t = raw.trim().replace(",", ".");
  if (!/^\d+(\.\d+)?$/.test(t)) return null;
  return Number(t);
}

/** One typed target → canonical value; null when it does not parse. A jump target may be 0. */
export function parseTier(type: CustomEventType, raw: string, unit: BodyWeightUnit): number | null {
  switch (type) {
    case "HOLD":
    case "CARDIO":
    case "TEST_TIME_FASTER":
    case "TEST_TIME_LONGER":
      return parseClockInput(raw);
    default: {
      const n = parseNumber(raw);
      if (n == null) return null;
      if (type === "TEST_JUMP") return unit === "KG" ? n : n * INCH_CM;
      if (n <= 0) return null;
      if (type === "LIFT_LOAD") return unit === "KG" ? n : n * LB;
      if (type === "TEST_DISTANCE") return unit === "KG" ? n : n * FOOT_M;
      if (type === "LIFT_BW") return n;
      return Math.round(n);
    }
  }
}

export function formatTier(type: CustomEventType, value: number, unit: BodyWeightUnit): string {
  const trim = (n: number, d: number) => String(Math.round(n * 10 ** d) / 10 ** d);
  switch (type) {
    case "HOLD":
    case "CARDIO":
    case "TEST_TIME_FASTER":
    case "TEST_TIME_LONGER":
      return formatClock(value);
    case "LIFT_LOAD":
      return trim(unit === "KG" ? value : kgToLb(value), 1);
    case "TEST_DISTANCE":
      return trim(unit === "KG" ? value : value / FOOT_M, 0);
    case "TEST_JUMP":
      return trim(unit === "KG" ? value : value / INCH_CM, 0);
    case "LIFT_BW":
      return trim(value, 2);
    default:
      return String(Math.round(value));
  }
}

function measureFor(e: CustomEventDraft, unit: BodyWeightUnit): StandardMeasure | null {
  const reps = Math.round(parseNumber(e.reps) ?? 0);
  switch (e.type) {
    case "LIFT_BW":
      return reps >= 1 ? { kind: "LIFT_MAX_BW", reps } : null;
    case "LIFT_LOAD":
      return reps >= 1 ? { kind: "LIFT_MAX_LOAD", reps } : null;
    case "MAX_REPS":
      return { kind: "MAX_REPS" };
    case "REPS_AT_BW":
      return { kind: "REPS_AT_BW" };
    case "HOLD":
      return { kind: "LONGEST_HOLD" };
    case "CARDIO": {
      const d = CUSTOM_CARDIO_DISTANCES.find((x) => x.key === e.distanceKey);
      if (!d) return null;
      const pack = e.cardioGroup === "RUCK" ? parseNumber(e.minPack) : null;
      return {
        kind: "FASTEST",
        meters: d.meters,
        cardioGroups: [e.cardioGroup],
        ...(pack && pack > 0 ? { minPackKg: unit === "KG" ? pack : pack * LB } : {}),
      };
    }
    default: {
      const manual: Record<string, ManualUnit> = {
        TEST_TIME_FASTER: "SECONDS",
        TEST_TIME_LONGER: "SECONDS",
        TEST_REPS: "REPS",
        TEST_DISTANCE: "METERS",
        TEST_JUMP: "CM_OVER_HEIGHT",
      };
      return { kind: "MANUAL", unit: manual[e.type] };
    }
  }
}

function detailFor(e: CustomEventDraft, exerciseName: string | null, unit: BodyWeightUnit): string {
  const reps = Math.round(parseNumber(e.reps) ?? 1);
  const of = exerciseName ? `${exerciseName}: ` : "";
  switch (e.type) {
    case "LIFT_BW":
      return `${of}${reps === 1 ? "Estimated 1RM" : `Heaviest ${reps} reps`}, times bodyweight`;
    case "LIFT_LOAD":
      return `${of}${reps === 1 ? "Estimated 1RM" : `Heaviest ${reps} reps`}`;
    case "REPS_AT_BW":
      return `${of}Bodyweight on the bar, most reps`;
    case "MAX_REPS":
      return `${of}Most reps in one set`;
    case "HOLD":
      return `${of}Longest hold`;
    case "CARDIO": {
      const d = CUSTOM_CARDIO_DISTANCES.find((x) => x.key === e.distanceKey)?.label ?? "";
      const g = CUSTOM_CARDIO_GROUPS.find((x) => x.id === e.cardioGroup)?.label.toLowerCase() ?? "";
      const pack = e.cardioGroup === "RUCK" ? parseNumber(e.minPack) : null;
      return `Fastest ${d} ${g}${pack ? ` with at least ${pack} ${unit === "KG" ? "kg" : "lb"}` : ""}`;
    }
    case "TEST_TIME_FASTER":
      return "Test result, faster is better";
    case "TEST_TIME_LONGER":
      return "Test result, longer is better";
    case "TEST_REPS":
      return "Test result, reps";
    case "TEST_DISTANCE":
      return "Test result, distance";
    case "TEST_JUMP":
      return "Jump against your height";
  }
}

function slug(text: string, used: Set<string>): string {
  const base =
    text
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-|-$/g, "") || "event";
  let id = base;
  for (let i = 2; used.has(id); i++) id = `${base}-${i}`;
  used.add(id);
  return id;
}

/** Turns the editor draft into a stored standard, or lists what still needs fixing. */
export function draftToStandard(
  draft: CustomStandardDraft,
  unit: BodyWeightUnit,
  exerciseNames: ReadonlyMap<string, string>,
  newId: () => string,
): { standard: FitnessStandard } | { errors: string[] } {
  const errors: string[] = [];
  if (!draft.name.trim()) errors.push("Give the standard a name.");
  if (draft.events.length === 0) errors.push("Add at least one event.");
  const used = new Set<string>();
  const events: StandardEvent[] = [];
  draft.events.forEach((e, i) => {
    const label = e.name.trim() || `Event ${i + 1}`;
    const exerciseName = usesExercise(e.type) ? (exerciseNames.get(e.exerciseId) ?? null) : null;
    const cardioName =
      e.type === "CARDIO"
        ? `${CUSTOM_CARDIO_DISTANCES.find((d) => d.key === e.distanceKey)?.label ?? ""} ${
            CUSTOM_CARDIO_GROUPS.find((g) => g.id === e.cardioGroup)?.label.toLowerCase() ?? ""
          }`.trim()
        : null;
    const name = e.name.trim() || exerciseName || cardioName || "";
    if (!name) errors.push(`${label}: give it a name.`);
    if (usesExercise(e.type) && !exerciseName) errors.push(`${label}: pick an exercise.`);
    const measure = measureFor(e, unit);
    if (!measure) errors.push(`${label}: check the reps or distance.`);
    const base = parseTier(e.type, e.base, unit);
    const strong = parseTier(e.type, e.strong, unit);
    const elite = parseTier(e.type, e.elite, unit);
    if (base == null || strong == null || elite == null) {
      errors.push(`${label}: fill in Base, Strong and Elite as ${tierUnitLabel(e.type, unit)}.`);
      return;
    }
    const lower = lowerIsBetterFor(e.type);
    const ordered = lower ? base > strong && strong > elite : base < strong && strong < elite;
    if (!ordered) {
      errors.push(
        `${label}: Elite must be ${lower ? "faster" : "more"} than Strong, and Strong ${lower ? "faster" : "more"} than Base.`,
      );
      return;
    }
    if (!measure || !name) return;
    // Keep an event's id across edits so its logged results stay attached.
    const id = e.eventId && !used.has(e.eventId) ? (used.add(e.eventId), e.eventId) : slug(name, used);
    const tiers: StandardTierValues = { BASE: base, STRONG: strong, ELITE: elite };
    events.push({
      id,
      name,
      detail: detailFor(e, exerciseName, unit),
      measure,
      ...(usesExercise(e.type) ? { exerciseIds: [e.exerciseId] } : {}),
      lowerIsBetter: lower,
      tiers,
    });
  });
  if (errors.length > 0) return { errors };
  return {
    standard: {
      id: draft.id ?? newId(),
      name: draft.name.trim(),
      description:
        draft.description.trim() || `${events.length} ${events.length === 1 ? "event" : "events"}, made by you.`,
      events,
    },
  };
}

function typeOf(event: StandardEvent): CustomEventType {
  const m = event.measure;
  switch (m.kind) {
    case "LIFT_MAX_BW":
      return "LIFT_BW";
    case "LIFT_MAX_LOAD":
      return "LIFT_LOAD";
    case "MAX_REPS":
      return "MAX_REPS";
    case "REPS_AT_BW":
      return "REPS_AT_BW";
    case "LONGEST_HOLD":
      return "HOLD";
    case "FASTEST":
      return "CARDIO";
    case "MANUAL":
      switch (m.unit) {
        case "SECONDS":
          return event.lowerIsBetter ? "TEST_TIME_FASTER" : "TEST_TIME_LONGER";
        case "REPS":
          return "TEST_REPS";
        case "METERS":
          return "TEST_DISTANCE";
        case "CM_OVER_HEIGHT":
          return "TEST_JUMP";
      }
  }
}

/** A stored standard back into an editor draft (also used to start a copy of a built-in). */
export function standardToDraft(standard: FitnessStandard, unit: BodyWeightUnit, keepId: boolean): CustomStandardDraft {
  return {
    id: keepId ? standard.id : null,
    name: keepId ? standard.name : `${standard.name} (copy)`,
    description: keepId ? standard.description : "",
    events: standard.events.map((ev) => {
      const type = typeOf(ev);
      const m = ev.measure;
      const draft = newEventDraft();
      const distance =
        m.kind === "FASTEST" ? CUSTOM_CARDIO_DISTANCES.find((d) => Math.abs(d.meters - m.meters) < 1) : undefined;
      return {
        ...draft,
        eventId: keepId ? ev.id : null,
        name: ev.name,
        type,
        exerciseId: ev.exerciseIds?.[0] ?? "",
        reps: m.kind === "LIFT_MAX_BW" || m.kind === "LIFT_MAX_LOAD" ? String(m.reps) : "1",
        cardioGroup: m.kind === "FASTEST" ? (m.cardioGroups[0] ?? "RUN") : "RUN",
        distanceKey: distance?.key ?? "1mi",
        minPack:
          m.kind === "FASTEST" && m.minPackKg
            ? String(Math.round(unit === "KG" ? m.minPackKg : kgToLb(m.minPackKg)))
            : "",
        base: formatTier(type, ev.tiers.BASE, unit),
        strong: formatTier(type, ev.tiers.STRONG, unit),
        elite: formatTier(type, ev.tiers.ELITE, unit),
      };
    }),
  };
}

function validStandard(raw: unknown): StoredCustomStandard | null {
  if (!raw || typeof raw !== "object") return null;
  const s = raw as Record<string, unknown>;
  if (typeof s.id !== "string" || !s.id.startsWith(CUSTOM_STANDARD_ID_PREFIX)) return null;
  if (typeof s.name !== "string" || !Array.isArray(s.events)) return null;
  const events = (s.events as unknown[]).filter((e): e is StandardEvent => {
    if (!e || typeof e !== "object") return false;
    const ev = e as Record<string, unknown>;
    const tiers = ev.tiers as Record<string, unknown> | undefined;
    const measure = ev.measure as Record<string, unknown> | undefined;
    return (
      typeof ev.id === "string" &&
      typeof ev.name === "string" &&
      typeof ev.lowerIsBetter === "boolean" &&
      !!measure &&
      typeof measure.kind === "string" &&
      !!tiers &&
      ["BASE", "STRONG", "ELITE"].every((k) => typeof tiers[k] === "number")
    );
  });
  if (events.length === 0) return null;
  return {
    id: s.id,
    name: s.name,
    description: typeof s.description === "string" ? s.description : "",
    events,
    lastModifiedEpochSeconds:
      typeof s.lastModifiedEpochSeconds === "number" ? Math.max(0, Math.floor(s.lastModifiedEpochSeconds)) : 0,
  };
}

export function parseCustomStandards(raw: string | null | undefined): CustomStandardsPayload {
  if (!raw) return emptyCustomStandards();
  try {
    const data = JSON.parse(raw) as Record<string, unknown>;
    const deleted: Record<string, number> = {};
    if (data.deletedStandards && typeof data.deletedStandards === "object") {
      for (const [id, at] of Object.entries(data.deletedStandards as Record<string, unknown>)) {
        if (typeof at === "number" && Number.isFinite(at)) deleted[id] = Math.floor(at);
      }
    }
    const standards = (Array.isArray(data.standards) ? data.standards : [])
      .map(validStandard)
      .filter((s): s is StoredCustomStandard => s != null)
      .filter((s) => !(s.id in deleted) || s.lastModifiedEpochSeconds > deleted[s.id]);
    return {
      ervCustomStandardsVersion: 1,
      standards,
      deletedStandards: deleted,
      lastModifiedEpochSeconds:
        typeof data.lastModifiedEpochSeconds === "number" ? Math.max(0, Math.floor(data.lastModifiedEpochSeconds)) : 0,
    };
  } catch {
    return emptyCustomStandards();
  }
}

const YEAR_SECONDS = 365 * 24 * 3600;

export function serializeCustomStandards(payload: CustomStandardsPayload, nowEpochSeconds: number): string {
  const deleted: Record<string, number> = {};
  for (const [id, at] of Object.entries(payload.deletedStandards)) {
    if (nowEpochSeconds - at < YEAR_SECONDS) deleted[id] = at;
  }
  return JSON.stringify({
    ervCustomStandardsVersion: 1,
    standards: payload.standards,
    deletedStandards: deleted,
    lastModifiedEpochSeconds: nowEpochSeconds,
  });
}

export function upsertCustomStandard(
  payload: CustomStandardsPayload,
  standard: FitnessStandard,
  nowEpochSeconds: number,
): CustomStandardsPayload {
  const stored: StoredCustomStandard = { ...standard, lastModifiedEpochSeconds: nowEpochSeconds };
  const exists = payload.standards.some((s) => s.id === standard.id);
  return {
    ...payload,
    standards: exists
      ? payload.standards.map((s) => (s.id === standard.id ? stored : s))
      : [...payload.standards, stored],
    lastModifiedEpochSeconds: nowEpochSeconds,
  };
}

export function deleteCustomStandard(
  payload: CustomStandardsPayload,
  id: string,
  nowEpochSeconds: number,
): CustomStandardsPayload {
  return {
    ...payload,
    standards: payload.standards.filter((s) => s.id !== id),
    deletedStandards: { ...payload.deletedStandards, [id]: nowEpochSeconds },
    lastModifiedEpochSeconds: nowEpochSeconds,
  };
}
