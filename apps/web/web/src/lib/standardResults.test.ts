import { describe, expect, it } from "vitest";
import {
  addStandardResult,
  emptyStandardResults,
  parseClockInput,
  parseStandardResults,
  removeStandardResult,
  serializeStandardResults,
} from "./standardResults";
import { evaluateEvent } from "./standards";
import { ERV_GENERAL } from "./standardsLibrary";

const NOW = 1_800_000_000;

describe("standard results payload", () => {
  it("round-trips, sorts newest first, and keeps delete markers", () => {
    let p = addStandardResult(emptyStandardResults(), { id: "a", standardId: "s", eventId: "e", value: 90, date: "2026-01-01" }, NOW);
    p = addStandardResult(p, { id: "b", standardId: "s", eventId: "e", value: 120, date: "2026-02-01", note: "felt good" }, NOW);
    p = removeStandardResult(p, "a", NOW + 1);
    const back = parseStandardResults(serializeStandardResults(p, NOW + 2));
    expect(back.results.map((r) => r.id)).toEqual(["b"]);
    expect(back.results[0].note).toBe("felt good");
    expect(back.deletedResults).toEqual({ a: NOW + 1 });
  });

  it("drops a result deleted after its last edit, and bad rows", () => {
    const raw = JSON.stringify({
      results: [
        { id: "a", standardId: "s", eventId: "e", value: 5, date: "2026-01-01", lastModifiedEpochSeconds: 10 },
        { id: "b", standardId: "s", eventId: "e", value: -1, date: "2026-01-01" },
        { id: "c", standardId: "s", eventId: "e", value: 5, date: "Jan 1" },
      ],
      deletedResults: { a: 20 },
    });
    expect(parseStandardResults(raw).results).toEqual([]);
    expect(parseStandardResults("not json").results).toEqual([]);
  });

  it("forgets delete markers after a year", () => {
    const p = removeStandardResult(emptyStandardResults(), "old", NOW);
    expect(JSON.parse(serializeStandardResults(p, NOW + 366 * 24 * 3600)).deletedResults).toEqual({});
  });
});

describe("parseClockInput", () => {
  it("reads m:ss, h:mm:ss and seconds", () => {
    expect(parseClockInput("2:30")).toBe(150);
    expect(parseClockInput("1:02:15")).toBe(3735);
    expect(parseClockInput("95")).toBe(95);
    expect(parseClockInput("2:75")).toBeNull();
    expect(parseClockInput("abc")).toBeNull();
    expect(parseClockInput("")).toBeNull();
  });
});

describe("tested lift loads", () => {
  it("are divided by bodyweight on the day", () => {
    const squat = ERV_GENERAL.events.find((e) => e.id === "squat")!;
    const r = evaluateEvent(ERV_GENERAL.id, squat, {
      weightLogs: [],
      cardioLogs: [],
      bodyWeights: [{ date: "2026-01-01", weightKg: 100 }],
      profile: { sex: "male" },
      exerciseNames: new Map(),
      testResults: [{ standardId: ERV_GENERAL.id, eventId: "squat", value: 160, date: "2026-03-01" }],
    });
    expect(r.value).toBeCloseTo(1.6);
    expect(r.source).toBe("TESTED");
    expect(r.tier).toBe("STRONG");
  });
});
