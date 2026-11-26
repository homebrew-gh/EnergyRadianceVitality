import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useWorkoutDrafts } from "../hooks/useWorkoutDrafts";
import { buildStandardTestWorkout } from "../lib/standardTestWorkout";
import type { BodyWeightEntry } from "../lib/bodyWeight";
import type { BodyWeightUnit } from "../lib/fitnessEquipment";
import {
  evaluateStandard,
  type FitnessStandard,
  type StandardEventResult,
  type StandardResult,
  type StandardTierId,
} from "../lib/standards";
import { BUILT_IN_STANDARDS, builtInStandard } from "../lib/standardsLibrary";
import {
  formatGap,
  formatRawResult,
  formatStandardValue,
  missingLabel,
  parseResultInput,
  resultInputLabel,
  sourceLabel,
  tierLabel,
} from "../lib/standardsFormat";
import { useTrainingProfile } from "../lib/trainingProfileData";
import { api, ApiError, type AppDataRecord } from "../lib/api";
import {
  addStandardResult,
  parseStandardResults,
  removeStandardResult,
  serializeStandardResults,
  STANDARD_RESULTS_D_TAG,
  type StandardResultsPayload,
  type StoredStandardResult,
} from "../lib/standardResults";
import { FieldLabel, SectionHeader } from "./FieldLabel";
import { CustomStandardEditor } from "./CustomStandardEditor";
import {
  CUSTOM_STANDARDS_D_TAG,
  deleteCustomStandard,
  newStandardDraft,
  parseCustomStandards,
  serializeCustomStandards,
  standardToDraft,
  upsertCustomStandard,
  type CustomStandardDraft,
  type CustomStandardsPayload,
} from "../lib/customStandards";
import type { CardioDayLog, WeightDayLog } from "../lib/trainingHistory";

const TIER_STYLE: Record<StandardTierId | "NONE", { background: string; color: string }> = {
  ELITE: { background: "var(--erv-primary)", color: "var(--erv-on-primary)" },
  STRONG: { background: "var(--erv-primary-container)", color: "var(--erv-on-primary-container)" },
  BASE: { background: "var(--erv-secondary-container)", color: "var(--erv-on-secondary-container)" },
  NONE: { background: "var(--erv-surface-variant)", color: "var(--erv-on-surface-variant)" },
};

function TierBadge({ tier }: { tier: StandardTierId | null }) {
  const style = TIER_STYLE[tier ?? "NONE"];
  return (
    <span className="inline-block whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium" style={style}>
      {tierLabel(tier)}
    </span>
  );
}

function formatDay(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

const PROFILE_FIELD_LABEL = { age: "age", sex: "sex", height: "height" } as const;

/** Tier rings with your score on each event's spoke. Needs 3 events or more to read as a shape. */
function StandardsRadar({ result }: { result: StandardResult }) {
  const n = result.events.length;
  const cx = 220;
  const cy = 190;
  const ring = 48;
  const angle = (i: number) => -Math.PI / 2 + (i * 2 * Math.PI) / n;
  const point = (i: number, level: number) => ({
    x: cx + Math.cos(angle(i)) * level * ring,
    y: cy + Math.sin(angle(i)) * level * ring,
  });
  const polygon = (levelOf: (i: number) => number) =>
    result.events
      .map((_, i) => {
        const p = point(i, levelOf(i));
        return `${p.x.toFixed(1)},${p.y.toFixed(1)}`;
      })
      .join(" ");
  const scored = result.events.some((e) => e.value != null);
  return (
    <svg
      viewBox="-60 0 560 380"
      className="w-full max-w-xl mx-auto"
      role="img"
      aria-label={`${result.standard.name} scorecard`}
    >
      {[1, 2, 3].map((level) => (
        <polygon
          key={level}
          points={polygon(() => level)}
          fill="none"
          stroke="var(--erv-outline-variant)"
          strokeWidth={level === 1 ? 1.5 : 1}
        />
      ))}
      {result.events.map((e, i) => {
        const p = point(i, 3);
        return (
          <line
            key={e.event.id}
            x1={cx}
            y1={cy}
            x2={p.x}
            y2={p.y}
            stroke="var(--erv-outline-variant)"
            strokeDasharray="3 3"
          />
        );
      })}
      {scored ? (
        <g>
          <polygon
            points={polygon((i) => Math.max(0.05, result.events[i].score))}
            fill="var(--erv-primary)"
            fillOpacity={0.18}
            stroke="var(--erv-primary)"
            strokeWidth={2}
          />
          {result.events.map((e, i) =>
            e.value == null ? null : (
              <circle
                key={e.event.id}
                cx={point(i, e.score).x}
                cy={point(i, e.score).y}
                r={4}
                fill="var(--erv-primary)"
              >
                <title>{`${e.event.name}: ${tierLabel(e.tier)}`}</title>
              </circle>
            ),
          )}
        </g>
      ) : null}
      {["Base", "Strong", "Elite"].map((label, i) => (
        <text
          key={label}
          x={cx + 5}
          y={cy - (i + 1) * ring + 12}
          fontSize="10"
          fill="var(--erv-on-surface-variant)"
          stroke="var(--erv-surface)"
          strokeWidth={3}
          paintOrder="stroke"
        >
          {label}
        </text>
      ))}
      {result.events.map((e, i) => {
        const p = point(i, 3.45);
        const cos = Math.cos(angle(i));
        const sin = Math.sin(angle(i));
        const anchor = cos > 0.3 ? "start" : cos < -0.3 ? "end" : "middle";
        const dy = sin > 0.5 ? 8 : sin < -0.9 ? -16 : -4;
        return (
          <text key={e.event.id} x={p.x} y={p.y + dy} textAnchor={anchor} fontSize="12" fill="var(--erv-on-surface)">
            <tspan fontWeight={600}>{e.event.name}</tspan>
            <tspan x={p.x} dy={15} fill="var(--erv-on-surface-variant)">
              {e.value == null ? "—" : tierLabel(e.tier)}
            </tspan>
          </text>
        );
      })}
    </svg>
  );
}

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function LogResultForm({
  event,
  unit,
  saving,
  onSave,
  onCancel,
}: {
  event: StandardEventResult["event"];
  unit: BodyWeightUnit;
  saving: boolean;
  onSave: (value: number, date: string, note: string | null) => Promise<boolean>;
  onCancel: () => void;
}) {
  const [raw, setRaw] = useState("");
  const [date, setDate] = useState(todayIso());
  const [note, setNote] = useState("");
  const value = parseResultInput(event, raw, unit);
  const invalid = raw.trim() !== "" && value == null;
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (value == null || !date) return;
    if (await onSave(value, date, note.trim() || null)) onCancel();
  };
  // Units stay as typed ("m:ss", "lb"); only the words go through title case.
  const fullLabel = resultInputLabel(event, unit);
  const unitMatch = /^(.*) \(([^)]*)\)$/.exec(fullLabel);
  const labelText = unitMatch ? unitMatch[1] : fullLabel;
  const labelUnit = unitMatch ? unitMatch[2] : null;
  const valueId = `result-${event.id}-value`;
  const dateId = `result-${event.id}-date`;
  const noteId = `result-${event.id}-note`;
  return (
    <form className="mt-2 rounded-lg border border-[var(--erv-outline-variant)] p-3 space-y-3" onSubmit={submit}>
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="block space-y-1" htmlFor={valueId}>
          <span className="label">
            <FieldLabel>{labelText}</FieldLabel>
            {labelUnit ? ` (${labelUnit})` : null}
          </span>
          <input
            id={valueId}
            className="input w-full"
            inputMode={resultInputLabel(event, unit).startsWith("Time") ? "text" : "decimal"}
            placeholder={resultInputLabel(event, unit).startsWith("Time") ? "2:30" : ""}
            value={raw}
            onChange={(e) => setRaw(e.target.value)}
            autoFocus
          />
          {invalid ? <span className="text-xs text-[var(--erv-error)] block">Check this value</span> : null}
        </label>
        <label className="block space-y-1" htmlFor={dateId}>
          <span className="label">
            <FieldLabel>Date</FieldLabel>
          </span>
          <input
            id={dateId}
            type="date"
            className="input w-full"
            max={todayIso()}
            value={date}
            onChange={(e) => setDate(e.target.value)}
          />
        </label>
        <label className="block space-y-1" htmlFor={noteId}>
          <span className="label">
            <FieldLabel>Note</FieldLabel> (optional)
          </span>
          <input id={noteId} className="input w-full" value={note} onChange={(e) => setNote(e.target.value)} />
        </label>
      </div>
      <div className="flex gap-2">
        <button type="submit" className="btn-primary px-3 py-1 text-sm" disabled={saving || value == null || !date}>
          {saving ? "Saving…" : "Save Result"}
        </button>
        <button type="button" className="btn-ghost px-3 py-1 text-sm" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}

function EventRow({
  result,
  unit,
  latestBodyWeightKg,
  tested,
  saving,
  onLog,
  onDelete,
}: {
  result: StandardEventResult;
  unit: BodyWeightUnit;
  latestBodyWeightKg: number | null;
  tested: StoredStandardResult[];
  saving: boolean;
  onLog: (value: number, date: string, note: string | null) => Promise<boolean>;
  onDelete: (id: string) => void;
}) {
  const { event } = result;
  const gap = formatGap(result, unit, latestBodyWeightKg);
  const source = sourceLabel(result);
  const [logging, setLogging] = useState(false);
  const [showTests, setShowTests] = useState(false);
  return (
    <li className="py-3">
      <div className="grid gap-x-4 gap-y-1 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)_6.5rem] sm:items-center">
        <div className="min-w-0">
          <p className="font-medium text-heading">{event.name}</p>
          {event.detail ? <p className="text-xs text-muted">{event.detail}</p> : null}
        </div>
        <div className="min-w-0 text-sm">
          {result.value == null ? (
            <p className="text-muted">{missingLabel(result)}</p>
          ) : (
            <>
              <p>
                <span className="font-medium text-heading">{formatStandardValue(event, result.value, unit)}</span>
                {source ? <span className="text-xs text-muted"> · {source}</span> : null}
                {result.date ? <span className="text-xs text-muted"> · {formatDay(result.date)}</span> : null}
              </p>
              {result.note ? <p className="text-xs text-muted">{result.note}</p> : null}
            </>
          )}
          <p className="text-xs text-muted">
            {gap ??
              `Base ${formatStandardValue(event, result.tiers.BASE, unit)} · Strong ${formatStandardValue(
                event,
                result.tiers.STRONG,
                unit,
              )} · Elite ${formatStandardValue(event, result.tiers.ELITE, unit)}`}
          </p>
        </div>
        <div className="sm:text-right">{result.value == null ? null : <TierBadge tier={result.tier} />}</div>
      </div>
      <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs">
        {!logging ? (
          <button type="button" className="underline text-[var(--erv-primary)]" onClick={() => setLogging(true)}>
            Log Result
          </button>
        ) : null}
        {tested.length > 0 ? (
          <button type="button" className="underline text-muted" onClick={() => setShowTests((v) => !v)}>
            {showTests ? "Hide" : "Show"} {tested.length} tested {tested.length === 1 ? "result" : "results"}
          </button>
        ) : null}
      </div>
      {logging ? (
        <LogResultForm event={event} unit={unit} saving={saving} onSave={onLog} onCancel={() => setLogging(false)} />
      ) : null}
      {showTests && tested.length > 0 ? (
        <ul className="mt-2 space-y-1 text-sm">
          {tested.map((t) => (
            <li key={t.id} className="flex flex-wrap items-center gap-x-2">
              <span className="font-medium text-heading">{formatRawResult(event, t.value, unit)}</span>
              <span className="text-xs text-muted">{formatDay(t.date)}</span>
              {t.note ? <span className="text-xs text-muted">· {t.note}</span> : null}
              <button
                type="button"
                className="btn-ghost px-2 py-0.5 text-xs"
                disabled={saving}
                aria-label={`Delete ${event.name} result from ${formatDay(t.date)}`}
                onClick={() => onDelete(t.id)}
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </li>
  );
}

/**
 * Fitness standards on Progress: follow one or more, then see each event's tier, filled from
 * lift and cardio history (all time, not the period filter), with the gap to the next tier.
 */
export function StandardsSection({
  weightLogs,
  cardioLogs,
  bodyWeights,
  exercises,
  weightUnit,
  relayRecords,
}: {
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  bodyWeights: BodyWeightEntry[];
  exercises: { id: string; name: string }[];
  weightUnit: BodyWeightUnit;
  /** Relay app data, for logged test results. */
  relayRecords: AppDataRecord[];
}) {
  const { published: profile, publishPatch, publishing, loading } = useTrainingProfile();

  const relayCustom = useMemo(
    () => parseCustomStandards(relayRecords.find((r) => r.d_tag === CUSTOM_STANDARDS_D_TAG)?.plaintext),
    [relayRecords],
  );
  const [localCustom, setLocalCustom] = useState<CustomStandardsPayload | null>(null);
  const custom =
    localCustom && localCustom.lastModifiedEpochSeconds >= relayCustom.lastModifiedEpochSeconds
      ? localCustom
      : relayCustom;
  const findStandard = (id: string): FitnessStandard | null =>
    builtInStandard(id) ?? custom.standards.find((s) => s.id === id) ?? null;
  const [editing, setEditing] = useState<CustomStandardDraft | null>(null);
  const [savingCustom, setSavingCustom] = useState(false);

  const followed = useMemo(
    () =>
      (profile.followedStandardIds ?? []).filter(
        (id) => builtInStandard(id) != null || custom.standards.some((s) => s.id === id),
      ),
    [profile.followedStandardIds, custom],
  );
  const mainId =
    profile.mainStandardId && followed.includes(profile.mainStandardId) ? profile.mainStandardId : followed[0];
  const [viewId, setViewId] = useState<string | null>(null);
  const [managing, setManaging] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const currentId = viewId && followed.includes(viewId) ? viewId : mainId;
  const standard = currentId ? findStandard(currentId) : null;
  const showPicker = managing || (!loading && followed.length === 0);

  const relayResults = useMemo(
    () => parseStandardResults(relayRecords.find((r) => r.d_tag === STANDARD_RESULTS_D_TAG)?.plaintext),
    [relayRecords],
  );
  const [localResults, setLocalResults] = useState<StandardResultsPayload | null>(null);
  const results =
    localResults && localResults.lastModifiedEpochSeconds >= relayResults.lastModifiedEpochSeconds
      ? localResults
      : relayResults;
  const [savingResult, setSavingResult] = useState(false);

  const publishResults = async (next: StandardResultsPayload): Promise<boolean> => {
    setSavingResult(true);
    setSaveError(null);
    try {
      const now = next.lastModifiedEpochSeconds;
      await api.publishAppData({ d_tag: STANDARD_RESULTS_D_TAG, plaintext: serializeStandardResults(next, now) });
      setLocalResults(next);
      return true;
    } catch (e) {
      setSaveError(e instanceof ApiError ? e.message : "Could not save the result. Try again.");
      return false;
    } finally {
      setSavingResult(false);
    }
  };

  const exerciseNames = useMemo(() => new Map(exercises.map((e) => [e.id, e.name])), [exercises]);
  const result = useMemo(
    () =>
      standard
        ? evaluateStandard(standard, {
            weightLogs,
            cardioLogs,
            bodyWeights,
            exerciseNames,
            profile: { ageYears: profile.ageYears, sex: profile.sex, heightCm: profile.heightCm },
            testResults: results.results,
          })
        : null,
    [
      standard,
      weightLogs,
      cardioLogs,
      bodyWeights,
      exerciseNames,
      profile.ageYears,
      profile.sex,
      profile.heightCm,
      results,
    ],
  );
  const latestBodyWeightKg = bodyWeights.length > 0 ? bodyWeights[bodyWeights.length - 1].weightKg : null;

  const navigate = useNavigate();
  const { updateDrafts } = useWorkoutDrafts();
  /** A test-day workout for [s], opened in the builder as a draft; nothing is published yet. */
  const buildTestWorkout = (s: FitnessStandard) => {
    const workout = buildStandardTestWorkout(s, {
      exercises,
      profile: { ageYears: profile.ageYears, sex: profile.sex, heightCm: profile.heightCm },
      unit: weightUnit,
      latestBodyWeightKg,
    });
    const id = crypto.randomUUID();
    updateDrafts((prev) => ({
      ...prev,
      [id]: { id, name: workout.name, segments: workout.segments, savedAtMs: Date.now() },
    }));
    navigate(`/app/workouts/${id}/edit`);
  };

  const save = async (nextFollowed: string[], nextMain: string | null) => {
    setSaveError(null);
    try {
      await publishPatch({
        followedStandardIds: nextFollowed,
        mainStandardId: nextMain && nextFollowed.includes(nextMain) ? nextMain : (nextFollowed[0] ?? null),
      });
    } catch {
      setSaveError("Could not save. Check the relay connection and try again.");
    }
  };

  const toggleFollow = (id: string) => {
    const next = followed.includes(id) ? followed.filter((f) => f !== id) : [...followed, id];
    void save(next, mainId ?? id);
  };

  const publishCustom = async (next: CustomStandardsPayload): Promise<boolean> => {
    setSavingCustom(true);
    setSaveError(null);
    try {
      const now = next.lastModifiedEpochSeconds;
      await api.publishAppData({ d_tag: CUSTOM_STANDARDS_D_TAG, plaintext: serializeCustomStandards(next, now) });
      setLocalCustom(next);
      return true;
    } catch (e) {
      setSaveError(e instanceof ApiError ? e.message : "Could not save the standard. Try again.");
      return false;
    } finally {
      setSavingCustom(false);
    }
  };

  const saveCustom = async (s: FitnessStandard) => {
    const isNew = !custom.standards.some((c) => c.id === s.id);
    const ok = await publishCustom(upsertCustomStandard(custom, s, Math.floor(Date.now() / 1000)));
    if (!ok) return;
    setEditing(null);
    // A new standard is followed straight away, so it shows up on the card.
    if (isNew && !followed.includes(s.id)) await save([...followed, s.id], mainId ?? s.id);
  };

  const removeCustom = async (id: string) => {
    const ok = await publishCustom(deleteCustomStandard(custom, id, Math.floor(Date.now() / 1000)));
    if (ok && followed.includes(id)) {
      await save(
        followed.filter((f) => f !== id),
        mainId === id ? null : (mainId ?? null),
      );
    }
  };

  const pickerStandards: { standard: FitnessStandard; isCustom: boolean }[] = [
    ...BUILT_IN_STANDARDS.map((standard) => ({ standard, isCustom: false })),
    ...custom.standards.map((standard) => ({ standard, isCustom: true })),
  ];

  const notScored = result ? result.events.length - result.scoredCount : 0;
  const belowBase = result
    ? result.scoredCount - result.tierCounts.BASE - result.tierCounts.STRONG - result.tierCounts.ELITE
    : 0;

  return (
    <section className="card p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <SectionHeader>Fitness Standards</SectionHeader>
        {followed.length > 0 ? (
          <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => setManaging((m) => !m)}>
            {managing ? "Done" : "Choose Standards"}
          </button>
        ) : null}
      </div>

      {saveError ? <p className="text-sm text-[var(--erv-error)]">{saveError}</p> : null}

      {editing ? (
        <CustomStandardEditor
          key={editing.id ?? "new"}
          initial={editing}
          unit={weightUnit}
          exercises={exercises}
          saving={savingCustom || publishing}
          onSave={saveCustom}
          onCancel={() => setEditing(null)}
        />
      ) : null}

      {showPicker && !editing ? (
        <div className="space-y-3">
          <p className="text-sm text-muted">
            Follow one or more standards. Scores fill in from the lifts, runs and rucks you already log.
          </p>
          <ul className="space-y-2">
            {pickerStandards.map(({ standard: s, isCustom }) => {
              const isFollowed = followed.includes(s.id);
              const isMain = isFollowed && s.id === mainId;
              return (
                <li
                  key={s.id}
                  className={`rounded-lg border p-3 flex flex-wrap items-start justify-between gap-3 ${
                    isFollowed ? "border-[var(--erv-primary)]" : "border-[var(--erv-outline-variant)]"
                  }`}
                >
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-heading">
                      {s.name}
                      {isCustom ? <span className="text-xs text-muted"> · Custom</span> : null}
                      {isMain ? <span className="text-xs text-muted"> · Main</span> : null}
                    </p>
                    {s.description ? <p className="text-sm text-muted">{s.description}</p> : null}
                    <p className="text-xs text-muted mt-1">
                      {s.events.map((e) => e.name).join(" · ")}
                      {s.source ? (
                        <>
                          {" · "}
                          <a href={s.source.url} target="_blank" rel="noreferrer" className="underline">
                            {s.source.label}
                          </a>
                        </>
                      ) : null}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {isCustom ? (
                      <>
                        <button
                          type="button"
                          className="btn-ghost px-2 py-1 text-xs"
                          disabled={savingCustom}
                          onClick={() => setEditing(standardToDraft(s, weightUnit, true))}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="btn-ghost px-2 py-1 text-xs"
                          disabled={savingCustom || publishing}
                          onClick={() => {
                            if (window.confirm(`Delete ${s.name}? Its logged test results stay saved.`)) {
                              void removeCustom(s.id);
                            }
                          }}
                        >
                          Delete
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        className="btn-ghost px-2 py-1 text-xs"
                        onClick={() => setEditing(standardToDraft(s, weightUnit, false))}
                      >
                        Copy
                      </button>
                    )}
                    {isFollowed && !isMain ? (
                      <button
                        type="button"
                        className="btn-ghost px-2 py-1 text-xs"
                        disabled={publishing}
                        onClick={() => void save(followed, s.id)}
                      >
                        Make Main
                      </button>
                    ) : null}
                    <button
                      type="button"
                      className={isFollowed ? "btn-ghost px-2 py-1 text-xs" : "btn-primary px-3 py-1 text-xs"}
                      disabled={publishing}
                      onClick={() => toggleFollow(s.id)}
                    >
                      {isFollowed ? "Unfollow" : "Follow"}
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
          <button type="button" className="btn-ghost px-3 py-1 text-sm" onClick={() => setEditing(newStandardDraft())}>
            Create Custom Standard
          </button>
        </div>
      ) : null}

      {!showPicker && !editing && result ? (
        <div className="space-y-4">
          {followed.length > 1 ? (
            <div className="flex flex-wrap gap-2" role="tablist" aria-label="Followed standards">
              {followed.map((id) => {
                const s = findStandard(id);
                if (!s) return null;
                const selected = id === currentId;
                return (
                  <button
                    key={id}
                    type="button"
                    role="tab"
                    aria-selected={selected}
                    className={`rounded-full border px-3 py-1 text-sm ${
                      selected
                        ? "border-[var(--erv-primary)] bg-[var(--erv-primary-container)] text-[var(--erv-on-primary-container)]"
                        : "border-[var(--erv-outline-variant)]"
                    }`}
                    onClick={() => setViewId(id)}
                  >
                    {s.name}
                  </button>
                );
              })}
            </div>
          ) : null}

          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <p className="font-medium text-heading">{result.standard.name}</p>
            {result.scoredCount > 0 ? <TierBadge tier={result.overallTier} /> : null}
            <p className="text-sm text-muted">
              {(["ELITE", "STRONG", "BASE"] as StandardTierId[])
                .filter((t) => result.tierCounts[t] > 0)
                .map((t) => `${result.tierCounts[t]} ${tierLabel(t)}`)
                .concat(belowBase > 0 ? [`${belowBase} Below Base`] : [])
                .concat(notScored > 0 ? [`${notScored} not scored yet`] : [])
                .join(" · ")}
            </p>
          </div>
          <div>
            <button
              type="button"
              className="btn-ghost px-3 py-1 text-sm"
              onClick={() => buildTestWorkout(result.standard)}
            >
              Build Test Workout
            </button>
            <span className="ml-2 text-xs text-muted">
              Opens a draft in the workout builder, one section per event.
            </span>
          </div>
          <p className="text-xs text-muted">
            Overall is the lowest tier across scored events. Est. means estimated from your lift records or the average
            pace of a longer session.
          </p>

          {result.needsProfile.length > 0 ? (
            <p className="text-sm rounded-lg bg-[var(--erv-surface-variant)] px-3 py-2">
              Add your {result.needsProfile.map((f) => PROFILE_FIELD_LABEL[f]).join(" and ")} on the{" "}
              <Link to="/app/profile" className="underline">
                Profile
              </Link>{" "}
              page for the right targets.
            </p>
          ) : null}

          {result.events.length >= 3 ? <StandardsRadar result={result} /> : null}

          <ul className="divide-y divide-[var(--erv-outline-variant)]">
            {result.events.map((e) => (
              <EventRow
                key={`${result.standard.id}:${e.event.id}`}
                result={e}
                unit={weightUnit}
                latestBodyWeightKg={latestBodyWeightKg}
                tested={results.results.filter((t) => t.standardId === result.standard.id && t.eventId === e.event.id)}
                saving={savingResult}
                onLog={(value, date, note) =>
                  publishResults(
                    addStandardResult(
                      results,
                      {
                        id: crypto.randomUUID(),
                        standardId: result.standard.id,
                        eventId: e.event.id,
                        value,
                        date,
                        note,
                      },
                      Math.max(Math.floor(Date.now() / 1000), results.lastModifiedEpochSeconds + 1),
                    ),
                  )
                }
                onDelete={(id) =>
                  void publishResults(
                    removeStandardResult(
                      results,
                      id,
                      Math.max(Math.floor(Date.now() / 1000), results.lastModifiedEpochSeconds + 1),
                    ),
                  )
                }
              />
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
