/**
 * Wellness logs from the Android app, read-only on the web: Hot + Cold (`erv/sauna/<date>`,
 * `erv/cold/<date>`), light therapy (`erv/light/<date>`), supplements (`erv/supplements/<date>`),
 * fasting (`erv/fasting/<date>`) and body tracker measurements (`erv/body_tracker/<date>` plus
 * `erv/body_tracker/settings`). All kind 30078; Android owns these documents.
 */

import type { AppDataRecord } from "./api";
import type { BodyWeightUnit } from "./fitnessEquipment";

const DATE = "(\\d{4}-\\d{2}-\\d{2})";
const SAUNA_TAG = new RegExp(`^erv/sauna/${DATE}$`);
const COLD_TAG = new RegExp(`^erv/cold/${DATE}$`);
const LIGHT_TAG = new RegExp(`^erv/light/${DATE}$`);
const SUPPLEMENT_TAG = new RegExp(`^erv/supplements/${DATE}$`);
const FASTING_TAG = new RegExp(`^erv/fasting/${DATE}$`);
const BODY_TAG = new RegExp(`^erv/body_tracker/${DATE}$`);
const BODY_SETTINGS_TAG = "erv/body_tracker/settings";

const CM_PER_INCH = 2.54;

type TempUnit = "FAHRENHEIT" | "CELSIUS";

export type HeatColdTotals = {
  sessions: number;
  minutes: number;
  /** Average logged temperature in [tempUnit], or null when none was logged. */
  averageTemp: number | null;
  tempUnit: "°F" | "°C" | null;
};

export type WellnessSummary = {
  periodStart: string | null;
  periodEnd: string;
  /** Days in the period, counted from the first wellness log when the period is all time. */
  periodDays: number;
  sauna: HeatColdTotals;
  cold: HeatColdTotals;
  contrastSessions: number;
  light: { sessions: number; minutes: number };
  supplements: { daysLogged: number; intakes: number };
  fasting: { completed: number; averageHours: number | null; longestHours: number | null };
  /** Latest waist and the change over the period, in the body tracker length unit. */
  waist: { unit: "in" | "cm"; latest: number; latestDate: string; change: number | null } | null;
};

type HeatColdSession = {
  id?: string;
  durationSeconds?: number;
  tempValue?: number | null;
  tempUnit?: TempUnit | null;
  contrastId?: string | null;
};

function parse(record: AppDataRecord): Record<string, unknown> | null {
  if (!record.plaintext) return null;
  try {
    const value = JSON.parse(record.plaintext) as unknown;
    return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function asArray(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter((v): v is Record<string, unknown> => !!v && typeof v === "object") : [];
}

function num(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

function daysBetweenInclusive(start: string, end: string): number {
  const a = Date.parse(`${start}T00:00:00Z`);
  const b = Date.parse(`${end}T00:00:00Z`);
  if (!Number.isFinite(a) || !Number.isFinite(b) || b < a) return 1;
  return Math.round((b - a) / 86_400_000) + 1;
}

function heatColdTotals(sessions: HeatColdSession[]): HeatColdTotals {
  const seconds = sessions.reduce((n, s) => n + Math.max(0, num(s.durationSeconds) ?? 0), 0);
  const withTemp = sessions.filter((s) => num(s.tempValue) != null && s.tempUnit);
  // Show temperatures in the unit of the newest logged one; convert the rest.
  const unit: TempUnit | null = withTemp.length > 0 ? (withTemp[withTemp.length - 1].tempUnit as TempUnit) : null;
  const temps = withTemp.map((s) => {
    const v = s.tempValue as number;
    if (s.tempUnit === unit) return v;
    return unit === "CELSIUS" ? ((v - 32) * 5) / 9 : (v * 9) / 5 + 32;
  });
  return {
    sessions: sessions.length,
    minutes: round1(seconds / 60),
    averageTemp: temps.length > 0 ? Math.round(temps.reduce((a, b) => a + b, 0) / temps.length) : null,
    tempUnit: unit == null ? null : unit === "CELSIUS" ? "°C" : "°F",
  };
}

/**
 * Totals for the wellness logs dated from [periodStart] (null = all time) to [today].
 * [weightUnit] picks inches or centimeters for the waist when the phone never chose one.
 */
export function buildWellnessSummary(
  records: AppDataRecord[],
  options: { today: string; periodStart: string | null; weightUnit: BodyWeightUnit },
): WellnessSummary {
  const { today, periodStart } = options;
  const inPeriod = (date: string) => date <= today && (periodStart == null || date >= periodStart);

  const sauna: { date: string; s: HeatColdSession }[] = [];
  const cold: { date: string; s: HeatColdSession }[] = [];
  let lightSessions = 0;
  let lightMinutes = 0;
  const supplementDays = new Set<string>();
  let intakes = 0;
  const fastHours: number[] = [];
  const waistByDate = new Map<string, number>();
  let lengthUnit: "in" | "cm" = options.weightUnit === "KG" ? "cm" : "in";
  let earliest: string | null = null;
  const seen = (date: string) => {
    if (earliest == null || date < earliest) earliest = date;
  };

  for (const record of records) {
    const tag = record.d_tag?.trim() ?? "";
    if (tag === BODY_SETTINGS_TAG) {
      const payload = parse(record);
      const picked = num(payload?.updatedAtEpochSeconds) ?? 0;
      if (payload && picked > 0) lengthUnit = payload.lengthUnit === "CENTIMETERS" ? "cm" : "in";
      continue;
    }
    let match: RegExpExecArray | null;
    if ((match = SAUNA_TAG.exec(tag)) || (match = COLD_TAG.exec(tag))) {
      const date = match[1];
      if (!inPeriod(date)) continue;
      const payload = parse(record);
      const sessions = asArray(payload?.sessions) as HeatColdSession[];
      if (sessions.length > 0) seen(date);
      const target = tag.startsWith("erv/sauna/") ? sauna : cold;
      for (const s of sessions) target.push({ date, s });
    } else if ((match = LIGHT_TAG.exec(tag))) {
      const date = match[1];
      if (!inPeriod(date)) continue;
      const sessions = asArray(parse(record)?.sessions);
      if (sessions.length > 0) seen(date);
      lightSessions += sessions.length;
      lightMinutes += sessions.reduce((n, s) => n + Math.max(0, num(s.minutes) ?? 0), 0);
    } else if ((match = SUPPLEMENT_TAG.exec(tag))) {
      const date = match[1];
      if (!inPeriod(date)) continue;
      const payload = parse(record);
      const runs = asArray(payload?.routineRuns);
      const count =
        runs.reduce((n, run) => n + Math.max(1, asArray(run.stepIntakes).length), 0) +
        asArray(payload?.adHocIntakes).length;
      if (count > 0) {
        supplementDays.add(date);
        intakes += count;
        seen(date);
      }
    } else if ((match = FASTING_TAG.exec(tag))) {
      const date = match[1];
      if (!inPeriod(date)) continue;
      for (const s of asArray(parse(record)?.sessions)) {
        if (s.status !== "COMPLETED") continue;
        const start = num(s.startedAtEpochSeconds);
        const end = num(s.endedAtEpochSeconds);
        if (start == null || end == null || end <= start) continue;
        fastHours.push((end - start) / 3600);
        seen(date);
      }
    } else if ((match = BODY_TAG.exec(tag))) {
      const date = match[1];
      if (!inPeriod(date)) continue;
      const measurements = parse(record)?.measurementsCm as Record<string, unknown> | undefined;
      const waistCm = num(measurements?.WAIST);
      if (waistCm != null && waistCm > 0) waistByDate.set(date, waistCm);
    }
  }

  const contrastIds = new Set(
    [...sauna, ...cold].map((r) => r.s.contrastId).filter((id): id is string => typeof id === "string" && id !== ""),
  );
  const waistDates = [...waistByDate.keys()].sort();
  const toLength = (cm: number) => round1(lengthUnit === "in" ? cm / CM_PER_INCH : cm);
  const waist =
    waistDates.length === 0
      ? null
      : {
          unit: lengthUnit,
          latest: toLength(waistByDate.get(waistDates[waistDates.length - 1])!),
          latestDate: waistDates[waistDates.length - 1],
          change:
            waistDates.length >= 2
              ? round1(toLength(waistByDate.get(waistDates[waistDates.length - 1])!) - toLength(waistByDate.get(waistDates[0])!))
              : null,
        };

  const start = periodStart ?? earliest ?? today;
  return {
    periodStart,
    periodEnd: today,
    periodDays: daysBetweenInclusive(start, today),
    sauna: heatColdTotals(sauna.sort((a, b) => a.date.localeCompare(b.date)).map((r) => r.s)),
    cold: heatColdTotals(cold.sort((a, b) => a.date.localeCompare(b.date)).map((r) => r.s)),
    contrastSessions: contrastIds.size,
    light: { sessions: lightSessions, minutes: lightMinutes },
    supplements: { daysLogged: supplementDays.size, intakes },
    fasting: {
      completed: fastHours.length,
      averageHours: fastHours.length > 0 ? round1(fastHours.reduce((a, b) => a + b, 0) / fastHours.length) : null,
      longestHours: fastHours.length > 0 ? round1(Math.max(...fastHours)) : null,
    },
    waist,
  };
}

export function wellnessHasData(summary: WellnessSummary): boolean {
  return (
    summary.sauna.sessions > 0 ||
    summary.cold.sessions > 0 ||
    summary.light.sessions > 0 ||
    summary.supplements.daysLogged > 0 ||
    summary.fasting.completed > 0 ||
    summary.waist != null
  );
}

/** The coach's view: only the parts with data, in plain numbers. */
export function wellnessForCoach(summary: WellnessSummary): Record<string, unknown> | null {
  if (!wellnessHasData(summary)) return null;
  const out: Record<string, unknown> = { periodDays: summary.periodDays };
  const heatCold = (t: HeatColdTotals) => ({
    sessions: t.sessions,
    minutes: t.minutes,
    ...(t.averageTemp != null ? { averageTemp: `${t.averageTemp} ${t.tempUnit}` } : {}),
  });
  if (summary.sauna.sessions > 0) out.sauna = heatCold(summary.sauna);
  if (summary.cold.sessions > 0) out.coldPlunge = heatCold(summary.cold);
  if (summary.contrastSessions > 0) out.contrastSessions = summary.contrastSessions;
  if (summary.light.sessions > 0) out.lightTherapy = summary.light;
  if (summary.supplements.daysLogged > 0) out.supplementDaysLogged = summary.supplements.daysLogged;
  if (summary.fasting.completed > 0) out.fasting = summary.fasting;
  if (summary.waist) {
    out.waist = {
      latest: `${summary.waist.latest} ${summary.waist.unit}`,
      ...(summary.waist.change != null ? { changeOverPeriod: `${summary.waist.change} ${summary.waist.unit}` } : {}),
    };
  }
  return out;
}
