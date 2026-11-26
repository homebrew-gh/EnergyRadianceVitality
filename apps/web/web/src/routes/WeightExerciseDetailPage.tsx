import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PageHeader } from "../components/PageHeader";
import { SectionHeader } from "../components/FieldLabel";
import type { BodyWeightUnit } from "../lib/fitnessEquipment";
import { kgToLb } from "../lib/fitnessEquipment";
import { bestLiftRecords, estimatedOneRmKg, LIFT_RECORD_TITLES, type LiftRecord } from "../lib/liftRecords";
import {
  compactSetsLine,
  daysBetween,
  formatHoldClock,
  formatLiftRecordValue,
  liftDateLabel,
  liftTrend,
  LIFT_TREND_TITLES,
  type LiftTrendMetric,
  type LiftTrendPoint,
} from "../lib/liftStats";
import { useTraining } from "../lib/trainingData";
import {
  formatIsoDate,
  historyForExercise,
  isWorkingSet,
  type ExerciseHistoryRow,
} from "../lib/trainingHistory";
import { useTrainingHistory } from "../lib/trainingHistoryData";
import { useWeightLoadUnit, weightLoadUnitSuffix } from "../lib/weightLoadUnit";
import { exerciseLabel } from "../lib/weightTraining";
import { formatCategoryLabel } from "../lib/catalog";

const HISTORY_PAGE = 30;

function sessionRowId(row: ExerciseHistoryRow): string {
  return `session-${row.workoutId}`;
}

/** Best est. 1RM among the row's working sets, in kg. */
function topEstOneRmKg(row: ExerciseHistoryRow): number | null {
  let best: number | null = null;
  for (const s of row.entry.sets) {
    if (!isWorkingSet(s) || !s.weightKg) continue;
    const e = estimatedOneRmKg(s.weightKg, s.reps);
    if (e != null && (best == null || e > best)) best = e;
  }
  return best;
}

function formatTrendValue(metric: LiftTrendMetric, value: number, unit: BodyWeightUnit): string {
  if (metric === "EST_ONE_RM") {
    return `${Math.round(unit === "KG" ? value : kgToLb(value))} ${weightLoadUnitSuffix(unit)}`;
  }
  if (metric === "MOST_REPS") return `${value} reps`;
  return formatHoldClock(value);
}

/** One weight exercise: last time, personal records, the trend and every session. */
export function WeightExerciseDetailPage() {
  const { exerciseId = "" } = useParams();
  const [unit] = useWeightLoadUnit();
  const { exercises } = useTraining();
  const { weightLogs, loading, error } = useTrainingHistory();
  const [highlight, setHighlight] = useState<string | null>(null);
  const [shown, setShown] = useState(HISTORY_PAGE);
  const todayIso = formatIsoDate(new Date());

  const exercise = exercises.find((e) => e.id === exerciseId);
  const name = exerciseLabel(exerciseId, exercises);
  const history = useMemo(() => historyForExercise(weightLogs, exerciseId), [weightLogs, exerciseId]);
  const records = useMemo(() => bestLiftRecords(weightLogs, exerciseId), [weightLogs, exerciseId]);
  const trend = useMemo(() => liftTrend(weightLogs, exerciseId), [weightLogs, exerciseId]);
  const recordWorkoutIds = useMemo(() => new Set(records.map((r) => r.workoutId)), [records]);
  const last = history.find((row) => compactSetsLine(row.entry, unit, false) != null) ?? history[0] ?? null;

  const showSession = (record: LiftRecord) => {
    const index = history.findIndex((row) => row.workoutId === record.workoutId);
    if (index < 0) return;
    if (index >= shown) setShown(index + 1);
    setHighlight(record.workoutId);
    // Let the row render before scrolling to it.
    window.setTimeout(() => {
      document.getElementById(`session-${record.workoutId}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
    }, 0);
  };

  const subtitle = exercise
    ? [formatCategoryLabel(exercise.muscleGroup), formatCategoryLabel(exercise.equipment)].join(" · ")
    : undefined;

  return (
    <div className="space-y-6">
      <div>
        <Link to="/app/exercises" className="text-sm text-muted hover:text-heading">
          ← Exercises
        </Link>
      </div>
      <PageHeader title={name} description={subtitle} />

      {error ? (
        <p className="card p-3 text-sm text-error" role="alert">
          {error}
        </p>
      ) : null}

      {loading && history.length === 0 ? (
        <p className="text-sm text-muted">Loading training history…</p>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2">
            <LastTimeCard row={last} unit={unit} todayIso={todayIso} />
            <RecordsCard records={records} unit={unit} todayIso={todayIso} onRecordClick={showSession} />
          </div>

          {trend ? (
            <TrendCard metric={trend.metric} points={trend.points} unit={unit} todayIso={todayIso} />
          ) : null}

          <section className="card p-5 space-y-3">
            <div className="flex items-baseline justify-between gap-3">
              <SectionHeader className="text-sm font-semibold text-heading">History</SectionHeader>
              <span className="text-xs text-muted">
                {history.length} session{history.length === 1 ? "" : "s"}
              </span>
            </div>
            {history.length === 0 ? (
              <p className="text-sm text-muted">
                Not logged yet. Sessions show up here once this exercise is logged on your phone.
              </p>
            ) : (
              <ul className="space-y-2">
                {history.slice(0, shown).map((row) => (
                  <SessionRow
                    key={`${row.date}-${row.workoutId}`}
                    row={row}
                    unit={unit}
                    todayIso={todayIso}
                    isRecordSession={recordWorkoutIds.has(row.workoutId)}
                    highlighted={highlight === row.workoutId}
                  />
                ))}
              </ul>
            )}
            {history.length > shown ? (
              <button type="button" className="btn-ghost text-sm" onClick={() => setShown(shown + HISTORY_PAGE)}>
                Show more
              </button>
            ) : null}
          </section>
        </>
      )}
    </div>
  );
}

function LastTimeCard({
  row,
  unit,
  todayIso,
}: {
  row: ExerciseHistoryRow | null;
  unit: BodyWeightUnit;
  todayIso: string;
}) {
  const e1rm = row ? topEstOneRmKg(row) : null;
  return (
    <section className="card p-5 space-y-2">
      <SectionHeader className="text-sm font-semibold text-heading">Last Time</SectionHeader>
      {row ? (
        <>
          <p className="text-xs text-muted">
            {[liftDateLabel(row.date, todayIso), row.routineName?.trim() || null].filter(Boolean).join(" · ")}
          </p>
          <p className="text-lg font-semibold text-heading">
            {compactSetsLine(row.entry, unit, false) ?? "Warm-ups only"}
          </p>
          {e1rm != null ? (
            <p className="text-xs text-muted">
              Top set est. 1RM {formatTrendValue("EST_ONE_RM", e1rm, unit)}
            </p>
          ) : null}
        </>
      ) : (
        <p className="text-sm text-muted">Not logged yet.</p>
      )}
    </section>
  );
}

function RecordsCard({
  records,
  unit,
  todayIso,
  onRecordClick,
}: {
  records: LiftRecord[];
  unit: BodyWeightUnit;
  todayIso: string;
  onRecordClick: (record: LiftRecord) => void;
}) {
  return (
    <section
      className={`card p-5 space-y-3 ${records.length > 0 ? "border-[var(--erv-primary)]/40" : ""}`}
    >
      <SectionHeader className="text-sm font-semibold text-heading">Personal Records</SectionHeader>
      {records.length === 0 ? (
        <p className="text-sm text-muted">Records show up after your first logged working set.</p>
      ) : (
        <div className="grid grid-cols-2 gap-2">
          {records.map((record) => (
            <button
              key={record.kind}
              type="button"
              onClick={() => onRecordClick(record)}
              title="Show this session"
              className="text-left rounded-lg border border-outline/30 bg-[var(--erv-surface-variant)]/50 px-3 py-2 hover:border-[var(--erv-primary)]/60"
            >
              <span className="flex items-baseline justify-between gap-2 text-xs text-muted">
                <span>{LIFT_RECORD_TITLES[record.kind]}</span>
                <span>{liftDateLabel(record.date, todayIso)}</span>
              </span>
              <span className="block font-semibold text-heading tabular-nums">
                {formatLiftRecordValue(record, unit)}
              </span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

const TREND_RANGES: { label: string; days: number | null }[] = [
  { label: "3M", days: 91 },
  { label: "6M", days: 182 },
  { label: "1Y", days: 365 },
  { label: "All", days: null },
];

/** The trend spaced by real dates, so a three-month gap looks like one; the best point marked. */
function TrendCard({
  metric,
  points,
  unit,
  todayIso,
}: {
  metric: LiftTrendMetric;
  points: LiftTrendPoint[];
  unit: BodyWeightUnit;
  todayIso: string;
}) {
  const [range, setRange] = useState("1Y");
  const days = TREND_RANGES.find((r) => r.label === range)?.days ?? null;
  const shown = days == null ? points : points.filter((p) => daysBetween(p.date, todayIso) <= days);

  const W = 640;
  const H = 180;
  const padX = 8;
  const padY = 12;
  const chart = useMemo(() => {
    if (shown.length < 2) return null;
    const first = shown[0]!.date;
    const spanDays = Math.max(1, daysBetween(first, shown[shown.length - 1]!.date));
    const values = shown.map((p) => p.value);
    const minV = Math.min(...values);
    const maxV = Math.max(...values);
    const spanV = maxV - minV > 0 ? maxV - minV : 1;
    const xy = shown.map((p) => ({
      p,
      x: padX + (daysBetween(first, p.date) / spanDays) * (W - padX * 2),
      y: padY + (H - padY * 2) * (1 - (p.value - minV) / spanV),
    }));
    const best = xy.reduce((a, b) => (b.p.value > a.p.value ? b : a));
    return { xy, best, minV, maxV };
  }, [shown]);

  return (
    <section className="card p-5 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <SectionHeader className="text-sm font-semibold text-heading">{LIFT_TREND_TITLES[metric]}</SectionHeader>
        <div className="flex gap-1">
          {TREND_RANGES.map((r) => (
            <button
              key={r.label}
              type="button"
              className={r.label === range ? "btn-primary text-xs py-1 px-3" : "btn-ghost text-xs py-1 px-3"}
              aria-pressed={r.label === range}
              onClick={() => setRange(r.label)}
            >
              {r.label}
            </button>
          ))}
        </div>
      </div>
      {!chart ? (
        <p className="text-sm text-muted">
          {shown.length === 0 ? "Nothing logged in this range." : "One session in this range. The trend needs two."}
        </p>
      ) : (
        <>
          <div className="flex gap-2">
            <div className="flex w-16 shrink-0 flex-col justify-between py-1 text-right text-xs text-muted tabular-nums">
              <span>{formatTrendValue(metric, chart.maxV, unit)}</span>
              <span>{formatTrendValue(metric, chart.minV, unit)}</span>
            </div>
            <svg
              viewBox={`0 0 ${W} ${H}`}
              preserveAspectRatio="none"
              className="h-44 w-full"
              role="img"
              aria-label={`${LIFT_TREND_TITLES[metric]} from ${chart.xy[0]!.p.date} to ${chart.xy[chart.xy.length - 1]!.p.date}`}
            >
              <line x1={padX} x2={W - padX} y1={padY} y2={padY} stroke="var(--erv-outline-variant)" strokeWidth={1} vectorEffect="non-scaling-stroke" />
              <line x1={padX} x2={W - padX} y1={H - padY} y2={H - padY} stroke="var(--erv-outline-variant)" strokeWidth={1} vectorEffect="non-scaling-stroke" />
              <polyline
                points={chart.xy.map((q) => `${q.x},${q.y}`).join(" ")}
                fill="none"
                stroke="var(--erv-on-surface-variant, currentColor)"
                strokeOpacity={0.6}
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                vectorEffect="non-scaling-stroke"
              />
              {chart.xy.map((q) => (
                <circle key={q.p.workoutId} cx={q.x} cy={q.y} r={3} fill="var(--erv-on-surface-variant, currentColor)" fillOpacity={0.7}>
                  <title>{`${liftDateLabel(q.p.date, todayIso)}: ${formatTrendValue(metric, q.p.value, unit)}`}</title>
                </circle>
              ))}
              <circle cx={chart.best.x} cy={chart.best.y} r={6} fill="var(--erv-primary)" />
            </svg>
          </div>
          <div className="flex justify-between pl-[4.5rem] text-xs text-muted">
            <span>{liftDateLabel(chart.xy[0]!.p.date, todayIso)}</span>
            <span>{liftDateLabel(chart.xy[chart.xy.length - 1]!.p.date, todayIso)}</span>
          </div>
          <p className="text-xs text-muted">
            Best {formatTrendValue(metric, chart.best.p.value, unit)} on {liftDateLabel(chart.best.p.date, todayIso)} ·
            latest {formatTrendValue(metric, chart.xy[chart.xy.length - 1]!.p.value, unit)}
          </p>
        </>
      )}
    </section>
  );
}

function SessionRow({
  row,
  unit,
  todayIso,
  isRecordSession,
  highlighted,
}: {
  row: ExerciseHistoryRow;
  unit: BodyWeightUnit;
  todayIso: string;
  isRecordSession: boolean;
  highlighted: boolean;
}) {
  const working = compactSetsLine(row.entry, unit, false);
  const warmups = compactSetsLine(row.entry, unit, true);
  return (
    <li
      id={sessionRowId(row)}
      className={`rounded-lg border px-3 py-2 ${
        highlighted ? "border-[var(--erv-primary)]" : "border-outline/30"
      }`}
    >
      <div className="flex items-baseline gap-2">
        <span className="font-medium text-heading">{liftDateLabel(row.date, todayIso)}</span>
        {row.routineName?.trim() ? <span className="truncate text-xs text-muted">{row.routineName.trim()}</span> : null}
        {isRecordSession ? (
          <span className="ml-auto rounded-pill border border-[var(--erv-primary)]/50 px-2 text-xs font-semibold text-[var(--erv-primary)]">
            PR
          </span>
        ) : null}
      </div>
      <p className="text-sm text-heading">{working ?? "Warm-ups only"}</p>
      {warmups ? <p className="text-xs text-muted">Warm-up: {warmups}</p> : null}
    </li>
  );
}
