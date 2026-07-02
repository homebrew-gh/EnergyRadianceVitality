import { useState } from "react";
import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { useUnsavedChangesWarning } from "../hooks/useUnsavedChangesWarning";
import { useTrainingProfile } from "../lib/trainingProfileData";
import {
  AVOID_MOVEMENT_PATTERNS,
  CARDIO_BIAS_OPTIONS,
  EXPERIENCE_LEVEL_OPTIONS,
  HEART_RATE_ZONE_METHOD_OPTIONS,
  MAX_TRAINING_STYLE_PRESETS,
  PRIMARY_GOAL_OPTIONS,
  PROGRESSION_STYLE_OPTIONS,
  SESSION_LENGTH_OPTIONS,
  TRAINING_DAYS_PER_WEEK_OPTIONS,
  TRAINING_STYLE_PRESETS,
  type TrainingProfilePayload,
} from "../lib/trainingProfile";

export function ProfileTab() {
  const {
    profile,
    loading,
    publishing,
    dirty,
    error,
    lastEventId,
    reload,
    updateProfile,
    publish,
    discardChanges,
    clearError,
  } = useTrainingProfile();

  const [success, setSuccess] = useState<string | null>(null);

  useUnsavedChangesWarning(dirty);

  const patch = (partial: Partial<TrainingProfilePayload>) => {
    updateProfile({ ...profile, ...partial });
  };

  const togglePreset = (id: string) => {
    const has = profile.stylePresetIds.includes(id);
    patch({
      stylePresetIds: has
        ? profile.stylePresetIds.filter((p) => p !== id)
        : [...profile.stylePresetIds, id].slice(0, MAX_TRAINING_STYLE_PRESETS),
    });
  };

  const toggleAvoid = (id: string) => {
    const has = profile.avoidMovementPatterns.includes(id);
    patch({
      avoidMovementPatterns: has
        ? profile.avoidMovementPatterns.filter((p) => p !== id)
        : [...profile.avoidMovementPatterns, id],
    });
  };

  const onPublish = async () => {
    clearError();
    setSuccess(null);
    try {
      await publish();
      setSuccess("Training profile pushed to your relay.");
    } catch {
      /* provider surfaces error */
    }
  };

  if (loading) {
    return <p className="text-sm text-muted">Loading training profile…</p>;
  }

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-2">
            <h2 className="text-2xl font-bold text-heading">Training profile</h2>
            <p className="text-sm text-muted max-w-2xl">
              Goals, training style, and movement limits for individualized workout planning.
              Syncs to your relay — the Android app shows a read-only summary after sync.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn-ghost text-sm" onClick={() => void reload(true)}>
              Reload
            </button>
            {dirty ? (
              <button type="button" className="btn-ghost text-sm" onClick={discardChanges}>
                Discard
              </button>
            ) : null}
            <button
              type="button"
              className="btn-primary text-sm"
              disabled={publishing || !dirty}
              onClick={() => void onPublish()}
            >
              {publishing ? "Publishing…" : "Publish profile"}
            </button>
          </div>
        </div>
        {dirty ? (
          <p className="text-sm text-amber-700 dark:text-amber-300">Unpublished changes</p>
        ) : null}
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        {success ? <p className="text-sm text-green-700">{success}</p> : null}
        {lastEventId ? (
          <p className="text-xs text-muted font-mono break-all">Last event: {lastEventId}</p>
        ) : null}
      </header>

      <section className="card p-5 space-y-4">
        <SectionHeader>Program intent</SectionHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Primary goal</FieldLabel>
            </span>
            <select
              className="input w-full"
              value={profile.primaryGoal ?? ""}
              onChange={(e) =>
                patch({
                  primaryGoal: e.target.value
                    ? (e.target.value as TrainingProfilePayload["primaryGoal"])
                    : null,
                })
              }
            >
              <option value="">Not set</option>
              {PRIMARY_GOAL_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Experience level</FieldLabel>
            </span>
            <select
              className="input w-full"
              value={profile.experienceLevel ?? ""}
              onChange={(e) =>
                patch({
                  experienceLevel: e.target.value
                    ? (e.target.value as TrainingProfilePayload["experienceLevel"])
                    : null,
                })
              }
            >
              <option value="">Not set</option>
              {EXPERIENCE_LEVEL_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Typical session length</FieldLabel>
            </span>
            <select
              className="input w-full"
              value={profile.typicalSessionMinutes ?? ""}
              onChange={(e) =>
                patch({
                  typicalSessionMinutes: e.target.value ? Number(e.target.value) : null,
                })
              }
            >
              <option value="">Not set</option>
              {SESSION_LENGTH_OPTIONS.map((m) => (
                <option key={m} value={m}>
                  {m} minutes
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Training days per week</FieldLabel>
            </span>
            <select
              className="input w-full"
              value={profile.typicalTrainingDaysPerWeek ?? ""}
              onChange={(e) =>
                patch({
                  typicalTrainingDaysPerWeek: e.target.value ? Number(e.target.value) : null,
                })
              }
            >
              <option value="">Not set</option>
              {TRAINING_DAYS_PER_WEEK_OPTIONS.map((days) => (
                <option key={days} value={days}>
                  {days} {days === 1 ? "day" : "days"}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Progression style</FieldLabel>
            </span>
            <select
              className="input w-full"
              value={profile.progressionStyle ?? ""}
              onChange={(e) =>
                patch({
                  progressionStyle: e.target.value
                    ? (e.target.value as TrainingProfilePayload["progressionStyle"])
                    : null,
                })
              }
            >
              <option value="">Not set</option>
              {PROGRESSION_STYLE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Cardio bias</FieldLabel>
            </span>
            <select
              className="input w-full"
              value={profile.cardioBias ?? ""}
              onChange={(e) =>
                patch({
                  cardioBias: e.target.value
                    ? (e.target.value as TrainingProfilePayload["cardioBias"])
                    : null,
                })
              }
            >
              <option value="">Not set</option>
              {CARDIO_BIAS_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Age (years)</FieldLabel>
            </span>
            <input
              type="number"
              className="input w-full"
              min={13}
              max={120}
              value={profile.ageYears ?? ""}
              onChange={(e) =>
                patch({
                  ageYears: e.target.value ? Number(e.target.value) : null,
                })
              }
            />
          </label>
        </div>
      </section>

      <section className="card p-5 space-y-4">
        <SectionHeader>Training style</SectionHeader>
        <p className="text-sm text-muted">
          Choose up to {MAX_TRAINING_STYLE_PRESETS} structured influences. These presets guide
          future workout generation; the actual split should be derived from your goal, schedule,
          equipment, and training history.
        </p>
        {profile.stylePresetIds.length >= MAX_TRAINING_STYLE_PRESETS ? (
          <p className="text-xs text-muted">
            Deselect one preset before choosing another.
          </p>
        ) : null}
        <div className="space-y-3">
          {TRAINING_STYLE_PRESETS.map((preset) => {
            const checked = profile.stylePresetIds.includes(preset.id);
            const disabled = !checked && profile.stylePresetIds.length >= MAX_TRAINING_STYLE_PRESETS;
            return (
              <label
                key={preset.id}
                className={`flex gap-3 rounded-lg border p-3 ${
                  checked ? "border-[var(--erv-primary)] bg-[var(--erv-surface)]" : "border-[var(--erv-outline-variant)]"
                } ${disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
              >
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={checked}
                  disabled={disabled}
                  onChange={() => togglePreset(preset.id)}
                />
                <span>
                  <span className="font-medium text-heading block">{preset.label}</span>
                  <span className="text-sm text-muted">{preset.description}</span>
                  <span className="text-xs text-muted block mt-1">
                    Influence examples: {preset.influenceExamples.join(", ")}
                  </span>
                </span>
              </label>
            );
          })}
        </div>
        <label className="block space-y-1">
          <span className="label">
            <FieldLabel>Style notes</FieldLabel>
          </span>
          <textarea
            className="input w-full min-h-[80px]"
            value={profile.styleNotes ?? ""}
            onChange={(e) => patch({ styleNotes: e.target.value || null })}
            placeholder="e.g. Emphasize knee-friendly progressions; conservative load jumps."
          />
        </label>
      </section>

      <section className="card p-5 space-y-4">
        <SectionHeader>Movement limits</SectionHeader>
        <p className="text-sm text-muted">
          Not medical advice — use these to steer exercise selection away from patterns you
          want to limit.
        </p>
        <div className="grid gap-2 sm:grid-cols-2">
          {AVOID_MOVEMENT_PATTERNS.map((pattern) => {
            const checked = profile.avoidMovementPatterns.includes(pattern.id);
            return (
              <label key={pattern.id} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggleAvoid(pattern.id)}
                />
                {pattern.label}
              </label>
            );
          })}
        </div>
        <label className="block space-y-1">
          <span className="label">
            <FieldLabel>Limitation notes</FieldLabel>
          </span>
          <textarea
            className="input w-full min-h-[80px]"
            value={profile.customAvoidNotes ?? ""}
            onChange={(e) => patch({ customAvoidNotes: e.target.value || null })}
            placeholder="Injuries, pain triggers, or movements to avoid."
          />
        </label>
      </section>

      <section className="card p-5 space-y-4">
        <SectionHeader>Heart rate zones</SectionHeader>
        <p className="text-sm text-muted">Optional — used for cardio prescription context.</p>
        <div className="grid gap-4 sm:grid-cols-3">
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Max heart rate</FieldLabel>
            </span>
            <input
              type="number"
              className="input w-full"
              min={100}
              max={230}
              value={profile.heartRateMaxBpm ?? ""}
              onChange={(e) =>
                patch({
                  heartRateMaxBpm: e.target.value ? Number(e.target.value) : null,
                })
              }
            />
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Resting heart rate</FieldLabel>
            </span>
            <input
              type="number"
              className="input w-full"
              min={30}
              max={120}
              value={profile.heartRateRestingBpm ?? ""}
              onChange={(e) =>
                patch({
                  heartRateRestingBpm: e.target.value ? Number(e.target.value) : null,
                })
              }
            />
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Zone method</FieldLabel>
            </span>
            <select
              className="input w-full"
              value={profile.heartRateZoneMethod ?? ""}
              onChange={(e) =>
                patch({
                  heartRateZoneMethod: e.target.value
                    ? (e.target.value as TrainingProfilePayload["heartRateZoneMethod"])
                    : null,
                })
              }
            >
              <option value="">Not set</option>
              {HEART_RATE_ZONE_METHOD_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>
    </div>
  );
}
