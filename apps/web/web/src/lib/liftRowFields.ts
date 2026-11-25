import type { BodyWeightUnit } from "./fitnessEquipment";
import { formatWeightLoadNumber, parseWeightInputToKg } from "./weightLoadUnit";
import {
  DEFAULT_TIMED_PREP_SECONDS,
  prescriptionSetTargets,
  prescriptionUsesPerSide,
  syncPrescriptionSetCount,
  type WeightSetLoggingStyle,
  type WorkoutWeightPrescription,
} from "./workoutTraining";

/**
 * Reading and writing the short text fields of a lift row in the workout editor: one field each
 * for sets, reps (or a hold time), weight, RIR and rest. Anything the row cannot say in one
 * field (per-side reps, different targets per set) shows read-only and is edited in the row's
 * details.
 */

export type RowField = { text: string; editable: boolean; hint?: string };

/** Seconds as "m:ss", e.g. 90 → "1:30". Blank for none. */
export function formatClock(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds)) return "";
  const whole = Math.max(0, Math.round(seconds));
  const minutes = Math.floor(whole / 60);
  const rest = whole % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

/**
 * Seconds from "90", "1:30", "90s", "2m" or "1m30s". Blank is `null` (no value);
 * text that is not a time is `undefined` (leave the value as it was).
 */
export function parseClock(raw: string): number | null | undefined {
  const text = raw.trim().toLowerCase().replace(/\s+/g, "");
  if (!text) return null;
  let match = text.match(/^(\d+):(\d{1,2})$/);
  if (match) {
    const seconds = Number(match[2]);
    if (seconds >= 60) return undefined;
    return Number(match[1]) * 60 + seconds;
  }
  match = text.match(/^(?:(\d+)m)?(?:(\d+)s?)?$/);
  if (match && (match[1] != null || match[2] != null)) {
    return Number(match[1] ?? 0) * 60 + Number(match[2] ?? 0);
  }
  return undefined;
}

function sameForEverySet<T>(values: T[]): boolean {
  return values.every((value) => value === values[0]);
}

/** True when the sets carry different reps, weights or RIR, which one field cannot show. */
export function setsDiffer(p: WorkoutWeightPrescription): boolean {
  const sets = p.sets ?? [];
  if (sets.length < 2) return false;
  return (
    !sameForEverySet(sets.map((s) => prescriptionSetTargets(s).reps)) ||
    !sameForEverySet(sets.map((s) => prescriptionSetTargets(s).weightKg)) ||
    !sameForEverySet(sets.map((s) => s.rir ?? null))
  );
}

function isTimed(p: WorkoutWeightPrescription, style: WeightSetLoggingStyle): boolean {
  return style === "time_only" || p.mode === "time_based";
}

export function setsField(p: WorkoutWeightPrescription): RowField {
  return { text: String(p.setCount ?? p.sets?.length ?? 3), editable: true };
}

export function applySets(p: WorkoutWeightPrescription, raw: string): WorkoutWeightPrescription {
  const count = Number.parseInt(raw.trim(), 10);
  if (!Number.isFinite(count) || count < 1) return p;
  return syncPrescriptionSetCount(p, Math.min(count, 50));
}

/** "8", "8-12", "max", a hold like "0:45", or read-only text for per-side and per-set targets. */
export function repsField(p: WorkoutWeightPrescription, style: WeightSetLoggingStyle): RowField {
  if (isTimed(p, style)) {
    return { text: formatClock(p.durationSeconds), editable: true, hint: "Hold" };
  }
  if (p.mode === "max_reps") return { text: "max", editable: true };
  if (prescriptionUsesPerSide(p)) {
    const first = (p.sets ?? []).find((s) => (s.repsPerSide ?? 0) > 0);
    return { text: `${first?.repsPerSide ?? "?"}/side`, editable: false, hint: "Per side, edit in details" };
  }
  if (setsDiffer(p)) return { text: "varied", editable: false, hint: "Different per set, edit in details" };
  const perSet = (p.sets ?? []).map((s) => prescriptionSetTargets(s).reps).find((r) => r != null);
  if (perSet != null) return { text: String(perSet), editable: true };
  if (p.targetReps != null && p.targetReps > 0) return { text: String(p.targetReps), editable: true };
  const min = p.repRangeMin ?? null;
  const max = p.repRangeMax ?? null;
  if (min != null && max != null && min !== max) return { text: `${min}-${max}`, editable: true };
  if (min != null || max != null) return { text: String(min ?? max), editable: true };
  return { text: "", editable: true };
}

function withoutPerSetReps(p: WorkoutWeightPrescription): WorkoutWeightPrescription {
  if (!p.sets) return p;
  return { ...p, sets: p.sets.map((s) => ({ ...s, reps: null, targetReps: null })) };
}

/**
 * Write the reps field back. A number is the target reps, "8-12" a rep range, "max" max reps,
 * and a time ("45s", "0:45") a timed hold. A plain number on a time-only exercise is seconds.
 */
export function applyReps(
  p: WorkoutWeightPrescription,
  raw: string,
  style: WeightSetLoggingStyle,
): WorkoutWeightPrescription {
  const text = raw.trim().toLowerCase().replace(/[–—]/g, "-").replace(/\s+/g, "");
  const timedText = /^\d+:\d{1,2}$/.test(text) || /^\d+(m|s|m\d+s)$/.test(text);
  // A time-only exercise is always a hold; one that takes reps or time stays a hold while it is
  // one (switch it back to reps in the details), and becomes one when a time is typed.
  const wantsTime =
    style === "time_only" ||
    (style === "reps_or_time" && (timedText || (isTimed(p, style) && (!text || /^\d+$/.test(text)))));
  if (wantsTime) {
    if (!text) return { ...p, durationSeconds: null };
    const seconds = parseClock(text);
    if (seconds === undefined) return p;
    return {
      ...withoutPerSetReps(p),
      mode: "time_based",
      durationSeconds: seconds,
      timedPrepSeconds: p.timedPrepSeconds ?? DEFAULT_TIMED_PREP_SECONDS,
      targetReps: null,
      repRangeMin: null,
      repRangeMax: null,
    };
  }
  const base: WorkoutWeightPrescription = {
    ...withoutPerSetReps(p),
    mode: "straight",
    durationSeconds: null,
  };
  if (!text) return { ...base, targetReps: null, repRangeMin: null, repRangeMax: null };
  if (text === "max" || text === "amrap") {
    return { ...base, mode: "max_reps", targetReps: null, repRangeMin: null, repRangeMax: null };
  }
  const range = text.match(/^(\d+)-(\d+)$/);
  if (range) {
    const low = Number(range[1]);
    const high = Number(range[2]);
    if (low < 1 || high < 1) return p;
    if (low === high) return { ...base, targetReps: low, repRangeMin: null, repRangeMax: null };
    return {
      ...base,
      targetReps: null,
      repRangeMin: Math.min(low, high),
      repRangeMax: Math.max(low, high),
    };
  }
  if (/^\d+$/.test(text)) {
    const reps = Number(text);
    if (reps < 1) return p;
    return { ...base, targetReps: reps, repRangeMin: null, repRangeMax: null };
  }
  return p;
}

export function weightField(p: WorkoutWeightPrescription, unit: BodyWeightUnit): RowField {
  if (p.mode === "max_reps" && p.targetWeightKg == null) return { text: "", editable: true };
  if (setsDiffer(p)) return { text: "varied", editable: false, hint: "Different per set, edit in details" };
  const perSet = (p.sets ?? []).map((s) => prescriptionSetTargets(s).weightKg).find((w) => w != null);
  const kg = p.targetWeightKg ?? perSet ?? null;
  return { text: kg != null && kg > 0 ? formatWeightLoadNumber(kg, unit) : "", editable: true };
}

export function applyWeight(
  p: WorkoutWeightPrescription,
  raw: string,
  unit: BodyWeightUnit,
): WorkoutWeightPrescription {
  const text = raw.trim();
  if (text && parseWeightInputToKg(text, unit) == null) return p;
  const kg = text ? parseWeightInputToKg(text, unit) : null;
  return {
    ...p,
    targetWeightKg: kg,
    // One weight for every set: clear per-set weights so they don't override it on the phone.
    ...(p.sets ? { sets: p.sets.map((s) => ({ ...s, weightKg: null, targetWeightKg: null })) } : {}),
  };
}

export function rirField(p: WorkoutWeightPrescription): RowField {
  if (setsDiffer(p)) return { text: "varied", editable: false, hint: "Different per set, edit in details" };
  const perSet = (p.sets ?? []).map((s) => s.rir).find((r) => r != null);
  const rir = p.targetRir ?? perSet ?? null;
  return { text: rir != null ? String(rir) : "", editable: true };
}

export function applyRir(p: WorkoutWeightPrescription, raw: string): WorkoutWeightPrescription {
  const text = raw.trim();
  if (text && !/^\d+$/.test(text)) return p;
  return {
    ...p,
    targetRir: text ? Number(text) : null,
    ...(p.sets ? { sets: p.sets.map((s) => ({ ...s, rir: null })) } : {}),
  };
}

export function restField(p: WorkoutWeightPrescription): RowField {
  return { text: formatClock(p.restBetweenSetsSeconds), editable: true };
}

export function applyRest(p: WorkoutWeightPrescription, raw: string): WorkoutWeightPrescription {
  const seconds = parseClock(raw);
  if (seconds === undefined) return p;
  return { ...p, restBetweenSetsSeconds: seconds };
}
