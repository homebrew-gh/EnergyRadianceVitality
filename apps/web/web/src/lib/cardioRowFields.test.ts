import { describe, expect, it } from "vitest";
import {
  applyCardioAmount,
  applyCardioHeartRate,
  applyCardioRest,
  applyCardioWork,
  applyMobilityHold,
  applyMobilityPerSide,
  cardioAmountField,
  cardioHeartRateField,
  cardioWorkField,
  mobilityPerSideField,
  setCardioMode,
} from "./cardioRowFields";
import type { WorkoutCardioPrescription } from "./workoutTraining";

const steady: WorkoutCardioPrescription = { activity: "RUN", mode: "steady", targetMinutes: 20 };

describe("cardio row fields", () => {
  it("reads minutes for steady and rounds for sprints and interval legs", () => {
    expect(cardioAmountField(steady)).toMatchObject({ text: "20", hint: "Minutes" });
    expect(applyCardioAmount(steady, "30").targetMinutes).toBe(30);
    expect(applyCardioAmount(steady, "soon")).toBe(steady);
    const sprints = setCardioMode(steady, "sprint_intervals");
    expect(sprints).toMatchObject({ rounds: 10, workSeconds: 60, restSeconds: 60, targetMinutes: 20 });
    expect(applyCardioAmount(sprints, "8").rounds).toBe(8);
    const legs = setCardioMode(steady, "interval_template");
    expect(applyCardioAmount(legs, "4").outerRounds).toBe(4);
    expect(cardioWorkField(legs)).toMatchObject({ text: "1 leg", editable: false });
  });

  it("reads sprint work and rest as m:ss and ignores them for other modes", () => {
    const sprints = setCardioMode(steady, "sprint_intervals");
    expect(applyCardioWork(sprints, "0:30").workSeconds).toBe(30);
    expect(applyCardioRest(sprints, "1:30").restSeconds).toBe(90);
    expect(applyCardioWork(steady, "0:30")).toBe(steady);
    expect(cardioWorkField(steady).editable).toBe(false);
  });

  it("takes one heart rate or a range, in either order", () => {
    const one = applyCardioHeartRate(steady, "140");
    expect(one).toMatchObject({ hrTargetBpm: 140, hrTargetMinBpm: null });
    const range = applyCardioHeartRate(one, "150 – 130");
    expect(range).toMatchObject({ hrTargetBpm: null, hrTargetMinBpm: 130, hrTargetMaxBpm: 150 });
    expect(cardioHeartRateField(range).text).toBe("130-150");
    expect(applyCardioHeartRate(range, "zone 2")).toBe(range);
    expect(applyCardioHeartRate(range, "")).toMatchObject({ hrTargetMinBpm: null, hrTargetMaxBpm: null });
  });
});

describe("mobility row fields", () => {
  it("reads holds as m:ss", () => {
    const m = applyMobilityHold({ catalogId: "s" }, "1:00");
    expect(m.holdSeconds).toBe(60);
    const sided = applyMobilityPerSide(m, "30");
    expect(mobilityPerSideField(sided).text).toBe("0:30");
    expect(applyMobilityPerSide(sided, "").holdSecondsPerSide).toBeNull();
  });
});
