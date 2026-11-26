/** Matches Android StretchingSync — `erv/stretching/routines`. */

export const STRETCHING_ROUTINES_D_TAG = "erv/stretching/routines";

export type StretchRoutine = {
  id: string;
  name: string;
  stretchIds: string[];
  holdSecondsPerStretch: number;
  /** Optional hold for particular stretches, by catalog id; others use holdSecondsPerStretch. */
  holdSecondsByStretchId?: Record<string, number>;
  /** Seconds between different stretches in the guided player; null/missing keeps the default. */
  gapSeconds?: number | null;
};

export const STRETCH_HOLD_MIN_SECONDS = 5;
export const STRETCH_HOLD_MAX_SECONDS = 300;
export const STRETCH_GAP_DEFAULT_SECONDS = 10;
export const STRETCH_GAP_MIN_SECONDS = 5;
export const STRETCH_GAP_MAX_SECONDS = 60;
/** Matches Android: switching sides of one stretch is always this short. */
export const STRETCH_SIDE_SWITCH_SECONDS = 5;
/** Matches Android: settling in before the first hold. */
export const STRETCH_FIRST_PREP_SECONDS = 10;

const clamp = (n: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, n));

/** The hold for one stretch in a routine, like Android's StretchRoutine.holdSecondsFor. */
export function stretchHoldFor(routine: StretchRoutine, stretchId: string): number {
  const own = routine.holdSecondsByStretchId?.[stretchId];
  const base = typeof own === "number" && own > 0 ? own : routine.holdSecondsPerStretch;
  return clamp(Math.round(base || 30), STRETCH_HOLD_MIN_SECONDS, STRETCH_HOLD_MAX_SECONDS);
}

export function stretchGapFor(routine: StretchRoutine): number {
  const g = routine.gapSeconds;
  return clamp(
    typeof g === "number" ? Math.round(g) : STRETCH_GAP_DEFAULT_SECONDS,
    STRETCH_GAP_MIN_SECONDS,
    STRETCH_GAP_MAX_SECONDS,
  );
}

/** Keeps only per-stretch times for stretches still in the routine that differ from its default. */
export function cleanedHoldOverrides(
  overrides: Record<string, number>,
  stretchIds: string[],
  defaultHold: number,
): Record<string, number> {
  const out: Record<string, number> = {};
  for (const id of stretchIds) {
    const v = overrides[id];
    if (typeof v !== "number" || !(v > 0)) continue;
    const c = clamp(Math.round(v), STRETCH_HOLD_MIN_SECONDS, STRETCH_HOLD_MAX_SECONDS);
    if (c !== defaultHold) out[id] = c;
  }
  return out;
}

/** Planned minutes for the guided player, matching Android's guidedSessionTotalMinutes. */
export function stretchRoutineMinutes(
  routine: StretchRoutine,
  catalog: { id: string; requiresBothSides?: boolean }[],
): number {
  const steps: { id: string; side: "right" | "left" | null; hold: number }[] = [];
  for (const id of routine.stretchIds) {
    const hold = stretchHoldFor(routine, id);
    const both = catalog.find((e) => e.id === id)?.requiresBothSides === true;
    if (both) {
      steps.push({ id, side: "right", hold }, { id, side: "left", hold });
    } else {
      steps.push({ id, side: null, hold });
    }
  }
  if (steps.length === 0) return 0;
  const gap = stretchGapFor(routine);
  let total = STRETCH_FIRST_PREP_SECONDS;
  steps.forEach((s, i) => {
    total += s.hold;
    if (i > 0) {
      const prev = steps[i - 1];
      const sideSwitch = prev.id === s.id && prev.side === "right" && s.side === "left";
      total += sideSwitch ? STRETCH_SIDE_SWITCH_SECONDS : gap;
    }
  });
  return Math.max(1, Math.ceil(total / 60));
}

export type StretchRoutinesPayload = {
  routines: StretchRoutine[];
};

export function parseStretchRoutinesPayload(raw: string): StretchRoutine[] {
  const parsed = JSON.parse(raw) as StretchRoutinesPayload;
  return Array.isArray(parsed.routines) ? parsed.routines : [];
}

export function stretchRoutinesPayload(routines: StretchRoutine[]): string {
  return JSON.stringify({ routines } satisfies StretchRoutinesPayload);
}

export function stretchLabel(
  stretchId: string,
  catalog: { id: string; name: string }[],
): string {
  return catalog.find((e) => e.id === stretchId)?.name ?? stretchId;
}
