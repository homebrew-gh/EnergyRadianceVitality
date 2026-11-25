import type { Workout, WorkoutItem, WorkoutSegment } from "./workoutTraining";

/**
 * The workout builder's open draft as one value, changed only through [workoutEditorReducer].
 *
 * Segments are addressed by id, not position, so adding, removing and moving them needs no
 * bookkeeping, and every change can be undone.
 */
export type EditorDraft = {
  name: string;
  segments: WorkoutSegment[];
};

export type WorkoutEditorState = {
  draft: EditorDraft;
  /** Segment the library adds to. Not part of undo. */
  activeSegmentId: string | null;
  /** Earlier drafts, oldest first. */
  past: EditorDraft[];
  /** Drafts undone, the next one to redo last. */
  future: EditorDraft[];
  /**
   * Key of the last change that may merge with the next one, such as typing into one field,
   * and when it happened. Changes with the same key less than [COALESCE_WINDOW_MS] apart are
   * one undo step.
   */
  lastCoalesceKey: string | null;
  lastChangeAtMs: number;
};

export type WorkoutEditorAction =
  /** Open a draft fresh: clears undo history. */
  | { type: "load"; name: string; segments: WorkoutSegment[] }
  | { type: "rename"; name: string; atMs: number }
  | { type: "addSegment"; segment: WorkoutSegment }
  /**
   * Replace one segment. Quick edits to the same segment, like typing, merge into one undo step;
   * `separate` keeps this change its own step (a library pick, for example).
   */
  | {
      type: "updateSegment";
      segmentId: string;
      segment: WorkoutSegment;
      atMs: number;
      separate?: boolean;
    }
  | { type: "removeSegment"; segmentId: string }
  | { type: "moveSegment"; from: number; to: number }
  | { type: "replaceSegments"; segments: WorkoutSegment[] }
  | { type: "selectSegment"; segmentId: string | null }
  | { type: "undo" }
  | { type: "redo" };

/** How many undo steps are kept. */
export const UNDO_LIMIT = 100;

/** Edits to the same field closer together than this undo as one step. */
export const COALESCE_WINDOW_MS = 1000;

/**
 * Give every segment and item an id, keeping the ones they have.
 *
 * Missing ids are made from the parent's id and the position, so the same workout read twice
 * gets the same ids and a draft never looks changed just because it was loaded again.
 */
export function withStableIds(segments: WorkoutSegment[], scope = "draft"): WorkoutSegment[] {
  let changed = false;
  const next = segments.map((segment, segmentIndex) => {
    const segmentId = segment.id?.trim() || `${scope}:s${segmentIndex}`;
    const items = segment.items;
    let itemsChanged = false;
    const nextItems = items?.map((item, itemIndex) => {
      if (item.id?.trim()) return item;
      itemsChanged = true;
      return { ...item, id: `${segmentId}:i${itemIndex}` } as WorkoutItem;
    });
    if (segmentId === segment.id && !itemsChanged) return segment;
    changed = true;
    return { ...segment, id: segmentId, ...(nextItems ? { items: nextItems } : {}) };
  });
  return changed ? next : segments;
}

/** [workout] with an id on every segment and item, see [withStableIds]. */
export function workoutWithStableIds(workout: Workout): Workout {
  const segments = withStableIds(workout.segments ?? [], workout.id);
  return segments === workout.segments ? workout : { ...workout, segments };
}

export function initialWorkoutEditorState(
  name = "",
  segments: WorkoutSegment[] = [],
): WorkoutEditorState {
  const withIds = withStableIds(segments);
  return {
    draft: { name, segments: withIds },
    activeSegmentId: withIds[0]?.id ?? null,
    past: [],
    future: [],
    lastCoalesceKey: null,
    lastChangeAtMs: 0,
  };
}

/** Record [draft] as the new current draft, as one undo step unless it merges with the last. */
function commit(
  state: WorkoutEditorState,
  draft: EditorDraft,
  coalesceKey: string | null,
  atMs: number,
  activeSegmentId = state.activeSegmentId,
): WorkoutEditorState {
  const merge =
    coalesceKey != null &&
    coalesceKey === state.lastCoalesceKey &&
    atMs - state.lastChangeAtMs <= COALESCE_WINDOW_MS &&
    state.past.length > 0;
  const past = merge ? state.past : [...state.past, state.draft].slice(-UNDO_LIMIT);
  return {
    draft,
    activeSegmentId: resolveActive(draft.segments, activeSegmentId),
    past,
    future: [],
    lastCoalesceKey: coalesceKey,
    lastChangeAtMs: atMs,
  };
}

/** Keep [preferred] when it still exists, else the first segment, else none. */
function resolveActive(segments: WorkoutSegment[], preferred: string | null): string | null {
  if (preferred != null && segments.some((segment) => segment.id === preferred)) return preferred;
  return segments[0]?.id ?? null;
}

function moveInList<T>(list: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return list;
  const next = [...list];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved!);
  return next;
}

export function workoutEditorReducer(
  state: WorkoutEditorState,
  action: WorkoutEditorAction,
): WorkoutEditorState {
  const { draft } = state;
  switch (action.type) {
    case "load":
      return initialWorkoutEditorState(action.name, action.segments);

    case "rename":
      if (action.name === draft.name) return state;
      return commit(state, { ...draft, name: action.name }, "rename", action.atMs);

    case "addSegment": {
      const [segment] = withStableIds([action.segment], `s${Date.now()}`);
      const segments = withStableIds([...draft.segments, segment!]);
      return commit(state, { ...draft, segments }, null, 0, segment!.id ?? null);
    }

    case "updateSegment": {
      const index = draft.segments.findIndex((segment) => segment.id === action.segmentId);
      if (index < 0) return state;
      const [segment] = withStableIds([{ ...action.segment, id: action.segmentId }]);
      const segments = draft.segments.map((existing, i) => (i === index ? segment! : existing));
      const key = action.separate ? null : `segment:${action.segmentId}`;
      return commit(state, { ...draft, segments }, key, action.atMs);
    }

    case "removeSegment": {
      const index = draft.segments.findIndex((segment) => segment.id === action.segmentId);
      if (index < 0) return state;
      const segments = draft.segments.filter((_, i) => i !== index);
      // The library moves on to the segment that took its place, or the one before.
      const neighbour = segments[Math.min(index, segments.length - 1)]?.id ?? null;
      const active = state.activeSegmentId === action.segmentId ? neighbour : state.activeSegmentId;
      return commit(state, { ...draft, segments }, null, 0, active);
    }

    case "moveSegment": {
      const segments = moveInList(draft.segments, action.from, action.to);
      if (segments === draft.segments) return state;
      return commit(state, { ...draft, segments }, null, 0);
    }

    case "replaceSegments":
      return commit(state, { ...draft, segments: withStableIds(action.segments) }, null, 0);

    case "selectSegment":
      return { ...state, activeSegmentId: resolveActive(draft.segments, action.segmentId) };

    case "undo": {
      const previous = state.past[state.past.length - 1];
      if (!previous) return state;
      return {
        draft: previous,
        activeSegmentId: resolveActive(previous.segments, state.activeSegmentId),
        past: state.past.slice(0, -1),
        future: [...state.future, draft],
        lastCoalesceKey: null,
        lastChangeAtMs: 0,
      };
    }

    case "redo": {
      const next = state.future[state.future.length - 1];
      if (!next) return state;
      return {
        draft: next,
        activeSegmentId: resolveActive(next.segments, state.activeSegmentId),
        past: [...state.past, draft].slice(-UNDO_LIMIT),
        future: state.future.slice(0, -1),
        lastCoalesceKey: null,
        lastChangeAtMs: 0,
      };
    }
  }
}

export function canUndo(state: WorkoutEditorState): boolean {
  return state.past.length > 0;
}

export function canRedo(state: WorkoutEditorState): boolean {
  return state.future.length > 0;
}
