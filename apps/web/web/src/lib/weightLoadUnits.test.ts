import { describe, expect, it } from "vitest";
import { lbToKg } from "./fitnessEquipment";
import { formatWeightKg } from "./trainingHistory";
import { formatWeightLoadNumber } from "./weightLoadUnit";
import { progressionIncrementKg, roundWeightKg } from "./workoutPrescriptionHints";

describe("pounds display", () => {
  it("shows whole pounds without a trailing .0 after a kg round trip", () => {
    expect(formatWeightLoadNumber(lbToKg(205), "LB")).toBe("205");
    expect(formatWeightLoadNumber(lbToKg(102.5), "LB")).toBe("102.5");
    expect(formatWeightKg(lbToKg(135), "LB")).toBe("135 lb");
    expect(formatWeightKg(60, "KG")).toBe("60 kg");
  });

  it("rounds suggested loads to 2.5 lb steps for pound lifters", () => {
    // 145 lb used to round to 66 kg (145.5 lb).
    expect(formatWeightLoadNumber(roundWeightKg(lbToKg(145), "LB"), "LB")).toBe("145");
    expect(formatWeightLoadNumber(roundWeightKg(lbToKg(146.4), "LB"), "LB")).toBe("147.5");
    expect(roundWeightKg(61.3, "KG")).toBe(61.5);
  });

  it("uses 2.5, 5 and 10 lb progression steps in pounds", () => {
    const label = (style: "conservative" | "moderate" | "aggressive") =>
      formatWeightLoadNumber(progressionIncrementKg(style, "LB"), "LB");
    expect(label("conservative")).toBe("2.5");
    expect(label("moderate")).toBe("5");
    expect(label("aggressive")).toBe("10");
    expect(progressionIncrementKg("moderate", "KG")).toBe(2.5);
  });
});
