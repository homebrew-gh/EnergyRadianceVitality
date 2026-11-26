import { describe, expect, it } from "vitest";
import {
  bestCardioRecords,
  cardioRecordTitle,
  formatCardioRecordValue,
  weeklyCardioTotals,
} from "./cardioProgress";
import { parseCardioDayLog, type CardioDayLog, type CardioSession } from "./trainingHistory";

function session(id: string, builtin: string, meters: number | null, seconds: number, extra: Partial<CardioSession> = {}): CardioSession {
  return {
    id,
    activity: { builtin, customTypeId: null, customName: null, displayLabel: builtin },
    durationMinutes: Math.round(seconds / 60),
    distanceMeters: meters,
    startEpochSeconds: 1_000,
    endEpochSeconds: 1_000 + seconds,
    ...extra,
  };
}

describe("cardio records", () => {
  const logs: CardioDayLog[] = [
    { date: "2026-11-02", sessions: [session("a", "RUN", 5000, 1600)] },
    { date: "2026-11-09", sessions: [session("b", "RUN", 10000, 3100), session("r", "BIKE", 41000, 5400)] },
    { date: "2026-11-10", sessions: [session("k", "RUCK", 8000, 4800, { ruckLoadKg: 20.4, elevationGainMeters: 120 })] },
  ];

  it("keeps the best of each record by activity, matching the phone", () => {
    const records = bestCardioRecords(logs);
    const run = records.filter((r) => r.group.id === "RUN");
    const fastest5k = run.find((r) => cardioRecordTitle(r.kind) === "Fastest 5K")!;
    // The 10K at 51:40 is a 25:50 5K, faster than the 26:40 5K.
    expect(fastest5k.sessionId).toBe("b");
    expect(formatCardioRecordValue(fastest5k, "MI")).toBe("25:50");
    expect(run.find((r) => r.kind.type === "LONGEST_DISTANCE")?.sessionId).toBe("b");
    // A 41 km ride has a 10 and a 25 mile record, not 50.
    const ride = records.filter((r) => r.group.id === "RIDE").map((r) => cardioRecordTitle(r.kind));
    expect(ride).toEqual(["Fastest 10 Miles", "Fastest 25 Miles", "Longest Distance", "Longest Time"]);
    const ruck = records.filter((r) => r.group.id === "RUCK");
    expect(formatCardioRecordValue(ruck.find((r) => r.kind.type === "HEAVIEST_PACK")!, "MI", "LB")).toBe("45 lb");
    expect(formatCardioRecordValue(ruck.find((r) => r.kind.type === "MOST_CLIMBING")!, "MI")).toBe("394 ft");
  });

  it("orders groups and records the same way every time", () => {
    const groups = [...new Set(bestCardioRecords(logs).map((r) => r.group.id))];
    expect(groups).toEqual(["RUN", "RIDE", "RUCK"]);
  });
});

describe("weekly cardio totals", () => {
  it("sums each Monday week and fills empty weeks", () => {
    const logs: CardioDayLog[] = [
      { date: "2026-11-03", sessions: [session("a", "RUN", 5000, 1800)] },
      { date: "2026-11-05", sessions: [session("b", "BIKE", 20000, 3600)] },
      { date: "2026-11-18", sessions: [session("c", "RUN", 3000, 900)] },
    ];
    const weeks = weeklyCardioTotals(logs, 4, new Date(2026, 10, 20));
    expect(weeks.map((w) => w.weekStart)).toEqual(["2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16"]);
    expect(weeks[1]).toMatchObject({ distanceMeters: 25000, seconds: 5400, sessions: 2 });
    expect(weeks[2].sessions).toBe(0);
  });
});

describe("cardio day log parsing", () => {
  it("reads splits, climbing and pack weight from the phone", () => {
    const log = parseCardioDayLog(
      JSON.stringify({
        date: "2026-11-20",
        sessions: [
          {
            id: "s1",
            activity: { builtin: "RUCK", displayLabel: "Ruck" },
            durationMinutes: 60,
            distanceMeters: 6000,
            elevationGainMeters: 80,
            treadmill: { speed: 3, speedUnit: "MPH", loadKg: 18 },
            splits: [
              { orderIndex: 1, elapsedSeconds: 1200, segmentElapsedSeconds: 600, totalDistanceMeters: 3200 },
              { orderIndex: 0, elapsedSeconds: 600, segmentElapsedSeconds: 600, totalDistanceMeters: 1600 },
            ],
          },
        ],
      }),
      "2026-11-20",
    );
    const s = log!.sessions[0];
    expect(s.splits?.map((x) => x.orderIndex)).toEqual([0, 1]);
    expect(s.elevationGainMeters).toBe(80);
    expect(s.ruckLoadKg).toBe(18);
    expect(s.segmentCount).toBe(0);
  });
});
