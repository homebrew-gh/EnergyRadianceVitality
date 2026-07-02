import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { ApiError, api, type AppDataRecord } from "./api";
import { APP_DATA_TTL_MS, appDataCacheAgeMs, getAppData } from "./appDataCache";
import {
  CARDIO_CATALOG_D_TAG,
  EMPTY_CATALOGS,
  FALLBACK_WEIGHT_EXERCISES,
  nextCatalogVersion,
  parseCardioCatalog,
  parseStretchCatalog,
  parseWeightCatalog,
  serializeCardioCatalog,
  serializeStretchCatalog,
  serializeWeightCatalog,
  STRETCH_CATALOG_D_TAG,
  notifyCatalogPublished,
  WEIGHT_CATALOG_D_TAG,
  type CardioCatalogActivity,
  type ErvCatalogs,
  type StretchCatalogEntry,
  type WeightCatalogExercise,
} from "./catalog";

export type CatalogEditorTabId = "weight" | "stretch" | "cardio";

const AUTO_PUBLISH_MS = 600;

type CatalogEditorContextValue = {
  loading: boolean;
  error: string | null;
  lastEventId: string | null;
  catalogs: ErvCatalogs;
  weightExercises: WeightCatalogExercise[];
  stretchEntries: StretchCatalogEntry[];
  cardioActivities: CardioCatalogActivity[];
  weightVersion: number | null;
  stretchVersion: number | null;
  cardioVersion: number | null;
  weightDirty: boolean;
  stretchDirty: boolean;
  cardioDirty: boolean;
  weightSyncing: boolean;
  stretchSyncing: boolean;
  cardioSyncing: boolean;
  reload: (force?: boolean) => Promise<void>;
  setWeightExercises: (next: WeightCatalogExercise[]) => void;
  setStretchEntries: (next: StretchCatalogEntry[]) => void;
  setCardioActivities: (next: CardioCatalogActivity[]) => void;
  retryWeightPublish: () => Promise<void>;
  retryStretchPublish: () => Promise<void>;
  retryCardioPublish: () => Promise<void>;
  clearError: () => void;
};

const CatalogEditorContext = createContext<CatalogEditorContextValue | null>(null);

function initialWeightDraft(records: AppDataRecord[]) {
  const record = records.find((r) => r.d_tag === WEIGHT_CATALOG_D_TAG);
  const payload = record?.plaintext ? parseWeightCatalog(record.plaintext) : null;
  return {
    exercises:
      payload?.exercises.length ? payload.exercises : [...FALLBACK_WEIGHT_EXERCISES],
    version: payload?.catalogVersion ?? null,
    dirty: false,
  };
}

function initialStretchDraft(records: AppDataRecord[]) {
  const record = records.find((r) => r.d_tag === STRETCH_CATALOG_D_TAG);
  const payload = record?.plaintext ? parseStretchCatalog(record.plaintext) : null;
  return {
    entries: payload?.stretches ?? [],
    version: payload?.catalogVersion ?? null,
    dirty: false,
  };
}

function initialCardioDraft(records: AppDataRecord[]) {
  const record = records.find((r) => r.d_tag === CARDIO_CATALOG_D_TAG);
  const payload = record?.plaintext ? parseCardioCatalog(record.plaintext) : null;
  return {
    activities: payload?.activities ?? [],
    version: payload?.catalogVersion ?? null,
    dirty: false,
  };
}

export function CatalogEditorProvider({ children }: { children: ReactNode }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastEventId, setLastEventId] = useState<string | null>(null);
  const [catalogs, setCatalogs] = useState<ErvCatalogs>(EMPTY_CATALOGS);

  const [weightExercises, setWeightExercisesState] = useState<WeightCatalogExercise[]>([]);
  const [stretchEntries, setStretchEntriesState] = useState<StretchCatalogEntry[]>([]);
  const [cardioActivities, setCardioActivitiesState] = useState<CardioCatalogActivity[]>([]);
  const [weightVersion, setWeightVersion] = useState<number | null>(null);
  const [stretchVersion, setStretchVersion] = useState<number | null>(null);
  const [cardioVersion, setCardioVersion] = useState<number | null>(null);
  const [weightDirty, setWeightDirty] = useState(false);
  const [stretchDirty, setStretchDirty] = useState(false);
  const [cardioDirty, setCardioDirty] = useState(false);
  const [weightSyncing, setWeightSyncing] = useState(false);
  const [stretchSyncing, setStretchSyncing] = useState(false);
  const [cardioSyncing, setCardioSyncing] = useState(false);

  const weightExercisesRef = useRef(weightExercises);
  const stretchEntriesRef = useRef(stretchEntries);
  const cardioActivitiesRef = useRef(cardioActivities);
  const weightVersionRef = useRef(weightVersion);
  const stretchVersionRef = useRef(stretchVersion);
  const cardioVersionRef = useRef(cardioVersion);
  const weightPublishTimer = useRef<number | null>(null);
  const stretchPublishTimer = useRef<number | null>(null);
  const cardioPublishTimer = useRef<number | null>(null);

  weightExercisesRef.current = weightExercises;
  stretchEntriesRef.current = stretchEntries;
  cardioActivitiesRef.current = cardioActivities;
  weightVersionRef.current = weightVersion;
  stretchVersionRef.current = stretchVersion;
  cardioVersionRef.current = cardioVersion;

  const reload = useCallback(async (force = false) => {
    setError(null);
    const cacheFresh =
      !force &&
      appDataCacheAgeMs() != null &&
      appDataCacheAgeMs()! < APP_DATA_TTL_MS;
    if (!cacheFresh) {
      setLoading(true);
    }
    try {
      const { records } = await getAppData({ force });
      const weight = initialWeightDraft(records);
      const stretch = initialStretchDraft(records);
      const cardio = initialCardioDraft(records);

      setCatalogs({
        weight: weight.exercises,
        stretch: stretch.entries,
        cardio: cardio.activities,
        catalogVersion:
          [weight.version, stretch.version, cardio.version]
            .filter((v): v is number => typeof v === "number")
            .reduce((max, v) => Math.max(max, v), 0) || null,
      });

      setWeightExercisesState(weight.exercises);
      setStretchEntriesState(stretch.entries);
      setCardioActivitiesState(cardio.activities);
      setWeightVersion(weight.version);
      setStretchVersion(stretch.version);
      setCardioVersion(cardio.version);
      setWeightDirty(false);
      setStretchDirty(false);
      setCardioDirty(false);
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : "Could not load catalogs from relay.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(
    () => () => {
      if (weightPublishTimer.current != null) {
        window.clearTimeout(weightPublishTimer.current);
      }
      if (stretchPublishTimer.current != null) {
        window.clearTimeout(stretchPublishTimer.current);
      }
      if (cardioPublishTimer.current != null) {
        window.clearTimeout(cardioPublishTimer.current);
      }
    },
    [],
  );

  const publishWeightCatalog = useCallback(async () => {
    if (weightPublishTimer.current != null) {
      window.clearTimeout(weightPublishTimer.current);
      weightPublishTimer.current = null;
    }
    setWeightSyncing(true);
    setError(null);
    const exercises = weightExercisesRef.current;
    const version = weightVersionRef.current;
    try {
      const nextVersion = nextCatalogVersion(version);
      const result = await api.publishAppData({
        d_tag: WEIGHT_CATALOG_D_TAG,
        plaintext: serializeWeightCatalog(exercises, nextVersion),
      });
      setWeightVersion(nextVersion);
      setWeightDirty(false);
      setLastEventId(result.event_id);
      setCatalogs((prev) => ({
        ...prev,
        weight: exercises,
        catalogVersion: Math.max(prev.catalogVersion ?? 0, nextVersion),
      }));
      notifyCatalogPublished();
    } catch (e) {
      setWeightDirty(true);
      setError(
        e instanceof ApiError ? e.message : "Could not publish weight catalog.",
      );
      throw e;
    } finally {
      setWeightSyncing(false);
    }
  }, []);

  const publishStretchCatalog = useCallback(async () => {
    if (stretchPublishTimer.current != null) {
      window.clearTimeout(stretchPublishTimer.current);
      stretchPublishTimer.current = null;
    }
    setStretchSyncing(true);
    setError(null);
    const entries = stretchEntriesRef.current;
    const version = stretchVersionRef.current;
    try {
      const nextVersion = nextCatalogVersion(version);
      const result = await api.publishAppData({
        d_tag: STRETCH_CATALOG_D_TAG,
        plaintext: serializeStretchCatalog(entries, nextVersion),
      });
      setStretchVersion(nextVersion);
      setStretchDirty(false);
      setLastEventId(result.event_id);
      setCatalogs((prev) => ({
        ...prev,
        stretch: entries,
        catalogVersion: Math.max(prev.catalogVersion ?? 0, nextVersion),
      }));
      notifyCatalogPublished();
    } catch (e) {
      setStretchDirty(true);
      setError(
        e instanceof ApiError ? e.message : "Could not publish stretch catalog.",
      );
      throw e;
    } finally {
      setStretchSyncing(false);
    }
  }, []);

  const publishCardioCatalog = useCallback(async () => {
    if (cardioPublishTimer.current != null) {
      window.clearTimeout(cardioPublishTimer.current);
      cardioPublishTimer.current = null;
    }
    setCardioSyncing(true);
    setError(null);
    const activities = cardioActivitiesRef.current;
    const version = cardioVersionRef.current;
    try {
      const nextVersion = nextCatalogVersion(version);
      const result = await api.publishAppData({
        d_tag: CARDIO_CATALOG_D_TAG,
        plaintext: serializeCardioCatalog(activities, nextVersion),
      });
      setCardioVersion(nextVersion);
      setCardioDirty(false);
      setLastEventId(result.event_id);
      setCatalogs((prev) => ({
        ...prev,
        cardio: activities,
        catalogVersion: Math.max(prev.catalogVersion ?? 0, nextVersion),
      }));
      notifyCatalogPublished();
    } catch (e) {
      setCardioDirty(true);
      setError(
        e instanceof ApiError ? e.message : "Could not publish cardio catalog.",
      );
      throw e;
    } finally {
      setCardioSyncing(false);
    }
  }, []);

  const scheduleWeightPublish = useCallback(() => {
    if (weightPublishTimer.current != null) {
      window.clearTimeout(weightPublishTimer.current);
    }
    weightPublishTimer.current = window.setTimeout(() => {
      weightPublishTimer.current = null;
      void publishWeightCatalog();
    }, AUTO_PUBLISH_MS);
  }, [publishWeightCatalog]);

  const scheduleStretchPublish = useCallback(() => {
    if (stretchPublishTimer.current != null) {
      window.clearTimeout(stretchPublishTimer.current);
    }
    stretchPublishTimer.current = window.setTimeout(() => {
      stretchPublishTimer.current = null;
      void publishStretchCatalog();
    }, AUTO_PUBLISH_MS);
  }, [publishStretchCatalog]);

  const scheduleCardioPublish = useCallback(() => {
    if (cardioPublishTimer.current != null) {
      window.clearTimeout(cardioPublishTimer.current);
    }
    cardioPublishTimer.current = window.setTimeout(() => {
      cardioPublishTimer.current = null;
      void publishCardioCatalog();
    }, AUTO_PUBLISH_MS);
  }, [publishCardioCatalog]);

  const setWeightExercises = useCallback(
    (next: WeightCatalogExercise[]) => {
      setWeightExercisesState(next);
      setWeightDirty(true);
      scheduleWeightPublish();
    },
    [scheduleWeightPublish],
  );

  const setStretchEntries = useCallback(
    (next: StretchCatalogEntry[]) => {
      setStretchEntriesState(next);
      setStretchDirty(true);
      scheduleStretchPublish();
    },
    [scheduleStretchPublish],
  );

  const setCardioActivities = useCallback(
    (next: CardioCatalogActivity[]) => {
      setCardioActivitiesState(next);
      setCardioDirty(true);
      scheduleCardioPublish();
    },
    [scheduleCardioPublish],
  );

  const clearError = useCallback(() => setError(null), []);

  const value = useMemo<CatalogEditorContextValue>(
    () => ({
      loading,
      error,
      lastEventId,
      catalogs,
      weightExercises,
      stretchEntries,
      cardioActivities,
      weightVersion,
      stretchVersion,
      cardioVersion,
      weightDirty,
      stretchDirty,
      cardioDirty,
      weightSyncing,
      stretchSyncing,
      cardioSyncing,
      reload,
      setWeightExercises,
      setStretchEntries,
      setCardioActivities,
      retryWeightPublish: publishWeightCatalog,
      retryStretchPublish: publishStretchCatalog,
      retryCardioPublish: publishCardioCatalog,
      clearError,
    }),
    [
      loading,
      error,
      lastEventId,
      catalogs,
      weightExercises,
      stretchEntries,
      cardioActivities,
      weightVersion,
      stretchVersion,
      cardioVersion,
      weightDirty,
      stretchDirty,
      cardioDirty,
      weightSyncing,
      stretchSyncing,
      cardioSyncing,
      reload,
      setWeightExercises,
      setStretchEntries,
      setCardioActivities,
      publishWeightCatalog,
      publishStretchCatalog,
      publishCardioCatalog,
      clearError,
    ],
  );

  return (
    <CatalogEditorContext.Provider value={value}>
      {children}
    </CatalogEditorContext.Provider>
  );
}

export function useCatalogEditor(): CatalogEditorContextValue {
  const ctx = useContext(CatalogEditorContext);
  if (!ctx) {
    throw new Error("useCatalogEditor must be used inside <CatalogEditorProvider>");
  }
  return ctx;
}
