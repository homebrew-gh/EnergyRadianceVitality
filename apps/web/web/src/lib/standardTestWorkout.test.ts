import { describe, expect, it } from "vitest";
import { buildStandardTestWorkout, testExerciseFor } from "./standardTestWorkout";
import { ERV_GENERAL, SEVEN_EVENT } from "./standardsLibrary";

const LB = 0.45359237;
const exercises = [
  { id: "erv-weight-exercise-squat-v1", name: "Squat" },
  { id: "erv-weight-exercise-bench-v1", name: "Bench Press" },
  { id: "erv-weight-exercise-deadlift-v1", name: "Deadlift" },
  { id: "erv-weight-exercise-bw-pullup-v1", name: "Pull-Up" },
  { id: "erv-weight-exercise-bw-plank-v1", name: "Plank" },
];
let n = 0;
const options = {
  exercises,
  profile: { ageYears: 40, sex: "male" as const, heightCm: 180 },
  unit: "LB" as const,
  latestBodyWeightKg: 201 * LB,
  newId: () => `id-${++n}`,
};

describe("buildStandardTestWorkout", () => {
  it("makes one section per event, in order, with no rest after the last", () => {
    const w = buildStandardTestWorkout(ERV_GENERAL, options);
    expect(w.name).toBe("ERV General Test");
    expect(w.segments.map((s) => s.title)).toEqual(ERV_GENERAL.events.map((e) => e.name));
    expect(w.segments[0].restAfterSeconds).toBe(240);
    expect(w.segments[w.segments.length - 1].restAfterSeconds).toBeNull();
  });

  it("tests a lift as one top set and shows the targets in a note", () => {
    const squat = buildStandardTestWorkout(ERV_GENERAL, options).segments[0];
    expect(squat.kind).toBe("straight_sets");
    const [note, lift] = squat.items ?? [];
    expect(lift).toMatchObject({
      type: "weight",
      exerciseId: "erv-weight-exercise-squat-v1",
      prescription: { mode: "straight", setCount: 1, targetReps: 1 },
    });
    expect(note.type === "note" && note.text).toContain("Base 1.25x BW · Strong 1.5x BW · Elite 2x BW");
  });

  it("puts bodyweight on the bar for reps-at-bodyweight, rounded to 5 lb", () => {
    const seg = buildStandardTestWorkout(SEVEN_EVENT, options).segments.find((s) => s.title === "Bench Press")!;
    const lift = seg.items?.find((i) => i.type === "weight");
    expect(lift?.type === "weight" && lift.prescription?.mode).toBe("max_reps");
    expect(lift?.type === "weight" && lift.prescription?.targetWeightKg).toBeCloseTo(200 * LB, 3);
  });

  it("times cardio over the event distance and leaves manual tests as a note", () => {
    const w = buildStandardTestWorkout(SEVEN_EVENT, options);
    const run = w.segments.find((s) => s.kind === "cardio")!;
    const cardio = run.items?.find((i) => i.type === "cardio");
    expect(cardio?.type === "cardio" && cardio.cardio).toMatchObject({ activity: "RUN", targetDistanceMeters: 800 });
    const jump = w.segments[0];
    expect(jump.kind).toBe("composite");
    expect(jump.items?.[0].type).toBe("note");
  });

  it("falls back to deadlift for a trap bar event when the library has no trap bar", () => {
    const trap = SEVEN_EVENT.events.find((e) => e.id === "trap-bar-5")!;
    expect(testExerciseFor(trap, exercises)).toBe("erv-weight-exercise-deadlift-v1");
    expect(testExerciseFor(trap, [...exercises, { id: "x", name: "Trap Bar Deadlift" }])).toBe("x");
  });
});
