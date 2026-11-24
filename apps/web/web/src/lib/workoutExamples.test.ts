import { describe, expect, it } from "vitest";
import type { CardioCatalogActivity, StretchCatalogEntry, WeightCatalogExercise } from "./catalog";
import { SEGMENT_KINDS, parseWorkoutImportEnvelope } from "./workoutImport";
import {
  buildWorkoutLibraryPublishEntries,
  parseWorkoutLibraryFromAppDataRecords,
  prescriptionSetTargets,
  type Workout,
  type WorkoutItem,
  type WorkoutPrescriptionSet,
} from "./workoutTraining";

type SharedExamples = {
  ervWorkoutImportVersion: number;
  segmentKinds: string[];
  reservedSegmentKinds: string[];
  setTargets: {
    name: string;
    set: WorkoutPrescriptionSet;
    reps: number | null;
    weightKg: number | null;
    durationSeconds: number | null;
  }[];
  workouts: Workout[];
};

// Read through a glob so the web build (which only sees apps/web/web) never needs the file.
const sharedFiles = import.meta.glob<SharedExamples>(
  "../../../../../shared/test-cases/workout_examples.json",
  { eager: true, import: "default" },
);
const shared = Object.values(sharedFiles)[0];
const examples = shared?.workouts ?? [];

function allItems(workouts: Workout[]): WorkoutItem[] {
  return workouts.flatMap((workout) => workout.segments.flatMap((segment) => segment.items ?? []));
}

/** Catalogs holding exactly the ids the examples use, so the import checks pass. */
function catalogsFor(workouts: Workout[]) {
  const items = allItems(workouts);
  const exerciseIds = new Set<string>();
  const stretchIds = new Set<string>();
  const cardioIds = new Set<string>();
  for (const item of items) {
    if (item.type === "weight") {
      exerciseIds.add(item.exerciseId);
      item.alternativeExerciseIds?.forEach((id) => exerciseIds.add(id));
    } else if (item.type === "mobility") {
      stretchIds.add(item.mobility.catalogId);
    } else if (item.type === "cardio") {
      cardioIds.add(item.cardio.activity);
    }
  }
  return {
    exercises: [...exerciseIds].map((id) => ({ id, name: id }) as WeightCatalogExercise),
    stretchCatalog: [...stretchIds].map((id) => ({ id, name: id }) as StretchCatalogEntry),
    cardioCatalog: [...cardioIds].map((id) => ({ id, displayName: id }) as unknown as CardioCatalogActivity),
  };
}

function withoutLastModified(workout: Workout): Workout {
  const copy = { ...workout };
  delete copy.lastModifiedEpochSeconds;
  return copy;
}

describe("shared workout examples", () => {
  it("finds the shared file", () => {
    expect(examples.length).toBeGreaterThan(0);
    expect(shared?.ervWorkoutImportVersion).toBe(1);
  });

  it("uses every segment kind and item type at least once", () => {
    const kinds = new Set(examples.flatMap((workout) => workout.segments.map((segment) => segment.kind)));
    expect([...kinds].sort()).toEqual([...(shared?.segmentKinds ?? [])].sort());
    const types = new Set(allItems(examples).map((item) => item.type));
    expect([...types].sort()).toEqual(["cardio", "mobility", "note", "rest", "weight"]);
  });

  it("builds the same segment kinds as the phone", () => {
    expect([...SEGMENT_KINDS].sort()).toEqual([...(shared?.segmentKinds ?? [])].sort());
  });

  it("imports every example without changing it", () => {
    const result = parseWorkoutImportEnvelope(JSON.stringify(shared), catalogsFor(examples), {
      nowEpochSeconds: 1_800_000_000,
      newId: () => "generated-id",
    });
    expect(result.issues).toEqual([]);
    // Import stamps its own lastModifiedEpochSeconds when it merges, so the reader leaves it out.
    expect(result.workouts).toEqual(examples.map(withoutLastModified));
  });

  it("publishes and reads back every example without changing it", () => {
    const entries = buildWorkoutLibraryPublishEntries(examples, 1_764_300_000);
    const parsed = parseWorkoutLibraryFromAppDataRecords(entries);
    expect(parsed.workouts).toEqual(examples);
  });

  it.each((shared?.setTargets ?? []).map((c) => [c.name, c] as const))(
    "reads set targets like the phone: %s",
    (_name, c) => {
      expect(prescriptionSetTargets(c.set)).toEqual({
        reps: c.reps,
        weightKg: c.weightKg,
        durationSeconds: c.durationSeconds,
      });
    },
  );
});
