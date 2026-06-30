import { useEffect, useMemo, useState } from "react";
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

  const patchProgram = (partial: Partial<FitnessProgram>) => {
    setMessage(null);
    setPublishError(null);
    setDraftProgram((program) => ({
      ...program,
      ...partial,
      lastModifiedEpochSeconds: Math.floor(Date.now() / 1000),
    }));
  };

  const setDayWorkout = (dayOfWeek: number, workoutId: string) => {
    const workout = workoutById.get(workoutId);
    patchProgram({
      weeklySchedule: draftProgram.weeklySchedule.map((day) => {
        if (day.dayOfWeek !== dayOfWeek) return day;
        const otherBlocks = day.blocks.filter((block) => block.kind !== "workout");
        return {
          ...day,
          blocks: workout ? [...otherBlocks, workoutBlock(workout.id, workout.name)] : otherBlocks,
        };
      }),
    });
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
              Assign saved workouts to days. Publish to sync this weekly plan to Android, then tap a
              planned workout there to run it live.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn-ghost text-sm" onClick={() => void reload()}>
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

      <section className="card p-5 space-y-4">
        <SectionHeader>Plan Details</SectionHeader>
        <div className="grid gap-4 sm:grid-cols-2">
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
      </section>

      <section className="card p-5 space-y-4">
        <SectionHeader>Week Grid</SectionHeader>
        {workouts.length === 0 ? (
          <p className="text-sm text-muted">
            Build and publish at least one workout in Workout Builder before assigning planner days.
          </p>
        ) : null}
        <div className="grid gap-3 lg:grid-cols-7 sm:grid-cols-2">
          {ISO_WEEK_DAYS.map((dayMeta) => {
            const day = draftProgram.weeklySchedule.find((d) => d.dayOfWeek === dayMeta.value) ?? {
              dayOfWeek: dayMeta.value,
              blocks: [],
            };
            const workoutBlocks = day.blocks.filter((block) => block.kind === "workout");
            const selectedWorkoutId = workoutBlocks[0]?.workoutId ?? "";
            return (
              <div key={dayMeta.value} className="rounded-card border border-[var(--erv-outline-variant)] p-3 space-y-3">
                <div>
                  <p className="font-semibold text-heading">{dayMeta.label}</p>
                  <p className="text-xs text-muted">{workoutBlocks.length} workout{workoutBlocks.length === 1 ? "" : "s"}</p>
                </div>
                <label className="block space-y-1">
                  <span className="label">
                    <FieldLabel>Primary workout</FieldLabel>
                  </span>
                  <select
                    className="input w-full text-sm"
                    value={selectedWorkoutId}
                    onChange={(e) => setDayWorkout(dayMeta.value, e.target.value)}
                  >
                    <option value="">Rest / Unassigned</option>
                    {workouts.map((workout) => (
                      <option key={workout.id} value={workout.id}>
                        {workout.name}
                      </option>
                    ))}
                  </select>
                </label>
                {workoutBlocks.length > 0 ? (
                  <div className="space-y-2">
                    {workoutBlocks.map((block) => {
                      const workout = block.workoutId ? workoutById.get(block.workoutId) : null;
                      return (
                        <div key={block.id} className="rounded-lg bg-[var(--erv-surface)] p-2 text-sm">
                          <p className="font-medium text-heading">{workout?.name ?? block.title ?? "Missing workout"}</p>
                          <button
                            type="button"
                            className="btn-ghost text-xs mt-2"
                            onClick={() => removeBlock(dayMeta.value, block.id)}
                          >
                            Remove
                          </button>
                        </div>
                      );
                    })}
                  </div>
                ) : null}
                <label className="block space-y-1">
                  <span className="label">
                    <FieldLabel>Add another workout</FieldLabel>
                  </span>
                  <select
                    className="input w-full text-sm"
                    value=""
                    onChange={(e) => addDayWorkout(dayMeta.value, e.target.value)}
                  >
                    <option value="">Choose workout…</option>
                    {workouts.map((workout) => (
                      <option key={workout.id} value={workout.id}>
                        {workout.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}
