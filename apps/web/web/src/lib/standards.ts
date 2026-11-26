/**
 * Fitness standards: a set of events, each with Base / Strong / Elite targets, scored from the
 * lift and cardio history ERV already keeps. Built-in standards live in `standardsLibrary.ts`.
 *
 * Every target is stored in one canonical unit per measure, so a standard reads the same in
 * pounds or kilograms:
 * - LIFT_MAX_BW: load ÷ bodyweight (1.5 = 1.5x bodyweight)
 * - LIFT_MAX_LOAD: kg
 * - MAX_REPS, REPS_AT_BW: reps
 * - LONGEST_HOLD, FASTEST: seconds
 * - MANUAL: its own unit (seconds, reps, meters, or cm beyond your height)
 *
 * Estimated values count (marked ESTIMATED); a logged or tested value of the same size wins.
 */
import type { BodyWeightEntry } from "./bodyWeight";
import { estimatedOneRmKg, EST_ONE_RM_MAX_REPS } from "./liftRecords";
import { cardioRecordGroupOf, cardioSessionSeconds, type CardioRecordGroupId } from "./cardioProgress";
import type { CardioDayLog, WeightDayLog, WeightSet } from "./trainingHistory";

export type StandardTierId = "BASE" | "STRONG" | "ELITE";

export const STANDARD_TIERS: { id: StandardTierId; label: string }[] = [
  { id: "BASE", label: "Base" },
  { id: "STRONG", label: "Strong" },
  { id: "ELITE", label: "Elite" },
];

export type StandardTierValues = Record<StandardTierId, number>;

export type ManualUnit = "SECONDS" | "REPS" | "METERS" | "CM_OVER_HEIGHT";

export type StandardMeasure =
  /** Heaviest load for at least [reps] reps, divided by bodyweight on that day. reps 1 = est. 1RM. */
  | { kind: "LIFT_MAX_BW"; reps: number }
  /** Heaviest load for at least [reps] reps, in kg. */
  | { kind: "LIFT_MAX_LOAD"; reps: number }
  /** Most reps in one set with at least bodyweight on the bar. */
  | { kind: "REPS_AT_BW" }
  /** Most reps in one set. */
  | { kind: "MAX_REPS" }
  /** Longest timed hold in one set, seconds. */
  | { kind: "LONGEST_HOLD" }
  /** Fastest time over [meters] in these cardio groups; a ruck can require a minimum pack. */
  | { kind: "FASTEST"; meters: number; cardioGroups: CardioRecordGroupId[]; minPackKg?: number }
  /** Only from a logged test result. */
  | { kind: "MANUAL"; unit: ManualUnit };

export type StandardSex = "male" | "female";

/** Targets for one group of people; the first variant that matches the profile wins. */
export type StandardVariant = {
  sex?: StandardSex;
  minAge?: number;
  maxAge?: number;
  tiers: StandardTierValues;
};

export type StandardEvent = {
  id: string;
  name: string;
  /** How the event is done, e.g. "Bodyweight on the bar, as many reps as possible". */
  detail?: string;
  measure: StandardMeasure;
  /** Lifts and holds: built-in exercise ids that count. */
  exerciseIds?: string[];
  /** Lifts and holds: a case-insensitive pattern on exercise names, for custom exercises. */
  exerciseNamePattern?: string;
  /** Used, and said so, only when nothing matches the ids or pattern (e.g. Deadlift for trap bar). */
  fallbackExerciseIds?: string[];
  lowerIsBetter: boolean;
  /** Targets when no variant matches. */
  tiers: StandardTierValues;
  variants?: StandardVariant[];
};

export type FitnessStandard = {
  id: string;
  name: string;
  description: string;
  source?: { label: string; url: string };
  events: StandardEvent[];
};

export type StandardProfile = {
  ageYears?: number | null;
  sex?: StandardSex | null;
  heightCm?: number | null;
};

/** A one-off result the user logged for an event (test day, a plank, a jump). */
export type StandardTestResult = {
  standardId: string;
  eventId: string;
  /**
   * The raw measurement: kg for lift events (a bodyweight ratio is worked out on the day),
   * reps, seconds, meters, or the full broad jump in cm.
   */
  value: number;
  date: string;
};

export type StandardInputs = {
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  bodyWeights: BodyWeightEntry[];
  profile: StandardProfile;
  /** Exercise id → name, for matching custom exercises by name. */
  exerciseNames: ReadonlyMap<string, string>;
  testResults?: StandardTestResult[];
};

export type StandardValueSource = "LOGGED" | "ESTIMATED" | "TESTED";

export type StandardEventResult = {
  event: StandardEvent;
  tiers: StandardTierValues;
  /** Canonical value, or null when nothing counts yet. */
  value: number | null;
  source: StandardValueSource | null;
  date: string | null;
  /** Why the value is what it is, e.g. "Deadlift used in place of trap bar". */
  note: string | null;
  /** What the event still needs before it can be scored. */
  missing: "BODYWEIGHT" | "HEIGHT" | "RESULT" | null;
  tier: StandardTierId | null;
  /** 0 to 3 along the rings: 1 = Base, 2 = Strong, 3 = Elite (capped at 3). */
  score: number;
  nextTier: StandardTierId | null;
  /** How far from the next tier, in the canonical unit (always positive). */
  gapToNext: number | null;
};

export type StandardResult = {
  standard: FitnessStandard;
  events: StandardEventResult[];
  /** The highest tier every scored event meets; null when one is below Base or none are scored. */
  overallTier: StandardTierId | null;
  scoredCount: number;
  tierCounts: Record<StandardTierId, number>;
  /** Profile fields that would sharpen the targets or unlock an event. */
  needsProfile: ("age" | "sex" | "height")[];
};

const EPS = 1e-6;

/** Variant targets for the profile, or the event's own targets. */
export function tiersFor(event: StandardEvent, profile: StandardProfile): StandardTierValues {
  for (const v of event.variants ?? []) {
    if (v.sex && v.sex !== profile.sex) continue;
    if (v.minAge != null && (profile.ageYears == null || profile.ageYears < v.minAge)) continue;
    if (v.maxAge != null && (profile.ageYears == null || profile.ageYears > v.maxAge)) continue;
    return v.tiers;
  }
  return event.tiers;
}

function meets(value: number, target: number, lowerIsBetter: boolean): boolean {
  return lowerIsBetter ? value <= target + EPS : value >= target - EPS;
}

/** Position along the rings: 0 at nothing, 1 at Base, 2 at Strong, 3 at Elite. */
export function standardScore(value: number, tiers: StandardTierValues, lowerIsBetter: boolean): number {
  const steps = [tiers.BASE, tiers.STRONG, tiers.ELITE];
  if (!lowerIsBetter) {
    if (value < tiers.BASE) return Math.max(0, value / tiers.BASE);
    for (let i = 0; i < 2; i++) {
      const lo = steps[i];
      const hi = steps[i + 1];
      if (value < hi) return i + 1 + (value - lo) / (hi - lo);
    }
    return 3;
  }
  if (value > tiers.BASE) return Math.max(0, tiers.BASE / value);
  for (let i = 0; i < 2; i++) {
    const lo = steps[i];
    const hi = steps[i + 1];
    if (value > hi) return i + 1 + (lo - value) / (lo - hi);
  }
  return 3;
}

export function tierOf(value: number, tiers: StandardTierValues, lowerIsBetter: boolean): StandardTierId | null {
  let tier: StandardTierId | null = null;
  for (const t of STANDARD_TIERS) {
    if (meets(value, tiers[t.id], lowerIsBetter)) tier = t.id;
  }
  return tier;
}

/** Bodyweight on [date]: the last weigh-in on or before it, else the first one after. */
export function bodyWeightOn(entries: BodyWeightEntry[], date: string): number | null {
  let before: BodyWeightEntry | null = null;
  for (const e of entries) {
    if (e.date <= date && (!before || e.date >= before.date)) before = e;
  }
  if (before) return before.weightKg;
  let after: BodyWeightEntry | null = null;
  for (const e of entries) {
    if (!after || e.date < after.date) after = e;
  }
  return after?.weightKg ?? null;
}

type Candidate = { value: number; source: StandardValueSource; date: string; note: string | null };

function better(a: Candidate, b: Candidate | null, lowerIsBetter: boolean): boolean {
  if (!b) return true;
  if (Math.abs(a.value - b.value) < EPS) {
    const rank = (s: StandardValueSource) => (s === "TESTED" ? 2 : s === "LOGGED" ? 1 : 0);
    return rank(a.source) > rank(b.source);
  }
  return lowerIsBetter ? a.value < b.value : a.value > b.value;
}

function isWorking(set: WeightSet): boolean {
  return set.warmup !== true && (set.reps > 0 || (set.durationSeconds ?? 0) > 0);
}

function matchingExerciseIds(event: StandardEvent, inputs: StandardInputs): Set<string> {
  const ids = new Set(event.exerciseIds ?? []);
  if (event.exerciseNamePattern) {
    const re = new RegExp(event.exerciseNamePattern, "i");
    for (const [id, name] of inputs.exerciseNames) if (re.test(name)) ids.add(id);
  }
  return ids;
}

function setsFor(inputs: StandardInputs, ids: ReadonlySet<string>): { date: string; set: WeightSet }[] {
  const out: { date: string; set: WeightSet }[] = [];
  for (const day of inputs.weightLogs) {
    for (const workout of day.workouts) {
      for (const entry of workout.entries) {
        if (entry.hiitBlock || !ids.has(entry.exerciseId)) continue;
        for (const set of entry.sets) if (isWorking(set)) out.push({ date: day.date, set });
      }
    }
  }
  return out;
}

/**
 * Loads (kg) one set is good for at [reps] reps: the set itself when it had enough reps, and an
 * Epley estimate from its 1RM when that says more (a single is never estimated).
 */
function loadsForReps(set: WeightSet, reps: number): { kg: number; estimated: boolean }[] {
  const kg = set.weightKg ?? 0;
  if (kg <= 0 || set.reps <= 0) return [];
  const out: { kg: number; estimated: boolean }[] = [];
  if (set.reps >= reps) out.push({ kg, estimated: false });
  if (set.reps !== reps) {
    const e1rm = estimatedOneRmKg(kg, set.reps);
    if (e1rm != null) {
      const kgAtReps = reps === 1 ? e1rm : e1rm / (1 + reps / 30);
      if (kgAtReps > kg || set.reps < reps) out.push({ kg: kgAtReps, estimated: true });
    }
  }
  return out;
}

const MILE = 1609.344;

function distanceLabel(meters: number): string {
  const miles = meters / MILE;
  return miles >= 0.95 ? `${miles.toFixed(1)} mi` : `${Math.round(meters)} m`;
}

function liftCandidates(
  event: StandardEvent,
  inputs: StandardInputs,
): { candidates: Candidate[]; missing: StandardEventResult["missing"] } {
  const m = event.measure;
  let ids = matchingExerciseIds(event, inputs);
  let sets = setsFor(inputs, ids);
  let fallbackNote: string | null = null;
  if (sets.length === 0 && event.fallbackExerciseIds?.length) {
    ids = new Set(event.fallbackExerciseIds);
    sets = setsFor(inputs, ids);
    if (sets.length > 0) {
      const name = inputs.exerciseNames.get(event.fallbackExerciseIds[0]) ?? "A related lift";
      fallbackNote = `${name} used in its place`;
    }
  }
  const out: Candidate[] = [];
  let neededBodyweight = false;
  for (const { date, set } of sets) {
    const kg = set.weightKg ?? 0;
    switch (m.kind) {
      case "LIFT_MAX_BW":
      case "LIFT_MAX_LOAD": {
        const loads = loadsForReps(set, m.reps);
        if (loads.length === 0) break;
        let divisor = 1;
        if (m.kind === "LIFT_MAX_BW") {
          const bw = bodyWeightOn(inputs.bodyWeights, date);
          if (bw == null) {
            neededBodyweight = true;
            break;
          }
          divisor = bw;
        }
        for (const load of loads) {
          out.push({
            value: load.kg / divisor,
            source: load.estimated ? "ESTIMATED" : "LOGGED",
            date,
            note: fallbackNote,
          });
        }
        break;
      }
      case "REPS_AT_BW": {
        if (set.reps <= 0 || kg <= 0) break;
        const bw = bodyWeightOn(inputs.bodyWeights, date);
        if (bw == null) {
          neededBodyweight = true;
          break;
        }
        if (kg >= bw * 0.99) {
          out.push({ value: set.reps, source: "LOGGED", date, note: fallbackNote });
        }
        if (set.reps <= EST_ONE_RM_MAX_REPS) {
          // Epley turned around: reps you could do at bodyweight from this set's 1RM estimate.
          const e1rm = estimatedOneRmKg(kg, set.reps);
          if (e1rm != null && e1rm > bw) {
            const reps = Math.floor(30 * (e1rm / bw - 1));
            if (reps >= 1) out.push({ value: reps, source: "ESTIMATED", date, note: fallbackNote });
          }
        }
        break;
      }
      case "MAX_REPS":
        if (set.reps > 0) out.push({ value: set.reps, source: "LOGGED", date, note: fallbackNote });
        break;
      case "LONGEST_HOLD":
        if ((set.durationSeconds ?? 0) > 0) {
          out.push({ value: set.durationSeconds as number, source: "LOGGED", date, note: fallbackNote });
        }
        break;
      default:
        break;
    }
  }
  return { candidates: out, missing: out.length === 0 && neededBodyweight ? "BODYWEIGHT" : null };
}

function cardioCandidates(
  m: Extract<StandardMeasure, { kind: "FASTEST" }>,
  inputs: StandardInputs,
): Candidate[] {
  const out: Candidate[] = [];
  for (const day of inputs.cardioLogs) {
    for (const session of day.sessions) {
      const group = cardioRecordGroupOf(session);
      if (!group || !m.cardioGroups.includes(group.id) || (session.segmentCount ?? 0) > 0) continue;
      if (m.minPackKg != null && (session.ruckLoadKg ?? 0) + 0.25 < m.minPackKg) continue;
      const distance = session.distanceMeters ?? 0;
      const seconds = cardioSessionSeconds(session);
      if (distance + 0.5 < m.meters || seconds <= 0) continue;
      const scaled = distance > m.meters * 1.05;
      out.push({
        value: (seconds * m.meters) / distance,
        source: scaled ? "ESTIMATED" : "LOGGED",
        date: day.date,
        note: scaled ? `Average pace of a ${distanceLabel(distance)} ${group.label.toLowerCase()}` : null,
      });
    }
  }
  return out;
}

function testCandidates(
  standardId: string,
  event: StandardEvent,
  inputs: StandardInputs,
): { candidates: Candidate[]; missing: StandardEventResult["missing"] } {
  const out: Candidate[] = [];
  let missing: StandardEventResult["missing"] = null;
  for (const r of inputs.testResults ?? []) {
    if (r.standardId !== standardId || r.eventId !== event.id || !(r.value > 0)) continue;
    let value = r.value;
    if (event.measure.kind === "LIFT_MAX_BW") {
      const bw = bodyWeightOn(inputs.bodyWeights, r.date);
      if (bw == null) {
        missing = "BODYWEIGHT";
        continue;
      }
      value = r.value / bw;
    }
    if (event.measure.kind === "MANUAL" && event.measure.unit === "CM_OVER_HEIGHT") {
      const h = inputs.profile.heightCm;
      if (!h) {
        missing = "HEIGHT";
        continue;
      }
      value = r.value - h;
    }
    out.push({ value, source: "TESTED", date: r.date, note: null });
  }
  return { candidates: out, missing };
}

export function evaluateEvent(
  standardId: string,
  event: StandardEvent,
  inputs: StandardInputs,
): StandardEventResult {
  const tiers = tiersFor(event, inputs.profile);
  const tested = testCandidates(standardId, event, inputs);
  let candidates = tested.candidates;
  let missing = tested.missing;
  const m = event.measure;
  if (m.kind === "FASTEST") {
    candidates = candidates.concat(cardioCandidates(m, inputs));
  } else if (m.kind !== "MANUAL") {
    const lifts = liftCandidates(event, inputs);
    candidates = candidates.concat(lifts.candidates);
    missing = missing ?? lifts.missing;
  }
  let best: Candidate | null = null;
  for (const c of candidates) if (better(c, best, event.lowerIsBetter)) best = c;
  if (!best) {
    return {
      event,
      tiers,
      value: null,
      source: null,
      date: null,
      note: null,
      missing: missing ?? "RESULT",
      tier: null,
      score: 0,
      nextTier: "BASE",
      gapToNext: null,
    };
  }
  const tier = tierOf(best.value, tiers, event.lowerIsBetter);
  const nextIndex = tier == null ? 0 : STANDARD_TIERS.findIndex((t) => t.id === tier) + 1;
  const nextTier = STANDARD_TIERS[nextIndex]?.id ?? null;
  return {
    event,
    tiers,
    value: best.value,
    source: best.source,
    date: best.date,
    note: best.note,
    missing: null,
    tier,
    score: Math.min(3, standardScore(best.value, tiers, event.lowerIsBetter)),
    nextTier,
    gapToNext: nextTier ? Math.abs(tiers[nextTier] - best.value) : null,
  };
}

export function evaluateStandard(standard: FitnessStandard, inputs: StandardInputs): StandardResult {
  const events = standard.events.map((e) => evaluateEvent(standard.id, e, inputs));
  const tierCounts: Record<StandardTierId, number> = { BASE: 0, STRONG: 0, ELITE: 0 };
  let overall = 3;
  let scoredCount = 0;
  for (const r of events) {
    if (r.value == null) continue;
    scoredCount++;
    if (r.tier) tierCounts[r.tier]++;
    const level = r.tier == null ? 0 : STANDARD_TIERS.findIndex((t) => t.id === r.tier) + 1;
    overall = Math.min(overall, level);
  }
  const needsProfile: StandardResult["needsProfile"] = [];
  const variants = standard.events.flatMap((e) => e.variants ?? []);
  if (variants.some((v) => v.minAge != null || v.maxAge != null) && inputs.profile.ageYears == null) {
    needsProfile.push("age");
  }
  if (variants.some((v) => v.sex) && !inputs.profile.sex) needsProfile.push("sex");
  if (
    standard.events.some((e) => e.measure.kind === "MANUAL" && e.measure.unit === "CM_OVER_HEIGHT") &&
    !inputs.profile.heightCm
  ) {
    needsProfile.push("height");
  }
  return {
    standard,
    events,
    overallTier: scoredCount > 0 && overall > 0 ? STANDARD_TIERS[overall - 1].id : null,
    scoredCount,
    tierCounts,
    needsProfile,
  };
}
