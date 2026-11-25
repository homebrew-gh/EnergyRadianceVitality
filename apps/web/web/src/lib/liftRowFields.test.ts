import { describe, expect, it } from "vitest";
import {
  applyReps,
  applyRest,
  applyRir,
  applySets,
  applyWeight,
  formatClock,
  parseClock,
  repsField,
  restField,
  rirField,
  weightField,
} from "./liftRowFields";
import { lbToKg } from "./fitnessEquipment";
import type { WorkoutWeightPrescription } from "./workoutTraining";

describe("clock fields", () => {
  it("formats seconds as minutes and seconds", () => {
    expect(formatClock(90)).toBe("1:30");
    expect(formatClock(5)).toBe("0:05");
    expect(formatClock(null)).toBe("");
  });

  it("reads plain seconds, m:ss and m/s suffixes", () => {
    expect(parseClock("90")).toBe(90);
    expect(parseClock("1:30")).toBe(90);
    expect(parseClock("2m")).toBe(120);
    expect(parseClock("45s")).toBe(45);
    expect(parseClock("1m30s")).toBe(90);
    expect(parseClock("")).toBeNull();
    expect(parseClock("1:75")).toBeUndefined();
    expect(parseClock("soon")).toBeUndefined();
  });
});

describe("reps field", () => {
  const straight: WorkoutWeightPrescription = { mode: "straight", setCount: 3, repRangeMin: 8, repRangeMax: 12 };

  it("shows a range, a single target, or max reps", () => {
    expect(repsField(straight, "reps").text).toBe("8-12");
    expect(repsField({ ...straight, repRangeMin: null, repRangeMax: null, targetReps: 5 }, "reps").text).toBe("5");
    expect(repsField({ mode: "max_reps" }, "reps").text).toBe("max");
  });

  it("writes one field into target reps or a range, never both", () => {
    expect(applyReps(straight, "5", "reps")).toMatchObject({ targetReps: 5, repRangeMin: null, repRangeMax: null });
    expect(applyReps(straight, "10 – 15", "reps")).toMatchObject({ targetReps: null, repRangeMin: 10, repRangeMax: 15 });
    expect(applyReps(straight, "12-8", "reps")).toMatchObject({ repRangeMin: 8, repRangeMax: 12 });
    expect(applyReps(straight, "6-6", "reps")).toMatchObject({ targetReps: 6, repRangeMin: null });
    expect(applyReps(straight, "max", "reps")).toMatchObject({ mode: "max_reps", targetReps: null });
    expect(applyReps(straight, "", "reps")).toMatchObject({ targetReps: null, repRangeMin: null, repRangeMax: null });
  });

  it("leaves the prescription alone for text it cannot read", () => {
    expect(applyReps(straight, "lots", "reps")).toBe(straight);
    expect(applyReps(straight, "45s", "reps")).toBe(straight);
  });

  it("reads a hold time for timed exercises", () => {
    const hold = applyReps({ setCount: 3 }, "0:45", "time_only");
    expect(hold).toMatchObject({ mode: "time_based", durationSeconds: 45 });
    expect(repsField(hold, "time_only")).toMatchObject({ text: "0:45", editable: true });
    expect(applyReps({ setCount: 3 }, "60", "time_only")).toMatchObject({ durationSeconds: 60 });
  });

  it("switches a reps-or-time exercise to a hold when a time is typed, and keeps it there", () => {
    const hold = applyReps(straight, "30s", "reps_or_time");
    expect(hold).toMatchObject({ mode: "time_based", durationSeconds: 30, repRangeMin: null });
    expect(applyReps(hold, "40", "reps_or_time")).toMatchObject({ mode: "time_based", durationSeconds: 40 });
    expect(applyReps(straight, "10", "reps_or_time")).toMatchObject({ mode: "straight", targetReps: 10 });
  });

  it("is read-only for per-side reps and different reps per set", () => {
    expect(repsField({ sets: [{ repsPerSide: 10, side: "each" }] }, "reps")).toMatchObject({
      text: "10/side",
      editable: false,
    });
    expect(repsField({ sets: [{ reps: 10 }, { reps: 8 }] }, "reps")).toMatchObject({ text: "varied", editable: false });
  });
});

describe("weight, RIR, sets and rest fields", () => {
  it("shows and stores weight in the lifter's unit", () => {
    const p = applyWeight({ setCount: 3 }, "225", "LB");
    expect(p.targetWeightKg).toBeCloseTo(lbToKg(225));
    expect(weightField(p, "LB").text).toBe("225");
    expect(applyWeight(p, "", "LB").targetWeightKg).toBeNull();
    expect(applyWeight(p, "heavy", "LB")).toBe(p);
  });

  it("clears per-set weights so one weight applies to every set", () => {
    const p = applyWeight({ sets: [{ weightKg: 50 }, { weightKg: 50 }] }, "135", "LB");
    expect(p.sets!.every((s) => s.weightKg == null && s.targetWeightKg == null)).toBe(true);
  });

  it("reads RIR, set count and rest", () => {
    expect(rirField(applyRir({}, "2")).text).toBe("2");
    expect(applySets({ setCount: 3 }, "5").setCount).toBe(5);
    expect(applySets({ setCount: 3 }, "0").setCount).toBe(3);
    const rested = applyRest({}, "2:30");
    expect(rested.restBetweenSetsSeconds).toBe(150);
    expect(restField(rested).text).toBe("2:30");
    expect(applyRest(rested, "").restBetweenSetsSeconds).toBeNull();
  });
});
