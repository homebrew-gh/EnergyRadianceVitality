/**
 * Plate calculator: which plates to put on a bar for a target weight, using the bars and plates
 * stored in `erv/equipment`.
 *
 * Keep this in step with the Android copy (`com.erv.app.weighttraining.PlateCalculator`). Both are
 * checked against `shared/test-cases/plate_calculator.json`, so the builder and the phone always show
 * the same plates.
 *
 * All arithmetic runs in the unit you lift in, in whole hundredths. Weights are stored in kg, and a
 * 45 lb plate is 20.4117 kg, so matching on stored kg would miss.
 */
import {
  kgToLb,
  type BarbellOwnership,
  type BodyWeightUnit,
  type OwnedEquipmentItem,
  type StandardBarbellType,
} from "./fitnessEquipment";

/** How plates go on: both sleeves, one end (landmine, T-bar row), or not loaded at all (rollout). */
export type PlateLoadingStyle = "BOTH_ENDS" | "ONE_END" | "NONE";

/** Plates of one weight, in the unit you lift in. A pair is one plate for each sleeve. */
export type PlateStack = { weight: number; pairCount: number };

/** A loadable total and its plates, per side (or on the loaded end), heaviest first. */
export type PlateLoadOption = { total: number; plates: number[] };

export type PlateLoadResult = {
  /** Plates that make the target exactly, or null when it cannot be loaded. */
  exact: PlateLoadOption | null;
  /** Nearest loadable total below the target. */
  below: PlateLoadOption | null;
  /** Nearest loadable total above the target. */
  above: PlateLoadOption | null;
};

const EMPTY_RESULT: PlateLoadResult = { exact: null, below: null, above: null };

const toHundredths = (v: number) => Math.round(v * 100);
const fromHundredths = (v: number) => v / 100;

type Combo = { counts: number[]; plateCount: number };

/** Fewer plates wins; on a tie, more of the heavier plates wins. */
function isBetter(a: Combo, b: Combo): boolean {
  if (a.plateCount !== b.plateCount) return a.plateCount < b.plateCount;
  for (let i = 0; i < a.counts.length; i++) {
    if (a.counts[i] !== b.counts[i]) return a.counts[i]! > b.counts[i]!;
  }
  return false;
}

/**
 * Bounded search over every combination of the plates you own (not largest-first, which gets
 * stuck on a real inventory). Prefers an exact match, then the fewest plates.
 */
export function calculatePlateLoad(input: {
  target: number;
  bar: number;
  plates: PlateStack[];
  loading?: PlateLoadingStyle;
}): PlateLoadResult {
  const loading = input.loading ?? "BOTH_ENDS";
  if (loading === "NONE" || !Number.isFinite(input.target) || input.target <= 0) return EMPTY_RESULT;

  const merged = new Map<number, number>();
  for (const stack of input.plates) {
    const w = toHundredths(stack.weight);
    const pairs = Math.floor(stack.pairCount);
    if (w <= 0 || pairs <= 0) continue;
    merged.set(w, (merged.get(w) ?? 0) + pairs);
  }
  const weights = [...merged.keys()].sort((a, b) => b - a);
  const maxCounts = weights.map((w) => (loading === "ONE_END" ? 2 : 1) * merged.get(w)!);
  const sides = loading === "ONE_END" ? 1 : 2;

  let reachable = new Map<number, Combo>([[0, { counts: weights.map(() => 0), plateCount: 0 }]]);
  weights.forEach((w, i) => {
    const next = new Map<number, Combo>();
    for (const [sum, combo] of reachable) {
      for (let k = 0; k <= maxCounts[i]!; k++) {
        const counts = combo.counts.slice();
        counts[i] = k;
        const candidate = { counts, plateCount: combo.plateCount + k };
        const key = sum + k * w;
        const existing = next.get(key);
        if (!existing || isBetter(candidate, existing)) next.set(key, candidate);
      }
    }
    reachable = next;
  });

  const bar = toHundredths(input.bar);
  const target = toHundredths(input.target);
  const option = (sum: number, combo: Combo): PlateLoadOption => ({
    total: fromHundredths(bar + sides * sum),
    plates: weights.flatMap((w, i) => Array<number>(combo.counts[i]!).fill(fromHundredths(w))),
  });

  let exact: PlateLoadOption | null = null;
  let below: { total: number; sum: number } | null = null;
  let above: { total: number; sum: number } | null = null;
  for (const [sum, combo] of reachable) {
    const total = bar + sides * sum;
    if (total === target) exact = option(sum, combo);
    else if (total < target && (!below || total > below.total)) below = { total, sum };
    else if (total > target && (!above || total < above.total)) above = { total, sum };
  }
  return {
    exact,
    below: below ? option(below.sum, reachable.get(below.sum)!) : null,
    above: above ? option(above.sum, reachable.get(above.sum)!) : null,
  };
}

// --- Equipment -------------------------------------------------------------------------------

/** A bar you can pick in the calculator, with its weight in the unit you lift in. */
export type PlateCalculatorBar = {
  id: string;
  label: string;
  weight: number;
  barType: StandardBarbellType;
};

/**
 * What lifters count each bar as. An Olympic bar is stored as 20 kg (44.1 lb) but is counted as
 * 45 lb, so pound answers use these nominal values (matching the bar labels) instead of converting.
 */
const NOMINAL_BAR_LB: Record<Exclude<StandardBarbellType, "CUSTOM">, number> = {
  OLYMPIC_MENS: 45,
  OLYMPIC_WOMENS: 33,
  TRAINING_10KG: 25,
  EZ_CURL: 18,
  TRAP_HEX_20: 45,
  TRAP_HEX_25: 55,
  OPEN_TRAP_BAR: 55,
  STANDARD_1INCH: 15,
};

const NOMINAL_BAR_KG: Record<Exclude<StandardBarbellType, "CUSTOM">, number> = {
  OLYMPIC_MENS: 20,
  OLYMPIC_WOMENS: 15,
  TRAINING_10KG: 10,
  EZ_CURL: 8,
  TRAP_HEX_20: 20,
  TRAP_HEX_25: 25,
  OPEN_TRAP_BAR: 25,
  STANDARD_1INCH: 7,
};

/** A stored kg weight in the unit you lift in, to the hundredth (so 20.4117 kg reads 45 lb). */
export function nominalWeight(kg: number, unit: BodyWeightUnit): number {
  return fromHundredths(toHundredths(unit === "LB" ? kgToLb(kg) : kg));
}

export function barbellNominalWeight(bar: BarbellOwnership, unit: BodyWeightUnit): number {
  if (bar.barType === "CUSTOM") {
    if (bar.customWeightKg && bar.customWeightKg > 0) return nominalWeight(bar.customWeightKg, unit);
    return unit === "LB" ? 45 : 20;
  }
  return (unit === "LB" ? NOMINAL_BAR_LB : NOMINAL_BAR_KG)[bar.barType];
}

const SHORT_BAR_LABEL: Record<Exclude<StandardBarbellType, "CUSTOM">, string> = {
  OLYMPIC_MENS: "Olympic bar",
  OLYMPIC_WOMENS: "Women's Olympic bar",
  TRAINING_10KG: "Training bar",
  EZ_CURL: "EZ curl bar",
  TRAP_HEX_20: "Trap bar",
  TRAP_HEX_25: "Trap bar",
  OPEN_TRAP_BAR: "Open trap bar",
  STANDARD_1INCH: 'Standard 1" bar',
};

function formatAmount(v: number): string {
  return String(fromHundredths(toHundredths(v)));
}

/** Bars from your equipment. When you have none, a standard Olympic bar is assumed. */
export function plateCalculatorBars(
  equipment: OwnedEquipmentItem[],
  unit: BodyWeightUnit,
): { bars: PlateCalculatorBar[]; assumed: boolean } {
  const suffix = unit === "LB" ? "lb" : "kg";
  const bars = equipment.flatMap((item): PlateCalculatorBar[] => {
    const b = item.barbell;
    if (item.catalogKind !== "BARBELL" || !b) return [];
    const weight = barbellNominalWeight(b, unit);
    const name =
      b.barType === "CUSTOM" ? b.customName?.trim() || "Custom bar" : SHORT_BAR_LABEL[b.barType];
    return [{ id: item.id, label: `${name} (${formatAmount(weight)} ${suffix})`, weight, barType: b.barType }];
  });
  if (bars.length > 0) return { bars, assumed: false };
  const weight = unit === "LB" ? 45 : 20;
  return {
    bars: [{ id: "standard-olympic", label: `Olympic bar (${weight} ${suffix})`, weight, barType: "OLYMPIC_MENS" }],
    assumed: true,
  };
}

/** A typical gym's plates, used when you have not entered any. */
const STANDARD_PLATES_LB: PlateStack[] = [
  { weight: 45, pairCount: 8 },
  { weight: 35, pairCount: 2 },
  { weight: 25, pairCount: 2 },
  { weight: 10, pairCount: 2 },
  { weight: 5, pairCount: 2 },
  { weight: 2.5, pairCount: 2 },
];

const STANDARD_PLATES_KG: PlateStack[] = [
  { weight: 25, pairCount: 8 },
  { weight: 20, pairCount: 2 },
  { weight: 15, pairCount: 2 },
  { weight: 10, pairCount: 2 },
  { weight: 5, pairCount: 2 },
  { weight: 2.5, pairCount: 2 },
  { weight: 1.25, pairCount: 2 },
];

/** Plates from your equipment, merged across entries. When you have none, a standard set is assumed. */
export function plateCalculatorPlates(
  equipment: OwnedEquipmentItem[],
  unit: BodyWeightUnit,
): { plates: PlateStack[]; assumed: boolean } {
  const merged = new Map<number, number>();
  for (const item of equipment) {
    const p = item.plates;
    if (item.catalogKind !== "PLATES" || !p) continue;
    const pairs = (p.pairs ?? []).filter((x) => x.pairCount > 0);
    const resolved = pairs.length > 0 ? pairs : (p.plateWeightsKg ?? []).map((weightKg) => ({ weightKg, pairCount: 1 }));
    for (const entry of resolved) {
      if (!(entry.weightKg > 0)) continue;
      const w = nominalWeight(entry.weightKg, unit);
      merged.set(w, (merged.get(w) ?? 0) + entry.pairCount);
    }
  }
  if (merged.size > 0) {
    const plates = [...merged.entries()]
      .map(([weight, pairCount]) => ({ weight, pairCount }))
      .sort((a, b) => b.weight - a.weight);
    return { plates, assumed: false };
  }
  return { plates: unit === "LB" ? STANDARD_PLATES_LB : STANDARD_PLATES_KG, assumed: true };
}

// --- Exercises -------------------------------------------------------------------------------

type ExerciseLike = { id: string; name: string; equipment: string };

/** How a barbell exercise is loaded, or null when it is not a barbell exercise. */
export function exercisePlateLoading(exercise: ExerciseLike | null | undefined): PlateLoadingStyle | null {
  if (!exercise || exercise.equipment.trim().toLowerCase() !== "barbell") return null;
  const name = `${exercise.name} ${exercise.id}`.toLowerCase();
  if (name.includes("rollout")) return "NONE";
  if (name.includes("landmine") || /\bt-?bar\b/.test(name) || name.includes("tbar")) return "ONE_END";
  return "BOTH_ENDS";
}

/**
 * The bar to start with: the one you last used for this exercise if you still own it, an EZ bar for
 * curls and skull crushers named for it, a trap bar for trap or hex bar lifts, otherwise an Olympic bar.
 */
export function defaultBarForExercise(
  exercise: ExerciseLike | null | undefined,
  bars: PlateCalculatorBar[],
  rememberedBarId?: string | null,
): PlateCalculatorBar | null {
  if (bars.length === 0) return null;
  const remembered = rememberedBarId ? bars.find((b) => b.id === rememberedBarId) : undefined;
  if (remembered) return remembered;
  const name = (exercise?.name ?? "").toLowerCase();
  const pick = (types: StandardBarbellType[]) => bars.find((b) => types.includes(b.barType));
  if (/\bez\b|ez-bar|skull crusher/.test(name)) {
    const ez = pick(["EZ_CURL"]);
    if (ez) return ez;
  }
  if (/trap bar|hex bar|trap-bar|hex-bar/.test(name)) {
    const trap = pick(["OPEN_TRAP_BAR", "TRAP_HEX_25", "TRAP_HEX_20"]);
    if (trap) return trap;
  }
  return (
    pick(["OLYMPIC_MENS"]) ??
    bars.find((b) => !["EZ_CURL", "TRAP_HEX_20", "TRAP_HEX_25", "OPEN_TRAP_BAR"].includes(b.barType)) ??
    bars[0]!
  );
}

/** "Per side: 45 · 25 · 5", "On the end: 25" for one-end loading, or "Empty bar". */
export function formatPlateList(plates: number[], loading: PlateLoadingStyle): string {
  if (plates.length === 0) return "Empty bar";
  const list = plates.map(formatAmount).join(" · ");
  return loading === "ONE_END" ? `On the end: ${list}` : `Per side: ${list}`;
}
