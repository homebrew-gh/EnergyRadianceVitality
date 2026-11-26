import { describe, expect, it } from "vitest";
import { isTrainingProfileBlank, parseTrainingProfilePayload, trainingProfilePayload } from "./trainingProfile";

describe("training profile sex and height", () => {
  it("round-trip and count as content", () => {
    const parsed = parseTrainingProfilePayload(
      JSON.stringify({ profileVersion: 2, stylePresetIds: [], avoidMovementPatterns: [], sex: "female", heightCm: 167.64, lastModifiedEpochSeconds: 5 }),
    );
    expect(parsed.sex).toBe("female");
    expect(parsed.heightCm).toBe(167.6);
    expect(isTrainingProfileBlank(parsed)).toBe(false);
    const again = JSON.parse(trainingProfilePayload(parsed));
    expect(again.sex).toBe("female");
    expect(again.heightCm).toBe(167.6);
  });

  it("drop values out of range", () => {
    const parsed = parseTrainingProfilePayload(JSON.stringify({ sex: "other", heightCm: 900 }));
    expect(parsed.sex).toBeNull();
    expect(parsed.heightCm).toBeNull();
  });
});
