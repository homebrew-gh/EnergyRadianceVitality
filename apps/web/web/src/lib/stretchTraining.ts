/** Matches Android StretchingSync — `erv/stretching/routines`. */

export const STRETCHING_ROUTINES_D_TAG = "erv/stretching/routines";

export type StretchRoutine = {
  id: string;
  name: string;
  stretchIds: string[];
  holdSecondsPerStretch: number;
};

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
