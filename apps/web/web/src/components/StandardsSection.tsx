import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { SectionHeader } from "./FieldLabel";
import type { BodyWeightEntry } from "../lib/bodyWeight";
import type { BodyWeightUnit } from "../lib/fitnessEquipment";
import { evaluateStandard, type StandardEventResult, type StandardResult, type StandardTierId } from "../lib/standards";
import { BUILT_IN_STANDARDS, builtInStandard } from "../lib/standardsLibrary";
import {
  formatGap,
  formatStandardValue,
  missingLabel,
  sourceLabel,
  tierLabel,
} from "../lib/standardsFormat";
import { useTrainingProfile } from "../lib/trainingProfileData";
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
      viewBox="0 0 440 380"
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
          <line key={e.event.id} x1={cx} y1={cy} x2={p.x} y2={p.y} stroke="var(--erv-outline-variant)" strokeDasharray="3 3" />
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
              <circle key={e.event.id} cx={point(i, e.score).x} cy={point(i, e.score).y} r={4} fill="var(--erv-primary)">
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

function EventRow({
  result,
  unit,
  latestBodyWeightKg,
}: {
  result: StandardEventResult;
  unit: BodyWeightUnit;
  latestBodyWeightKg: number | null;
}) {
  const { event } = result;
  const gap = formatGap(result, unit, latestBodyWeightKg);
  const source = sourceLabel(result);
  return (
    <li className="py-3 grid gap-x-4 gap-y-1 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)_6.5rem] sm:items-center">
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
}: {
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  bodyWeights: BodyWeightEntry[];
  exercises: { id: string; name: string }[];
  weightUnit: BodyWeightUnit;
}) {
  const { published: profile, publishPatch, publishing, loading } = useTrainingProfile();
  const followed = useMemo(
    () => (profile.followedStandardIds ?? []).filter((id) => builtInStandard(id) != null),
    [profile.followedStandardIds],
  );
  const mainId = profile.mainStandardId && followed.includes(profile.mainStandardId) ? profile.mainStandardId : followed[0];
  const [viewId, setViewId] = useState<string | null>(null);
  const [managing, setManaging] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const currentId = viewId && followed.includes(viewId) ? viewId : mainId;
  const standard = currentId ? builtInStandard(currentId) : null;
  const showPicker = managing || (!loading && followed.length === 0);

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
          })
        : null,
    [standard, weightLogs, cardioLogs, bodyWeights, exerciseNames, profile.ageYears, profile.sex, profile.heightCm],
  );
  const latestBodyWeightKg = bodyWeights.length > 0 ? bodyWeights[bodyWeights.length - 1].weightKg : null;

  const save = async (nextFollowed: string[], nextMain: string | null) => {
    setSaveError(null);
    try {
      await publishPatch({
        followedStandardIds: nextFollowed,
        mainStandardId: nextMain && nextFollowed.includes(nextMain) ? nextMain : nextFollowed[0] ?? null,
      });
    } catch {
      setSaveError("Could not save. Check the relay connection and try again.");
    }
  };

  const toggleFollow = (id: string) => {
    const next = followed.includes(id) ? followed.filter((f) => f !== id) : [...followed, id];
    void save(next, mainId ?? id);
  };

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

      {showPicker ? (
        <div className="space-y-3">
          <p className="text-sm text-muted">
            Follow one or more standards. Scores fill in from the lifts, runs and rucks you already log.
          </p>
          <ul className="space-y-2">
            {BUILT_IN_STANDARDS.map((s) => {
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
                      {isMain ? <span className="text-xs text-muted"> · Main</span> : null}
                    </p>
                    <p className="text-sm text-muted">{s.description}</p>
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
                  <div className="flex gap-2">
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
        </div>
      ) : null}

      {!showPicker && result ? (
        <div className="space-y-4">
          {followed.length > 1 ? (
            <div className="flex flex-wrap gap-2" role="tablist" aria-label="Followed standards">
              {followed.map((id) => {
                const s = builtInStandard(id);
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
          <p className="text-xs text-muted">
            Overall is the lowest tier across scored events. Est. means estimated from your lift records or
            the average pace of a longer session.
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
              <EventRow key={e.event.id} result={e} unit={weightUnit} latestBodyWeightKg={latestBodyWeightKg} />
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
