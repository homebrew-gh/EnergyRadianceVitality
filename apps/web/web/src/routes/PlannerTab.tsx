import { useEffect, useMemo, useRef, useState } from "react";
import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { useUnsavedChangesWarning } from "../hooks/useUnsavedChangesWarning";
import { useTraining } from "../lib/trainingData";
import {
  createEmptyFitnessProgram,
  emptyProgramMaster,
  ISO_WEEK_DAYS,
  type FitnessProgram,
  type ProgramDayBlock,
  type ProgramMasterPayload,
} from "../lib/programTraining";
import { segmentKindLabel, type Workout } from "../lib/workoutTraining";

function fingerprint(master: ProgramMasterPayload): string {
  return JSON.stringify(master);
}

function activeOrFirstProgram(master: ProgramMasterPayload): FitnessProgram {
  return (
    master.programs.find((p) => p.id === master.activeProgramId) ??
    master.programs[0] ??
    createEmptyFitnessProgram()
  );
}

function workoutBlock(workoutId: string, title: string): ProgramDayBlock {
  return {
    id: crypto.randomUUID(),
    kind: "workout",
    workoutId,
    title,
  };
}

function replaceProgram(master: ProgramMasterPayload, program: FitnessProgram): ProgramMasterPayload {
  const exists = master.programs.some((p) => p.id === program.id);
  return {
    ...master,
    programs: exists
      ? master.programs.map((p) => (p.id === program.id ? program : p))
      : [...master.programs, program],
    activeProgramId: program.id,
  };
}

/** One-line segment recap, e.g. "2× Straight sets · Cardio · Mobility". */
function workoutSummary(workout: Workout): string {
  const counts = new Map<string, number>();
  for (const segment of workout.segments) {
    const label = segmentKindLabel(segment.kind);
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }
  if (counts.size === 0) return "No segments yet";
  return [...counts.entries()]
    .map(([label, n]) => (n > 1 ? `${n}× ${label}` : label))
    .join(" · ");
}

type DayMeta = (typeof ISO_WEEK_DAYS)[number];

export function PlannerTab() {
  const {
    workouts,
    programMaster,
    saveProgramMaster,
    saving,
    loading,
    error,
    lastEventId,
    reload,
  } = useTraining();
  const baseMaster = useMemo(
    () => (programMaster.programs.length > 0 ? programMaster : emptyProgramMaster()),
    [programMaster],
  );
  const initialProgram = useMemo(() => activeOrFirstProgram(baseMaster), [baseMaster]);
  const [draftProgram, setDraftProgram] = useState<FitnessProgram>(initialProgram);
  const [lastSavedFingerprint, setLastSavedFingerprint] = useState(() =>
    fingerprint(replaceProgram(baseMaster, initialProgram)),
  );
  const [message, setMessage] = useState<string | null>(null);
  const [publishError, setPublishError] = useState<string | null>(null);
  const [selectedDay, setSelectedDay] = useState<number>(1);
  const [librarySearch, setLibrarySearch] = useState("");
  const libraryRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (loading) return;
    const next = activeOrFirstProgram(baseMaster);
    setDraftProgram(next);
    setLastSavedFingerprint(fingerprint(replaceProgram(baseMaster, next)));
  }, [baseMaster, loading]);

  const draftMaster = useMemo(
    () => replaceProgram(baseMaster, draftProgram),
    [baseMaster, draftProgram],
  );
  const dirty = fingerprint(draftMaster) !== lastSavedFingerprint;
  useUnsavedChangesWarning(dirty);

  const workoutById = useMemo(() => new Map(workouts.map((w) => [w.id, w])), [workouts]);
  const sortedWorkouts = useMemo(
    () => workouts.slice().sort((a, b) => a.name.localeCompare(b.name)),
    [workouts],
  );
  const visibleWorkouts = useMemo(() => {
    const q = librarySearch.trim().toLowerCase();
    if (!q) return sortedWorkouts;
    return sortedWorkouts.filter(
      (w) =>
        w.name.toLowerCase().includes(q) ||
        (w.tags ?? []).some((t) => t.toLowerCase().includes(q)),
    );
  }, [sortedWorkouts, librarySearch]);

  const dayBlocks = (dayOfWeek: number): ProgramDayBlock[] =>
    (draftProgram.weeklySchedule.find((d) => d.dayOfWeek === dayOfWeek)?.blocks ?? []).filter(
      (block) => block.kind === "workout",
    );

  const selectedDayMeta: DayMeta =
    ISO_WEEK_DAYS.find((d) => d.value === selectedDay) ?? ISO_WEEK_DAYS[0];
  const selectedDayWorkoutIds = new Set(
    dayBlocks(selectedDay)
      .map((b) => b.workoutId)
      .filter((id): id is string => Boolean(id)),
  );
  const plannedDays = ISO_WEEK_DAYS.filter((d) => dayBlocks(d.value).length > 0).length;

  const patchProgram = (partial: Partial<FitnessProgram>) => {
    setMessage(null);
    setPublishError(null);
    setDraftProgram((program) => ({
      ...program,
      ...partial,
      lastModifiedEpochSeconds: Math.floor(Date.now() / 1000),
    }));
  };

  const addDayWorkout = (dayOfWeek: number, workoutId: string) => {
    const workout = workoutById.get(workoutId);
    if (!workout) return;
    patchProgram({
      weeklySchedule: draftProgram.weeklySchedule.map((day) =>
        day.dayOfWeek === dayOfWeek
          ? { ...day, blocks: [...day.blocks, workoutBlock(workout.id, workout.name)] }
          : day,
      ),
    });
  };

  const removeBlock = (dayOfWeek: number, blockId: string) => {
    patchProgram({
      weeklySchedule: draftProgram.weeklySchedule.map((day) =>
        day.dayOfWeek === dayOfWeek
          ? { ...day, blocks: day.blocks.filter((block) => block.id !== blockId) }
          : day,
      ),
    });
  };

  const clearDay = (dayOfWeek: number) => {
    patchProgram({
      weeklySchedule: draftProgram.weeklySchedule.map((day) =>
        day.dayOfWeek === dayOfWeek
          ? { ...day, blocks: day.blocks.filter((block) => block.kind !== "workout") }
          : day,
      ),
    });
  };

  const focusDay = (dayOfWeek: number, scrollToLibrary = false) => {
    setSelectedDay(dayOfWeek);
    if (scrollToLibrary && window.matchMedia("(max-width: 1023px)").matches) {
      libraryRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  };

  const onPublish = async () => {
    setMessage(null);
    setPublishError(null);
    try {
      await saveProgramMaster(draftMaster);
      setLastSavedFingerprint(fingerprint(draftMaster));
      setMessage("Weekly planner pushed to your relay.");
    } catch (e) {
      setPublishError(e instanceof Error ? e.message : "Could not publish weekly planner.");
    }
  };

  if (loading) {
    return <p className="text-sm text-muted">Loading planner…</p>;
  }

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-2">
            <h2 className="text-2xl font-bold text-heading">Weekly Planner</h2>
            <p className="text-sm text-muted max-w-2xl">
              Pick a day, then add workouts from your library. Publish to sync this week to Android,
              where you can tap a planned workout to run it live.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn-ghost text-sm" onClick={() => void reload(true)}>
              Reload
            </button>
            <button
              type="button"
              className="btn-primary text-sm"
              disabled={saving || !dirty}
              onClick={() => void onPublish()}
            >
              {saving ? "Publishing…" : "Publish planner"}
            </button>
          </div>
        </div>
        {dirty ? <p className="text-sm text-amber-700 dark:text-amber-300">Unpublished changes</p> : null}
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        {publishError ? <p className="text-sm text-red-600">{publishError}</p> : null}
        {message ? <p className="text-sm text-green-700">{message}</p> : null}
        {lastEventId ? (
          <p className="text-xs text-muted font-mono break-all">Last event: {lastEventId}</p>
        ) : null}
      </header>

      {/* Week at a glance */}
      <section className="hero-card space-y-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <SectionHeader>Week At A Glance</SectionHeader>
          <p className="text-xs text-muted">
            {plannedDays} training {plannedDays === 1 ? "day" : "days"} · {7 - plannedDays} rest
          </p>
        </div>
        <div className="grid grid-cols-7 gap-2">
          {ISO_WEEK_DAYS.map((dayMeta) => {
            const count = dayBlocks(dayMeta.value).length;
            const isSelected = dayMeta.value === selectedDay;
            return (
              <button
                key={dayMeta.value}
                type="button"
                onClick={() => focusDay(dayMeta.value)}
                aria-pressed={isSelected}
                aria-label={`${dayMeta.label}: ${count === 0 ? "rest" : `${count} workout${count === 1 ? "" : "s"}`}`}
                className={`flex flex-col items-center gap-1 rounded-card border px-1 py-2 text-xs transition ${
                  isSelected
                    ? "border-[var(--erv-primary)] bg-[var(--erv-primary-container)]/60 shadow-sm"
                    : count > 0
                      ? "border-[var(--erv-outline-variant)] bg-[var(--erv-surface)]"
                      : "border-dashed border-[var(--erv-outline-variant)] bg-transparent"
                }`}
              >
                <span className="font-semibold text-heading">{dayMeta.shortLabel}</span>
                <span
                  className={`h-2.5 w-2.5 rounded-full ${
                    count > 0 ? "bg-[var(--erv-primary)]" : "bg-[var(--erv-outline-variant)]"
                  }`}
                />
                <span className="text-muted">{count === 0 ? "Rest" : count}</span>
              </button>
            );
          })}
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
        {/* Day list */}
        <section className="space-y-3">
          {ISO_WEEK_DAYS.map((dayMeta) => {
            const blocks = dayBlocks(dayMeta.value);
            const isSelected = dayMeta.value === selectedDay;
            return (
              <article
                key={dayMeta.value}
                onClick={() => focusDay(dayMeta.value)}
                className={`card p-4 space-y-3 cursor-pointer transition ${
                  isSelected ? "erv-pulse-border" : "hover:border-[var(--erv-outline)]"
                }`}
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-3">
                    <span
                      className={`inline-flex h-10 w-10 items-center justify-center rounded-card text-sm font-bold ${
                        blocks.length > 0
                          ? "bg-[var(--erv-primary-container)] text-[var(--erv-on-primary-container)]"
                          : "bg-[var(--erv-surface-variant)] text-muted"
                      }`}
                    >
                      {dayMeta.shortLabel}
                    </span>
                    <div>
                      <p className="font-semibold text-heading leading-tight">{dayMeta.label}</p>
                      <p className="text-xs text-muted">
                        {blocks.length === 0
                          ? "Rest day"
                          : `${blocks.length} workout${blocks.length === 1 ? "" : "s"}`}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {blocks.length > 0 ? (
                      <button
                        type="button"
                        className="btn-ghost text-xs py-1 px-3"
                        onClick={(e) => {
                          e.stopPropagation();
                          clearDay(dayMeta.value);
                        }}
                      >
                        Clear day
                      </button>
                    ) : null}
                    <button
                      type="button"
                      className={`text-xs py-1 px-3 ${isSelected ? "btn-primary" : "btn-ghost"}`}
                      disabled={workouts.length === 0}
                      onClick={(e) => {
                        e.stopPropagation();
                        focusDay(dayMeta.value, true);
                      }}
                    >
                      Add workout
                    </button>
                  </div>
                </div>

                {blocks.length > 0 ? (
                  <ul className="space-y-2">
                    {blocks.map((block, index) => {
                      const workout = block.workoutId ? workoutById.get(block.workoutId) : null;
                      return (
                        <li
                          key={block.id}
                          className="flex items-start justify-between gap-3 rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-surface)] px-3 py-2"
                        >
                          <div className="min-w-0 flex items-start gap-3">
                            <span className="mt-0.5 sun-chip px-2 py-0.5">{index + 1}</span>
                            <div className="min-w-0">
                              <p className="truncate font-medium text-heading">
                                {workout?.name ?? block.title ?? "Missing workout"}
                              </p>
                              <p className="text-xs text-muted">
                                {workout ? workoutSummary(workout) : "Not in your library — remove or replace"}
                              </p>
                            </div>
                          </div>
                          <button
                            type="button"
                            aria-label={`Remove ${workout?.name ?? block.title ?? "workout"} from ${dayMeta.label}`}
                            className="btn-ghost text-xs py-1 px-2 shrink-0"
                            onClick={(e) => {
                              e.stopPropagation();
                              removeBlock(dayMeta.value, block.id);
                            }}
                          >
                            Remove
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                ) : (
                  <p className="rounded-card border border-dashed border-[var(--erv-outline-variant)] px-3 py-3 text-center text-sm text-muted">
                    {isSelected
                      ? "Choose a workout from the library to plan this day."
                      : "Nothing planned. Select this day to add a workout."}
                  </p>
                )}
              </article>
            );
          })}
        </section>

        {/* Workout library */}
        <aside ref={libraryRef} className="card p-4 space-y-3 lg:sticky lg:top-24 scroll-mt-24">
          <div className="space-y-1">
            <SectionHeader>Workout Library</SectionHeader>
            <p className="text-sm text-muted">
              Adding to <span className="font-semibold text-heading">{selectedDayMeta.label}</span>
            </p>
          </div>

          {workouts.length === 0 ? (
            <p className="text-sm text-muted">
              Build and publish at least one workout in Workout Builder before assigning planner days.
            </p>
          ) : (
            <>
              <label className="block space-y-1">
                <span className="label">
                  <FieldLabel>Search workouts</FieldLabel>
                </span>
                <input
                  className="input w-full text-sm"
                  value={librarySearch}
                  placeholder="Name or tag…"
                  onChange={(e) => setLibrarySearch(e.target.value)}
                />
              </label>
              <ul className="space-y-2 max-h-[60vh] overflow-y-auto pr-1">
                {visibleWorkouts.length === 0 ? (
                  <li className="text-sm text-muted">No workouts match “{librarySearch}”.</li>
                ) : null}
                {visibleWorkouts.map((workout) => {
                  const alreadyOnDay = selectedDayWorkoutIds.has(workout.id);
                  return (
                    <li
                      key={workout.id}
                      className="flex items-start justify-between gap-3 rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-surface)] px-3 py-2"
                    >
                      <div className="min-w-0">
                        <p className="truncate font-medium text-heading">{workout.name}</p>
                        <p className="text-xs text-muted">{workoutSummary(workout)}</p>
                      </div>
                      <button
                        type="button"
                        className={`text-xs py-1 px-3 shrink-0 ${alreadyOnDay ? "btn-ghost" : "btn-primary"}`}
                        disabled={alreadyOnDay}
                        title={
                          alreadyOnDay
                            ? `Already planned for ${selectedDayMeta.label}`
                            : `Add to ${selectedDayMeta.label}`
                        }
                        onClick={() => addDayWorkout(selectedDay, workout.id)}
                      >
                        {alreadyOnDay ? "Added" : `Add to ${selectedDayMeta.shortLabel}`}
                      </button>
                    </li>
                  );
                })}
              </ul>
            </>
          )}
        </aside>
      </div>

      <details className="card p-5 space-y-4">
        <summary className="cursor-pointer list-none">
          <SectionHeader>Plan Details</SectionHeader>
          <p className="text-xs text-muted mt-1">Name, source label, and description for this plan.</p>
        </summary>
        <div className="grid gap-4 sm:grid-cols-2 pt-2">
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Program name</FieldLabel>
            </span>
            <input
              className="input w-full"
              value={draftProgram.name}
              onChange={(e) => patchProgram({ name: e.target.value || "Weekly Plan" })}
            />
          </label>
          <label className="block space-y-1">
            <span className="label">
              <FieldLabel>Source label</FieldLabel>
            </span>
            <input
              className="input w-full"
              value={draftProgram.sourceLabel ?? ""}
              placeholder="Web Planner"
              onChange={(e) => patchProgram({ sourceLabel: e.target.value || null })}
            />
          </label>
        </div>
        <label className="block space-y-1">
          <span className="label">
            <FieldLabel>Description</FieldLabel>
          </span>
          <textarea
            className="input w-full min-h-[72px]"
            value={draftProgram.description ?? ""}
            onChange={(e) => patchProgram({ description: e.target.value || null })}
          />
        </label>
      </details>
    </div>
  );
}
