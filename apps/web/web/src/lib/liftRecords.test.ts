import { describe, expect, it } from "vitest";
import { bestLiftRecords, estimatedOneRmKg, liftRecordsOf } from "./liftRecords";
import {
  historyForExercise,
  maxWeightByRepBucketKg,
  type WeightDayLog,
  type WeightSet,
  type WeightWorkoutSession,
} from "./trainingHistory";
import { lastLiftByExercise } from "./lastLift";

const BENCH = "bench";

function workout(id: string, sets: WeightSet[], exerciseId = BENCH): WeightWorkoutSession {
  return { id, source: "LIVE", entries: [{ exerciseId, sets }] };
}

function day(date: string, ...workouts: WeightWorkoutSession[]): WeightDayLog {
  return { date, workouts };
}

describe("estimatedOneRmKg", () => {
  it("is Epley, a single is its weight, long sets give none", () => {
    expect(estimatedOneRmKg(100, 5)).toBeCloseTo(116.67, 2);
    expect(estimatedOneRmKg(100, 1)).toBe(100);
    expect(estimatedOneRmKg(105, 10)).toBeCloseTo(140, 2);
    expect(estimatedOneRmKg(60, 20)).toBeNull();
  });
});

describe("lift records", () => {
  it("leaves warm-ups out of records, buckets and the last-time line", () => {
    const w = workout("w1", [
      { reps: 10, weightKg: 40, warmup: true },
      { reps: 5, weightKg: 60, warmup: true },
      { reps: 5, weightKg: 100 },
    ]);
    const byKind = new Map(liftRecordsOf(w, "2026-11-01", BENCH).map((r) => [r.kind, r]));
    expect(byKind.get("HEAVIEST")?.value).toBe(100);
    expect(byKind.get("BEST_VOLUME")?.value).toBe(500);
    expect(byKind.get("MOST_REPS")?.value).toBe(5);
    expect(byKind.has("TEN_REP_MAX")).toBe(false);

    const logs = [day("2026-11-01", w)];
    const buckets = maxWeightByRepBucketKg(logs, BENCH);
    expect(buckets[9]?.maxKg).toBeNull();
    expect(buckets[4]?.maxKg).toBe(100);
    expect(lastLiftByExercise(logs).get(BENCH)?.sets).toHaveLength(1);
  });

  it("keeps the best of each kind with its date", () => {
    const logs = [
      day("2026-11-15", workout("c", [{ reps: 12, weightKg: 82 }])),
      day("2026-11-01", workout("a", [{ reps: 5, weightKg: 100 }, { reps: 10, weightKg: 80 }])),
      day("2026-11-08", workout("b", [{ reps: 2, weightKg: 110 }])),
    ];
    const best = new Map(bestLiftRecords(logs, BENCH).map((r) => [r.kind, r]));
    expect(best.get("HEAVIEST")?.workoutId).toBe("b");
    expect(best.get("TEN_REP_MAX")?.value).toBe(82);
    expect(best.get("TEN_REP_MAX")?.reps).toBe(12);
    expect(best.get("EST_ONE_RM")?.workoutId).toBe("b");
    expect(best.get("BEST_VOLUME")?.date).toBe("2026-11-01");
  });

  it("tracks reps for bodyweight lifts and holds for timed ones", () => {
    const logs = [
      day("2026-11-01", workout("p", [{ reps: 12 }], "pullup")),
      day("2026-11-02", workout("k", [{ reps: 0, durationSeconds: 90 }], "plank")),
    ];
    expect(bestLiftRecords(logs, "pullup").map((r) => r.kind)).toEqual(["MOST_REPS"]);
    expect(bestLiftRecords(logs, "plank").find((r) => r.kind === "LONGEST_HOLD")?.value).toBe(90);
  });

  it("counts an exercise done twice in one workout", () => {
    const w: WeightWorkoutSession = {
      id: "w",
      source: "LIVE",
      entries: [
        { exerciseId: BENCH, sets: [{ reps: 5, weightKg: 100 }] },
        { exerciseId: "row", sets: [{ reps: 8, weightKg: 60 }] },
        { exerciseId: BENCH, sets: [{ reps: 3, weightKg: 120 }] },
      ],
    };
    const logs = [day("2026-11-01", w)];
    expect(historyForExercise(logs, BENCH)[0]?.entry.sets).toHaveLength(2);
    expect(maxWeightByRepBucketKg(logs, BENCH)[2]?.maxKg).toBe(120);
  });
});
