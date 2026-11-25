import type { Workout, WorkoutSegment } from "./workoutTraining";

const KEY_PREFIX = "erv-workout-draft:";
const DRAFTS_KEY_PREFIX = "erv-workout-drafts:";

/** The builder's unpublished work, kept in this browser so a reload or tab close does not lose it. */
export type StoredWorkoutDraft = {
  /** Saved workout being edited, or null for a new one. */
  editingId: string | null;
  /** Id the open draft is kept under in the drafts list (the workout id, or a new one's id). */
  draftId?: string;
  name: string;
  segments: WorkoutSegment[];
  /** Draft text when editing started, so a restored draft still knows what is unpublished. */
  baseline: string;
  savedAtMs: number;
};

/** Stable text for comparing a draft with what it started from. */
export function workoutDraftFingerprint(name: string, segments: WorkoutSegment[]): string {
  // A missing item list and an empty one are the same workout.
  const normalized = segments.map((segment) => ({ ...segment, items: segment.items ?? [] }));
  return JSON.stringify({ name: name.trim(), segments: normalized });
}

function storageKey(accountKey: string): string {
  return `${KEY_PREFIX}${accountKey}`;
}

export function readStoredWorkoutDraft(accountKey: string): StoredWorkoutDraft | null {
  try {
    const raw = window.localStorage.getItem(storageKey(accountKey));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredWorkoutDraft>;
    if (typeof parsed.name !== "string" || !Array.isArray(parsed.segments)) return null;
    return {
      editingId: typeof parsed.editingId === "string" ? parsed.editingId : null,
      draftId: typeof parsed.draftId === "string" ? parsed.draftId : undefined,
      name: parsed.name,
      segments: parsed.segments,
      baseline: typeof parsed.baseline === "string" ? parsed.baseline : workoutDraftFingerprint("", []),
      savedAtMs: typeof parsed.savedAtMs === "number" ? parsed.savedAtMs : 0,
    };
  } catch {
    return null;
  }
}

export function writeStoredWorkoutDraft(accountKey: string, draft: StoredWorkoutDraft): void {
  try {
    window.localStorage.setItem(storageKey(accountKey), JSON.stringify(draft));
  } catch {
    // Storage full or blocked: the in-page draft and the leave warning still protect it.
  }
}

export function clearStoredWorkoutDraft(accountKey: string): void {
  try {
    window.localStorage.removeItem(storageKey(accountKey));
  } catch {
    // Nothing to clear.
  }
}

/** Removes every account's stored draft, for when this companion's data is wiped. */
export function clearAllStoredWorkoutDrafts(): void {
  try {
    for (let index = window.localStorage.length - 1; index >= 0; index -= 1) {
      const key = window.localStorage.key(index);
      if (key?.startsWith(KEY_PREFIX) || key?.startsWith(DRAFTS_KEY_PREFIX)) {
        window.localStorage.removeItem(key);
      }
    }
  } catch {
    // Nothing to clear.
  }
}

/**
 * A workout's unpublished version kept in this browser. Every workout you change gets one, so
 * switching between workouts keeps your edits, and nothing goes to the relay until you publish.
 */
export type SavedWorkoutDraft = {
  /** The workout's id; a workout that was never published has an id of its own already. */
  id: string;
  name: string;
  segments: WorkoutSegment[];
  savedAtMs: number;
};

export type WorkoutDrafts = Record<string, SavedWorkoutDraft>;

function draftsKey(accountKey: string): string {
  return `${DRAFTS_KEY_PREFIX}${accountKey}`;
}

export function readWorkoutDrafts(accountKey: string): WorkoutDrafts {
  try {
    const raw = window.localStorage.getItem(draftsKey(accountKey));
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, Partial<SavedWorkoutDraft>>;
    const drafts: WorkoutDrafts = {};
    for (const [id, draft] of Object.entries(parsed ?? {})) {
      if (!draft || typeof draft.name !== "string" || !Array.isArray(draft.segments)) continue;
      drafts[id] = {
        id,
        name: draft.name,
        segments: draft.segments,
        savedAtMs: typeof draft.savedAtMs === "number" ? draft.savedAtMs : 0,
      };
    }
    return drafts;
  } catch {
    return {};
  }
}

export function writeWorkoutDrafts(accountKey: string, drafts: WorkoutDrafts): void {
  try {
    if (Object.keys(drafts).length === 0) {
      window.localStorage.removeItem(draftsKey(accountKey));
    } else {
      window.localStorage.setItem(draftsKey(accountKey), JSON.stringify(drafts));
    }
  } catch {
    // Storage full or blocked: the open draft and the leave warning still protect it.
  }
}

/**
 * The old Workouts page kept its open workout in a single slot of its own. Folds that into the
 * drafts list (when it holds edits and is newer than the listed draft) so nothing is lost now
 * that the page is gone.
 */
export function foldLegacyWorkoutDraft(drafts: WorkoutDrafts, legacy: StoredWorkoutDraft | null): WorkoutDrafts {
  if (!legacy) return drafts;
  const id = legacy.draftId ?? legacy.editingId;
  if (!id) return drafts;
  if (workoutDraftFingerprint(legacy.name, legacy.segments) === legacy.baseline) return drafts;
  const listed = drafts[id];
  if (listed && listed.savedAtMs >= legacy.savedAtMs) return drafts;
  return { ...drafts, [id]: { id, name: legacy.name, segments: legacy.segments, savedAtMs: legacy.savedAtMs } };
}

/** This account's drafts, with the old page's open workout folded in and its slot cleared. */
export function loadWorkoutDrafts(accountKey: string): WorkoutDrafts {
  const drafts = readWorkoutDrafts(accountKey);
  const legacy = readStoredWorkoutDraft(accountKey);
  if (!legacy) return drafts;
  const folded = foldLegacyWorkoutDraft(drafts, legacy);
  if (folded !== drafts) writeWorkoutDrafts(accountKey, folded);
  clearStoredWorkoutDraft(accountKey);
  return folded;
}

/** Where a workout stands between this browser and the relay. */
export type WorkoutPublishState = "draft" | "unpublished_changes" | "published";

export function workoutPublishStateLabel(state: WorkoutPublishState): string {
  switch (state) {
    case "draft":
      return "Draft";
    case "unpublished_changes":
      return "Unpublished Changes";
    case "published":
      return "Published";
  }
}

/** One row of the saved-workouts list: a published workout, a draft of one, or a new draft. */
export type WorkoutLibraryRow = {
  id: string;
  name: string;
  segments: WorkoutSegment[];
  state: WorkoutPublishState;
  /** The relay version, when the workout has been published. */
  published: Workout | null;
  /** This browser's unpublished version, when there is one. */
  draft: SavedWorkoutDraft | null;
};

/** True when [draft] says something different from the published [workout]. */
export function draftDiffersFromPublished(draft: SavedWorkoutDraft, workout: Workout): boolean {
  return (
    workoutDraftFingerprint(draft.name, draft.segments) !==
    workoutDraftFingerprint(workout.name, workout.segments)
  );
}

/**
 * The saved-workouts list: never-published drafts first (newest first), then published
 * workouts in their order, each marked with whether this browser holds unpublished changes.
 * A draft that matches its published workout is ignored.
 */
export function workoutLibraryRows(workouts: Workout[], drafts: WorkoutDrafts): WorkoutLibraryRow[] {
  const publishedIds = new Set(workouts.map((workout) => workout.id));
  const newDrafts = Object.values(drafts)
    .filter((draft) => !publishedIds.has(draft.id))
    .sort((a, b) => b.savedAtMs - a.savedAtMs)
    .map(
      (draft): WorkoutLibraryRow => ({
        id: draft.id,
        name: draft.name,
        segments: draft.segments,
        state: "draft",
        published: null,
        draft,
      }),
    );
  const published = workouts.map((workout): WorkoutLibraryRow => {
    const draft = drafts[workout.id];
    const changed = draft != null && draftDiffersFromPublished(draft, workout);
    return {
      id: workout.id,
      name: changed ? draft.name : workout.name,
      segments: changed ? draft.segments : workout.segments,
      state: changed ? "unpublished_changes" : "published",
      published: workout,
      draft: changed ? draft : null,
    };
  });
  return [...newDrafts, ...published];
}
