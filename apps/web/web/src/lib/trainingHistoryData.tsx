import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { ApiError, type AppDataFetchMeta, type AppDataRecord } from "./api";
import { APP_DATA_TTL_MS, appDataCacheAgeMs, getAppData } from "./appDataCache";
import {
  buildTimeline,
  exerciseIdsInLogs,
  filterLogsByPeriod,
  parseLogsFromAppData,
  type CardioDayLog,
  type HistoryTimelineItem,
  type WeightDayLog,
} from "./trainingHistory";

export type HistoryPeriodWeeks = 4 | 8 | 12 | null;

type TrainingHistoryContextValue = {
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  filteredWeightLogs: WeightDayLog[];
  filteredCardioLogs: CardioDayLog[];
  timeline: HistoryTimelineItem[];
  exerciseIds: string[];
  periodWeeks: HistoryPeriodWeeks;
  setPeriodWeeks: (weeks: HistoryPeriodWeeks) => void;
  loading: boolean;
  error: string | null;
  decryptErrors: string[];
  emptyDayLogs: string[];
  relayFetchTruncated: boolean;
  relayMeta: AppDataFetchMeta | null;
  relayRecords: AppDataRecord[];
  lastLoadedAt: number | null;
  reload: (force?: boolean) => Promise<void>;
};

const TrainingHistoryContext = createContext<TrainingHistoryContextValue | null>(null);

export function TrainingHistoryProvider({ children }: { children: ReactNode }) {
  const [weightLogs, setWeightLogs] = useState<WeightDayLog[]>([]);
  const [cardioLogs, setCardioLogs] = useState<CardioDayLog[]>([]);
  const [decryptErrors, setDecryptErrors] = useState<string[]>([]);
  const [emptyDayLogs, setEmptyDayLogs] = useState<string[]>([]);
  const [relayFetchTruncated, setRelayFetchTruncated] = useState(false);
  const [relayMeta, setRelayMeta] = useState<AppDataFetchMeta | null>(null);
  const [relayRecords, setRelayRecords] = useState<AppDataRecord[]>([]);
  const [periodWeeks, setPeriodWeeks] = useState<HistoryPeriodWeeks>(12);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastLoadedAt, setLastLoadedAt] = useState<number | null>(null);

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
      const { records, meta } = await getAppData({ force });
      setRelayRecords(records);
      const parsed = parseLogsFromAppData(records);
      setWeightLogs(parsed.weightLogs);
      setCardioLogs(parsed.cardioLogs);
      setDecryptErrors(parsed.decryptErrors);
      setEmptyDayLogs(parsed.emptyDayLogs);
      setRelayFetchTruncated(meta.relay_fetch_possibly_truncated);
      setRelayMeta(meta);
      setLastLoadedAt(Date.now());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load training history.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const { weightLogs: filteredWeightLogs, cardioLogs: filteredCardioLogs } = useMemo(
    () => filterLogsByPeriod(weightLogs, cardioLogs, periodWeeks),
    [weightLogs, cardioLogs, periodWeeks],
  );

  const timeline = useMemo(
    () => buildTimeline(filteredWeightLogs, filteredCardioLogs),
    [filteredWeightLogs, filteredCardioLogs],
  );

  const exerciseIds = useMemo(
    () => exerciseIdsInLogs(filteredWeightLogs),
    [filteredWeightLogs],
  );

  const value = useMemo<TrainingHistoryContextValue>(
    () => ({
      weightLogs,
      cardioLogs,
      filteredWeightLogs,
      filteredCardioLogs,
      timeline,
      exerciseIds,
      periodWeeks,
      setPeriodWeeks,
      loading,
      error,
      decryptErrors,
      emptyDayLogs,
      relayFetchTruncated,
      relayMeta,
      relayRecords,
      lastLoadedAt,
      reload,
    }),
    [
      weightLogs,
      cardioLogs,
      filteredWeightLogs,
      filteredCardioLogs,
      timeline,
      exerciseIds,
      periodWeeks,
      loading,
      error,
      decryptErrors,
      emptyDayLogs,
      relayFetchTruncated,
      relayMeta,
      relayRecords,
      lastLoadedAt,
      reload,
    ],
  );

  return (
    <TrainingHistoryContext.Provider value={value}>{children}</TrainingHistoryContext.Provider>
  );
}

export function useTrainingHistory(): TrainingHistoryContextValue {
  const ctx = useContext(TrainingHistoryContext);
  if (!ctx) {
    throw new Error("useTrainingHistory must be used inside <TrainingHistoryProvider>");
  }
  return ctx;
}
