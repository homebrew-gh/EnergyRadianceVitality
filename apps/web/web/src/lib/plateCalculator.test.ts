import { describe, expect, it } from "vitest";
import { lbToKg, type OwnedEquipmentItem } from "./fitnessEquipment";
import {
  calculatePlateLoad,
  defaultBarForExercise,
  exercisePlateLoading,
  formatPlateList,
  plateCalculatorBars,
  plateCalculatorPlates,
  type PlateLoadResult,
  type PlateLoadingStyle,
  type PlateStack,
} from "./plateCalculator";

type SharedCase = PlateLoadResult & {
  name: string;
  target: number;
  bar: number;
  loading: PlateLoadingStyle;
  plates: PlateStack[];
};

// Read through a glob so the web build (which only sees apps/web/web) never needs the file.
const sharedFiles = import.meta.glob<{ cases: SharedCase[] }>(
  "../../../../../shared/test-cases/plate_calculator.json",
  { eager: true, import: "default" },
);
const shared = Object.values(sharedFiles)[0];

describe("shared plate calculator cases", () => {
  it("finds the shared file", () => {
    expect(shared?.cases.length).toBeGreaterThan(0);
  });

  it.each((shared?.cases ?? []).map((c) => [c.name, c] as const))("%s", (_name, c) => {
    const result = calculatePlateLoad(c);
    expect(result).toEqual({ exact: c.exact, below: c.below, above: c.above });
  });
});

const equipment: OwnedEquipmentItem[] = [
  { id: "bar-oly", name: "Bar", catalogKind: "BARBELL", barbell: { barType: "OLYMPIC_MENS" } },
  { id: "bar-ez", name: "EZ", catalogKind: "BARBELL", barbell: { barType: "EZ_CURL" } },
  {
    id: "bar-dl",
    name: "Deadlift bar",
    catalogKind: "BARBELL",
    barbell: { barType: "CUSTOM", customName: "Open deadlift bar", customWeightKg: lbToKg(60) },
  },
  {
    id: "plates-a",
    name: "Plates",
    catalogKind: "PLATES",
    plates: { pairs: [{ weightKg: lbToKg(45), pairCount: 2 }, { weightKg: lbToKg(2.5), pairCount: 1 }] },
  },
  {
    id: "plates-b",
    name: "More plates",
    catalogKind: "PLATES",
    plates: { pairs: [{ weightKg: 20.41164, pairCount: 1 }] },
  },
];

describe("plate calculator equipment", () => {
  it("counts an Olympic bar as 45 lb and keeps custom bars exact", () => {
    const { bars, assumed } = plateCalculatorBars(equipment, "LB");
    expect(assumed).toBe(false);
    expect(bars.map((b) => [b.label, b.weight])).toEqual([
      ["Olympic bar (45 lb)", 45],
      ["EZ curl bar (18 lb)", 18],
      ["Open deadlift bar (60 lb)", 60],
    ]);
  });

  it("merges plates entered with either pound conversion", () => {
    expect(plateCalculatorPlates(equipment, "LB")).toEqual({
      plates: [
        { weight: 45, pairCount: 3 },
        { weight: 2.5, pairCount: 1 },
      ],
      assumed: false,
    });
  });

  it("assumes a standard bar and plates when none are entered", () => {
    expect(plateCalculatorBars([], "LB").bars[0]!.weight).toBe(45);
    expect(plateCalculatorPlates([], "LB").assumed).toBe(true);
  });

  it("picks loading style and bar per exercise", () => {
    const ex = (name: string, eq = "barbell") => ({ id: name, name, equipment: eq });
    expect(exercisePlateLoading(ex("Bench Press"))).toBe("BOTH_ENDS");
    expect(exercisePlateLoading(ex("Landmine Press"))).toBe("ONE_END");
    expect(exercisePlateLoading(ex("T-Bar Row"))).toBe("ONE_END");
    expect(exercisePlateLoading(ex("Barbell Rollout"))).toBe("NONE");
    expect(exercisePlateLoading(ex("Goblet Squat", "kettlebell"))).toBeNull();

    const { bars } = plateCalculatorBars(equipment, "LB");
    expect(defaultBarForExercise(ex("EZ-Bar Curl"), bars)!.id).toBe("bar-ez");
    expect(defaultBarForExercise(ex("Deadlift"), bars)!.id).toBe("bar-oly");
    expect(defaultBarForExercise(ex("Deadlift"), bars, "bar-dl")!.id).toBe("bar-dl");
    expect(defaultBarForExercise(ex("Deadlift"), bars, "sold-bar")!.id).toBe("bar-oly");
  });

  it("formats the plate line", () => {
    expect(formatPlateList([45, 25, 2.5], "BOTH_ENDS")).toBe("Per side: 45 · 25 · 2.5");
    expect(formatPlateList([25], "ONE_END")).toBe("On the end: 25");
    expect(formatPlateList([], "BOTH_ENDS")).toBe("Empty bar");
  });
});
