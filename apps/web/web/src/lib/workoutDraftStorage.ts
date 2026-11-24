import type { WorkoutSegment } from "./workoutTraining";

const KEY_PREFIX = "erv-workout-draft:";

/** The builder's unpublished work, kept in this browser so a reload or tab close does not lose it. */
export type StoredWorkoutDraft = {
  /** Saved workout being edited, or null for a new one. */
  editingId: string | null;
  name: string;
  segments: WorkoutSegment[];
  /** Draft text when editing started, so a restored draft still knows what is unpublished. */
  baseline: string;
  savedAtMs: number;
};

/** Stable text for comparing a draft with what it started from. */
export function workoutDraftFingerprint(name: string, segments: WorkoutSegment[]): string {
  return JSON.stringify({ name: name.trim(), segments });
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
      if (key?.startsWith(KEY_PREFIX)) window.localStorage.removeItem(key);
    }
  } catch {
    // Nothing to clear.
  }
}
