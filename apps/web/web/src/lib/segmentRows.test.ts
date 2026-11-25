import { describe, expect, it } from "vitest";
import { copyRowDown, duplicateItemAt, moveItem, moveItemBetween, segmentAcceptsItem, swapExercise } from "./segmentRows";
import type { WorkoutItem, WorkoutSegment } from "./workoutTraining";

type WeightItem = Extract<WorkoutItem, { type: "weight" }>;
const lift = (id: string, exerciseId: string, prescription = {}): WeightItem => ({ type: "weight", id, exerciseId, prescription });

const segment: WorkoutSegment = {
  id: "s1",
  kind: "straight_sets",
  items: [
    lift("a", "bench", { mode: "straight", setCount: 4, repRangeMin: 6, repRangeMax: 8, targetRir: 2, restBetweenSetsSeconds: 150, targetWeightKg: 80 }),
    lift("b", "row", { setCount: 3, targetReps: 10, targetWeightKg: 60 }),
    lift("c", "plank", { mode: "time_based", setCount: 3, durationSeconds: 45 }),
  ],
};

describe("segment rows", () => {
  it("duplicates a row right after itself with a new id", () => {
    const next = duplicateItemAt(segment, 0, "a2");
    expect(next.items!.map((i) => i.id)).toEqual(["a", "a2", "b", "c"]);
    expect((next.items![1] as WeightItem).prescription).toEqual((segment.items![0] as WeightItem).prescription);
    expect((next.items![1] as WeightItem).prescription).not.toBe((segment.items![0] as WeightItem).prescription);
  });

  it("moves rows within a segment and between segments", () => {
    expect(moveItem(segment, 2, 0).items!.map((i) => i.id)).toEqual(["c", "a", "b"]);
    expect(moveItem(segment, 0, 5)).toBe(segment);
    const other: WorkoutSegment = { id: "s2", kind: "straight_sets", items: [lift("x", "squat")] };
    const moved = moveItemBetween(segment, 1, other, 0)!;
    expect(moved.source.items!.map((i) => i.id)).toEqual(["a", "c"]);
    expect(moved.target.items!.map((i) => i.id)).toEqual(["b", "x"]);
    expect(moveItemBetween(segment, 0, { ...other, items: [lift("y", "bench")] }, 0)).toBeNull();
  });

  it("only moves rows into segments that can hold them", () => {
    const stretch: WorkoutItem = { type: "mobility", id: "m", mobility: { catalogId: "hip" } };
    const note: WorkoutItem = { type: "note", id: "n", text: "Breathe" };
    const mobility: WorkoutSegment = { id: "s3", kind: "mobility", items: [stretch] };
    const flow: WorkoutSegment = { id: "s4", kind: "composite", items: [] };
    const superset: WorkoutSegment = { id: "s5", kind: "superset", items: [] };
    expect(segmentAcceptsItem(mobility, segment.items![0]!)).toBe(false);
    expect(segmentAcceptsItem(flow, segment.items![0]!)).toBe(true);
    expect(segmentAcceptsItem(superset, note)).toBe(false);
    expect(moveItemBetween(segment, 0, mobility, 0)).toBeNull();
    expect(moveItemBetween(mobility, 0, flow, 0)!.target.items).toHaveLength(1);
    expect(moveItemBetween(mobility, 0, { ...flow, items: [{ ...stretch, id: "m2" }] }, 0)).toBeNull();
  });

  it("swaps the exercise and keeps the numbers", () => {
    const item = { ...(segment.items![0] as WeightItem), title: "Bench Press", alternativeExerciseIds: ["dbbench", "incline"] };
    const swapped = swapExercise(item, "dbbench", "Dumbbell Bench");
    expect(swapped).toMatchObject({ exerciseId: "dbbench", title: "Dumbbell Bench", alternativeExerciseIds: ["incline"] });
    expect(swapped.prescription).toBe(item.prescription);
  });

  it("copies sets, reps, RIR and rest down but not weight, and skips values a row cannot take", () => {
    const next = copyRowDown(segment, 0, (id) => (id === "plank" ? "time_only" : "reps"));
    const [, row, plank] = next.items as WeightItem[];
    expect(row!.prescription).toMatchObject({ setCount: 4, repRangeMin: 6, repRangeMax: 8, targetReps: null, targetRir: 2, restBetweenSetsSeconds: 150, targetWeightKg: 60 });
    expect(plank!.prescription).toMatchObject({ setCount: 4, durationSeconds: 45, targetRir: 2, restBetweenSetsSeconds: 150 });
  });
});
