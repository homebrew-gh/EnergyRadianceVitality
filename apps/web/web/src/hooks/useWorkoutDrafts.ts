import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../lib/auth";
import { loadWorkoutDrafts, writeWorkoutDrafts, type WorkoutDrafts } from "../lib/workoutDraftStorage";

const NO_DRAFTS: WorkoutDrafts = {};

/**
 * Unpublished workouts kept in this browser, per account. Each page that uses this reads them
 * when it opens and writes back on every change, so the Workouts list and the editor agree.
 */
export function useWorkoutDrafts() {
  const { status } = useAuth();
  const accountKey = status?.npub ?? "default";
  const [store, setStore] = useState<{ account: string; drafts: WorkoutDrafts }>(() => ({
    account: accountKey,
    drafts: loadWorkoutDrafts(accountKey),
  }));

  useEffect(() => {
    setStore((prev) => (prev.account === accountKey ? prev : { account: accountKey, drafts: loadWorkoutDrafts(accountKey) }));
  }, [accountKey]);

  useEffect(() => {
    writeWorkoutDrafts(store.account, store.drafts);
  }, [store]);

  const updateDrafts = useCallback(
    (change: (prev: WorkoutDrafts) => WorkoutDrafts) =>
      setStore((prev) => {
        const drafts = change(prev.drafts);
        if (drafts === prev.drafts) return prev;
        // Written now as well as after render, so a change made just before leaving the page sticks.
        writeWorkoutDrafts(prev.account, drafts);
        return { ...prev, drafts };
      }),
    [],
  );

  const removeDraft = useCallback(
    (id: string) =>
      updateDrafts((prev) => {
        if (!(id in prev)) return prev;
        const next = { ...prev };
        delete next[id];
        return next;
      }),
    [updateDrafts],
  );

  return {
    accountKey,
    drafts: store.account === accountKey ? store.drafts : NO_DRAFTS,
    updateDrafts,
    removeDraft,
  };
}
