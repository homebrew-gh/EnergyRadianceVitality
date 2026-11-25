/**
 * Body weight read from the Android body tracker day logs (`erv/body_tracker/YYYY-MM-DD`,
 * kind 30078, payload `{ date, weightKg, measurementsCm, note, updatedAtEpochSeconds }`).
 * Read-only on the web companion; Android owns these documents.
 */

import type { AppDataRecord } from "./api";

export type BodyWeightEntry = { date: string; weightKg: number };

const BODY_TRACKER_DAY_TAG = /^erv\/body_tracker\/(\d{4}-\d{2}-\d{2})$/;

export function parseBodyWeightLogs(records: AppDataRecord[]): BodyWeightEntry[] {
  const byDate = new Map<string, BodyWeightEntry>();
  for (const record of records) {
    const match = BODY_TRACKER_DAY_TAG.exec(record.d_tag?.trim() ?? "");
    if (!match || !record.plaintext) continue;
    try {
      const payload = JSON.parse(record.plaintext) as { weightKg?: unknown };
      const weightKg = payload.weightKg;
      if (typeof weightKg === "number" && Number.isFinite(weightKg) && weightKg > 0) {
        byDate.set(match[1], { date: match[1], weightKg });
      }
    } catch {
      continue;
    }
  }
  return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
}
