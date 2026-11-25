import { describe, expect, it } from "vitest";
import {
  canRedo,
  canUndo,
  COALESCE_WINDOW_MS,
  initialWorkoutEditorState,
  UNDO_LIMIT,
  withStableIds,
  workoutEditorReducer,
  type WorkoutEditorAction,
  type WorkoutEditorState,
} from "./workoutEditor";
import type { WorkoutSegment } from "./workoutTraining";

function play(state: WorkoutEditorState, ...actions: WorkoutEditorAction[]): WorkoutEditorState {
  return actions.reduce(workoutEditorReducer, state);
}

function segment(id: string, title = id): WorkoutSegment {
  return { id, kind: "straight_sets", title, items: [] };
}

const squat: WorkoutSegment = {
  id: "main",
  kind: "straight_sets",
  title: "Main",
  items: [{ type: "weight", id: "squat", exerciseId: "squat" }],
};

describe("withStableIds", () => {
  it("fills missing ids from the parent and position, the same way every time", () => {
    const raw: WorkoutSegment[] = [
      { kind: "composite", items: [{ type: "note", text: "Breathe" }, { type: "rest", durationSeconds: 60 }] },
    ];
    const first = withStableIds(raw, "w1");
    const second = withStableIds(raw, "w1");
    expect(first).toEqual(second);
    expect(first[0]!.id).toBe("w1:s0");
    expect(first[0]!.items!.map((item) => item.id)).toEqual(["w1:s0:i0", "w1:s0:i1"]);
  });

  it("keeps existing ids and returns the same array when nothing was missing", () => {
    const segments = [squat];
    expect(withStableIds(segments, "w1")).toBe(segments);
  });
});

describe("workoutEditorReducer", () => {
  it("adds a segment, selects it, and undoes and redoes the add", () => {
    let state = play(initialWorkoutEditorState("Leg day"), { type: "addSegment", segment: segment("a") });
    state = play(state, { type: "addSegment", segment: segment("b") });
    expect(state.draft.segments.map((s) => s.id)).toEqual(["a", "b"]);
    expect(state.activeSegmentId).toBe("b");

    state = play(state, { type: "undo" });
    expect(state.draft.segments.map((s) => s.id)).toEqual(["a"]);
    expect(state.activeSegmentId).toBe("a");
    expect(canRedo(state)).toBe(true);

    state = play(state, { type: "redo" });
    expect(state.draft.segments.map((s) => s.id)).toEqual(["a", "b"]);
    expect(canRedo(state)).toBe(false);
  });

  it("keeps the selected segment selected when segments move", () => {
    let state = initialWorkoutEditorState("", [segment("a"), segment("b"), segment("c")]);
    state = play(state, { type: "selectSegment", segmentId: "b" }, { type: "moveSegment", from: 1, to: 0 });
    expect(state.draft.segments.map((s) => s.id)).toEqual(["b", "a", "c"]);
    expect(state.activeSegmentId).toBe("b");
  });

  it("moves the selection to the next segment when the selected one is removed", () => {
    let state = initialWorkoutEditorState("", [segment("a"), segment("b"), segment("c")]);
    state = play(state, { type: "selectSegment", segmentId: "b" }, { type: "removeSegment", segmentId: "b" });
    expect(state.activeSegmentId).toBe("c");
    state = play(state, { type: "removeSegment", segmentId: "c" });
    expect(state.activeSegmentId).toBe("a");
    state = play(state, { type: "removeSegment", segmentId: "a" });
    expect(state.activeSegmentId).toBeNull();
  });

  it("merges a burst of typing into one undo step, but not typing after a pause", () => {
    let state = initialWorkoutEditorState("");
    state = play(
      state,
      { type: "rename", name: "P", atMs: 1_000 },
      { type: "rename", name: "Pu", atMs: 1_200 },
      { type: "rename", name: "Push", atMs: 1_500 },
    );
    expect(state.past).toHaveLength(1);
    state = play(state, { type: "rename", name: "Push A", atMs: 1_500 + COALESCE_WINDOW_MS + 1 });
    expect(state.past).toHaveLength(2);

    state = play(state, { type: "undo" });
    expect(state.draft.name).toBe("Push");
    state = play(state, { type: "undo" });
    expect(state.draft.name).toBe("");
    expect(canUndo(state)).toBe(false);
  });

  it("keeps a library pick its own undo step, even right after typing in that segment", () => {
    let state = initialWorkoutEditorState("", [segment("main", "")]);
    state = play(
      state,
      { type: "updateSegment", segmentId: "main", segment: segment("main", "M"), atMs: 1_000 },
      { type: "updateSegment", segmentId: "main", segment: segment("main", "Main"), atMs: 1_100 },
      { type: "updateSegment", segmentId: "main", segment: squat, atMs: 1_200, separate: true },
    );
    expect(state.past).toHaveLength(2);
    state = play(state, { type: "undo" });
    expect(state.draft.segments[0]!.title).toBe("Main");
    expect(state.draft.segments[0]!.items).toEqual([]);
  });

  it("gives new items an id when a segment is updated", () => {
    const withNewItem: WorkoutSegment = { ...segment("main"), items: [{ type: "rest", durationSeconds: 90 }] };
    const state = play(initialWorkoutEditorState("", [segment("main")]), {
      type: "updateSegment",
      segmentId: "main",
      segment: withNewItem,
      atMs: 1,
    });
    expect(state.draft.segments[0]!.items![0]!.id).toBe("main:i0");
  });

  it("starts fresh on load, with no undo history", () => {
    let state = play(initialWorkoutEditorState(""), { type: "rename", name: "Old", atMs: 1 });
    state = play(state, { type: "load", name: "Opened", segments: [squat] });
    expect(state.draft).toEqual({ name: "Opened", segments: [squat] });
    expect(state.activeSegmentId).toBe("main");
    expect(canUndo(state)).toBe(false);
  });

  it("drops a new change's redo history", () => {
    let state = play(
      initialWorkoutEditorState("", [segment("a")]),
      { type: "addSegment", segment: segment("b") },
      { type: "undo" },
      { type: "addSegment", segment: segment("c") },
    );
    expect(canRedo(state)).toBe(false);
    state = play(state, { type: "undo" });
    expect(state.draft.segments.map((s) => s.id)).toEqual(["a"]);
  });

  it("keeps at most UNDO_LIMIT steps", () => {
    let state = initialWorkoutEditorState("");
    for (let i = 0; i < UNDO_LIMIT + 20; i++) {
      state = play(state, { type: "addSegment", segment: segment(`s${i}`) });
    }
    expect(state.past).toHaveLength(UNDO_LIMIT);
  });

  it("ignores undo and redo with nothing to do", () => {
    const state = initialWorkoutEditorState("Same");
    expect(play(state, { type: "undo" })).toBe(state);
    expect(play(state, { type: "redo" })).toBe(state);
  });
});
