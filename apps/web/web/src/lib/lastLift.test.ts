import { describe, expect, it } from "vitest";
import { lbToKg } from "./fitnessEquipment";
import { formatLastLift, lastLiftByExercise, lastLiftTopWeightKg } from "./lastLift";
import type { WeightDayLog } from "./trainingHistory";

const lb = (n: number) => lbToKg(n);

const logs: WeightDayLog[] = [
  {
    date: "2026-11-18",
    workouts: [{ id: "a", source: "LIVE", entries: [{ exerciseId: "bench", sets: [{ reps: 5, weightKg: lb(205) }] }] }],
  },
  {
    date: "2026-11-20",
    workouts: [
      {
        id: "b",
        source: "LIVE",
        finishedAtEpochSeconds: 100,
        entries: [{ exerciseId: "bench", sets: [{ reps: 8, weightKg: lb(185) }, { reps: 8, weightKg: lb(185) }, { reps: 8, weightKg: lb(185) }] }],
      },
      {
        id: "c",
        source: "LIVE",
        finishedAtEpochSeconds: 200,
        entries: [
          { exerciseId: "row", sets: [{ reps: 8, weightKg: lb(155) }, { reps: 7, weightKg: lb(155) }, { reps: 0, weightKg: lb(155) }] },
          { exerciseId: "pullup", sets: [{ reps: 10 }, { reps: 10 }] },
          { exerciseId: "plank", sets: [{ reps: 0, durationSeconds: 45 }, { reps: 0, durationSeconds: 45 }] },
        ],
      },
    ],
  },
];

describe("last lift", () => {
  const last = lastLiftByExercise(logs);

  it("takes the newest session for each exercise and drops empty sets", () => {
    expect(last.get("bench")?.date).toBe("2026-11-20");
    expect(last.get("row")?.sets).toHaveLength(2);
    expect(lastLiftTopWeightKg(last.get("bench")!)).toBeCloseTo(lb(185));
    expect(lastLiftTopWeightKg(last.get("pullup")!)).toBeNull();
  });

  it("reads as sets × reps @ weight, or set by set when they differ", () => {
    expect(formatLastLift(last.get("bench")!, "LB", "2026-11-25")).toBe("3×8 @ 185 lb · Nov 20");
    expect(formatLastLift(last.get("row")!, "LB", "2026-11-25")).toBe("155×8, 155×7 lb · Nov 20");
    expect(formatLastLift(last.get("pullup")!, "LB", "2026-11-20")).toBe("2×10 · today");
    expect(formatLastLift(last.get("plank")!, "LB", "2026-11-25")).toBe("2×0:45 · Nov 20");
  });
});
