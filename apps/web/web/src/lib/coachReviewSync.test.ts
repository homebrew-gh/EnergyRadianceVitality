import { describe, expect, it } from "vitest";
import type { CoachReviewJson } from "./coachReview";
import { buildCoachReviewSyncPayload, COACH_REVIEW_D_TAG } from "./coachReviewSync";
import type { WeightExercise } from "./weightTraining";

const exercises: WeightExercise[] = [
  { id: "erv-weight-exercise-bench-v1", name: "Bench Press", muscleGroup: "chest", pushOrPull: "push", equipment: "barbell" },
  { id: "custom-row", name: "Seal Row", muscleGroup: "back", pushOrPull: "pull", equipment: "barbell" },
];

const review: CoachReviewJson = {
  headline: "Hold bench, add rows.",
  status: { consistency: "on_track", progress: "flat", recovery: "ok" },
  lifts: [
    { name: "bench press", trend: "flat", next: "185 × 8, 8, 8", why: "Reps fell." },
    { name: "Seal Row", trend: "up", next: "135 × 10 × 4", why: "Pulling is low." },
    { name: "Made Up Lift", trend: "new", next: "95 × 10", why: "" },
  ],
  nextWeek: [{ action: "Add a row set.", why: "" }],
  wins: [],
  watchOuts: [],
  bodyWeight: null,
  confidence: "medium",
  missingData: null,
};

describe("buildCoachReviewSyncPayload", () => {
  it("uses the shared d-tag", () => {
    expect(COACH_REVIEW_D_TAG).toBe("erv/coach/review");
  });

  it("maps review lifts to exercise ids and skips unknown names", () => {
    const payload = buildCoachReviewSyncPayload({
      review,
      exercises,
      generatedAt: new Date("2026-11-25T12:00:00Z"),
      periodLabel: "Last 4 weeks",
      periodStart: "2026-10-29",
      periodEnd: "2026-11-25",
      unit: "LB",
      model: "test-model",
      promptVersion: "3",
    });
    expect(payload.ervCoachReviewVersion).toBe(1);
    expect(payload.generatedAtEpochSeconds).toBe(1795608000);
    expect(payload.weightUnit).toBe("lb");
    expect(payload.liftTargets.map((target) => target.exerciseId)).toEqual([
      "erv-weight-exercise-bench-v1",
      "custom-row",
    ]);
  });
});
