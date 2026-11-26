import { describe, expect, it } from "vitest";
import {
  cleanedHoldOverrides,
  parseStretchRoutinesPayload,
  stretchGapFor,
  stretchHoldFor,
  stretchRoutineMinutes,
  type StretchRoutine,
} from "./stretchTraining";

const catalog = [
  { id: "neck", requiresBothSides: true },
  { id: "child", requiresBothSides: false },
];

describe("stretch routine holds", () => {
  it("reads routines saved before per-stretch holds", () => {
    const [r] = parseStretchRoutinesPayload(
      JSON.stringify({ routines: [{ id: "x", name: "Old", stretchIds: ["neck"], holdSecondsPerStretch: 45 }] }),
    );
    expect(stretchHoldFor(r!, "neck")).toBe(45);
    expect(stretchGapFor(r!)).toBe(10);
  });

  it("reads the phone's nulls and empty maps", () => {
    const r: StretchRoutine = {
      id: "x",
      name: "Phone",
      stretchIds: ["child"],
      holdSecondsPerStretch: 30,
      holdSecondsByStretchId: {},
      gapSeconds: null,
    };
    expect(stretchHoldFor(r, "child")).toBe(30);
    expect(stretchGapFor(r)).toBe(10);
  });

  it("matches the phone's planned minutes", () => {
    // Same case as Android GuidedStretchSessionStateTest: neck 45 a side, child 120, 30 s gap → 255 s → 5 min.
    const r: StretchRoutine = {
      id: "x",
      name: "R",
      stretchIds: ["neck", "child"],
      holdSecondsPerStretch: 30,
      holdSecondsByStretchId: { neck: 45, child: 120 },
      gapSeconds: 30,
    };
    expect(stretchRoutineMinutes(r, catalog)).toBe(5);
  });

  it("drops stale and default per-stretch times", () => {
    expect(cleanedHoldOverrides({ neck: 30, child: 90, gone: 40 }, ["neck", "child"], 30)).toEqual({ child: 90 });
  });
});
