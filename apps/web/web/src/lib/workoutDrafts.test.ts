import { describe, expect, it } from "vitest";
import {
  draftDiffersFromPublished,
  foldLegacyWorkoutDraft,
  workoutDraftFingerprint,
  workoutLibraryRows,
  workoutPublishStateLabel,
  type WorkoutDrafts,
} from "./workoutDraftStorage";
import type { Workout } from "./workoutTraining";

const push: Workout = {
  id: "push",
  name: "Push Day",
  segments: [{ id: "s1", kind: "straight_sets", items: [{ type: "weight", exerciseId: "bench" }] }],
  lastModifiedEpochSeconds: 100,
};
const pull: Workout = { id: "pull", name: "Pull Day", segments: [{ id: "s2", kind: "straight_sets" }] };

describe("workout drafts", () => {
  it("treats a missing item list like an empty one", () => {
    expect(workoutDraftFingerprint("Pull Day", [{ id: "s2", kind: "straight_sets" }])).toBe(
      workoutDraftFingerprint(" Pull Day ", [{ id: "s2", kind: "straight_sets", items: [] }]),
    );
  });

  it("lists new drafts first, then published workouts with their state", () => {
    const drafts: WorkoutDrafts = {
      legs: { id: "legs", name: "Leg Day", segments: [], savedAtMs: 2 },
      arms: { id: "arms", name: "", segments: [], savedAtMs: 5 },
      push: {
        id: "push",
        name: "Push Day v2",
        segments: push.segments,
        savedAtMs: 3,
      },
      // Same as published: not an unpublished change.
      pull: { id: "pull", name: "Pull Day", segments: [{ id: "s2", kind: "straight_sets", items: [] }], savedAtMs: 4 },
    };
    const rows = workoutLibraryRows([push, pull], drafts);
    expect(rows.map((row) => [row.id, row.state])).toEqual([
      ["arms", "draft"],
      ["legs", "draft"],
      ["push", "unpublished_changes"],
      ["pull", "published"],
    ]);
    expect(rows[2].name).toBe("Push Day v2");
    expect(rows[2].published).toBe(push);
    expect(rows[3].draft).toBeNull();
    expect(rows[0].published).toBeNull();
  });

  it("compares a draft with its published version", () => {
    expect(
      draftDiffersFromPublished({ id: "push", name: "Push Day", segments: push.segments, savedAtMs: 0 }, push),
    ).toBe(false);
    expect(
      draftDiffersFromPublished({ id: "push", name: "Push Day", segments: [], savedAtMs: 0 }, push),
    ).toBe(true);
  });

  it("labels each state in title case", () => {
    expect(workoutPublishStateLabel("draft")).toBe("Draft");
    expect(workoutPublishStateLabel("unpublished_changes")).toBe("Unpublished Changes");
    expect(workoutPublishStateLabel("published")).toBe("Published");
  });
});

describe("old Workouts page draft", () => {
  const base = workoutDraftFingerprint("Push Day", push.segments);
  const legacy = { editingId: "push", name: "Push Day v2", segments: push.segments, baseline: base, savedAtMs: 50 };

  it("adds an edited open workout to the drafts list", () => {
    const folded = foldLegacyWorkoutDraft({}, legacy);
    expect(folded.push?.name).toBe("Push Day v2");
  });

  it("ignores one with no edits, or older than the listed draft", () => {
    const drafts: WorkoutDrafts = { push: { id: "push", name: "Newer", segments: [], savedAtMs: 60 } };
    expect(foldLegacyWorkoutDraft(drafts, legacy)).toBe(drafts);
    expect(foldLegacyWorkoutDraft({}, { ...legacy, name: "Push Day" })).toEqual({});
    expect(foldLegacyWorkoutDraft({}, null)).toEqual({});
  });

  it("keeps a new workout under its own draft id", () => {
    const folded = foldLegacyWorkoutDraft({}, { ...legacy, editingId: null, draftId: "new-1" });
    expect(Object.keys(folded)).toEqual(["new-1"]);
  });
});
