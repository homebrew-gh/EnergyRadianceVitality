import { describe, expect, it } from "vitest";
import { rebaseWorkoutSave, type Workout } from "./workoutTraining";

function workout(id: string, name = `Workout ${id}`, lastModifiedEpochSeconds = 100): Workout {
  return { id, name, segments: [], lastModifiedEpochSeconds };
}

function loaded(workouts: Workout[]): Map<string, string> {
  return new Map(workouts.map((w) => [w.id, JSON.stringify(w)]));
}

const ids = (workouts: Workout[]) => workouts.map((w) => w.id).sort();

describe("rebaseWorkoutSave", () => {
  const a = workout("a");
  const b = workout("b");

  it("keeps a workout made on the phone after the page loaded", () => {
    const phone = workout("phone", "Phone workout", 200);
    const edited = { ...a, name: "Edited", lastModifiedEpochSeconds: 300 };
    const result = rebaseWorkoutSave(loaded([a, b]), [edited, b], [a, b, phone]);
    expect(ids(result)).toEqual(["a", "b", "phone"]);
    expect(result.find((w) => w.id === "a")?.name).toBe("Edited");
  });

  it("applies a delete made in this tab", () => {
    const phone = workout("phone");
    expect(ids(rebaseWorkoutSave(loaded([a, b]), [b], [a, b, phone]))).toEqual(["b", "phone"]);
  });

  it("does not bring back a workout deleted on the phone", () => {
    expect(ids(rebaseWorkoutSave(loaded([a, b]), [a, b, workout("new")], [a]))).toEqual([
      "a",
      "new",
    ]);
  });

  it("takes the relay copy of a workout this tab did not touch", () => {
    const phoneEdit = { ...b, name: "Renamed on phone", lastModifiedEpochSeconds: 250 };
    const result = rebaseWorkoutSave(loaded([a, b]), [a, b], [a, phoneEdit]);
    expect(result.find((w) => w.id === "b")?.name).toBe("Renamed on phone");
  });
});
