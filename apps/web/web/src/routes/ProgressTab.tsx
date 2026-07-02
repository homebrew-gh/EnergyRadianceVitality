import { useMemo, useState } from "react";
import { SessionMediaGallery } from "../components/SessionMediaPreview";
import { TrainingBaselinePanel } from "../components/TrainingBaselinePanel";
import { TrainingRelayDiagnostics } from "../components/TrainingRelayDiagnostics";
import { TrainingContextExportCard } from "../components/TrainingContextExportCard";
import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { useAuth } from "../lib/auth";
import { useEquipment } from "../lib/equipmentData";
import { useTrainingProfile } from "../lib/trainingProfileData";
import { useTraining } from "../lib/trainingData";
import { useTrainingHistory, type HistoryPeriodWeeks } from "../lib/trainingHistoryData";
import {
  buildTrainingSnapshot,
  snapshotHasData,
} from "../lib/trainingSnapshot";
import {
  exerciseLabel,
} from "../lib/weightTraining";
import {
  formatSetLine,
  formatWeightKg,
  buildRecentWorkouts,
  historyForExercise,
  maxWeightByRepBucketKg,
  summarizeCardioSession,
  summarizeWeightSession,
  volumeByMuscleGroup,
  weeklySessionCounts,
  weightSourceLabel,
  formatDistanceMeters,
  type RecentWorkoutItem,
  type HistoryTimelineItem,
} from "../lib/trainingHistory";
import {
  sessionMediaForId,
  useSessionMediaLibrary,
  type SessionMediaIndex,
} from "../lib/sessionMedia";

const PERIOD_OPTIONS: { value: HistoryPeriodWeeks; label: string }[] = [
  { value: 4, label: "4 weeks" },
  { value: 8, label: "8 weeks" },
  { value: 12, label: "12 weeks" },
  { value: null, label: "All time" },
];

function StatCard({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail?: string;
}) {
  return (
    <div className="metric-card">
      <p className="text-xs text-muted">{label}</p>
      <p className="text-2xl font-semibold text-heading mt-1">{value}</p>
      {detail ? <p className="mt-1 text-xs text-muted">{detail}</p> : null}
    </div>
  );
}

function InsightCard({
  title,
  value,
  detail,
}: {
  title: string;
  value: string;
  detail: string;
}) {
  return (
    <div className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-4">
      <p className="text-xs font-semibold text-muted">{title}</p>
      <p className="mt-2 text-lg font-bold text-heading">{value}</p>
      <p className="mt-1 text-xs text-muted">{detail}</p>
    </div>
  );
}

function HorizontalBarChart({
  title,
  buckets,
  maxValue,
  barClassName,
}: {
  title: string;
  buckets: { label: string; value: number }[];
  maxValue: number;
  barClassName?: string;
}) {
  if (buckets.length === 0) return null;
  const max = Math.max(maxValue, 1);
  return (
    <div className="space-y-2">
      <SectionHeader>{title}</SectionHeader>
      <div className="space-y-2">
        {buckets.map((b) => (
          <div key={b.label} className="grid grid-cols-[5rem_1fr_2rem] items-center gap-2 text-sm">
            <span className="text-muted truncate">{b.label}</span>
            <div className="h-3 rounded-full bg-[var(--erv-surface-variant)] overflow-hidden">
              <div
                className={`h-full rounded-full ${barClassName ?? "bg-[var(--erv-primary)]"}`}
                style={{ width: `${Math.max(4, (b.value / max) * 100)}%` }}
              />
            </div>
            <span className="text-right font-medium tabular-nums">{b.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function VerticalWeekChart({
  buckets,
}: {
  buckets: ReturnType<typeof weeklySessionCounts>;
}) {
  if (buckets.length === 0) return null;
  const max = Math.max(
    ...buckets.map((b) => b.weightSessions + b.cardioSessions),
    1,
  );
  return (
    <div className="space-y-2">
      <SectionHeader>Weekly session frequency</SectionHeader>
      <p className="text-xs text-muted">
        Stacked bars: strength (primary) + cardio (accent) per calendar week.
      </p>
      <div className="flex items-end gap-1 h-44 rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]/70 px-2 pt-3 pb-2">
        {buckets.map((b) => {
          const total = b.weightSessions + b.cardioSessions;
          const barTotalPct = (total / max) * 100;
          const weightPct = total > 0 ? (b.weightSessions / total) * barTotalPct : 0;
          const cardioPct = total > 0 ? (b.cardioSessions / total) * barTotalPct : 0;
          return (
            <div
              key={b.weekStart}
              className="flex-1 min-w-0 flex flex-col items-center gap-1"
              title={`Week of ${b.label}: ${b.weightSessions} strength, ${b.cardioSessions} cardio`}
            >
              <div className="w-full flex flex-col justify-end h-32 gap-0.5">
                {b.cardioSessions > 0 ? (
                  <div
                    className="w-full rounded-t bg-[var(--erv-secondary)] opacity-80"
                    style={{ height: `${cardioPct}%`, minHeight: b.cardioSessions > 0 ? "4px" : 0 }}
                  />
                ) : null}
                {b.weightSessions > 0 ? (
                  <div
                    className="w-full rounded-t bg-[var(--erv-primary)]"
                    style={{ height: `${weightPct}%`, minHeight: b.weightSessions > 0 ? "4px" : 0 }}
                  />
                ) : null}
                {total === 0 ? (
                  <div className="w-full h-1 rounded bg-[var(--erv-outline-variant)] opacity-30" />
                ) : null}
              </div>
              <span className="text-[10px] text-muted truncate w-full text-center">{b.label}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function RecentWorkoutsPanel({
  items,
  exercises,
  sessionMediaIndex,
}: {
  items: RecentWorkoutItem[];
  exercises: ReturnType<typeof useTraining>["exercises"];
  sessionMediaIndex: SessionMediaIndex;
}) {
  const [openKey, setOpenKey] = useState<string | null>(items[0]?.contextKey ?? null);

  if (items.length === 0) return null;

  return (
    <section className="card p-5 space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <SectionHeader>Recent Workouts</SectionHeader>
          <p className="mt-1 text-sm text-muted">
            Latest five sessions across all synced history. This is the first
            structured slice future AI planning can reuse.
          </p>
        </div>
        <span className="rounded-full bg-[var(--erv-input-bg)] px-3 py-1 text-xs text-muted">
          {items.length} shown
        </span>
      </div>

      <div className="space-y-3">
        {items.map((item) => {
          const open = openKey === item.contextKey;
          return (
            <article
              key={item.contextKey}
              className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]/70"
            >
              <button
                type="button"
                className="w-full p-4 text-left"
                onClick={() => setOpenKey(open ? null : item.contextKey)}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-mono text-muted">{item.date}</p>
                    <p className="mt-1 font-semibold text-heading">
                      {item.kind === "weight"
                        ? summarizeWeightSession(item.session)
                        : summarizeCardioSession(item.session)}
                    </p>
                  </div>
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs ${
                      item.kind === "weight"
                        ? "bg-[var(--erv-primary-container)] text-[var(--erv-on-primary-container)]"
                        : "bg-[var(--erv-secondary-container)] text-[var(--erv-on-secondary-container)]"
                    }`}
                  >
                    {item.kind === "weight" ? "Strength" : "Cardio"}
                  </span>
                </div>
              </button>
              {open ? (
                <div className="border-t border-[var(--erv-outline-variant)] px-4 pb-4 pt-3">
                  {item.kind === "weight" ? (
                    <RecentStrengthDetails
                      item={item}
                      exercises={exercises}
                      sessionMediaIndex={sessionMediaIndex}
                    />
                  ) : (
                    <RecentCardioDetails
                      item={item}
                      sessionMediaIndex={sessionMediaIndex}
                    />
                  )}
                </div>
              ) : null}
            </article>
          );
        })}
      </div>
    </section>
  );
}

function RecentStrengthDetails({
  item,
  exercises,
  sessionMediaIndex,
}: {
  item: Extract<RecentWorkoutItem, { kind: "weight" }>;
  exercises: ReturnType<typeof useTraining>["exercises"];
  sessionMediaIndex: SessionMediaIndex;
}) {
  const session = item.session;
  const mediaItems = sessionMediaForId(sessionMediaIndex, session.id);
  const elapsed = formatElapsedSeconds(
    session.durationSeconds ??
      (session.startedAtEpochSeconds && session.finishedAtEpochSeconds
        ? session.finishedAtEpochSeconds - session.startedAtEpochSeconds
        : null),
  );

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2 text-xs text-muted">
        <span>{weightSourceLabel(session.source)}</span>
        {elapsed ? <span>· {elapsed}</span> : null}
        {session.estimatedKcal ? <span>· ~{Math.round(session.estimatedKcal)} kcal</span> : null}
      </div>
      <div className="space-y-2">
        {session.entries.map((entry) => (
          <div key={entry.exerciseId} className="rounded-xl bg-[var(--erv-surface)] p-3">
            <p className="text-sm font-medium text-heading">
              {exerciseLabel(entry.exerciseId, exercises)}
            </p>
            <p className="mt-1 text-xs text-muted">
              {entry.hiitBlock
                ? `HIIT ${entry.hiitBlock.intervals} x ${entry.hiitBlock.workSeconds}s`
                : entry.sets.map((set) => formatSetLine(set)).filter(Boolean).join(" · ") || "No sets"}
            </p>
          </div>
        ))}
      </div>
      {session.heartRate ? (
        <div className="grid gap-2 sm:grid-cols-3">
          <MiniMetric
            label="Avg HR"
            value={session.heartRate.avgBpm ? `${session.heartRate.avgBpm} bpm` : "—"}
          />
          <MiniMetric
            label="Max HR"
            value={session.heartRate.maxBpm ? `${session.heartRate.maxBpm} bpm` : "—"}
          />
          <MiniMetric
            label="Min HR"
            value={session.heartRate.minBpm ? `${session.heartRate.minBpm} bpm` : "—"}
          />
        </div>
      ) : null}
      <SessionMediaGallery
        items={mediaItems}
        emptyMessage={
          session.heartRate
            ? "Heart rate graph appears here after Android backs it up to Blossom."
            : undefined
        }
      />
    </div>
  );
}

function RecentCardioDetails({
  item,
  sessionMediaIndex,
}: {
  item: Extract<RecentWorkoutItem, { kind: "cardio" }>;
  sessionMediaIndex: SessionMediaIndex;
}) {
  const session = item.session;
  const mediaItems = sessionMediaForId(sessionMediaIndex, session.id);
  const elapsed = formatElapsedSeconds(
    session.startEpochSeconds && session.endEpochSeconds
      ? session.endEpochSeconds - session.startEpochSeconds
      : session.durationMinutes * 60,
  );
  const hr = session.heartRate;

  return (
    <div className="space-y-3 text-sm">
      <div className="grid gap-2 sm:grid-cols-3">
        <MiniMetric label="Duration" value={elapsed ?? `${session.durationMinutes} min`} />
        <MiniMetric
          label="Distance"
          value={
            session.distanceMeters && session.distanceMeters > 0
              ? formatDistanceMeters(session.distanceMeters)
              : "Not logged"
          }
        />
        <MiniMetric
          label="Calories"
          value={session.estimatedKcal ? `~${Math.round(session.estimatedKcal)} kcal` : "Not estimated"}
        />
      </div>
      {hr ? (
        <div className="grid gap-2 sm:grid-cols-3">
          <MiniMetric label="Avg HR" value={hr.avgBpm ? `${hr.avgBpm} bpm` : "—"} />
          <MiniMetric label="Max HR" value={hr.maxBpm ? `${hr.maxBpm} bpm` : "—"} />
          <MiniMetric label="Min HR" value={hr.minBpm ? `${hr.minBpm} bpm` : "—"} />
        </div>
      ) : (
        <p className="text-xs text-muted">Heart rate summary was not recorded for this session.</p>
      )}
      <SessionMediaGallery
        items={mediaItems}
        emptyMessage={
          session.routeImageUrl
            ? undefined
            : "Route and heart rate images appear here after Android backs them up to Blossom."
        }
      />
      {session.routeImageUrl && mediaItems.length === 0 ? (
        <a
          href={session.routeImageUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-flex text-xs font-medium text-[var(--erv-primary)] hover:underline"
        >
          Open Public Route Image
        </a>
      ) : null}
    </div>
  );
}

function TimelineSessionDetails({
  item,
  exercises,
  sessionMediaIndex,
}: {
  item: HistoryTimelineItem;
  exercises: ReturnType<typeof useTraining>["exercises"];
  sessionMediaIndex: SessionMediaIndex;
}) {
  if (item.kind === "weight") {
    return (
      <RecentStrengthDetails
        item={{
          ...item,
          contextKey: `${item.kind}-${item.date}-${item.session.id}`,
        }}
        exercises={exercises}
        sessionMediaIndex={sessionMediaIndex}
      />
    );
  }
  return (
    <RecentCardioDetails
      item={{
        ...item,
        contextKey: `${item.kind}-${item.date}-${item.session.id}`,
      }}
      sessionMediaIndex={sessionMediaIndex}
    />
  );
}

function TimelineSessionList({
  items,
  exercises,
  sessionMediaIndex,
}: {
  items: HistoryTimelineItem[];
  exercises: ReturnType<typeof useTraining>["exercises"];
  sessionMediaIndex: SessionMediaIndex;
}) {
  const [openKey, setOpenKey] = useState<string | null>(null);

  return (
    <ul className="divide-y divide-[var(--erv-outline-variant)]">
      {items.map((item) => {
        const key = `${item.kind}-${item.date}-${item.session.id}`;
        const open = openKey === key;
        const mediaCount = sessionMediaForId(sessionMediaIndex, item.session.id).length;
        return (
          <li key={key} className="py-3 first:pt-0">
            <button
              type="button"
              className="w-full text-left"
              onClick={() => setOpenKey(open ? null : key)}
            >
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <span className="text-xs font-mono text-muted">{item.date}</span>
                <div className="flex items-center gap-2">
                  {mediaCount > 0 ? (
                    <span className="text-[11px] text-muted">{mediaCount} media</span>
                  ) : null}
                  <span
                    className={`text-xs px-2 py-0.5 rounded-full ${
                      item.kind === "weight"
                        ? "bg-[var(--erv-primary-container)] text-[var(--erv-on-primary-container)]"
                        : "bg-[var(--erv-secondary-container)] text-[var(--erv-on-secondary-container)]"
                    }`}
                  >
                    {item.kind === "weight" ? "Strength" : "Cardio"}
                  </span>
                </div>
              </div>
              <p className="text-sm font-medium text-heading mt-1">
                {item.kind === "weight"
                  ? summarizeWeightSession(item.session)
                  : summarizeCardioSession(item.session)}
              </p>
              {item.kind === "weight" ? (
                <p className="text-xs text-muted mt-0.5">
                  {weightSourceLabel(item.session.source)}
                </p>
              ) : null}
            </button>
            {open ? (
              <div className="mt-3 rounded-xl border border-[var(--erv-outline-variant)] bg-[var(--erv-surface)]/60 p-4">
                <TimelineSessionDetails
                  item={item}
                  exercises={exercises}
                  sessionMediaIndex={sessionMediaIndex}
                />
              </div>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

function MiniMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-[var(--erv-surface)] p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 font-semibold text-heading">{value}</p>
    </div>
  );
}

function formatElapsedSeconds(seconds: number | null | undefined): string | null {
  if (seconds == null || seconds <= 0) return null;
  const minutes = Math.floor(seconds / 60);
  const rem = Math.floor(seconds % 60);
  if (minutes < 60) return `${minutes}:${String(rem).padStart(2, "0")}`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return `${hours}h ${mins}m`;
}

export function ProgressTab() {
  const { status } = useAuth();
  const {
    exercises,
    stretchCatalog,
    cardioCatalog,
    workouts,
    routines: weightRoutines,
    cardioRoutines,
    stretchRoutines,
  } = useTraining();
  const { profile } = useTrainingProfile();
  const { gymMembership, equipment, enabledWeightExercisePackIds } = useEquipment();
  const {
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
  } = useTrainingHistory();
  const { index: sessionMediaIndex, reload: reloadSessionMedia } = useSessionMediaLibrary();

  const snapshot = useMemo(
    () =>
      buildTrainingSnapshot({
        weightLogs,
        cardioLogs,
        exercises,
        computedAtMs: lastLoadedAt ?? Date.now(),
      }),
    [weightLogs, cardioLogs, exercises, lastLoadedAt],
  );

  const contextBundleInput = useMemo(
    () => ({
      profile,
      snapshot,
      equipment: {
        gymMembership,
        equipment,
        enabledWeightExercisePackIds,
      },
      exercises,
      stretchCatalog,
      cardioCatalog,
      workouts,
      weightRoutines,
      cardioRoutines,
      stretchRoutines,
      weightLogs,
      cardioLogs,
    }),
    [
      profile,
      snapshot,
      gymMembership,
      equipment,
      enabledWeightExercisePackIds,
      exercises,
      stretchCatalog,
      cardioCatalog,
      workouts,
      weightRoutines,
      cardioRoutines,
      stretchRoutines,
      weightLogs,
      cardioLogs,
    ],
  );

  const [selectedExerciseId, setSelectedExerciseId] = useState<string>("");

  const weightSessionCount = useMemo(
    () => filteredWeightLogs.reduce((n, l) => n + l.workouts.length, 0),
    [filteredWeightLogs],
  );
  const cardioSessionCount = useMemo(
    () => filteredCardioLogs.reduce((n, l) => n + l.sessions.length, 0),
    [filteredCardioLogs],
  );

  const weeklyBuckets = useMemo(
    () => weeklySessionCounts(filteredWeightLogs, filteredCardioLogs),
    [filteredWeightLogs, filteredCardioLogs],
  );

  const recentWorkouts = useMemo(
    () => buildRecentWorkouts(weightLogs, cardioLogs, 5),
    [weightLogs, cardioLogs],
  );

  const muscleVolumes = useMemo(
    () => volumeByMuscleGroup(filteredWeightLogs, exercises),
    [filteredWeightLogs, exercises],
  );

  const exerciseOptions = useMemo(() => {
    return exerciseIds.map((id) => ({
      id,
      label: exerciseLabel(id, exercises),
    }));
  }, [exerciseIds, exercises]);

  const activeExerciseId = selectedExerciseId || exerciseOptions[0]?.id || "";
  const exerciseHistory = useMemo(
    () => (activeExerciseId ? historyForExercise(filteredWeightLogs, activeExerciseId) : []),
    [filteredWeightLogs, activeExerciseId],
  );
  const repBuckets = useMemo(
    () => (activeExerciseId ? maxWeightByRepBucketKg(filteredWeightLogs, activeExerciseId) : []),
    [filteredWeightLogs, activeExerciseId],
  );

  const repBucketChart = repBuckets.filter((b) => b.maxKg != null);

  const visibleDates = useMemo(() => {
    const dates = new Set<string>();
    for (const log of filteredWeightLogs) dates.add(log.date);
    for (const log of filteredCardioLogs) dates.add(log.date);
    return dates;
  }, [filteredWeightLogs, filteredCardioLogs]);

  const hiddenByPeriod =
    weightLogs.length - filteredWeightLogs.length + (cardioLogs.length - filteredCardioLogs.length);

  const workingSetCount = useMemo(
    () =>
      filteredWeightLogs.reduce(
        (total, log) =>
          total +
          log.workouts.reduce(
            (sessionTotal, session) =>
              sessionTotal +
              session.entries.reduce(
                (entryTotal, entry) =>
                  entryTotal +
                  entry.sets.filter(
                    (set) => set.reps > 0 || (set.durationSeconds ?? 0) > 0,
                  ).length +
                  (entry.hiitBlock ? 1 : 0),
                0,
              ),
            0,
          ),
        0,
      ),
    [filteredWeightLogs],
  );

  const cardioMinutes = useMemo(
    () =>
      filteredCardioLogs.reduce(
        (total, log) =>
          total + log.sessions.reduce((sessionTotal, session) => sessionTotal + session.durationMinutes, 0),
        0,
      ),
    [filteredCardioLogs],
  );

  const bestWeek = useMemo(() => {
    return weeklyBuckets.reduce<{ label: string; total: number } | null>((best, bucket) => {
      const total = bucket.weightSessions + bucket.cardioSessions;
      if (!best || total > best.total) return { label: bucket.label, total };
      return best;
    }, null);
  }, [weeklyBuckets]);

  const weeklyAverage = weeklyBuckets.length > 0
    ? ((weightSessionCount + cardioSessionCount) / weeklyBuckets.length).toFixed(1)
    : "0.0";

  const topMuscleGroup = muscleVolumes[0];
  const selectedPeriodLabel =
    PERIOD_OPTIONS.find((opt) => opt.value === periodWeeks)?.label ?? "Selected period";

  if (loading) {
    return <p className="text-sm text-muted">Loading training history from relay…</p>;
  }

  const emptyPeriod =
    weightSessionCount === 0 && cardioSessionCount === 0;
  const showBaseline = snapshotHasData(snapshot);
  const noDataAtAll = !showBaseline && emptyPeriod;

  return (
    <div className="space-y-6">
      <header className="hero-card space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-5">
          <div className="max-w-2xl space-y-3">
            <span className="sun-chip">Training Pulse</span>
            <div>
              <h2 className="text-3xl font-bold text-heading">Progress</h2>
              <p className="mt-2 text-sm text-muted">
                Read-only history synced from Android. Use this page to spot your rhythm,
                review load trends, and sanity-check the baseline before planning.
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm text-muted">
                <FieldLabel>Period</FieldLabel>
              </span>
              {PERIOD_OPTIONS.map((opt) => (
                <button
                  key={String(opt.value)}
                  type="button"
                  className={periodWeeks === opt.value ? "btn-primary text-sm" : "btn-ghost text-sm"}
                  onClick={() => setPeriodWeeks(opt.value)}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
          <div className="flex flex-col items-start gap-2 sm:items-end">
            <button type="button" className="btn-primary text-sm" onClick={() => {
              void reload(true);
              void reloadSessionMedia(true);
            }}>
              Reload From Relay
            </button>
            {lastLoadedAt ? (
              <p className="text-xs text-muted">
                Last loaded {new Date(lastLoadedAt).toLocaleString()}
              </p>
            ) : null}
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="Active Days"
            value={String(visibleDates.size)}
            detail={selectedPeriodLabel}
          />
          <StatCard
            label="Working Sets"
            value={String(workingSetCount)}
            detail={`${weightSessionCount} strength sessions`}
          />
          <StatCard
            label="Cardio Minutes"
            value={String(cardioMinutes)}
            detail={`${cardioSessionCount} cardio sessions`}
          />
          <StatCard
            label="Sessions Per Week"
            value={weeklyAverage}
            detail={bestWeek ? `Best week: ${bestWeek.label} (${bestWeek.total})` : "No week yet"}
          />
        </div>

        <div className="grid gap-3 md:grid-cols-3">
          <InsightCard
            title="Baseline"
            value={showBaseline ? "Ready" : "Waiting For Logs"}
            detail={
              showBaseline
                ? `Computed from a ${snapshot.windowDays}-day window.`
                : "Log sessions on Android, sync, then reload this page."
            }
          />
          <InsightCard
            title="Strength Focus"
            value={topMuscleGroup?.muscleGroup ?? "No Volume Yet"}
            detail={
              topMuscleGroup
                ? `${topMuscleGroup.setCount} working sets in this period.`
                : "Strength volume by muscle group appears once logs sync."
            }
          />
          <InsightCard
            title="Relay Read"
            value={
              relayMeta
                ? `${weightLogs.length + cardioLogs.length} Parsed Logs`
                : "Not Loaded Yet"
            }
            detail={
              relayMeta
                ? `${relayMeta.relay_events_fetched} events fetched${
                    status?.npub ? ` for ${status.npub.slice(0, 12)}...` : ""
                  }`
                : "Reload after Android sync to refresh history."
            }
          />
        </div>

        <div className="space-y-2">
          {error ? <p className="rounded-card bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}
          {decryptErrors.length > 0 ? (
            <p className="rounded-card bg-amber-50 p-3 text-sm text-amber-800">
              {decryptErrors.length} weight or cardio day
              {decryptErrors.length === 1 ? "" : "s"} could not be decrypted. Other relay data is
              still shown below.
              <span className="block text-xs mt-1 font-mono truncate">
                {decryptErrors.slice(0, 3).join(" · ")}
                {decryptErrors.length > 3 ? ` · +${decryptErrors.length - 3} more` : ""}
              </span>
            </p>
          ) : null}
          {relayFetchTruncated ? (
            <p className="rounded-card bg-amber-50 p-3 text-sm text-amber-800">
              Relay returned the maximum number of stored events ({2500}). Older workout days may
              be missing here. Open Android Settings and use force resync for affected dates if
              needed.
            </p>
          ) : null}
          {emptyDayLogs.length > 0 ? (
            <p className="rounded-card bg-amber-50 p-3 text-sm text-amber-800">
              {emptyDayLogs.length} day log
              {emptyDayLogs.length === 1 ? "" : "s"} on the relay decrypt but contain no readable workouts or
              sessions. Force resync on Android if local logs still have data.
              <span className="block text-xs mt-1 font-mono truncate">
                {emptyDayLogs.slice(0, 4).join(" · ")}
                {emptyDayLogs.length > 4 ? ` · +${emptyDayLogs.length - 4} more` : ""}
              </span>
            </p>
          ) : null}
          {hiddenByPeriod > 0 && periodWeeks != null ? (
            <p className="rounded-card bg-[var(--erv-surface)]/70 p-3 text-sm text-muted">
              {hiddenByPeriod} day log{hiddenByPeriod === 1 ? "" : "s"} on the relay fall outside the
              selected {periodWeeks}-week period. Switch to <strong className="font-medium">All time</strong>{" "}
              if a workout is missing here.
            </p>
          ) : null}
        </div>
      </header>

      <TrainingRelayDiagnostics records={relayRecords} visibleDates={visibleDates} />

      {showBaseline ? (
        <TrainingBaselinePanel
          snapshot={snapshot}
          exercises={exercises}
          dataLoadedAtMs={lastLoadedAt}
        />
      ) : null}

      <TrainingContextExportCard bundleInput={contextBundleInput} />

      <RecentWorkoutsPanel
        items={recentWorkouts}
        exercises={exercises}
        sessionMediaIndex={sessionMediaIndex}
      />

      {noDataAtAll ? (
        <section className="card p-6 space-y-2">
          <p className="font-medium text-heading">No sessions in this period</p>
          <p className="text-sm text-muted">
            Log a weight or cardio session on Android, wait for relay sync, then reload this tab.
            Day logs publish as <code className="text-xs">erv/weight/YYYY-MM-DD</code> and{" "}
            <code className="text-xs">erv/cardio/YYYY-MM-DD</code>.
          </p>
        </section>
      ) : !emptyPeriod ? (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <StatCard label="Strength sessions" value={String(weightSessionCount)} />
            <StatCard label="Cardio sessions" value={String(cardioSessionCount)} />
            <StatCard label="Exercises logged" value={String(exerciseIds.length)} />
          </div>

          <section className="card p-5 space-y-4">
            <VerticalWeekChart buckets={weeklyBuckets} />
          </section>

          {muscleVolumes.length > 0 ? (
            <section className="card p-5">
              <HorizontalBarChart
                title="Working sets by muscle group"
                buckets={muscleVolumes.slice(0, 10).map((v) => ({
                  label: v.muscleGroup,
                  value: v.setCount,
                }))}
                maxValue={muscleVolumes[0]?.setCount ?? 1}
              />
            </section>
          ) : null}

          <section className="card p-5 space-y-4">
            <SectionHeader>Session timeline</SectionHeader>
            <p className="text-xs text-muted">
              Tap a session to expand route and heart rate images backed up from Android.
            </p>
            <TimelineSessionList
              items={timeline.slice(0, 50)}
              exercises={exercises}
              sessionMediaIndex={sessionMediaIndex}
            />
            {timeline.length > 50 ? (
              <p className="text-xs text-muted">Showing 50 most recent sessions.</p>
            ) : null}
          </section>

          {exerciseOptions.length > 0 ? (
            <section className="card p-5 space-y-4">
              <SectionHeader>Exercise history</SectionHeader>
              <label className="block space-y-1 max-w-md">
                <span className="label">
                  <FieldLabel>Exercise</FieldLabel>
                </span>
                <select
                  className="input w-full"
                  value={activeExerciseId}
                  onChange={(e) => setSelectedExerciseId(e.target.value)}
                >
                  {exerciseOptions.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </label>

              {repBucketChart.length > 0 ? (
                <div className="space-y-2">
                  <SectionHeader>Max weight by rep bucket</SectionHeader>
                  <div className="space-y-2">
                    {repBucketChart.map((b) => (
                      <div
                        key={b.label}
                        className="grid grid-cols-[5rem_1fr_5rem] items-center gap-2 text-sm"
                      >
                        <span className="text-muted">{b.label}</span>
                        <div className="h-3 rounded-full bg-[var(--erv-surface-variant)] overflow-hidden">
                          <div
                            className="h-full rounded-full bg-[var(--erv-primary)] opacity-70"
                            style={{
                              width: `${Math.max(
                                4,
                                ((b.maxKg ?? 0) /
                                  Math.max(...repBucketChart.map((x) => x.maxKg ?? 0))) *
                                  100,
                              )}%`,
                            }}
                          />
                        </div>
                        <span className="text-right font-medium tabular-nums">
                          {formatWeightKg(b.maxKg ?? 0)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}

              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-muted border-b border-[var(--erv-outline-variant)]">
                      <th className="py-2 pr-3 font-medium">Date</th>
                      <th className="py-2 pr-3 font-medium">Sets</th>
                    </tr>
                  </thead>
                  <tbody>
                    {exerciseHistory.slice(0, 30).map((row) => (
                      <tr
                        key={`${row.date}-${row.workoutId}-${row.entry.exerciseId}`}
                        className="border-b border-[var(--erv-outline-variant)]/50"
                      >
                        <td className="py-2 pr-3 whitespace-nowrap font-mono text-xs">
                          {row.date}
                        </td>
                        <td className="py-2 pr-3">
                          {row.entry.hiitBlock ? (
                            <span>
                              HIIT {row.entry.hiitBlock.intervals}×
                              {row.entry.hiitBlock.workSeconds}s
                              {row.entry.hiitBlock.weightKg
                                ? ` @ ${formatWeightKg(row.entry.hiitBlock.weightKg)}`
                                : ""}
                            </span>
                          ) : (
                            <span className="text-muted">
                              {row.entry.sets
                                .map((s) => formatSetLine(s))
                                .filter(Boolean)
                                .join(" · ") || "—"}
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {exerciseHistory.length > 30 ? (
                  <p className="text-xs text-muted mt-2">Showing 30 most recent occurrences.</p>
                ) : null}
              </div>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
