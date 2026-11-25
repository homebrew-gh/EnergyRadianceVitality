import { describe, expect, it } from "vitest";
import { parseBodyWeightLogs } from "./bodyWeight";
import {
  buildBodyWeightTrend,
  buildLiftFacts,
  buildWeeklySetsByMuscle,
  buildWeeklyTraining,
} from "./coachFacts";
import { lbToKg } from "./fitnessEquipment";
import type { WeightDayLog, WeightSet } from "./trainingHistory";
import type { WeightExercise } from "./weightTraining";
import type { Workout } from "./workoutTraining";

const bench: WeightExercise = {
  id: "bench",
  name: "Bench Press",
  muscleGroup: "chest",
  pushOrPull: "push",
  equipment: "barbell",
};
const squat: WeightExercise = {
  id: "squat",
  name: "Back Squat",
  muscleGroup: "legs",
  pushOrPull: "push",
  equipment: "barbell",
};

function sets(lb: number, reps: number[], rpe?: number): WeightSet[] {
  return reps.map((r) => ({ reps: r, weightKg: lbToKg(lb), rpe: rpe ?? null }));
}

function day(date: string, entries: { exerciseId: string; sets: WeightSet[] }[]): WeightDayLog {
  return { date, workouts: [{ id: date, source: "LIVE", routineName: "Day", entries }] };
}

const plan: Workout = {
  id: "w1",
  name: "Push Day",
  segments: [
    {
      kind: "straight_sets",
      items: [
        {
          type: "weight",
          exerciseId: "bench",
          prescription: { setCount: 3, repRangeMin: 8, repRangeMax: 10, targetWeightKg: lbToKg(185), restBetweenSetsSeconds: 120 },
        },
        {
          type: "weight",
          exerciseId: "squat",
          prescription: { setCount: 3, repRangeMin: 5, repRangeMax: 6 },
        },
      ],
    },
  ],
};

const logs: WeightDayLog[] = [
  day("2026-11-20", [
    { exerciseId: "bench", sets: sets(185, [8, 7, 6], 9.5) },
    { exerciseId: "squat", sets: sets(225, [6, 6, 6]) },
  ]),
  day("2026-11-13", [
    { exerciseId: "bench", sets: sets(185, [8, 8, 6]) },
    { exerciseId: "squat", sets: sets(215, [6, 6, 6]) },
  ]),
  day("2026-11-06", [{ exerciseId: "bench", sets: sets(185, [9, 8, 8]) }]),
];

function facts() {
  return buildLiftFacts({
    weightLogs: logs,
    exercises: [bench, squat],
    workouts: [plan],
    unit: "LB",
    today: "2026-11-24",
    liftLimit: 8,
    sessionsPerLift: 3,
  });
}

describe("buildLiftFacts", () => {
  it("ranks lifts by how often they were done and shows the plan in pounds", () => {
    const [first] = facts();
    expect(first.name).toBe("Bench Press");
    expect(first.planned).toBe("3 × 8–10 @ 185 lb, rest 2:00");
    expect(first.plannedIn).toEqual(["Push Day"]);
    expect(first.recentSessions[0]).toMatchObject({ date: "2026-11-20", sets: "185×8, 185×7, 185×6 lb", avgRpe: 9.5 });
  });

  it("flags a stalled lift that missed its rep target twice and suggests a reset", () => {
    const benchFact = facts()[0];
    expect(benchFact.trend).toBe("down");
    expect(benchFact.flags).toContain("no new best for 2 sessions");
    expect(benchFact.flags).toContain("missed rep target last session (planned 8+)");
    expect(benchFact.flags).toContain("RPE 9.5 last session");
    expect(benchFact.ruleSuggestion).toBe("missed reps twice: drop to about 165 lb and build back up");
  });

  it("adds 10 lb to a lower-body lift that hit the top of its range", () => {
    const squatFact = facts()[1];
    expect(squatFact.trend).toBe("up");
    expect(squatFact.flags).toContain("new best in period");
    expect(squatFact.flags).toContain("hit top of rep range on every set");
    expect(squatFact.ruleSuggestion).toBe("add 10 lb: 235 lb × 5");
  });

  it("marks a lift with one session as new and flags a long gap", () => {
    const [only] = buildLiftFacts({
      weightLogs: [day("2026-10-01", [{ exerciseId: "bench", sets: sets(135, [10, 10]) }])],
      exercises: [bench],
      workouts: [],
      unit: "LB",
      today: "2026-11-24",
      liftLimit: 8,
      sessionsPerLift: 3,
    });
    expect(only.trend).toBe("new");
    expect(only.flags).toContain("last done 54 days ago");
    expect(only.ruleSuggestion).toBe("hold 135 lb and add a rep per set");
  });
});

describe("weekly facts", () => {
  it("counts training days per week against the plan", () => {
    const weekly = buildWeeklyTraining({
      weightLogs: logs,
      cardioLogs: [],
      today: "2026-11-24",
      periodStart: "2026-10-28",
      plannedDaysPerWeek: 4,
    });
    expect(weekly.weeks).toHaveLength(4);
    expect(weekly.weeks[0]).toMatchObject({ weekStart: "2026-11-18", weekEnd: "2026-11-24", trainingDays: 1 });
    expect(weekly.averageTrainingDaysPerWeek).toBe(0.8);
  });

  it("averages sets per muscle group per week", () => {
    const rows = buildWeeklySetsByMuscle({
      weightLogs: logs,
      exercises: [bench, squat],
      today: "2026-11-24",
      periodStart: "2026-10-28",
    });
    expect(rows).toEqual([
      { muscleGroup: "Chest", setsPerWeek: 2.3 },
      { muscleGroup: "Legs", setsPerWeek: 1.5 },
    ]);
  });
});

describe("buildBodyWeightTrend", () => {
  it("reports the latest weight, change and weekly averages in pounds", () => {
    const trend = buildBodyWeightTrend({
      entries: [
        { date: "2026-10-01", weightKg: lbToKg(190) },
        { date: "2026-11-01", weightKg: lbToKg(186) },
        { date: "2026-11-19", weightKg: lbToKg(184) },
        { date: "2026-11-23", weightKg: lbToKg(183) },
      ],
      today: "2026-11-24",
      periodStart: "2026-10-28",
      unit: "LB",
    });
    expect(trend).toMatchObject({
      unit: "lb",
      latest: { date: "2026-11-23", weight: 183 },
      entriesInPeriod: 3,
      changeOverPeriod: -3,
    });
    expect(trend?.weeklyAverages[0]).toEqual({ weekEnd: "2026-11-24", average: 183.5, entries: 2 });
  });

  it("returns null when nothing was logged in the period", () => {
    expect(
      buildBodyWeightTrend({ entries: [], today: "2026-11-24", periodStart: null, unit: "LB" }),
    ).toBeNull();
  });
});

describe("parseBodyWeightLogs", () => {
  it("reads weights from body tracker day logs only", () => {
    const parsed = parseBodyWeightLogs([
      { event_id: "1", d_tag: "erv/body_tracker/2026-11-23", ciphertext: "", plaintext: JSON.stringify({ date: "2026-11-23", weightKg: 83 }) },
      { event_id: "2", d_tag: "erv/body_tracker/2026-11-22", ciphertext: "", plaintext: JSON.stringify({ date: "2026-11-22", weightKg: null }) },
      { event_id: "3", d_tag: "erv/body_tracker/settings", ciphertext: "", plaintext: "{}" },
    ]);
    expect(parsed).toEqual([{ date: "2026-11-23", weightKg: 83 }]);
  });
});
