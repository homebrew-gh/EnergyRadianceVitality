import { describe, expect, it } from "vitest";
import type { AppDataRecord } from "./api";
import { buildWellnessSummary, wellnessForCoach, wellnessHasData } from "./wellness";

function rec(d_tag: string, payload: unknown): AppDataRecord {
  return { event_id: d_tag, d_tag, ciphertext: "", plaintext: JSON.stringify(payload) };
}

describe("buildWellnessSummary", () => {
  const records = [
    rec("erv/sauna/2026-11-20", {
      date: "2026-11-20",
      sessions: [
        { id: "a", durationSeconds: 900, tempValue: 170, tempUnit: "FAHRENHEIT", contrastId: "c1" },
        { id: "b", durationSeconds: 600, tempValue: 180, tempUnit: "FAHRENHEIT" },
      ],
    }),
    rec("erv/cold/2026-11-20", { date: "2026-11-20", sessions: [{ id: "c", durationSeconds: 120, contrastId: "c1" }] }),
    rec("erv/sauna/2026-09-01", { date: "2026-09-01", sessions: [{ id: "old", durationSeconds: 900 }] }),
    rec("erv/light/2026-11-21", { date: "2026-11-21", sessions: [{ id: "l", minutes: 12 }] }),
    rec("erv/light/list", { devices: [] }),
    rec("erv/supplements/2026-11-21", {
      date: "2026-11-21",
      routineRuns: [{ routineId: "r", routineName: "AM", stepIntakes: [{}, {}] }],
      adHocIntakes: [{}],
    }),
    rec("erv/fasting/2026-11-22", {
      date: "2026-11-22",
      sessions: [
        { status: "COMPLETED", startedAtEpochSeconds: 0, endedAtEpochSeconds: 15.5 * 3600 },
        { status: "CANCELLED", startedAtEpochSeconds: 0, endedAtEpochSeconds: 3600 },
      ],
    }),
    rec("erv/fasting/state", { active: null }),
    rec("erv/body_tracker/2026-11-01", { date: "2026-11-01", measurementsCm: { WAIST: 88.9 } }),
    rec("erv/body_tracker/2026-11-22", { date: "2026-11-22", measurementsCm: { WAIST: 86.36 } }),
  ];

  it("totals the period and converts the waist to inches for lb users", () => {
    const s = buildWellnessSummary(records, { today: "2026-11-26", periodStart: "2026-10-30", weightUnit: "LB" });
    expect(s.sauna).toEqual({ sessions: 2, minutes: 25, averageTemp: 175, tempUnit: "°F" });
    expect(s.cold.sessions).toBe(1);
    expect(s.contrastSessions).toBe(1);
    expect(s.light).toEqual({ sessions: 1, minutes: 12 });
    expect(s.supplements).toEqual({ daysLogged: 1, intakes: 3 });
    expect(s.fasting).toEqual({ completed: 1, averageHours: 15.5, longestHours: 15.5 });
    expect(s.waist).toEqual({ unit: "in", latest: 34, latestDate: "2026-11-22", change: -1 });
    expect(s.periodDays).toBe(28);
    expect(wellnessHasData(s)).toBe(true);
  });

  it("uses the unit picked on the phone and leaves empty parts out of the coach view", () => {
    const withSettings = [
      ...records,
      rec("erv/body_tracker/settings", { lengthUnit: "CENTIMETERS", updatedAtEpochSeconds: 5 }),
    ];
    const s = buildWellnessSummary(withSettings, { today: "2026-11-26", periodStart: "2026-11-22", weightUnit: "LB" });
    expect(s.waist?.unit).toBe("cm");
    const coach = wellnessForCoach(s)!;
    expect(coach.sauna).toBeUndefined();
    expect(coach.fasting).toEqual({ completed: 1, averageHours: 15.5, longestHours: 15.5 });
    expect(coach.waist).toEqual({ latest: "86.4 cm" });
  });

  it("returns null for the coach with nothing logged", () => {
    const s = buildWellnessSummary([], { today: "2026-11-26", periodStart: null, weightUnit: "LB" });
    expect(wellnessHasData(s)).toBe(false);
    expect(wellnessForCoach(s)).toBeNull();
  });
});
