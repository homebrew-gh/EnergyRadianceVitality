import { describe, expect, it } from "vitest";
import { buildAnalysisContext, loadInUnit, profileForReview, setDetail } from "./aiContext";
import { lbToKg } from "./fitnessEquipment";
import type { TrainingContextBundleInput } from "./trainingContextBundle";
import type { WeightDayLog } from "./trainingHistory";
import { emptyTrainingProfile } from "./trainingProfile";
import { buildTrainingSnapshot } from "./trainingSnapshot";
import type { WeightExercise } from "./weightTraining";

const bench: WeightExercise = {
  id: "erv-weight-exercise-bench-press",
  name: "Bench Press",
  muscleGroup: "chest",
  pushOrPull: "push",
  equipment: "barbell",
};

const logs: WeightDayLog[] = [
  {
    date: "2026-11-20",
    workouts: [
      {
        id: "s1",
        source: "LIVE",
        routineName: "Push Day",
        entries: [
          {
            exerciseId: bench.id,
            sets: [
              { reps: 8, weightKg: lbToKg(185) },
              { reps: 7, weightKg: lbToKg(185) },
            ],
          },
        ],
      },
    ],
  },
];

function input(): TrainingContextBundleInput {
  const profile = {
    ...emptyTrainingProfile(),
    primaryGoal: "hypertrophy" as const,
    stylePresetIds: ["hypertrophy_bodybuilding"],
  };
  return {
    profile,
    snapshot: buildTrainingSnapshot({
      weightLogs: logs,
      cardioLogs: [],
      exercises: [bench],
      computedAtMs: Date.parse("2026-11-24T12:00:00Z"),
    }),
    equipment: { gymMembership: true, equipment: [], enabledWeightExercisePackIds: [] },
    exercises: [bench],
    stretchCatalog: [],
    cardioCatalog: [],
    workouts: [],
    weightRoutines: [],
    cardioRoutines: [],
    stretchRoutines: [],
    weightLogs: logs,
    cardioLogs: [],
    equipmentUnit: "LB",
  };
}

describe("loadInUnit", () => {
  it("rounds stored kilograms back to whole pounds", () => {
    expect(loadInUnit(lbToKg(185), "LB")).toBe(185);
    expect(loadInUnit(lbToKg(102.5), "LB")).toBe(102.5);
    expect(loadInUnit(100, "KG")).toBe(100);
  });
});

describe("setDetail", () => {
  it("lists each set in pounds", () => {
    expect(
      setDetail(
        [
          { reps: 8, weightKg: lbToKg(185) },
          { reps: 6, weightKg: lbToKg(185) },
        ],
        "LB",
      ),
    ).toBe("185×8, 185×6 lb");
  });

  it("leaves bodyweight sets without a unit", () => {
    expect(setDetail([{ reps: 12 }, { reps: 10 }], "LB")).toBe("12 reps, 10 reps");
  });
});

describe("profileForReview", () => {
  it("sends labels and style hints instead of codes", () => {
    const profile = profileForReview(input().profile);
    expect(profile?.primaryGoal).not.toBe("hypertrophy_bodybuilding");
    const styles = profile?.trainingStyles as { name: string; coachingHints: string[] }[];
    expect(styles[0].coachingHints.length).toBeGreaterThan(0);
  });

  it("returns null for a blank profile", () => {
    expect(profileForReview(emptyTrainingProfile())).toBeNull();
  });
});

describe("buildAnalysisContext", () => {
  it("never sends kilogram fields when the athlete uses pounds", () => {
    const { context, json } = buildAnalysisContext(input(), {
      contextLevel: "FULL",
      periodWeeks: 4,
    });
    expect(json).not.toMatch(/Kg"/);
    expect(json).not.toMatch(/\bkg\b/);
    expect(context.units).toEqual({ weight: "lb", distance: "mi" });
    expect(context.today).toBe("2026-11-24");
    expect(context.periodStart).toBe("2026-10-28");
    expect(context.snapshot).not.toHaveProperty("workingWeights");
    const lifts = context.liftTrends as { name: string; recentSessions: { sets: string }[] }[];
    expect(lifts[0].name).toBe("Bench Press");
    expect(lifts[0].recentSessions[0].sets).toBe("185×8, 185×7 lb");
  });

  it("sends a short heart-rate note when there is no heart-rate data", () => {
    const { context } = buildAnalysisContext(input(), { contextLevel: "STANDARD", periodWeeks: 4 });
    expect(context.progressionGuardrails).toMatchObject({
      heartRate: "No heart-rate data in this period.",
    });
  });
});
