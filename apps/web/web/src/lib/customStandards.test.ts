import { describe, expect, it } from "vitest";
import {
  deleteCustomStandard,
  draftToStandard,
  emptyCustomStandards,
  newEventDraft,
  parseCustomStandards,
  serializeCustomStandards,
  standardToDraft,
  upsertCustomStandard,
  type CustomStandardDraft,
} from "./customStandards";
import { SEVEN_EVENT } from "./standardsLibrary";

const LB = 0.45359237;
const names = new Map([["bench", "Bench Press"]]);
const newId = () => "custom-abc";

function draft(partial: Partial<CustomStandardDraft> = {}): CustomStandardDraft {
  return {
    id: null,
    name: "My Card",
    description: "",
    events: [
      { ...newEventDraft(), type: "LIFT_LOAD", exerciseId: "bench", reps: "5", base: "185", strong: "225", elite: "275" },
      { ...newEventDraft(), name: "Mile", type: "CARDIO", cardioGroup: "RUN", distanceKey: "1mi", base: "9:00", strong: "8:00", elite: "7:00" },
      { ...newEventDraft(), name: "Ruck", type: "CARDIO", cardioGroup: "RUCK", distanceKey: "12mi", minPack: "45", base: "3:30:00", strong: "3:00:00", elite: "2:45:00" },
    ],
    ...partial,
  };
}

describe("draftToStandard", () => {
  it("builds canonical events from typed values", () => {
    const out = draftToStandard(draft(), "LB", names, newId);
    if (!("standard" in out)) throw new Error(out.errors.join(", "));
    const [bench, mile, ruck] = out.standard.events;
    expect(out.standard.id).toBe("custom-abc");
    expect(bench.name).toBe("Bench Press");
    expect(bench.measure).toEqual({ kind: "LIFT_MAX_LOAD", reps: 5 });
    expect(bench.tiers.BASE).toBeCloseTo(185 * LB, 4);
    expect(bench.detail).toBe("Bench Press: Heaviest 5 reps");
    expect(mile.lowerIsBetter).toBe(true);
    expect(mile.tiers).toEqual({ BASE: 540, STRONG: 480, ELITE: 420 });
    expect(ruck.measure).toMatchObject({ kind: "FASTEST", cardioGroups: ["RUCK"] });
    expect((ruck.measure as { minPackKg: number }).minPackKg).toBeCloseTo(45 * LB, 4);
  });

  it("lists what to fix", () => {
    const bad = draft({
      name: " ",
      events: [{ ...newEventDraft(), name: "Run", type: "CARDIO", base: "7:00", strong: "8:00", elite: "9:00" }],
    });
    const out = draftToStandard(bad, "LB", names, newId);
    expect("errors" in out && out.errors).toEqual([
      "Give the standard a name.",
      "Run: Elite must be faster than Strong, and Strong faster than Base.",
    ]);
  });

  it("round-trips a built-in as a copy, keeping targets", () => {
    const copy = standardToDraft(SEVEN_EVENT, "LB", false);
    expect(copy.id).toBeNull();
    expect(copy.name).toBe("Seven-Event Performance (copy)");
    const jump = copy.events.at(0)!;
    expect([jump.type, jump.base, jump.strong, jump.elite]).toEqual(["TEST_JUMP", "0", "12", "24"]);
    const trap = copy.events.at(3)!;
    expect([trap.type, trap.reps, trap.base]).toEqual(["LIFT_BW", "5", "1.5"]);
  });

  it("keeps event ids when editing so results stay attached", () => {
    const first = draftToStandard(draft(), "LB", names, newId);
    if (!("standard" in first)) throw new Error("no standard");
    const edit = standardToDraft(first.standard, "LB", true);
    edit.events.reverse();
    const second = draftToStandard(edit, "LB", names, newId);
    if (!("standard" in second)) throw new Error("no standard");
    expect(second.standard.events.map((e) => e.id).sort()).toEqual(first.standard.events.map((e) => e.id).sort());
  });
});

describe("custom standards payload", () => {
  it("round-trips and honours deletes", () => {
    const out = draftToStandard(draft(), "LB", names, newId);
    if (!("standard" in out)) throw new Error("no standard");
    let p = upsertCustomStandard(emptyCustomStandards(), out.standard, 100);
    expect(parseCustomStandards(serializeCustomStandards(p, 101)).standards.map((s) => s.id)).toEqual(["custom-abc"]);
    p = deleteCustomStandard(p, "custom-abc", 102);
    const back = parseCustomStandards(serializeCustomStandards(p, 103));
    expect(back.standards).toEqual([]);
    expect(back.deletedStandards).toEqual({ "custom-abc": 102 });
  });
  it("ignores standards without the custom id prefix", () => {
    const raw = JSON.stringify({ standards: [{ ...SEVEN_EVENT, lastModifiedEpochSeconds: 1 }] });
    expect(parseCustomStandards(raw).standards).toEqual([]);
  });
});
