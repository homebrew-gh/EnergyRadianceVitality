/**
 * Distance and pace targets on cardio rows. Stored in meters and seconds per kilometer (the
 * shared contract with the phone); shown and typed in the athlete's unit, miles unless they
 * weigh in kilograms.
 */
import type { BodyWeightUnit } from "./fitnessEquipment";

export type CardioDistanceUnit = "MI" | "KM";

export const METERS_PER_MILE = 1609.344;

export function cardioDistanceUnitFor(weightUnit: BodyWeightUnit | null | undefined): CardioDistanceUnit {
  return weightUnit === "KG" ? "KM" : "MI";
}

export function distanceUnitSuffix(unit: CardioDistanceUnit): string {
  return unit === "KM" ? "km" : "mi";
}

function trimNumber(value: number, places: number): string {
  return value.toFixed(places).replace(/\.?0+$/, "");
}

/** "1", "0.25", "3.1" in the unit; meters under a kilometer show as "400 m". */
export function formatCardioDistance(meters: number | null | undefined, unit: CardioDistanceUnit): string {
  if (meters == null || !(meters > 0)) return "";
  if (unit === "KM" && meters < 1000) return `${Math.round(meters)} m`;
  const value = unit === "KM" ? meters / 1000 : meters / METERS_PER_MILE;
  return trimNumber(value, 2);
}

/**
 * Typed distance to meters: a plain number is in [unit]; "400m", "5k", "5 km", "2 mi" name their
 * own. Empty clears (null); anything else is undefined.
 */
export function parseCardioDistance(raw: string, unit: CardioDistanceUnit): number | null | undefined {
  const text = raw.trim().toLowerCase();
  if (!text) return null;
  const match = text.match(/^(\d+(?:\.\d+)?|\.\d+)\s*(m|k|km|mi|mile|miles)?$/);
  if (!match) return undefined;
  const value = Number(match[1]);
  if (!(value > 0)) return undefined;
  const suffix = match[2];
  let meters: number;
  if (suffix === "m") meters = value;
  else if (suffix === "k" || suffix === "km") meters = value * 1000;
  else if (suffix === "mi" || suffix === "mile" || suffix === "miles") meters = value * METERS_PER_MILE;
  else meters = unit === "KM" ? value * 1000 : value * METERS_PER_MILE;
  return Math.round(meters * 10) / 10;
}

function paceClock(seconds: number): string {
  const s = Math.round(seconds);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

function parsePaceClock(text: string): number | undefined {
  const match = text.trim().match(/^(\d{1,2}):([0-5]\d)$/);
  if (!match) return undefined;
  const seconds = Number(match[1]) * 60 + Number(match[2]);
  return seconds > 0 ? seconds : undefined;
}

const perUnit = (unit: CardioDistanceUnit) => (unit === "KM" ? 1 : METERS_PER_MILE / 1000);

/** A pace band as typed: "9:30-10:00" per mile (or km), or one pace "9:45". */
export function formatPaceBand(
  fastSecondsPerKm: number | null | undefined,
  slowSecondsPerKm: number | null | undefined,
  unit: CardioDistanceUnit,
): string {
  const factor = perUnit(unit);
  const fast = fastSecondsPerKm != null && fastSecondsPerKm > 0 ? paceClock(fastSecondsPerKm * factor) : null;
  const slow = slowSecondsPerKm != null && slowSecondsPerKm > 0 ? paceClock(slowSecondsPerKm * factor) : null;
  if (fast && slow) return fast === slow ? fast : `${fast}-${slow}`;
  return fast ?? slow ?? "";
}

/**
 * Typed pace band to seconds per km, fastest first. Empty clears both; one pace gives a band of
 * that pace alone. Undefined when it can't be read.
 */
export function parsePaceBand(
  raw: string,
  unit: CardioDistanceUnit,
): { fast: number | null; slow: number | null } | undefined {
  const text = raw.trim();
  if (!text) return { fast: null, slow: null };
  const parts = text.split(/\s*[-–]\s*/);
  if (parts.length > 2) return undefined;
  const values = parts.map(parsePaceClock);
  if (values.some((v) => v === undefined)) return undefined;
  const factor = perUnit(unit);
  const perKm = (values as number[]).map((v) => Math.round((v / factor) * 10) / 10);
  const fast = Math.min(...perKm);
  const slow = Math.max(...perKm);
  return { fast, slow };
}
