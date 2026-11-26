/**
 * Logged fitness standard test results, kind 30078 `erv/standards/results` (replaceable).
 *
 * Each result keeps the raw measurement, so it stays right if bodyweight or height changes:
 * - lift events: the load in kg (LIFT_MAX_BW divides by bodyweight on the day when scored)
 * - rep events: reps
 * - holds, runs, timed tests: seconds
 * - carries: meters
 * - broad jump: the full jump in cm (scored against height)
 *
 * Deletes travel as `deletedResults` (id → epoch seconds) so a second writer can merge by id.
 */
import type { StandardTestResult } from "./standards";

export const STANDARD_RESULTS_D_TAG = "erv/standards/results";

export type StoredStandardResult = StandardTestResult & {
  id: string;
  note?: string | null;
  lastModifiedEpochSeconds: number;
};

export type StandardResultsPayload = {
  ervStandardResultsVersion: 1;
  results: StoredStandardResult[];
  deletedResults: Record<string, number>;
  lastModifiedEpochSeconds: number;
};

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export function emptyStandardResults(): StandardResultsPayload {
  return { ervStandardResultsVersion: 1, results: [], deletedResults: {}, lastModifiedEpochSeconds: 0 };
}

function parseResult(raw: unknown): StoredStandardResult | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as Record<string, unknown>;
  const str = (v: unknown) => (typeof v === "string" && v.trim() ? v.trim() : null);
  const id = str(r.id);
  const standardId = str(r.standardId);
  const eventId = str(r.eventId);
  const date = str(r.date);
  const value = typeof r.value === "number" && Number.isFinite(r.value) && r.value > 0 ? r.value : null;
  if (!id || !standardId || !eventId || !date || !ISO_DATE.test(date) || value == null) return null;
  return {
    id,
    standardId,
    eventId,
    value,
    date,
    note: str(r.note),
    lastModifiedEpochSeconds:
      typeof r.lastModifiedEpochSeconds === "number" ? Math.max(0, Math.floor(r.lastModifiedEpochSeconds)) : 0,
  };
}

export function parseStandardResults(raw: string | null | undefined): StandardResultsPayload {
  if (!raw) return emptyStandardResults();
  try {
    const data = JSON.parse(raw) as Record<string, unknown>;
    const deleted: Record<string, number> = {};
    if (data.deletedResults && typeof data.deletedResults === "object") {
      for (const [id, at] of Object.entries(data.deletedResults as Record<string, unknown>)) {
        if (typeof at === "number" && Number.isFinite(at)) deleted[id] = Math.floor(at);
      }
    }
    const results = (Array.isArray(data.results) ? data.results : [])
      .map(parseResult)
      .filter((r): r is StoredStandardResult => r != null)
      .filter((r) => !(r.id in deleted) || r.lastModifiedEpochSeconds > deleted[r.id]);
    return {
      ervStandardResultsVersion: 1,
      results: sortResults(results),
      deletedResults: deleted,
      lastModifiedEpochSeconds:
        typeof data.lastModifiedEpochSeconds === "number" ? Math.max(0, Math.floor(data.lastModifiedEpochSeconds)) : 0,
    };
  } catch {
    return emptyStandardResults();
  }
}

/** Newest first. */
function sortResults(results: StoredStandardResult[]): StoredStandardResult[] {
  return [...results].sort((a, b) => b.date.localeCompare(a.date) || b.lastModifiedEpochSeconds - a.lastModifiedEpochSeconds);
}

const YEAR_SECONDS = 365 * 24 * 3600;

export function serializeStandardResults(payload: StandardResultsPayload, nowEpochSeconds: number): string {
  const deleted: Record<string, number> = {};
  for (const [id, at] of Object.entries(payload.deletedResults)) {
    if (nowEpochSeconds - at < YEAR_SECONDS) deleted[id] = at;
  }
  return JSON.stringify({
    ervStandardResultsVersion: 1,
    results: sortResults(payload.results).map((r) => ({
      id: r.id,
      standardId: r.standardId,
      eventId: r.eventId,
      value: r.value,
      date: r.date,
      ...(r.note ? { note: r.note } : {}),
      lastModifiedEpochSeconds: r.lastModifiedEpochSeconds,
    })),
    deletedResults: deleted,
    lastModifiedEpochSeconds: nowEpochSeconds,
  });
}

export function addStandardResult(
  payload: StandardResultsPayload,
  result: Omit<StoredStandardResult, "lastModifiedEpochSeconds">,
  nowEpochSeconds: number,
): StandardResultsPayload {
  return {
    ...payload,
    results: sortResults([...payload.results, { ...result, lastModifiedEpochSeconds: nowEpochSeconds }]),
    lastModifiedEpochSeconds: nowEpochSeconds,
  };
}

export function removeStandardResult(
  payload: StandardResultsPayload,
  id: string,
  nowEpochSeconds: number,
): StandardResultsPayload {
  return {
    ...payload,
    results: payload.results.filter((r) => r.id !== id),
    deletedResults: { ...payload.deletedResults, [id]: nowEpochSeconds },
    lastModifiedEpochSeconds: nowEpochSeconds,
  };
}

/** "1:45", "12:30", "1:02:15" or plain seconds → seconds; null when it does not parse. */
export function parseClockInput(raw: string): number | null {
  const t = raw.trim();
  if (!t) return null;
  if (/^\d+(\.\d+)?$/.test(t)) {
    const n = Number(t);
    return n > 0 ? n : null;
  }
  const parts = t.split(":");
  if (parts.length < 2 || parts.length > 3 || parts.some((p) => !/^\d+$/.test(p))) return null;
  const nums = parts.map(Number);
  if (nums.slice(1).some((n) => n >= 60)) return null;
  const seconds = nums.reduce((acc, n) => acc * 60 + n, 0);
  return seconds > 0 ? seconds : null;
}
