import { describe, expect, it } from "vitest";
import { phoneCompatibilityWarnings } from "./phoneCompatibility";
import type { WorkoutSegment } from "./workoutTraining";

const names: Record<string, string> = { bench: "Bench Press", row: "Barbell Row" };
const options = { exerciseName: (id: string) => names[id] };

describe("phone check", () => {
  it("passes plain straight sets with rest", () => {
    const segments: WorkoutSegment[] = [
      {
        kind: "straight_sets",
        items: [
          {
            type: "weight",
            exerciseId: "bench",
            prescription: { setCount: 4, targetReps: 5, restBetweenSetsSeconds: 120 },
          },
        ],
      },
    ];
    expect(phoneCompatibilityWarnings(segments, options)).toEqual([]);
  });

  it("flags superset lifts whose sets, targets or rest the phone ignores", () => {
    const segments: WorkoutSegment[] = [
      {
        kind: "superset",
        title: "Upper Pair",
        rounds: 3,
        items: [
          {
            type: "weight",
            exerciseId: "bench",
            prescription: { setCount: 4, restBetweenSetsSeconds: 90 },
          },
          {
            type: "weight",
            exerciseId: "row",
            prescription: {
              sets: [
                { targetReps: 8, targetWeightKg: 60 },
                { targetReps: 8, targetWeightKg: 60 },
                { targetReps: 6, targetWeightKg: 70 },
              ],
            },
          },
          { type: "rest", durationSeconds: 60 },
        ],
      },
    ];
    const messages = phoneCompatibilityWarnings(segments, options).map((w) => w.message);
    expect(messages).toHaveLength(4);
    expect(messages[0]).toContain("Bench Press has 4 sets");
    expect(messages[0]).toContain("3 rounds");
    expect(messages[1]).toContain("Bench Press's own rest isn't used");
    expect(messages[2]).toContain("Barbell Row has different targets per set");
    expect(messages[3]).toContain("Rest is skipped on the phone");
  });

  it("accepts a circuit whose set count matches its rounds", () => {
    const segments: WorkoutSegment[] = [
      {
        kind: "circuit",
        rounds: 3,
        items: [
          { type: "weight", exerciseId: "bench", prescription: { setCount: 3, targetReps: 10 } },
          { type: "weight", exerciseId: "row", prescription: { setCount: 1 } },
        ],
      },
    ];
    expect(phoneCompatibilityWarnings(segments, options)).toEqual([]);
  });

  it("flags a circuit with no lifts", () => {
    const segments: WorkoutSegment[] = [
      { kind: "circuit", rounds: 2, items: [{ type: "note", text: "Go hard" }] },
    ];
    const warnings = phoneCompatibilityWarnings(segments, options);
    expect(warnings[0].message).toContain("has no lifts");
  });

  it("leaves interval legs in seconds alone, since the phone times them exactly", () => {
    const segments: WorkoutSegment[] = [
      {
        kind: "interval",
        items: [
          {
            type: "cardio",
            title: "Hill Sprints",
            cardio: { activity: "RUN", mode: "sprint_intervals", rounds: 8, workSeconds: 30, restSeconds: 90 },
          },
          {
            type: "cardio",
            cardio: { activity: "BIKE", mode: "interval_template", legs: [{ workSeconds: 45, restSeconds: 15 }] },
          },
        ],
      },
    ];
    expect(phoneCompatibilityWarnings(segments, options)).toEqual([]);
  });
});
