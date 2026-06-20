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
  CARDIO_ROUTINES_D_TAG,
  cardioMasterPayload,
  parseCardioMasterPayload,
  type CardioMasterPayload,
  type CardioRoutine,
} from "./cardioTraining";
import {
  CATALOG_PUBLISHED_EVENT,
  catalogsFromAppData,
  type CardioCatalogActivity,
  type ErvCatalogs,
  EMPTY_CATALOGS,
  type StretchCatalogEntry,
} from "./catalog";
import {
  parseStretchRoutinesPayload,
  STRETCHING_ROUTINES_D_TAG,
  stretchRoutinesPayload,
  type StretchRoutine,
} from "./stretchTraining";
import {
  mergeExerciseCatalog,
  parseExercisesPayload,
  parseRoutinesPayload,
  routinesPayload,
  WEIGHT_EXERCISES_D_TAG,
  WEIGHT_ROUTINES_D_TAG,
  type WeightExercise,
  type WeightRoutine,
} from "./weightTraining";
import {
  parseWorkoutLibraryPayload,
  WORKOUTS_LIBRARY_D_TAG,
  workoutLibraryPayload,
  type Workout,
} from "./workoutTraining";

export const ROUTINES_PUBLISHED_EVENT = "erv-routines-published";

export function notifyRoutinesPublished() {
  window.dispatchEvent(new CustomEvent(ROUTINES_PUBLISHED_EVENT));
}

type TrainingContextValue = {
  routines: WeightRoutine[];
  stretchRoutines: StretchRoutine[];
  cardioRoutines: CardioRoutine[];
  workouts: Workout[];
  exercises: WeightExercise[];
  catalogs: ErvCatalogs;
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
  loading: boolean;
  saving: boolean;
  error: string | null;
  lastEventId: string | null;
  reload: () => Promise<void>;
  saveWeightRoutines: (routines: WeightRoutine[]) => Promise<void>;
  saveStretchRoutines: (routines: StretchRoutine[]) => Promise<void>;
  saveCardioRoutines: (routines: CardioRoutine[]) => Promise<void>;
  saveWorkouts: (workouts: Workout[]) => Promise<void>;
  /** @deprecated Use saveWeightRoutines */
  createWeightRoutine: (input: {
    name: string;
    exerciseIds: string[];
    notes?: string;
  }) => Promise<void>;
  /** @deprecated Use saveWeightRoutines */
  createRoutine: TrainingContextValue["createWeightRoutine"];
};

const TrainingContext = createContext<TrainingContextValue | null>(null);

export function TrainingProvider({ children }: { children: ReactNode }) {
  const [routines, setRoutines] = useState<WeightRoutine[]>([]);
  const [stretchRoutines, setStretchRoutines] = useState<StretchRoutine[]>([]);
  const [cardioRoutines, setCardioRoutines] = useState<CardioRoutine[]>([]);
  const [workouts, setWorkouts] = useState<Workout[]>([]);
  const [exercises, setExercises] = useState<WeightExercise[]>([]);
  const [catalogs, setCatalogs] = useState<ErvCatalogs>(EMPTY_CATALOGS);
  const [cardioMaster, setCardioMaster] = useState<CardioMasterPayload>({
    routines: [],
    customActivityTypes: [],
    quickLaunches: [],
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastEventId, setLastEventId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const routinesRecord = records.find(
        (r) => r.d_tag === WEIGHT_ROUTINES_D_TAG,
      );
      const exercisesRecord = records.find(
        (r) => r.d_tag === WEIGHT_EXERCISES_D_TAG,
      );
      const stretchRecord = records.find(
        (r) => r.d_tag === STRETCHING_ROUTINES_D_TAG,
      );
      const cardioRecord = records.find(
        (r) => r.d_tag === CARDIO_ROUTINES_D_TAG,
      );
      const workoutsRecord = records.find(
        (r) => r.d_tag === WORKOUTS_LIBRARY_D_TAG,
      );

      const nextRoutines = routinesRecord?.plaintext
        ? parseRoutinesPayload(routinesRecord.plaintext)
        : [];
      const relayUserExercises = exercisesRecord?.plaintext
        ? parseExercisesPayload(exercisesRecord.plaintext)
        : [];
      const nextCatalogs = catalogsFromAppData(records);
      const nextStretch = stretchRecord?.plaintext
        ? parseStretchRoutinesPayload(stretchRecord.plaintext)
        : [];
      const nextCardioMaster = cardioRecord?.plaintext
        ? parseCardioMasterPayload(cardioRecord.plaintext)
        : { routines: [], customActivityTypes: [], quickLaunches: [] };
      const nextWorkouts = workoutsRecord?.plaintext
        ? parseWorkoutLibraryPayload(workoutsRecord.plaintext)
        : [];

      setRoutines(nextRoutines);
      setStretchRoutines(nextStretch);
      setCardioRoutines(nextCardioMaster.routines);
      setWorkouts(nextWorkouts);
      setCardioMaster(nextCardioMaster);
      setCatalogs(nextCatalogs);
      setExercises(
        mergeExerciseCatalog(nextCatalogs.weight, relayUserExercises),
      );
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : "Could not load training data.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    const onRefresh = () => void reload();
    window.addEventListener(CATALOG_PUBLISHED_EVENT, onRefresh);
    return () => window.removeEventListener(CATALOG_PUBLISHED_EVENT, onRefresh);
  }, [reload]);

  const saveWeightRoutines = useCallback(async (nextRoutines: WeightRoutine[]) => {
    setSaving(true);
    setError(null);
    try {
      const sorted = [...nextRoutines].sort((a, b) => a.name.localeCompare(b.name));
      const result = await api.publishAppData({
        d_tag: WEIGHT_ROUTINES_D_TAG,
        plaintext: routinesPayload(sorted),
      });
      setRoutines(sorted);
      setLastEventId(result.event_id);
      notifyRoutinesPublished();
    } catch (e) {
      const msg =
        e instanceof ApiError ? e.message : "Could not publish weight routines.";
      setError(msg);
      throw e;
    } finally {
      setSaving(false);
    }
  }, []);

  const createWeightRoutine = useCallback(
    async (input: { name: string; exerciseIds: string[]; notes?: string }) => {
      const name = input.name.trim();
      if (!name) throw new Error("Routine name is required.");
      if (input.exerciseIds.length === 0) {
        throw new Error("Pick at least one exercise.");
      }

      const now = Math.floor(Date.now() / 1000);
      const routine: WeightRoutine = {
        id: crypto.randomUUID(),
        name,
        exerciseIds: input.exerciseIds,
        notes: input.notes?.trim() || null,
        lastModifiedEpochSeconds: now,
      };

      await saveWeightRoutines([...routines, routine]);
    },
    [routines, saveWeightRoutines],
  );

  const saveStretchRoutines = useCallback(
    async (nextRoutines: StretchRoutine[]) => {
      setSaving(true);
      setError(null);
      try {
        const sorted = [...nextRoutines].sort((a, b) =>
          a.name.localeCompare(b.name),
        );
        const result = await api.publishAppData({
          d_tag: STRETCHING_ROUTINES_D_TAG,
          plaintext: stretchRoutinesPayload(sorted),
        });
        setStretchRoutines(sorted);
        setLastEventId(result.event_id);
        notifyRoutinesPublished();
      } catch (e) {
        const msg =
          e instanceof ApiError ? e.message : "Could not publish stretch routines.";
        setError(msg);
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [],
  );

  const saveCardioRoutines = useCallback(
    async (nextRoutines: CardioRoutine[]) => {
      setSaving(true);
      setError(null);
      try {
        const sorted = [...nextRoutines].sort((a, b) =>
          a.name.localeCompare(b.name),
        );
        const payload: CardioMasterPayload = {
          ...cardioMaster,
          routines: sorted,
        };
        const result = await api.publishAppData({
          d_tag: CARDIO_ROUTINES_D_TAG,
          plaintext: cardioMasterPayload(payload),
        });
        setCardioRoutines(sorted);
        setCardioMaster(payload);
        setLastEventId(result.event_id);
        notifyRoutinesPublished();
      } catch (e) {
        const msg =
          e instanceof ApiError ? e.message : "Could not publish cardio routines.";
        setError(msg);
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [cardioMaster],
  );

  const saveWorkouts = useCallback(async (nextWorkouts: Workout[]) => {
    setSaving(true);
    setError(null);
    try {
      const sorted = [...nextWorkouts].sort((a, b) => a.name.localeCompare(b.name));
      const now = Math.floor(Date.now() / 1000);
      const stamped = sorted.map((workout) => ({
        ...workout,
        lastModifiedEpochSeconds: workout.lastModifiedEpochSeconds ?? now,
      }));
      const result = await api.publishAppData({
        d_tag: WORKOUTS_LIBRARY_D_TAG,
        plaintext: workoutLibraryPayload(stamped, now),
      });
      setWorkouts(stamped);
      setLastEventId(result.event_id);
      notifyRoutinesPublished();
    } catch (e) {
      const msg =
        e instanceof ApiError ? e.message : "Could not publish workout library.";
      setError(msg);
      throw e;
    } finally {
      setSaving(false);
    }
  }, []);

  const value = useMemo<TrainingContextValue>(
    () => ({
      routines,
      stretchRoutines,
      cardioRoutines,
      workouts,
      exercises,
      catalogs,
      stretchCatalog: catalogs.stretch,
      cardioCatalog: catalogs.cardio,
      loading,
      saving,
      error,
      lastEventId,
      reload,
      saveWeightRoutines,
      saveStretchRoutines,
      saveCardioRoutines,
      saveWorkouts,
      createWeightRoutine,
      createRoutine: createWeightRoutine,
    }),
    [
      routines,
      stretchRoutines,
      cardioRoutines,
      workouts,
      exercises,
      catalogs,
      loading,
      saving,
      error,
      lastEventId,
      reload,
      saveWeightRoutines,
      saveStretchRoutines,
      saveCardioRoutines,
      saveWorkouts,
      createWeightRoutine,
    ],
  );

  return (
    <TrainingContext.Provider value={value}>{children}</TrainingContext.Provider>
  );
}

export function useTraining(): TrainingContextValue {
  const ctx = useContext(TrainingContext);
  if (!ctx) throw new Error("useTraining must be used inside <TrainingProvider>");
  return ctx;
}

/** @deprecated Use useTraining */
export function useRoutines(): TrainingContextValue {
  return useTraining();
}

export function useCatalogs(): ErvCatalogs {
  return useTraining().catalogs;
}

/** @deprecated Use TrainingProvider */
export const RoutinesProvider = TrainingProvider;
