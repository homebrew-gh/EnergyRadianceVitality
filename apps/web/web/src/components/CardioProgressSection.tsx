import { useMemo } from "react";
import { SectionHeader } from "./FieldLabel";
import type { BodyWeightUnit } from "../lib/fitnessEquipment";
import { cardioDistanceUnitFor, distanceUnitSuffix, METERS_PER_MILE } from "../lib/cardioDistance";
import {
  bestCardioRecords,
  cardioRecordTitle,
  formatCardioRecordValue,
  formatClock,
  weeklyCardioTotals,
  type CardioRecord,
} from "../lib/cardioProgress";
import { cardioSessionEpoch, parseIsoDate, type CardioDayLog, type CardioSession } from "../lib/trainingHistory";

function formatDay(iso: string): string {
  return parseIsoDate(iso).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

function distanceIn(meters: number, unit: "MI" | "KM"): number {
  return unit === "KM" ? meters / 1000 : meters / METERS_PER_MILE;
}

/** Pace per mile or km for [seconds] over [meters], or null without both. */
function pace(seconds: number, meters: number | null | undefined, unit: "MI" | "KM"): string | null {
  if (!meters || meters < 1 || seconds <= 0) return null;
  return `${formatClock(seconds / distanceIn(meters, unit))} /${distanceUnitSuffix(unit)}`;
}

/**
 * Cardio on the Progress tab: distance and time by week for the chosen period, personal records
 * over all time, and recent sessions with their splits.
 */
export function CardioProgressSection({
  cardioLogs,
  filteredCardioLogs,
  periodWeeks,
  weightUnit,
}: {
  /** Every cardio day log, for all-time records. */
  cardioLogs: CardioDayLog[];
  /** The period picked at the top of the page, for the weekly chart and recent sessions. */
  filteredCardioLogs: CardioDayLog[];
  periodWeeks: number | null;
  weightUnit: BodyWeightUnit;
}) {
  const unit = cardioDistanceUnitFor(weightUnit);
  const weeks = useMemo(
    () => weeklyCardioTotals(filteredCardioLogs, periodWeeks).slice(-26),
    [filteredCardioLogs, periodWeeks],
  );
  const records = useMemo(() => bestCardioRecords(cardioLogs), [cardioLogs]);
  const recordGroups = useMemo(() => {
    const groups = new Map<string, { label: string; records: CardioRecord[] }>();
    for (const r of records) {
      const g = groups.get(r.group.id) ?? { label: r.group.label, records: [] };
      g.records.push(r);
      groups.set(r.group.id, g);
    }
    return [...groups.values()];
  }, [records]);
  const withSplits = useMemo(() => {
    const rows: { date: string; session: CardioSession }[] = [];
    for (const log of filteredCardioLogs) {
      for (const session of log.sessions) {
        if ((session.splits?.length ?? 0) > 0) rows.push({ date: log.date, session });
      }
    }
    return rows.sort((a, b) => cardioSessionEpoch(b.session) - cardioSessionEpoch(a.session)).slice(0, 10);
  }, [filteredCardioLogs]);

  const totalMeters = weeks.reduce((n, w) => n + w.distanceMeters, 0);
  const totalSeconds = weeks.reduce((n, w) => n + w.seconds, 0);
  const maxMeters = Math.max(...weeks.map((w) => w.distanceMeters), 1);
  const suffix = distanceUnitSuffix(unit);

  if (filteredCardioLogs.length === 0 && records.length === 0) return null;

  return (
    <section className="card p-5 space-y-6">
      <div className="space-y-1">
        <SectionHeader className="text-base font-semibold text-heading">Cardio</SectionHeader>
        <p className="text-sm text-muted">
          {distanceIn(totalMeters, unit).toFixed(1)} {suffix} and {formatClock(totalSeconds)} over{" "}
          {weeks.length} week{weeks.length === 1 ? "" : "s"}.
        </p>
      </div>

      {weeks.length > 0 ? (
        <div className="space-y-2">
          <SectionHeader>Weekly distance</SectionHeader>
          <div className="space-y-1.5">
            {weeks.map((w) => (
              <div key={w.weekStart} className="grid grid-cols-[4.5rem_1fr_9rem] items-center gap-2 text-sm">
                <span className="text-muted truncate">{w.label}</span>
                <div className="h-3 rounded-full bg-[var(--erv-surface-variant)] overflow-hidden">
                  {w.distanceMeters > 0 ? (
                    <div
                      className="h-full rounded-full bg-[var(--erv-tertiary,var(--erv-primary))]"
                      style={{ width: `${Math.max(3, (w.distanceMeters / maxMeters) * 100)}%` }}
                    />
                  ) : null}
                </div>
                <span className="text-right tabular-nums">
                  {w.sessions > 0 ? (
                    <>
                      <span className="font-medium">
                        {distanceIn(w.distanceMeters, unit).toFixed(1)} {suffix}
                      </span>
                      <span className="text-muted"> · {formatClock(w.seconds)}</span>
                    </>
                  ) : (
                    <span className="text-muted">—</span>
                  )}
                </span>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {recordGroups.length > 0 ? (
        <div className="space-y-3">
          <SectionHeader>Personal records</SectionHeader>
          <p className="text-xs text-muted">
            All time. Fastest efforts here use each session's average pace; the phone also checks
            the quickest stretch of a GPS route, so its numbers can be a little faster.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            {recordGroups.map((g) => (
              <div key={g.label} className="rounded-card border border-[var(--erv-outline-variant)] p-3 space-y-1">
                <p className="font-semibold text-heading">{g.label}</p>
                <table className="w-full text-sm">
                  <tbody>
                    {g.records.map((r) => (
                      <tr key={cardioRecordTitle(r.kind)}>
                        <td className="py-0.5 pr-2 text-muted">{cardioRecordTitle(r.kind)}</td>
                        <td className="py-0.5 pr-2 text-right font-medium tabular-nums">
                          {formatCardioRecordValue(r, unit, weightUnit)}
                        </td>
                        <td className="py-0.5 text-right text-xs text-muted whitespace-nowrap">{formatDay(r.date)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {withSplits.length > 0 ? (
        <div className="space-y-2">
          <SectionHeader>Recent splits</SectionHeader>
          <div className="space-y-2">
            {withSplits.map(({ date, session }) => (
              <details
                key={session.id}
                className="rounded-card border border-[var(--erv-outline-variant)] px-3 py-2"
              >
                <summary className="cursor-pointer text-sm">
                  <span className="font-medium text-heading">{session.activity.displayLabel}</span>
                  <span className="text-muted">
                    {" "}
                    · {formatDay(date)} · {session.splits!.length} split{session.splits!.length === 1 ? "" : "s"}
                    {session.distanceMeters ? ` · ${distanceIn(session.distanceMeters, unit).toFixed(2)} ${suffix}` : ""}
                  </span>
                </summary>
                <table className="mt-2 w-full text-sm">
                  <thead>
                    <tr className="text-left text-muted border-b border-[var(--erv-outline-variant)]">
                      <th className="py-1 pr-3 font-medium">#</th>
                      <th className="py-1 pr-3 font-medium">Split</th>
                      <th className="py-1 pr-3 font-medium">Distance</th>
                      <th className="py-1 pr-3 font-medium">Pace</th>
                      <th className="py-1 font-medium">Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {session.splits!.map((s, i) => (
                      <tr key={i} className="border-b border-[var(--erv-outline-variant)]/50 tabular-nums">
                        <td className="py-1 pr-3 text-muted">{i + 1}</td>
                        <td className="py-1 pr-3">{formatClock(s.segmentElapsedSeconds)}</td>
                        <td className="py-1 pr-3">
                          {s.segmentDistanceMeters ? `${distanceIn(s.segmentDistanceMeters, unit).toFixed(2)} ${suffix}` : "—"}
                        </td>
                        <td className="py-1 pr-3">{pace(s.segmentElapsedSeconds, s.segmentDistanceMeters, unit) ?? "—"}</td>
                        <td className="py-1 text-muted">{formatClock(s.elapsedSeconds)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </details>
            ))}
          </div>
        </div>
      ) : null}
    </section>
  );
}
