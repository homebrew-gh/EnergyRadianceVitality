import { describe, expect, it } from "vitest";
import {
  bodyWeightOn,
  evaluateEvent,
  evaluateStandard,
  standardScore,
  tierOf,
  tiersFor,
  type StandardEvent,
  type StandardInputs,
} from "./standards";
import { ARMY_AFT, BUILT_IN_STANDARDS, ERV_GENERAL, RUCK_12_MILE, SEVEN_EVENT } from "./standardsLibrary";
import type { CardioDayLog, CardioSession, WeightDayLog, WeightSet } from "./trainingHistory";

const LB = 0.45359237;
const MILE = 1609.344;

function lifts(date: string, exerciseId: string, sets: WeightSet[]): WeightDayLog {
  return { date, workouts: [{ id: `${date}-${exerciseId}`, source: "LIVE", entries: [{ exerciseId, sets }] }] };
}

function cardio(date: string, builtin: string, meters: number, minutes: number, extra: Partial<CardioSession> = {}): CardioDayLog {
  return {
    date,
    sessions: [
      {
        id: `${date}-${builtin}`,
        activity: { builtin, displayLabel: builtin },
        durationMinutes: minutes,
        distanceMeters: meters,
        ...extra,
      },
    ],
  };
}

function inputs(partial: Partial<StandardInputs> = {}): StandardInputs {
  return {
    weightLogs: [],
    cardioLogs: [],
    bodyWeights: [{ date: "2026-01-01", weightKg: 200 * LB }],
    profile: { ageYears: 40, sex: "male", heightCm: 180 },
    exerciseNames: new Map([
      ["erv-weight-exercise-deadlift-v1", "Deadlift"],
      ["custom-trap", "Trap Bar Deadlift"],
      ["custom-hrp", "Hand-Release Push-Up"],
    ]),
    ...partial,
  };
}

const event = (id: string, standard = ERV_GENERAL): StandardEvent => standard.events.find((e) => e.id === id)!;

describe("scoring", () => {
  const tiers = { BASE: 10, STRONG: 15, ELITE: 20 };
  it("places values along the rings", () => {
    expect(standardScore(5, tiers, false)).toBeCloseTo(0.5);
    expect(standardScore(10, tiers, false)).toBeCloseTo(1);
    expect(standardScore(12.5, tiers, false)).toBeCloseTo(1.5);
    expect(standardScore(25, tiers, false)).toBe(3);
    expect(tierOf(9, tiers, false)).toBeNull();
    expect(tierOf(15, tiers, false)).toBe("STRONG");
  });
  it("flips for times", () => {
    const run = { BASE: 540, STRONG: 450, ELITE: 390 };
    expect(standardScore(495, run, true)).toBeCloseTo(1.5);
    expect(standardScore(1080, run, true)).toBeCloseTo(0.5);
    expect(tierOf(450, run, true)).toBe("STRONG");
    expect(tierOf(600, run, true)).toBeNull();
  });
});

describe("bodyWeightOn", () => {
  const entries = [
    { date: "2026-02-01", weightKg: 90 },
    { date: "2026-03-01", weightKg: 88 },
  ];
  it("uses the last weigh-in on or before the day, else the first after", () => {
    expect(bodyWeightOn(entries, "2026-02-15")).toBe(90);
    expect(bodyWeightOn(entries, "2026-03-01")).toBe(88);
    expect(bodyWeightOn(entries, "2026-01-01")).toBe(90);
    expect(bodyWeightOn([], "2026-01-01")).toBeNull();
  });
});

describe("lift events", () => {
  it("scores est. 1RM against bodyweight and marks it estimated", () => {
    const r = evaluateEvent(
      ERV_GENERAL.id,
      event("squat"),
      inputs({ weightLogs: [lifts("2026-05-01", "erv-weight-exercise-squat-v1", [{ reps: 5, weightKg: 255 * LB }])] }),
    );
    // 255 × (1 + 5/30) = 297.5 lb at 200 lb bodyweight.
    expect(r.value).toBeCloseTo(1.4875, 3);
    expect(r.source).toBe("ESTIMATED");
    expect(r.tier).toBe("BASE");
    expect(r.nextTier).toBe("STRONG");
    expect(r.gapToNext).toBeCloseTo(0.0125, 3);
  });

  it("a real single beats an equal estimate, warm-ups never count", () => {
    const r = evaluateEvent(
      ERV_GENERAL.id,
      event("bench"),
      inputs({
        weightLogs: [
          lifts("2026-05-01", "erv-weight-exercise-bench-v1", [
            { reps: 1, weightKg: 300 * LB, warmup: true },
            { reps: 1, weightKg: 225 * LB },
          ]),
        ],
      }),
    );
    expect(r.value).toBeCloseTo(1.125, 3);
    expect(r.source).toBe("LOGGED");
  });

  it("needs a bodyweight for ratio events", () => {
    const r = evaluateEvent(
      ERV_GENERAL.id,
      event("squat"),
      inputs({
        bodyWeights: [],
        weightLogs: [lifts("2026-05-01", "erv-weight-exercise-squat-v1", [{ reps: 5, weightKg: 100 }])],
      }),
    );
    expect(r.value).toBeNull();
    expect(r.missing).toBe("BODYWEIGHT");
  });

  it("finds a trap bar lift by name, else falls back to deadlift and says so", () => {
    const trap = event("trap-bar-5", SEVEN_EVENT);
    const byName = evaluateEvent(
      SEVEN_EVENT.id,
      trap,
      inputs({ weightLogs: [lifts("2026-05-01", "custom-trap", [{ reps: 5, weightKg: 350 * LB }])] }),
    );
    expect(byName.value).toBeCloseTo(1.75, 3);
    expect(byName.source).toBe("LOGGED");
    expect(byName.note).toBeNull();
    const fallback = evaluateEvent(
      SEVEN_EVENT.id,
      trap,
      inputs({ weightLogs: [lifts("2026-05-01", "erv-weight-exercise-deadlift-v1", [{ reps: 5, weightKg: 300 * LB }])] }),
    );
    expect(fallback.value).toBeCloseTo(1.5, 3);
    expect(fallback.note).toBe("Deadlift used in its place");
  });

  it("a set with more reps estimates a heavier N-rep max", () => {
    const r = evaluateEvent(
      SEVEN_EVENT.id,
      event("trap-bar-5", SEVEN_EVENT),
      inputs({ weightLogs: [lifts("2026-05-01", "custom-trap", [{ reps: 8, weightKg: 300 * LB }])] }),
    );
    // 300 × (1 + 8/30) / (1 + 5/30) = 325.7 lb.
    expect(r.value).toBeCloseTo(1.6286, 3);
    expect(r.source).toBe("ESTIMATED");
  });

  it("bench reps at bodyweight: logged sets, or an estimate from a heavier set", () => {
    const ev = event("bench-bw-reps", SEVEN_EVENT);
    const logged = evaluateEvent(
      SEVEN_EVENT.id,
      ev,
      inputs({ weightLogs: [lifts("2026-05-01", "erv-weight-exercise-bench-v1", [{ reps: 12, weightKg: 200 * LB }])] }),
    );
    expect(logged.value).toBe(12);
    expect(logged.source).toBe("LOGGED");
    const est = evaluateEvent(
      SEVEN_EVENT.id,
      ev,
      inputs({ weightLogs: [lifts("2026-05-01", "erv-weight-exercise-bench-v1", [{ reps: 5, weightKg: 240 * LB }])] }),
    );
    // e1RM 280 lb = 1.4x bodyweight → 30 × 0.4 = 12 reps.
    expect(est.value).toBe(12);
    expect(est.source).toBe("ESTIMATED");
  });

  it("uses the age band for Army targets", () => {
    const dl = event("deadlift-3rm", ARMY_AFT);
    expect(tiersFor(dl, { ageYears: 40 }).BASE).toBeCloseTo(140 * LB, 3);
    expect(tiersFor(dl, { ageYears: 20 }).BASE).toBeCloseTo(150 * LB, 3);
    expect(tiersFor(dl, { ageYears: 70 }).ELITE).toBeCloseTo(230 * LB, 3);
    expect(tiersFor(dl, {}).BASE).toBeCloseTo(150 * LB, 3);
  });
});

describe("cardio events", () => {
  it("a run exactly the distance is logged; a longer one is estimated from its pace", () => {
    const mile = event("mile");
    const exact = evaluateEvent(ERV_GENERAL.id, mile, inputs({ cardioLogs: [cardio("2026-05-01", "RUN", MILE, 8)] }));
    expect(exact.value).toBeCloseTo(480, 0);
    expect(exact.source).toBe("LOGGED");
    const long = evaluateEvent(
      ERV_GENERAL.id,
      mile,
      inputs({ cardioLogs: [cardio("2026-05-01", "RUN", 5 * MILE, 45)] }),
    );
    expect(long.value).toBeCloseTo(540, 0);
    expect(long.source).toBe("ESTIMATED");
    expect(long.note).toBe("Average pace of a 5.0 mi run");
  });

  it("a ruck needs the pack weight", () => {
    const ruck = RUCK_12_MILE.events[0];
    const light = evaluateEvent(
      RUCK_12_MILE.id,
      ruck,
      inputs({ cardioLogs: [cardio("2026-05-01", "RUCK", 12 * MILE, 170, { ruckLoadKg: 20 * LB })] }),
    );
    expect(light.value).toBeNull();
    const heavy = evaluateEvent(
      RUCK_12_MILE.id,
      ruck,
      inputs({ cardioLogs: [cardio("2026-05-01", "RUCK", 12 * MILE, 170, { ruckLoadKg: 35 * LB })] }),
    );
    expect(heavy.value).toBeCloseTo(170 * 60, 0);
    expect(heavy.tier).toBe("BASE");
  });
});

describe("test results", () => {
  it("broad jump counts against height and needs it", () => {
    const jump = event("broad-jump", SEVEN_EVENT);
    const results = [{ standardId: SEVEN_EVENT.id, eventId: "broad-jump", value: 215, date: "2026-05-01" }];
    const r = evaluateEvent(SEVEN_EVENT.id, jump, inputs({ testResults: results }));
    expect(r.value).toBeCloseTo(35, 3);
    expect(r.source).toBe("TESTED");
    expect(r.tier).toBe("STRONG");
    const noHeight = evaluateEvent(
      SEVEN_EVENT.id,
      jump,
      inputs({ testResults: results, profile: { ageYears: 40 } }),
    );
    expect(noHeight.value).toBeNull();
    expect(noHeight.missing).toBe("HEIGHT");
  });

  it("a tested value wins a tie with a logged one", () => {
    const r = evaluateEvent(
      ERV_GENERAL.id,
      event("plank"),
      inputs({
        weightLogs: [lifts("2026-05-01", "erv-weight-exercise-bw-plank-v1", [{ reps: 0, durationSeconds: 150 }])],
        testResults: [{ standardId: ERV_GENERAL.id, eventId: "plank", value: 150, date: "2026-06-01" }],
      }),
    );
    expect(r.source).toBe("TESTED");
    expect(r.date).toBe("2026-06-01");
  });
});

describe("evaluateStandard", () => {
  it("counts tiers and takes the lowest as the overall tier", () => {
    const result = evaluateStandard(
      ERV_GENERAL,
      inputs({
        weightLogs: [
          lifts("2026-05-01", "erv-weight-exercise-squat-v1", [{ reps: 1, weightKg: 300 * LB }]),
          lifts("2026-05-02", "erv-weight-exercise-deadlift-v1", [{ reps: 1, weightKg: 420 * LB }]),
        ],
      }),
    );
    expect(result.scoredCount).toBe(2);
    expect(result.tierCounts).toEqual({ BASE: 0, STRONG: 2, ELITE: 0 });
    expect(result.overallTier).toBe("STRONG");
    expect(result.needsProfile).toEqual([]);
  });

  it("asks for the profile fields a standard uses", () => {
    expect(evaluateStandard(ARMY_AFT, inputs({ profile: {} })).needsProfile).toEqual(["age"]);
    expect(evaluateStandard(ERV_GENERAL, inputs({ profile: {} })).needsProfile).toEqual(["sex"]);
    expect(evaluateStandard(SEVEN_EVENT, inputs({ profile: {} })).needsProfile).toEqual(["height"]);
  });

  it("built-in ids and event ids are unique and tiers run in order", () => {
    const ids = new Set(BUILT_IN_STANDARDS.map((s) => s.id));
    expect(ids.size).toBe(BUILT_IN_STANDARDS.length);
    for (const s of BUILT_IN_STANDARDS) {
      expect(new Set(s.events.map((e) => e.id)).size).toBe(s.events.length);
      for (const e of s.events) {
        for (const tiers of [e.tiers, ...(e.variants ?? []).map((v) => v.tiers)]) {
          if (e.lowerIsBetter) {
            expect(tiers.BASE).toBeGreaterThan(tiers.STRONG);
            expect(tiers.STRONG).toBeGreaterThan(tiers.ELITE);
          } else {
            expect(tiers.BASE).toBeLessThan(tiers.STRONG);
            expect(tiers.STRONG).toBeLessThan(tiers.ELITE);
          }
        }
      }
    }
  });
});
