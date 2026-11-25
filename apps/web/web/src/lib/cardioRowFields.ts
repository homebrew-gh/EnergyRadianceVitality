/**
 * Short text fields for cardio and mobility rows in the workout editor, in the same style as
 * liftRowFields: each reader gives the text a cell shows, each writer reads what was typed and
 * returns the prescription unchanged when it cannot.
 */
import { formatClock, parseClock, type RowField } from "./liftRowFields";
import type { WorkoutCardioPrescription, WorkoutMobilityPrescription } from "./workoutTraining";

export type CardioMode = NonNullable<WorkoutCardioPrescription["mode"]>;

export const CARDIO_MODE_LABELS: Record<CardioMode, string> = {
  steady: "Steady",
  sprint_intervals: "Sprints",
  interval_template: "Interval Legs",
};

export function cardioMode(c: WorkoutCardioPrescription): CardioMode {
  return c.mode ?? "steady";
}

/** Switch mode, filling the new mode's fields with sensible starting values. */
export function setCardioMode(c: WorkoutCardioPrescription, mode: CardioMode): WorkoutCardioPrescription {
  if (mode === cardioMode(c)) return c;
  if (mode === "sprint_intervals") {
    return { ...c, mode, rounds: c.rounds ?? 10, workSeconds: c.workSeconds ?? 60, restSeconds: c.restSeconds ?? 60 };
  }
  if (mode === "interval_template") {
    return {
      ...c,
      mode,
      outerRounds: c.outerRounds ?? 3,
      legs: c.legs?.length ? c.legs : [{ workSeconds: 240, restSeconds: 240 }],
    };
  }
  return { ...c, mode: "steady", targetMinutes: c.targetMinutes ?? 10 };
}

function positiveInt(raw: string): number | null | undefined {
  const text = raw.trim();
  if (!text) return null;
  if (!/^\d+$/.test(text)) return undefined;
  const n = Number(text);
  return n > 0 ? n : undefined;
}

/** Steady: minutes. Sprints: rounds. Interval legs: rounds of the whole leg pattern. */
export function cardioAmountField(c: WorkoutCardioPrescription): RowField {
  switch (cardioMode(c)) {
    case "steady":
      return { text: c.targetMinutes ? String(c.targetMinutes) : "", editable: true, hint: "Minutes" };
    case "sprint_intervals":
      return { text: c.rounds ? String(c.rounds) : "", editable: true, hint: "Rounds" };
    case "interval_template":
      return { text: c.outerRounds ? String(c.outerRounds) : "", editable: true, hint: "Rounds" };
  }
}

export function applyCardioAmount(c: WorkoutCardioPrescription, raw: string): WorkoutCardioPrescription {
  const n = positiveInt(raw);
  if (n === undefined) return c;
  switch (cardioMode(c)) {
    case "steady":
      return { ...c, targetMinutes: n };
    case "sprint_intervals":
      return { ...c, rounds: n };
    case "interval_template":
      return { ...c, outerRounds: n };
  }
}

/** Work time of a sprint, or a summary of the legs (edited in the row's details). */
export function cardioWorkField(c: WorkoutCardioPrescription): RowField {
  const mode = cardioMode(c);
  if (mode === "sprint_intervals") return { text: formatClock(c.workSeconds), editable: true };
  if (mode === "interval_template") {
    const legs = c.legs?.length ?? 0;
    return { text: legs === 1 ? "1 leg" : `${legs} legs`, editable: false, hint: "Edit the legs in details" };
  }
  return { text: "", editable: false };
}

export function applyCardioWork(c: WorkoutCardioPrescription, raw: string): WorkoutCardioPrescription {
  if (cardioMode(c) !== "sprint_intervals") return c;
  const seconds = parseClock(raw);
  if (seconds === undefined) return c;
  return { ...c, workSeconds: seconds };
}

export function cardioRestField(c: WorkoutCardioPrescription): RowField {
  if (cardioMode(c) === "sprint_intervals") return { text: formatClock(c.restSeconds), editable: true };
  return { text: "", editable: false };
}

export function applyCardioRest(c: WorkoutCardioPrescription, raw: string): WorkoutCardioPrescription {
  if (cardioMode(c) !== "sprint_intervals") return c;
  const seconds = parseClock(raw);
  if (seconds === undefined) return c;
  return { ...c, restSeconds: seconds };
}

/** Heart rate target: "140" for one number, "130-150" for a range. */
export function cardioHeartRateField(c: WorkoutCardioPrescription): RowField {
  if (c.hrTargetMinBpm != null && c.hrTargetMaxBpm != null) {
    return { text: `${c.hrTargetMinBpm}-${c.hrTargetMaxBpm}`, editable: true };
  }
  if (c.hrTargetBpm != null) return { text: String(c.hrTargetBpm), editable: true };
  return { text: "", editable: true };
}

export function applyCardioHeartRate(c: WorkoutCardioPrescription, raw: string): WorkoutCardioPrescription {
  const text = raw.trim();
  if (!text) return { ...c, hrTargetBpm: null, hrTargetMinBpm: null, hrTargetMaxBpm: null };
  const single = text.match(/^(\d{2,3})$/);
  if (single) return { ...c, hrTargetBpm: Number(single[1]), hrTargetMinBpm: null, hrTargetMaxBpm: null };
  const range = text.match(/^(\d{2,3})\s*[-–]\s*(\d{2,3})$/);
  if (range) {
    const a = Number(range[1]);
    const b = Number(range[2]);
    if (a === b) return { ...c, hrTargetBpm: a, hrTargetMinBpm: null, hrTargetMaxBpm: null };
    return { ...c, hrTargetBpm: null, hrTargetMinBpm: Math.min(a, b), hrTargetMaxBpm: Math.max(a, b) };
  }
  return c;
}

/** Mobility: one hold for the stretch, or a hold per side. */
export function mobilityHoldField(m: WorkoutMobilityPrescription): RowField {
  return { text: formatClock(m.holdSeconds), editable: true };
}

export function applyMobilityHold(m: WorkoutMobilityPrescription, raw: string): WorkoutMobilityPrescription {
  const seconds = parseClock(raw);
  if (seconds === undefined) return m;
  return { ...m, holdSeconds: seconds };
}

export function mobilityPerSideField(m: WorkoutMobilityPrescription): RowField {
  return { text: formatClock(m.holdSecondsPerSide), editable: true };
}

export function applyMobilityPerSide(m: WorkoutMobilityPrescription, raw: string): WorkoutMobilityPrescription {
  const seconds = parseClock(raw);
  if (seconds === undefined) return m;
  return { ...m, holdSecondsPerSide: seconds };
}
