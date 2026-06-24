import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { ApiError, api } from "./api";
import {
  emptyTrainingProfile,
  isTrainingProfileBlank,
  parseTrainingProfilePayload,
  TRAINING_PROFILE_D_TAG,
  trainingProfileDraftFingerprint,
  trainingProfilePayload,
  type TrainingProfilePayload,
} from "./trainingProfile";

export const TRAINING_PROFILE_PUBLISHED_EVENT = "erv-training-profile-published";

export function notifyTrainingProfilePublished() {
  window.dispatchEvent(new CustomEvent(TRAINING_PROFILE_PUBLISHED_EVENT));
}

type TrainingProfileContextValue = {
  profile: TrainingProfilePayload;
  published: TrainingProfilePayload;
  loading: boolean;
  publishing: boolean;
  dirty: boolean;
  error: string | null;
  lastEventId: string | null;
  reload: () => Promise<void>;
  updateProfile: (next: TrainingProfilePayload) => void;
  publish: () => Promise<void>;
  discardChanges: () => void;
  clearError: () => void;
};

const TrainingProfileContext = createContext<TrainingProfileContextValue | null>(null);

export function TrainingProfileProvider({ children }: { children: ReactNode }) {
  const [published, setPublished] = useState<TrainingProfilePayload>(emptyTrainingProfile());
  const [profile, setProfile] = useState<TrainingProfilePayload>(emptyTrainingProfile());
  const [loading, setLoading] = useState(true);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastEventId, setLastEventId] = useState<string | null>(null);

  const applyPublished = useCallback((payload: TrainingProfilePayload) => {
    setPublished(payload);
    setProfile(payload);
  }, []);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const { records } = await api.listAppData();
      const record = records.find((r) => r.d_tag === TRAINING_PROFILE_D_TAG);
      const payload = record?.plaintext
        ? parseTrainingProfilePayload(record.plaintext)
        : emptyTrainingProfile();
      applyPublished(payload);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load training profile.");
    } finally {
      setLoading(false);
    }
  }, [applyPublished]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const dirty = useMemo(
    () => trainingProfileDraftFingerprint(profile) !== trainingProfileDraftFingerprint(published),
    [profile, published],
  );

  const updateProfile = useCallback((next: TrainingProfilePayload) => {
    setProfile(next);
  }, []);

  const discardChanges = useCallback(() => {
    setProfile(published);
  }, [published]);

  const publish = useCallback(async () => {
    setPublishing(true);
    setError(null);
    try {
      const toPublish: TrainingProfilePayload = {
        ...profile,
        lastModifiedEpochSeconds: Math.floor(Date.now() / 1000),
      };
      const result = await api.publishAppData({
        d_tag: TRAINING_PROFILE_D_TAG,
        plaintext: trainingProfilePayload(toPublish),
      });
      applyPublished(toPublish);
      setLastEventId(result.event_id);
      notifyTrainingProfilePublished();
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Could not publish training profile.";
      setError(msg);
      throw e;
    } finally {
      setPublishing(false);
    }
  }, [applyPublished, profile]);

  const clearError = useCallback(() => setError(null), []);

  const value = useMemo<TrainingProfileContextValue>(
    () => ({
      profile,
      published,
      loading,
      publishing,
      dirty,
      error,
      lastEventId,
      reload,
      updateProfile,
      publish,
      discardChanges,
      clearError,
    }),
    [
      profile,
      published,
      loading,
      publishing,
      dirty,
      error,
      lastEventId,
      reload,
      updateProfile,
      publish,
      discardChanges,
      clearError,
    ],
  );

  return (
    <TrainingProfileContext.Provider value={value}>{children}</TrainingProfileContext.Provider>
  );
}

export function useTrainingProfile(): TrainingProfileContextValue {
  const ctx = useContext(TrainingProfileContext);
  if (!ctx) {
    throw new Error("useTrainingProfile must be used inside <TrainingProfileProvider>");
  }
  return ctx;
}

export { isTrainingProfileBlank };
