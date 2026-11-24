import { describe, expect, it } from "vitest";
import {
  addIntervalLeg,
  cardioItemSummary,
  removeIntervalLeg,
  updateIntervalLeg,
  type WorkoutCardioIntervalLeg,
  type WorkoutItem,
} from "./workoutTraining";

const legs: WorkoutCardioIntervalLeg[] = [
  { workSeconds: 240, restSeconds: 120, label: "Hard" },
  { workSeconds: 60, restSeconds: 30, label: "Sprint" },
  { workSeconds: 300, restSeconds: 0, label: "Easy" },
];

describe("interval template legs", () => {
  it("changes one leg and keeps the others", () => {
    const next = updateIntervalLeg(legs, 1, { workSeconds: 45 });
    expect(next).toEqual([legs[0], { ...legs[1], workSeconds: 45 }, legs[2]]);
  });

  it("starts from a default leg when there are none", () => {
    expect(updateIntervalLeg(undefined, 0, { restSeconds: 60 })).toEqual([
      { workSeconds: 240, restSeconds: 60 },
    ]);
  });

  it("adds a leg that copies the last one", () => {
    expect(addIntervalLeg(legs)).toHaveLength(4);
    expect(addIntervalLeg(legs)[3]).toEqual({ workSeconds: 300, restSeconds: 0 });
  });

  it("removes a leg but never the last one", () => {
    expect(removeIntervalLeg(legs, 0)).toEqual([legs[1], legs[2]]);
    expect(removeIntervalLeg([legs[0]!], 0)).toEqual([legs[0]]);
  });

  it("summarises every leg", () => {
    const item = {
      type: "cardio",
      cardio: { activity: "run", mode: "interval_template", outerRounds: 2, legs },
    } as unknown as Extract<WorkoutItem, { type: "cardio" }>;
    expect(cardioItemSummary(item, "Run")).toContain(
      "3 legs: 240s/120s, 60s/30s, 300s/0s",
    );
  });
});
