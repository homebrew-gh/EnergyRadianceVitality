import { describe, expect, it } from "vitest";
import {
  buildWorkoutLibraryPublishEntries,
  nextWorkoutDeleteMarkers,
  parseWorkoutLibraryFromAppDataRecords,
  WORKOUT_DELETE_MARKER_RETENTION_SECONDS,
  WORKOUTS_LIBRARY_D_TAG,
  workoutLibraryWorkoutShardTag,
  type AppDataRecordLike,
  type Workout,
  type WorkoutLibraryPayload,
} from "./workoutTraining";

function workout(id: string, lastModifiedEpochSeconds: number): Workout {
  return { id, name: `Workout ${id}`, segments: [], lastModifiedEpochSeconds };
}

function shardRecord(w: Workout, created_at: number): AppDataRecordLike {
  return {
    d_tag: workoutLibraryWorkoutShardTag(w.id),
    plaintext: JSON.stringify({ workout: w }),
    created_at,
  };
}

function indexRecord(payload: WorkoutLibraryPayload, created_at: number): AppDataRecordLike {
  return { d_tag: WORKOUTS_LIBRARY_D_TAG, plaintext: JSON.stringify(payload), created_at };
}

describe("buildWorkoutLibraryPublishEntries", () => {
  it("publishes an index with delete markers when the last workout is deleted", () => {
    const entries = buildWorkoutLibraryPublishEntries([], 300, undefined, { w1: 300 });
    expect(entries.map((e) => e.d_tag)).toEqual([WORKOUTS_LIBRARY_D_TAG]);
    expect(JSON.parse(entries[0].plaintext)).toEqual({
      sharded: true,
      workoutIds: [],
      libraryUpdatedAtEpochSeconds: 300,
      deletedWorkouts: { w1: 300 },
    });
  });

  it("leaves deletedWorkouts out of the index when there are no markers", () => {
    const entries = buildWorkoutLibraryPublishEntries([workout("w1", 100)], 100);
    const index = JSON.parse(entries[entries.length - 1].plaintext) as WorkoutLibraryPayload;
    expect(index.workoutIds).toEqual(["w1"]);
    expect(index.deletedWorkouts).toBeUndefined();
  });
});

describe("nextWorkoutDeleteMarkers", () => {
  it("marks removed ids, clears re-added ids and prunes expired markers", () => {
    const now = 10 * WORKOUT_DELETE_MARKER_RETENTION_SECONDS;
    const markers = nextWorkoutDeleteMarkers(
      { back: now - 100, expired: now - WORKOUT_DELETE_MARKER_RETENTION_SECONDS - 1, kept: now - 50 },
      ["w1", "w2"],
      [workout("w2", now), workout("back", now)],
      now,
    );
    expect(markers).toEqual({ w1: now, kept: now - 50 });
  });
});

describe("parseWorkoutLibraryFromAppDataRecords", () => {
  it("drops workouts covered by a delete marker unless edited after the delete", () => {
    const stale = workout("stale", 100);
    const edited = workout("edited", 500);
    const result = parseWorkoutLibraryFromAppDataRecords([
      shardRecord(stale, 100),
      shardRecord(edited, 500),
      indexRecord(
        {
          sharded: true,
          workoutIds: ["stale", "edited"],
          libraryUpdatedAtEpochSeconds: 500,
          deletedWorkouts: { stale: 300, edited: 300 },
        },
        500,
      ),
    ]);
    expect(result.workouts.map((w) => w.id)).toEqual(["edited"]);
    expect(result.deletedWorkouts).toEqual({ stale: 300, edited: 300 });
  });

  it("reads the empty index Android publishes after its last workout is deleted", () => {
    const result = parseWorkoutLibraryFromAppDataRecords([
      shardRecord(workout("w1", 100), 100),
      indexRecord(
        {
          workouts: [],
          libraryUpdatedAtEpochSeconds: 300,
          sharded: true,
          workoutIds: [],
          deletedWorkouts: { w1: 300 },
        },
        300,
      ),
    ]);
    expect(result.workouts).toEqual([]);
    expect(result.deletedWorkouts).toEqual({ w1: 300 });
  });

  it("ignores delete markers that are not numbers", () => {
    const result = parseWorkoutLibraryFromAppDataRecords([
      indexRecord(
        {
          sharded: true,
          workoutIds: [],
          deletedWorkouts: { w1: "soon" } as unknown as Record<string, number>,
        },
        300,
      ),
    ]);
    expect(result.deletedWorkouts).toEqual({});
  });
});

describe("delete round trip", () => {
  it("a save without a workout publishes a marker that survives the next load", () => {
    const keep = workout("keep", 100);
    const drop = workout("drop", 100);
    const markers = nextWorkoutDeleteMarkers({}, ["keep", "drop"], [keep], 300);
    const entries = buildWorkoutLibraryPublishEntries([keep], 300, new Set<string>(), markers);
    const relay: AppDataRecordLike[] = [
      shardRecord(keep, 100),
      shardRecord(drop, 100),
      ...entries.map((e) => ({ ...e, created_at: 300 })),
    ];
    const loaded = parseWorkoutLibraryFromAppDataRecords(relay);
    expect(loaded.workouts.map((w) => w.id)).toEqual(["keep"]);
    expect(loaded.deletedWorkouts).toEqual({ drop: 300 });
  });
});
