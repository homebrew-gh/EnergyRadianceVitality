import { describe, expect, it } from "vitest";
import { lbToKg } from "./fitnessEquipment";
import {
  compactSetsLine,
  daysBetween,
  formatLiftRecordValue,
  liftListStats,
  liftTrend,
} from "./liftStats";
import type { LiftRecord } from "./liftRecords";
import type { WeightDayLog, WeightSet } from "./trainingHistory";

function logs(days: [string, WeightSet[]][], exerciseId = "bench"): WeightDayLog[] {
  return days.map(([date, sets], i) => ({
    date,
    workouts: [{ id: `w${i}`, source: "LIVE", entries: [{ exerciseId, sets }] }],
  }));
}

describe("compactSetsLine", () => {
  it("joins same-weight sets and splits warm-ups", () => {
    const entry = {
      exerciseId: "bench",
      sets: [
        { reps: 5, weightKg: lbToKg(135), warmup: true },
        { reps: 3, weightKg: lbToKg(185), warmup: true },
        { reps: 5, weightKg: lbToKg(230) },
        { reps: 5, weightKg: lbToKg(215) },
        { reps: 5, weightKg: lbToKg(215) },
      ],
    };
    expect(compactSetsLine(entry, "LB", false)).toBe("230 lb × 5 · 215 lb × 5, 5");
    expect(compactSetsLine(entry, "LB", true)).toBe("135 lb × 5 · 185 lb × 3");
    const pullups = { exerciseId: "p", sets: [{ reps: 12 }, { reps: 10 }] };
    expect(compactSetsLine(pullups, "LB", false)).toBe("12, 10 reps");
    expect(compactSetsLine(pullups, "LB", true)).toBeNull();
    expect(compactSetsLine({ exerciseId: "k", sets: [{ reps: 0, durationSeconds: 90 }] }, "LB", false)).toBe(
      "1:30 hold",
    );
  });
});

describe("formatLiftRecordValue", () => {
  const rec = (kind: LiftRecord["kind"], value: number, extra: Partial<LiftRecord> = {}): LiftRecord => ({
    exerciseId: "bench",
    kind,
    value,
    workoutId: "w",
    date: "2026-11-01",
    ...extra,
  });
  it("reads in pounds", () => {
    expect(formatLiftRecordValue(rec("EST_ONE_RM", lbToKg(268.33)), "LB")).toBe("268 lb");
    expect(formatLiftRecordValue(rec("HEAVIEST", lbToKg(245), { reps: 2 }), "LB")).toBe("245 lb × 2");
    expect(formatLiftRecordValue(rec("TEN_REP_MAX", lbToKg(185), { reps: 10 }), "LB")).toBe("185 lb");
    expect(formatLiftRecordValue(rec("BEST_VOLUME", lbToKg(3375)), "LB")).toBe("3,375 lb");
    expect(formatLiftRecordValue(rec("MOST_REPS", 15, { reps: 15, weightKg: lbToKg(135) }), "LB")).toBe(
      "15 @ 135 lb",
    );
    expect(formatLiftRecordValue(rec("MOST_REPS", 12, { reps: 12 }), "LB")).toBe("12 reps");
    expect(formatLiftRecordValue(rec("LONGEST_HOLD", 90), "LB")).toBe("1:30");
  });
});

describe("liftTrend", () => {
  it("uses est. 1RM for loaded lifts and reps for bodyweight", () => {
    const loaded = logs([
      ["2026-11-10", [{ reps: 5, weightKg: 100 }]],
      ["2026-08-01", [{ reps: 5, weightKg: 90 }, { reps: 20, weightKg: 60 }]],
    ]);
    const trend = liftTrend(loaded, "bench")!;
    expect(trend.metric).toBe("EST_ONE_RM");
    expect(trend.points.map((p) => p.date)).toEqual(["2026-08-01", "2026-11-10"]);
    expect(trend.points[0]!.value).toBeCloseTo(105, 2);
    const bodyweight = logs([["2026-11-01", [{ reps: 12 }]]], "pullup");
    expect(liftTrend(bodyweight, "pullup")!.metric).toBe("MOST_REPS");
    expect(liftTrend(bodyweight, "bench")).toBeNull();
  });
});

describe("liftListStats", () => {
  const s = logs([
    ["2026-11-01", [{ reps: 5, weightKg: 100 }]],
    ["2026-11-08", [{ reps: 5, weightKg: 105 }]],
    ["2026-11-15", [{ reps: 5, weightKg: 95 }, { reps: 5, weightKg: 60, warmup: true }]],
  ]);
  it("has last session, best 1RM and last record day", () => {
    const stat = liftListStats(s).get("bench")!;
    expect(stat.lastDate).toBe("2026-11-15");
    expect(stat.bestEstOneRmKg).toBeCloseTo(122.5, 2);
    expect(stat.lastRecordDate).toBe("2026-11-08");
    expect(liftListStats(logs([["2026-11-01", [{ reps: 5, weightKg: 100 }]]])).get("bench")!.lastRecordDate).toBeNull();
  });
  it("counts days between dates", () => {
    expect(daysBetween("2026-11-01", "2026-11-15")).toBe(14);
  });
});
