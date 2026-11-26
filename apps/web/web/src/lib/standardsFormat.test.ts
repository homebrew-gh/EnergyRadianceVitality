import { describe, expect, it } from "vitest";
import { evaluateEvent } from "./standards";
import { formatGap, formatRawResult, formatStandardValue, missingLabel, parseResultInput, resultInputLabel } from "./standardsFormat";
import { ARMY_AFT, ERV_GENERAL, SEVEN_EVENT } from "./standardsLibrary";

const LB = 0.45359237;
const ev = (s: typeof ERV_GENERAL, id: string) => s.events.find((e) => e.id === id)!;

describe("formatStandardValue", () => {
  it("speaks each measure in the athlete's units", () => {
    expect(formatStandardValue(ev(ERV_GENERAL, "squat"), 1.5, "LB")).toBe("1.5x BW");
    expect(formatStandardValue(ev(ARMY_AFT, "deadlift-3rm"), 250 * LB, "LB")).toBe("250 lb");
    expect(formatStandardValue(ev(ERV_GENERAL, "pull-ups"), 1, "LB")).toBe("1 rep");
    expect(formatStandardValue(ev(ERV_GENERAL, "ruck-12mi"), 10800, "LB")).toBe("3:00:00");
    expect(formatStandardValue(ev(SEVEN_EVENT, "farmers-carry"), 175 * 0.3048, "LB")).toBe("175 ft");
    expect(formatStandardValue(ev(SEVEN_EVENT, "broad-jump"), 12 * 2.54, "LB")).toBe("Height + 12 in");
    expect(formatStandardValue(ev(SEVEN_EVENT, "broad-jump"), 0, "LB")).toBe("Your height");
  });
});

describe("formatGap", () => {
  const base = {
    weightLogs: [],
    cardioLogs: [],
    bodyWeights: [{ date: "2026-01-01", weightKg: 200 * LB }],
    profile: { sex: "male" as const },
    exerciseNames: new Map<string, string>(),
  };
  it("gives a bodyweight ratio gap with its load", () => {
    const r = evaluateEvent(ERV_GENERAL.id, ev(ERV_GENERAL, "bench"), {
      ...base,
      weightLogs: [{ date: "2026-05-01", workouts: [{ id: "w", source: "LIVE", entries: [{ exerciseId: "erv-weight-exercise-bench-v1", sets: [{ reps: 1, weightKg: 230 * LB }] }] }] }],
    });
    expect(formatGap(r, "LB", 200 * LB)).toBe("0.1x BW to Strong (about 20 lb)");
  });
  it("says what a manual event needs", () => {
    const r = evaluateEvent(SEVEN_EVENT.id, ev(SEVEN_EVENT, "farmers-carry"), base);
    expect(missingLabel(r)).toBe("Test result needed");
    expect(formatGap(r, "LB", null)).toBeNull();
  });
});

describe("result inputs", () => {
  it("parse typed values into raw stored units and back", () => {
    const squat = ev(ERV_GENERAL, "squat");
    expect(parseResultInput(squat, "315", "LB")).toBeCloseTo(315 * LB, 3);
    expect(formatRawResult(squat, 315 * LB, "LB")).toBe("315 lb");
    const jump = ev(SEVEN_EVENT, "broad-jump");
    expect(parseResultInput(jump, "88", "LB")).toBeCloseTo(88 * 2.54, 3);
    expect(formatRawResult(jump, 88 * 2.54, "LB")).toBe("7 ft 4 in");
    const plank = ev(ERV_GENERAL, "plank");
    expect(parseResultInput(plank, "2:30", "LB")).toBe(150);
    expect(parseResultInput(plank, "two", "LB")).toBeNull();
    const carry = ev(SEVEN_EVENT, "farmers-carry");
    expect(formatRawResult(carry, parseResultInput(carry, "200", "LB")!, "LB")).toBe("200 ft");
    expect(resultInputLabel(ev(SEVEN_EVENT, "trap-bar-5"), "LB")).toBe("Weight for 5 reps (lb)");
    expect(resultInputLabel(squat, "KG")).toBe("Weight for a single (kg)");
  });
});
