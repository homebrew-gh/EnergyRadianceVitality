import { SectionHeader } from "../components/FieldLabel";
import { exerciseLabel } from "../lib/weightTraining";
import { formatWeightKg } from "../lib/trainingHistory";
import {
  formatDaysSince,
  formatRelativeTime,
  type TrainingSnapshot,
} from "../lib/trainingSnapshot";

type TrainingBaselinePanelProps = {
  snapshot: TrainingSnapshot;
  exercises: Parameters<typeof exerciseLabel>[1];
  /** When relay logs were last fetched (epoch ms). */
  dataLoadedAtMs: number | null;
};

function stalenessHint(dataLoadedAtMs: number | null, computedAtEpochSeconds: number): string | null {
  if (dataLoadedAtMs == null) return null;
  const loadedSec = Math.floor(dataLoadedAtMs / 1000);
  const ageHours = (Date.now() / 1000 - loadedSec) / 3600;
  if (ageHours >= 24) {
    return "Relay data is over 24 hours old — reload to refresh your baseline.";
  }
  if (loadedSec < computedAtEpochSeconds - 60) {
    return "Baseline was recomputed after your last relay fetch.";
  }
  return null;
}

export function TrainingBaselinePanel({
  snapshot,
  exercises,
  dataLoadedAtMs,
}: TrainingBaselinePanelProps) {
  const stale = stalenessHint(dataLoadedAtMs, snapshot.computedAtEpochSeconds);
  const topWorking = snapshot.workingWeights.slice(0, 12);
  const topMuscleRecency = snapshot.muscleGroupRecency.slice(0, 8);
  const topCardio = snapshot.cardioByActivity.slice(0, 5);

  return (
    <section className="card overflow-hidden border-[var(--erv-primary)]/25">
      <div className="space-y-1 border-b border-[var(--erv-outline-variant)] bg-[var(--erv-primary-container)]/35 p-5">
        <span className="sun-chip">Planning Baseline</span>
        <h3 className="text-xl font-semibold text-heading">Your Training Baseline</h3>
        <p className="text-sm text-muted">
          Computed summary for workout planning and future AI context. Use it as coaching context,
          not medical advice.
        </p>
        <p className="text-xs text-muted">
          Computed {formatRelativeTime(snapshot.computedAtEpochSeconds)} · rolling{" "}
          {snapshot.windowDays}-day window
        </p>
        {stale ? <p className="text-xs text-amber-700">{stale}</p> : null}
      </div>

      <div className="space-y-5 p-5">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <BaselineStat label="Strength sessions" value={String(snapshot.strengthSessions)} />
        <BaselineStat label="Cardio sessions" value={String(snapshot.cardioSessions)} />
        <BaselineStat
          label="Last strength"
          value={snapshot.lastStrengthDate ?? "—"}
        />
        <BaselineStat
          label="Cardio minutes"
          value={String(snapshot.cardioTotalMinutes)}
        />
      </div>

      {topWorking.length > 0 ? (
        <div className="space-y-2">
          <SectionHeader>Recent working weights</SectionHeader>
          <p className="text-xs text-muted">
            Best recent set per exercise (prefers 4–12 reps when available).
          </p>
          <ul className="space-y-2">
            {topWorking.map((row) => (
              <li
                key={row.exerciseId}
                className="flex flex-wrap items-baseline justify-between gap-2 text-sm border-b border-[var(--erv-outline-variant)]/40 pb-2"
              >
                <span className="font-medium text-heading">
                  {exerciseLabel(row.exerciseId, exercises)}
                </span>
                <span className="text-muted tabular-nums">
                  {row.reps} @ {formatWeightKg(row.weightKg)}
                  {row.estimatedOneRepMaxKg != null ? (
                    <span className="text-xs ml-1">
                      (est. 1RM {formatWeightKg(row.estimatedOneRepMaxKg)})
                    </span>
                  ) : null}
                  <span className="text-xs ml-2">{row.lastDate}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {topMuscleRecency.length > 0 ? (
        <div className="space-y-2">
          <SectionHeader>Muscle group recency</SectionHeader>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-muted border-b border-[var(--erv-outline-variant)]">
                  <th className="py-2 pr-3 font-medium">Muscle group</th>
                  <th className="py-2 pr-3 font-medium">Last trained</th>
                  <th className="py-2 pr-3 font-medium">Sets ({snapshot.windowDays}d)</th>
                </tr>
              </thead>
              <tbody>
                {topMuscleRecency.map((row) => (
                  <tr
                    key={row.muscleGroup}
                    className="border-b border-[var(--erv-outline-variant)]/50"
                  >
                    <td className="py-2 pr-3">{row.muscleGroup}</td>
                    <td className="py-2 pr-3 text-muted">
                      {formatDaysSince(row.daysSince)}
                      {row.lastDate ? (
                        <span className="text-xs ml-1 font-mono">({row.lastDate})</span>
                      ) : null}
                    </td>
                    <td className="py-2 pr-3 tabular-nums">{row.setCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      {topCardio.length > 0 ? (
        <div className="space-y-2">
          <SectionHeader>Cardio load by activity</SectionHeader>
          <ul className="grid gap-2 sm:grid-cols-2 text-sm">
            {topCardio.map((row) => (
              <li
                key={row.activityLabel}
                className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] px-3 py-2"
              >
                <span className="font-medium text-heading">{row.activityLabel}</span>
                <span className="block text-muted tabular-nums">
                  {row.totalMinutes} min · {row.sessions} session
                  {row.sessions === 1 ? "" : "s"}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      </div>
    </section>
  );
}

function BaselineStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-card">
      <p className="text-xs text-muted">{label}</p>
      <p className="text-lg font-semibold text-heading tabular-nums">{value}</p>
    </div>
  );
}
