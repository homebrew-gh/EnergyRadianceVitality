/** Display text for fitness standard values, targets and gaps, in the athlete's units. */
import { formatClock } from "./cardioProgress";
import { kgToLb, type BodyWeightUnit } from "./fitnessEquipment";
import type { StandardEvent, StandardEventResult, StandardTierId } from "./standards";
import { STANDARD_TIERS } from "./standards";

const INCH_CM = 2.54;
const FOOT_M = 0.3048;

export function tierLabel(tier: StandardTierId | null): string {
  return tier ? STANDARD_TIERS.find((t) => t.id === tier)?.label ?? tier : "Below Base";
}

function trim(n: number, digits: number): string {
  return String(Math.round(n * 10 ** digits) / 10 ** digits);
}

function load(kg: number, unit: BodyWeightUnit): string {
  return unit === "KG" ? `${Math.round(kg)} kg` : `${Math.round(kgToLb(kg))} lb`;
}

/** A value or target of [event] in its own unit, e.g. "1.5x BW", "12 reps", "3:15", "+12 in". */
export function formatStandardValue(event: StandardEvent, value: number, unit: BodyWeightUnit): string {
  const m = event.measure;
  switch (m.kind) {
    case "LIFT_MAX_BW":
      return `${trim(value, 2)}x BW`;
    case "LIFT_MAX_LOAD":
      return load(value, unit);
    case "MAX_REPS":
    case "REPS_AT_BW":
      return `${Math.round(value)} ${Math.round(value) === 1 ? "rep" : "reps"}`;
    case "LONGEST_HOLD":
    case "FASTEST":
      return formatClock(value);
    case "MANUAL":
      switch (m.unit) {
        case "SECONDS":
          return formatClock(value);
        case "REPS":
          return `${Math.round(value)} reps`;
        case "METERS":
          return unit === "KG" ? `${Math.round(value)} m` : `${Math.round(value / FOOT_M)} ft`;
        case "CM_OVER_HEIGHT": {
          const shown = unit === "KG" ? Math.round(value) : Math.round(value / INCH_CM);
          const suffix = unit === "KG" ? "cm" : "in";
          if (shown === 0) return "Your height";
          return `Height ${shown > 0 ? "+" : "−"} ${Math.abs(shown)} ${suffix}`;
        }
      }
  }
}

/**
 * How far from the next tier, e.g. "0.10x BW to Strong (about 20 lb)", "3 reps to Elite",
 * "0:12 faster to Strong". Null at Elite or with nothing scored.
 */
export function formatGap(
  result: StandardEventResult,
  unit: BodyWeightUnit,
  latestBodyWeightKg: number | null,
): string | null {
  if (result.value == null || result.nextTier == null || result.gapToNext == null) return null;
  const to = tierLabel(result.nextTier);
  const m = result.event.measure;
  const gap = result.gapToNext;
  switch (m.kind) {
    case "LIFT_MAX_BW": {
      const extra = latestBodyWeightKg ? ` (about ${load(gap * latestBodyWeightKg, unit)})` : "";
      return `${trim(Math.max(gap, 0.01), 2)}x BW to ${to}${extra}`;
    }
    case "LIFT_MAX_LOAD":
      return `${load(gap, unit)} to ${to}`;
    case "MAX_REPS":
    case "REPS_AT_BW": {
      const reps = Math.max(1, Math.ceil(gap - 1e-6));
      return `${reps} ${reps === 1 ? "rep" : "reps"} to ${to}`;
    }
    case "LONGEST_HOLD":
      return `${formatClock(Math.max(1, gap))} longer to ${to}`;
    case "FASTEST":
      return `${formatClock(Math.max(1, gap))} faster to ${to}`;
    case "MANUAL":
      if (m.unit === "SECONDS") {
        return `${formatClock(Math.max(1, gap))} ${result.event.lowerIsBetter ? "faster" : "longer"} to ${to}`;
      }
      if (m.unit === "CM_OVER_HEIGHT") {
        const n = unit === "KG" ? Math.ceil(gap) : Math.ceil(gap / INCH_CM);
        return `${n} ${unit === "KG" ? "cm" : "in"} to ${to}`;
      }
      if (m.unit === "METERS") {
        return unit === "KG" ? `${Math.ceil(gap)} m to ${to}` : `${Math.ceil(gap / FOOT_M)} ft to ${to}`;
      }
      return `${Math.ceil(gap)} to ${to}`;
  }
}

export function sourceLabel(result: StandardEventResult): string | null {
  switch (result.source) {
    case "ESTIMATED":
      return "Est.";
    case "LOGGED":
      return "Logged";
    case "TESTED":
      return "Tested";
    default:
      return null;
  }
}

/** Why an event has no value yet. */
export function missingLabel(result: StandardEventResult): string | null {
  switch (result.missing) {
    case "BODYWEIGHT":
      return "Needs a weigh-in";
    case "HEIGHT":
      return "Add your height on Profile";
    case "RESULT":
      return result.event.measure.kind === "MANUAL" ? "Test result needed" : "Nothing logged yet";
    default:
      return null;
  }
}
