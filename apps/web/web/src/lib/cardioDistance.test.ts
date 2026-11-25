import { describe, expect, it } from "vitest";
import {
  cardioDistanceUnitFor,
  formatCardioDistance,
  formatPaceBand,
  parseCardioDistance,
  parsePaceBand,
} from "./cardioDistance";

describe("cardio distance", () => {
  it("uses miles unless the athlete weighs in kilograms", () => {
    expect(cardioDistanceUnitFor("LB")).toBe("MI");
    expect(cardioDistanceUnitFor(undefined)).toBe("MI");
    expect(cardioDistanceUnitFor("KG")).toBe("KM");
  });

  it("reads plain numbers in the unit and named units as written", () => {
    expect(parseCardioDistance("1", "MI")).toBeCloseTo(1609.3, 1);
    expect(parseCardioDistance(".25", "MI")).toBeCloseTo(402.3, 1);
    expect(parseCardioDistance("400m", "MI")).toBe(400);
    expect(parseCardioDistance("5k", "MI")).toBe(5000);
    expect(parseCardioDistance("3 km", "KM")).toBe(3000);
    expect(parseCardioDistance("2 mi", "KM")).toBeCloseTo(3218.7, 1);
    expect(parseCardioDistance("", "MI")).toBeNull();
    expect(parseCardioDistance("far", "MI")).toBeUndefined();
    expect(parseCardioDistance("0", "MI")).toBeUndefined();
  });

  it("shows distances in the unit", () => {
    expect(formatCardioDistance(1609.344, "MI")).toBe("1");
    expect(formatCardioDistance(402.336, "MI")).toBe("0.25");
    expect(formatCardioDistance(400, "KM")).toBe("400 m");
    expect(formatCardioDistance(5000, "KM")).toBe("5");
    expect(formatCardioDistance(null, "MI")).toBe("");
  });

  it("reads and shows pace bands per mile, stored per km", () => {
    const band = parsePaceBand("10:00-9:30", "MI")!;
    expect(band.fast).toBeCloseTo(354.2, 1);
    expect(band.slow).toBeCloseTo(372.8, 1);
    expect(formatPaceBand(band.fast, band.slow, "MI")).toBe("9:30-10:00");
    const one = parsePaceBand("5:00", "KM")!;
    expect(one).toEqual({ fast: 300, slow: 300 });
    expect(formatPaceBand(300, 300, "KM")).toBe("5:00");
    expect(parsePaceBand("", "MI")).toEqual({ fast: null, slow: null });
    expect(parsePaceBand("fast", "MI")).toBeUndefined();
  });
});
