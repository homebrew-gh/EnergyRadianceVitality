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

type CatalogEditorContextValue = {
  loading: boolean;
  saving: boolean;
  error: string | null;
  success: string | null;
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
  reload: () => Promise<void>;
  setWeightExercises: (next: WeightCatalogExercise[]) => void;
  setStretchEntries: (next: StretchCatalogEntry[]) => void;
  setCardioActivities: (next: CardioCatalogActivity[]) => void;
  publishWeightCatalog: () => Promise<void>;
  publishStretchCatalog: () => Promise<void>;
  publishCardioCatalog: () => Promise<void>;
  clearSuccess: () => void;
};

const CatalogEditorContext = createContext<CatalogEditorContextValue | null>(null);

function initialWeightDraft(records: Awaited<ReturnType<typeof api.listAppData>>) {
  const record = records.find((r) => r.d_tag === WEIGHT_CATALOG_D_TAG);
  const payload = record?.plaintext ? parseWeightCatalog(record.plaintext) : null;
  return {
    exercises:
      payload?.exercises.length ? payload.exercises : [...FALLBACK_WEIGHT_EXERCISES],
    version: payload?.catalogVersion ?? null,
    dirty: false,
  };
}

function initialStretchDraft(records: Awaited<ReturnType<typeof api.listAppData>>) {
  const record = records.find((r) => r.d_tag === STRETCH_CATALOG_D_TAG);
  const payload = record?.plaintext ? parseStretchCatalog(record.plaintext) : null;
  return {
    entries: payload?.stretches ?? [],
    version: payload?.catalogVersion ?? null,
    dirty: false,
  };
}

function initialCardioDraft(records: Awaited<ReturnType<typeof api.listAppData>>) {
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
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
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

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const weight = initialWeightDraft(records);
      const stretch = initialStretchDraft(records);
      const cardio = initialCardioDraft(records);

      setCatalogs({
        weight: weight.exercises,
        stretch: stretch.entries,
        cardio: cardio.activities,
        catalogVersion: [
          weight.version,
          stretch.version,
          cardio.version,
        ].filter((v): v is number => typeof v === "number").reduce(
          (max, v) => Math.max(max, v),
          0,
        ) || null,
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

  const setWeightExercises = useCallback((next: WeightCatalogExercise[]) => {
    setWeightExercisesState(next);
    setWeightDirty(true);
  }, []);

  const setStretchEntries = useCallback((next: StretchCatalogEntry[]) => {
    setStretchEntriesState(next);
    setStretchDirty(true);
  }, []);

  const setCardioActivities = useCallback((next: CardioCatalogActivity[]) => {
    setCardioActivitiesState(next);
    setCardioDirty(true);
  }, []);

  const publishWeightCatalog = useCallback(async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const version = nextCatalogVersion(weightVersion);
      const result = await api.publishAppData({
        d_tag: WEIGHT_CATALOG_D_TAG,
        plaintext: serializeWeightCatalog(weightExercises, version),
      });
      setWeightVersion(version);
      setWeightDirty(false);
      setLastEventId(result.event_id);
      setSuccess("Weight catalog published to relay.");
      notifyCatalogPublished();
      await reload();
    } catch (e) {
      const msg =
        e instanceof ApiError ? e.message : "Could not publish weight catalog.";
      setError(msg);
      throw e;
    } finally {
      setSaving(false);
    }
  }, [reload, weightExercises, weightVersion]);

  const publishStretchCatalog = useCallback(async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const version = nextCatalogVersion(stretchVersion);
      const result = await api.publishAppData({
        d_tag: STRETCH_CATALOG_D_TAG,
        plaintext: serializeStretchCatalog(stretchEntries, version),
      });
      setStretchVersion(version);
      setStretchDirty(false);
      setLastEventId(result.event_id);
      setSuccess("Stretch catalog published to relay.");
      notifyCatalogPublished();
      await reload();
    } catch (e) {
      const msg =
        e instanceof ApiError ? e.message : "Could not publish stretch catalog.";
      setError(msg);
      throw e;
    } finally {
      setSaving(false);
    }
  }, [reload, stretchEntries, stretchVersion]);

  const publishCardioCatalog = useCallback(async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const version = nextCatalogVersion(cardioVersion);
      const result = await api.publishAppData({
        d_tag: CARDIO_CATALOG_D_TAG,
        plaintext: serializeCardioCatalog(cardioActivities, version),
      });
      setCardioVersion(version);
      setCardioDirty(false);
      setLastEventId(result.event_id);
      setSuccess("Cardio catalog published to relay.");
      notifyCatalogPublished();
      await reload();
    } catch (e) {
      const msg =
        e instanceof ApiError ? e.message : "Could not publish cardio catalog.";
      setError(msg);
      throw e;
    } finally {
      setSaving(false);
    }
  }, [cardioActivities, cardioVersion, reload]);

  const clearSuccess = useCallback(() => setSuccess(null), []);

  const value = useMemo<CatalogEditorContextValue>(
    () => ({
      loading,
      saving,
      error,
      success,
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
      reload,
      setWeightExercises,
      setStretchEntries,
      setCardioActivities,
      publishWeightCatalog,
      publishStretchCatalog,
      publishCardioCatalog,
      clearSuccess,
    }),
    [
      loading,
      saving,
      error,
      success,
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
      reload,
      setWeightExercises,
      setStretchEntries,
      setCardioActivities,
      publishWeightCatalog,
      publishStretchCatalog,
      publishCardioCatalog,
      clearSuccess,
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
