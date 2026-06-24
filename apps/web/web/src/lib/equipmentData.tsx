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
  equipmentDraftFingerprint,
  FITNESS_EQUIPMENT_D_TAG,
  fitnessEquipmentPayload,
  parseFitnessEquipmentPayload,
  type FitnessEquipmentPayload,
  type OwnedEquipmentItem,
} from "./fitnessEquipment";

export const EQUIPMENT_PUBLISHED_EVENT = "erv-equipment-published";

export function notifyEquipmentPublished() {
  window.dispatchEvent(new CustomEvent(EQUIPMENT_PUBLISHED_EVENT));
}

type EquipmentContextValue = {
  gymMembership: boolean;
  equipment: OwnedEquipmentItem[];
  enabledWeightExercisePackIds: string[];
  loading: boolean;
  publishing: boolean;
  dirty: boolean;
  error: string | null;
  lastEventId: string | null;
  reload: () => Promise<void>;
  setGymMembership: (value: boolean) => void;
  setEquipment: (items: OwnedEquipmentItem[]) => void;
  setEnabledWeightExercisePackIds: (ids: string[]) => void;
  toggleExercisePack: (packId: string, enabled: boolean) => void;
  upsertEquipmentItem: (item: OwnedEquipmentItem) => void;
  removeEquipmentItem: (id: string) => void;
  publish: () => Promise<void>;
  discardChanges: () => void;
  clearError: () => void;
};

const EquipmentContext = createContext<EquipmentContextValue | null>(null);

const EMPTY_PUBLISHED: FitnessEquipmentPayload = {
  gymMembership: false,
  equipment: [],
  enabledWeightExercisePackIds: [],
};

export function EquipmentProvider({ children }: { children: ReactNode }) {
  const [published, setPublished] = useState<FitnessEquipmentPayload>(EMPTY_PUBLISHED);
  const [gymMembership, setGymMembershipState] = useState(false);
  const [equipment, setEquipmentState] = useState<OwnedEquipmentItem[]>([]);
  const [enabledWeightExercisePackIds, setEnabledWeightExercisePackIdsState] = useState<string[]>(
    [],
  );
  const [loading, setLoading] = useState(true);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastEventId, setLastEventId] = useState<string | null>(null);

  const applyPublished = useCallback((payload: FitnessEquipmentPayload) => {
    setPublished(payload);
    setGymMembershipState(payload.gymMembership);
    setEquipmentState(payload.equipment);
    setEnabledWeightExercisePackIdsState(payload.enabledWeightExercisePackIds);
  }, []);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const { records } = await api.listAppData();
      const record = records.find((r) => r.d_tag === FITNESS_EQUIPMENT_D_TAG);
      const payload = record?.plaintext
        ? parseFitnessEquipmentPayload(record.plaintext)
        : EMPTY_PUBLISHED;
      applyPublished(payload);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load equipment profile.");
    } finally {
      setLoading(false);
    }
  }, [applyPublished]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const draft = useMemo<FitnessEquipmentPayload>(
    () => ({ gymMembership, equipment, enabledWeightExercisePackIds }),
    [gymMembership, equipment, enabledWeightExercisePackIds],
  );

  const dirty = useMemo(
    () => equipmentDraftFingerprint(draft) !== equipmentDraftFingerprint(published),
    [draft, published],
  );

  const setGymMembership = useCallback((value: boolean) => {
    setGymMembershipState(value);
  }, []);

  const setEquipment = useCallback((items: OwnedEquipmentItem[]) => {
    setEquipmentState(items);
  }, []);

  const setEnabledWeightExercisePackIds = useCallback((ids: string[]) => {
    setEnabledWeightExercisePackIdsState(ids);
  }, []);

  const toggleExercisePack = useCallback((packId: string, enabled: boolean) => {
    setEnabledWeightExercisePackIdsState((prev) => {
      if (enabled) return prev.includes(packId) ? prev : [...prev, packId];
      return prev.filter((id) => id !== packId);
    });
  }, []);

  const upsertEquipmentItem = useCallback((item: OwnedEquipmentItem) => {
    setEquipmentState((prev) => {
      const idx = prev.findIndex((e) => e.id === item.id);
      if (idx < 0) return [...prev, item];
      const next = [...prev];
      next[idx] = item;
      return next;
    });
  }, []);

  const removeEquipmentItem = useCallback((id: string) => {
    setEquipmentState((prev) => prev.filter((e) => e.id !== id));
  }, []);

  const discardChanges = useCallback(() => {
    applyPublished(published);
  }, [applyPublished, published]);

  const publish = useCallback(async () => {
    setPublishing(true);
    setError(null);
    try {
      const payload: FitnessEquipmentPayload = {
        gymMembership,
        equipment,
        enabledWeightExercisePackIds,
      };
      const result = await api.publishAppData({
        d_tag: FITNESS_EQUIPMENT_D_TAG,
        plaintext: fitnessEquipmentPayload(payload),
      });
      applyPublished(payload);
      setLastEventId(result.event_id);
      notifyEquipmentPublished();
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Could not publish equipment profile.";
      setError(msg);
      throw e;
    } finally {
      setPublishing(false);
    }
  }, [applyPublished, equipment, enabledWeightExercisePackIds, gymMembership]);

  const clearError = useCallback(() => setError(null), []);

  const value = useMemo<EquipmentContextValue>(
    () => ({
      gymMembership,
      equipment,
      enabledWeightExercisePackIds,
      loading,
      publishing,
      dirty,
      error,
      lastEventId,
      reload,
      setGymMembership,
      setEquipment,
      setEnabledWeightExercisePackIds,
      toggleExercisePack,
      upsertEquipmentItem,
      removeEquipmentItem,
      publish,
      discardChanges,
      clearError,
    }),
    [
      gymMembership,
      equipment,
      enabledWeightExercisePackIds,
      loading,
      publishing,
      dirty,
      error,
      lastEventId,
      reload,
      setGymMembership,
      setEquipment,
      setEnabledWeightExercisePackIds,
      toggleExercisePack,
      upsertEquipmentItem,
      removeEquipmentItem,
      publish,
      discardChanges,
      clearError,
    ],
  );

  return <EquipmentContext.Provider value={value}>{children}</EquipmentContext.Provider>;
}

export function useEquipment(): EquipmentContextValue {
  const ctx = useContext(EquipmentContext);
  if (!ctx) throw new Error("useEquipment must be used inside <EquipmentProvider>");
  return ctx;
}
