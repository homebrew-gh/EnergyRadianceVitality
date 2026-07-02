import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import type { LibraryItemKind } from "../components/LibrarySidebar";
import { RoutineBuilderLayout } from "../components/RoutineBuilderLayout";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { ReorderableList } from "../components/ReorderableList";
import { SavedRoutinesPanel } from "../components/SavedRoutinesPanel";
import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { WorkoutPreviewCard } from "../components/WorkoutPreviewCard";
import { WorkoutComposerDock } from "../components/WorkoutComposerDock";
import { WorkoutSegmentEditor } from "../components/WorkoutSegmentEditor";
import { useEquipment } from "../lib/equipmentData";
import { useTrainingProfile } from "../lib/trainingProfileData";
import { useTrainingHistory } from "../lib/trainingHistoryData";
import { useTraining } from "../lib/trainingData";
import { buildTrainingSnapshot, snapshotHasData } from "../lib/trainingSnapshot";
import type { WeightExercisePickerFilter } from "../lib/weightExerciseAvailability";
import { useWeightLoadUnit } from "../lib/weightLoadUnit";
import {
  applyBaselineLoadsToWorkout,
  applyTargetWeightToPrescription,
  duplicateWorkoutWithProgression,
  progressionIncrementKg,
  suggestedTargetWeightKg,
} from "../lib/workoutPrescriptionHints";
import {
  defaultCardioModeForSegment,
  defaultRestPolicy,
  defaultSegmentTitle,
  newCardioItem,
  newMobilityItem,
  newWeightItem,
  segmentIsEmpty,
  segmentItems,
  segmentKindHint,
  segmentKindLabel,
  segmentLibraryKinds,
  upsertWorkout,
  duplicateWorkout,
  weightItems,
  type Workout,
  type WorkoutSegment,
  type WorkoutSegmentKind,
  WORKOUTS_LIBRARY_D_TAG,
} from "../lib/workoutTraining";
import {
  instantiateSegmentTemplate,
  WORKOUT_SEGMENT_TEMPLATES,
} from "../lib/segmentTemplates";

const WORKOUT_COMPOSER_FORM_ID = "workout-composer-form";

function newSegment(kind: WorkoutSegmentKind): WorkoutSegment {
  const base: WorkoutSegment = {
    id: crypto.randomUUID(),
    kind,
    title: defaultSegmentTitle(kind),
    items: [],
  };
  if (kind === "circuit" || kind === "superset") {
    return {
      ...base,
      rounds: 3,
      restPolicy: defaultRestPolicy(),
    };
  }
  return base;
}

function moveSegment(segments: WorkoutSegment[], from: number, to: number): WorkoutSegment[] {
  if (from === to || from < 0 || to < 0 || from >= segments.length || to >= segments.length) {
    return segments;
  }
  const next = [...segments];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item!);
  return next;
}

export function WorkoutsTab() {
  const {
    workouts,
    exercises,
    catalogs,
    cardioRoutines,
    loading,
    saving,
    error,
    lastEventId,
    reload,
    saveWorkouts,
  } = useTraining();
  const { profile } = useTrainingProfile();
  const { gymMembership, equipment, enabledWeightExercisePackIds } = useEquipment();
  const { weightLogs, cardioLogs, lastLoadedAt } = useTrainingHistory();
  const [weightLoadUnit] = useWeightLoadUnit();

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
  const hasBaseline = snapshotHasData(snapshot);
  const progressIncrementKg = progressionIncrementKg(profile.progressionStyle);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [segments, setSegments] = useState<WorkoutSegment[]>([]);
  const [activeSegmentIndex, setActiveSegmentIndex] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [weightPickerFilter, setWeightPickerFilter] =
    useState<WeightExercisePickerFilter>("ALL");
  const alertsRef = useRef<HTMLDivElement>(null);

  const catalogExercises = exercises;
  const activeSegmentIndexResolved =
    activeSegmentIndex ?? (segments.length > 0 ? 0 : null);
  const activeSegment =
    activeSegmentIndexResolved != null
      ? segments[activeSegmentIndexResolved] ?? null
      : null;

  useEffect(() => {
    if (segments.length === 0) {
      if (activeSegmentIndex != null) setActiveSegmentIndex(null);
      return;
    }
    if (activeSegmentIndex == null || activeSegmentIndex >= segments.length) {
      setActiveSegmentIndex(0);
    }
  }, [activeSegmentIndex, segments.length]);
  const catalogPickEnabled = activeSegmentIndexResolved != null;
  const pulseSegmentTypes = segments.length === 0;
  const libraryKinds = useMemo((): LibraryItemKind[] => {
    if (!activeSegment) return ["weight"];
    return segmentLibraryKinds(activeSegment.kind);
  }, [activeSegment?.kind]);
  const selectionKind: LibraryItemKind | undefined =
    libraryKinds.length === 1 ? libraryKinds[0] : undefined;

  const activeWeightIds = useMemo(
    () => new Set(activeSegment ? weightItems(activeSegment).map((i) => i.exerciseId) : []),
    [activeSegment],
  );
  const activeStretchIds = useMemo(
    () =>
      new Set(
        activeSegment
          ? segmentItems(activeSegment)
              .filter((i) => i.type === "mobility")
              .map((i) => i.mobility.catalogId)
          : [],
      ),
    [activeSegment],
  );
  const activeCardioIds = useMemo(
    () =>
      new Set(
        activeSegment
          ? segmentItems(activeSegment)
              .filter((i) => i.type === "cardio")
              .map((i) => i.cardio.activity)
          : [],
      ),
    [activeSegment],
  );

  const resetForm = () => {
    setEditingId(null);
    setName("");
    setSegments([]);
    setActiveSegmentIndex(null);
    setFormError(null);
  };

  const startEdit = (workout: Workout) => {
    setEditingId(workout.id);
    setName(workout.name);
    setSegments(workout.segments.map((s) => ({ ...s, items: [...(s.items ?? [])] })));
    setActiveSegmentIndex(workout.segments.length > 0 ? 0 : null);
    setFormError(null);
    setSuccess(null);
  };

  const onDelete = async (workout: Workout) => {
    if (
      !window.confirm(
        `Delete "${workout.name}"? This publishes an updated library to the relay.`,
      )
    ) {
      return;
    }
    setFormError(null);
    setSuccess(null);
    try {
      await saveWorkouts(workouts.filter((w) => w.id !== workout.id));
      if (editingId === workout.id) resetForm();
      setSuccess(`Deleted "${workout.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Delete failed.");
    }
  };

  const addSegment = (kind: WorkoutSegmentKind) => {
    setSegments((prev) => {
      const next = [...prev, newSegment(kind)];
      setActiveSegmentIndex(next.length - 1);
      return next;
    });
  };

  const addSegmentTemplate = (templateId: string) => {
    const template = WORKOUT_SEGMENT_TEMPLATES.find((t) => t.id === templateId);
    if (!template) return;
    setSegments((prev) => {
      const next = [...prev, instantiateSegmentTemplate(template)];
      setActiveSegmentIndex(next.length - 1);
      return next;
    });
  };

  const updateSegment = (index: number, segment: WorkoutSegment) => {
    setSegments((prev) => prev.map((s, i) => (i === index ? segment : s)));
  };

  const removeSegment = (index: number) => {
    setSegments((prev) => prev.filter((_, i) => i !== index));
    setActiveSegmentIndex((prev) => {
      if (prev == null) return null;
      if (prev === index) return null;
      if (prev > index) return prev - 1;
      return prev;
    });
  };

  const moveSegmentByIndex = (from: number, to: number) => {
    setSegments((prev) => moveSegment(prev, from, to));
    setActiveSegmentIndex((prev) => {
      if (prev == null) return null;
      if (prev === from) return to;
      if (from < prev && to >= prev) return prev - 1;
      if (from > prev && to <= prev) return prev + 1;
      return prev;
    });
  };

  const addCatalogPick = (kind: LibraryItemKind, id: string) => {
    const index = activeSegmentIndexResolved;
    if (index == null) {
      setFormError("Add a segment first, then pick items from the library.");
      return;
    }
    const segment = segments[index];
    if (!segment) return;

    if (kind === "weight") {
      if (weightItems(segment).some((item) => item.exerciseId === id)) return;
      const exercise = catalogExercises.find((ex) => ex.id === id);
      const item = newWeightItem(id, segment.kind, exercise);
      const suggested = suggestedTargetWeightKg(id, snapshot);
      if (suggested != null) {
        item.prescription = applyTargetWeightToPrescription(item.prescription, suggested);
      }
      updateSegment(index, {
        ...segment,
        items: [...segmentItems(segment), item],
      });
      return;
    }
    if (kind === "stretch") {
      if (activeStretchIds.has(id)) return;
      updateSegment(index, {
        ...segment,
        items: [...segmentItems(segment), newMobilityItem(id)],
      });
      return;
    }
    if (kind === "cardio") {
      if (activeCardioIds.has(id)) return;
      updateSegment(index, {
        ...segment,
        items: [
          ...segmentItems(segment),
          newCardioItem(id, defaultCardioModeForSegment(segment.kind)),
        ],
      });
    }
  };

  const onDuplicateWithProgress = async (workout: Workout) => {
    setFormError(null);
    setSuccess(null);
    try {
      const copy = duplicateWorkoutWithProgression(workout, {
        snapshot,
        weightLogs,
        profile,
        computedAtMs: lastLoadedAt ?? Date.now(),
      });
      await saveWorkouts(upsertWorkout(workouts, copy));
      setSuccess(`Duplicated "${workout.name}" with progressed loads as "${copy.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Duplicate with progress failed.");
    }
  };

  const applyBaselineToEditor = (incrementKg = 0) => {
    if (!hasBaseline) return;
    const draft: Workout = {
      id: editingId ?? "draft",
      name: name.trim() || "Draft",
      segments,
      sourceLabel: "Start9",
      lastModifiedEpochSeconds: 0,
      createdAtEpochSeconds: 0,
    };
    const updated = applyBaselineLoadsToWorkout(draft, snapshot, incrementKg);
    setSegments(updated.segments);
    setSuccess(
      incrementKg > 0
        ? `Applied baseline loads + ${incrementKg} kg progress to weight items.`
        : "Applied baseline loads from your training history.",
    );
  };

  const onDuplicate = async (workout: Workout) => {
    setFormError(null);
    setSuccess(null);
    try {
      const copy = duplicateWorkout(workout);
      await saveWorkouts(upsertWorkout(workouts, copy));
      setSuccess(`Duplicated "${workout.name}" as "${copy.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Duplicate failed.");
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSuccess(null);
    const trimmed = name.trim();
    if (!trimmed) {
      setFormError("Workout name is required.");
      return;
    }
    if (segments.length === 0) {
      setFormError("Add at least one segment.");
      return;
    }
    if (segments.some((segment) => segmentIsEmpty(segment))) {
      setFormError("Each segment needs at least one item.");
      return;
    }

    const now = Math.floor(Date.now() / 1000);
    const normalizedSegments = segments.map((segment) => ({
      ...segment,
      title: segment.title?.trim() || null,
    }));
    const workout: Workout = {
      id: editingId ?? crypto.randomUUID(),
      name: trimmed,
      segments: normalizedSegments,
      sourceLabel: "Start9",
      lastModifiedEpochSeconds: now,
      createdAtEpochSeconds: now,
    };

    try {
      const wasEditing = editingId != null;
      await saveWorkouts(upsertWorkout(workouts, workout));
      resetForm();
      setSuccess(
        wasEditing
          ? "Workout updated on the relay. Open ERV on your phone and sync to use it."
          : "Workout published to the relay. Open ERV on your phone and sync to use it.",
      );
      alertsRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Save failed.");
    }
  };

  const draftItemCount = segments.reduce((total, segment) => total + segmentItems(segment).length, 0);
  const activeSegmentLabel = activeSegment
    ? `${segmentKindLabel(activeSegment.kind)}${activeSegment.title ? ` - ${activeSegment.title}` : ""}`
    : "Choose a segment";
  const baselineLabel = hasBaseline ? "Baseline loads ready" : "Sync logs for load hints";
  const showComposerDock =
    editingId != null || name.trim().length > 0 || segments.length > 0;

  const segmentChoice = (
    kind: WorkoutSegmentKind,
    title: string,
    description: string,
    pulse = false,
  ) => (
    <button
      type="button"
      className={`rounded-card border p-3 text-left transition hover:-translate-y-0.5 hover:shadow-card ${
        pulse && pulseSegmentTypes
          ? "erv-pulse-border bg-[var(--erv-primary-container)]/45"
          : "border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]"
      }`}
      title={segmentKindHint(kind)}
      onClick={() => addSegment(kind)}
    >
      <span className="block text-sm font-semibold text-heading">{title}</span>
      <span className="mt-1 block text-xs text-muted">{description}</span>
    </button>
  );

  return (
    <div className={`space-y-5 ${showComposerDock ? "lg:pr-[15rem]" : ""}`}>
      <section className="hero-card">
        <div className="flex flex-wrap items-start justify-between gap-5">
          <div className="max-w-2xl space-y-3">
            <span className="sun-chip">Phase 2 Workout Composer</span>
            <div>
              <h2 className="text-3xl font-bold text-heading">Build A Session That Flows</h2>
              <p className="mt-2 text-sm text-muted">
                Storyboard warm-up, lifting, cardio, mobility, rest, and notes in one place.
                Publish to your relay, then run it on Android after sync.
              </p>
            </div>
            <div className="flex flex-wrap gap-2 text-xs text-muted">
              <span className="rounded-pill bg-[var(--erv-surface)]/75 px-3 py-1">
                {WORKOUTS_LIBRARY_D_TAG}
              </span>
              <span className="rounded-pill bg-[var(--erv-surface)]/75 px-3 py-1">
                {baselineLabel}
              </span>
            </div>
          </div>
          <div className="grid min-w-[16rem] flex-1 grid-cols-3 gap-2 sm:max-w-md">
            <div className="metric-card">
              <p className="text-xs text-muted">Saved</p>
              <p className="mt-1 text-2xl font-bold text-heading tabular-nums">{workouts.length}</p>
            </div>
            <div className="metric-card">
              <p className="text-xs text-muted">Segments</p>
              <p className="mt-1 text-2xl font-bold text-heading tabular-nums">{segments.length}</p>
            </div>
            <div className="metric-card">
              <p className="text-xs text-muted">Items</p>
              <p className="mt-1 text-2xl font-bold text-heading tabular-nums">{draftItemCount}</p>
            </div>
          </div>
        </div>
      </section>

      {catalogs.weight.length === 0 && catalogs.stretch.length === 0 ? (
        <p className="text-sm text-muted card p-3">
          Sync ERV on your phone to load full catalogs, or{" "}
          <Link to="/app/catalog" className="underline text-heading">
            edit the catalog
          </Link>
          . Cardio activities are available offline from built-in defaults.
        </p>
      ) : null}

      <div ref={alertsRef}>
        <RoutineFormAlerts
          error={error}
          formError={formError}
          success={success}
          lastEventId={lastEventId}
        />
      </div>

      <SavedRoutinesPanel
        title="Saved workouts"
        loading={loading}
        routines={workouts}
        editingId={editingId}
        onReload={() => void reload(true)}
        getId={(w) => w.id}
        getName={(w) => w.name}
        getDetail={(w) =>
          w.segments
            .map((s) => `${segmentKindLabel(s.kind)} (${segmentItems(s).length})`)
            .join(" · ")
        }
        onEdit={startEdit}
        onDelete={(w) => void onDelete(w)}
        onDuplicate={(w) => void onDuplicate(w)}
        onDuplicateWithProgress={
          weightLogs.length > 0 || hasBaseline
            ? (w) => void onDuplicateWithProgress(w)
            : undefined
        }
      />

      <RoutineBuilderLayout
        sidebarKinds={libraryKinds}
        selectionKind={selectionKind}
        enableWeightEquipmentFilter={libraryKinds.includes("weight")}
        gymMembership={gymMembership}
        ownedEquipment={equipment}
        enabledWeightExercisePackIds={enabledWeightExercisePackIds}
        weightPickerFilter={weightPickerFilter}
        onWeightPickerFilterChange={setWeightPickerFilter}
        selectedIds={
          libraryKinds.length === 1 && libraryKinds[0] === "weight"
            ? activeWeightIds
            : libraryKinds.length === 1 && libraryKinds[0] === "stretch"
              ? activeStretchIds
              : libraryKinds.length === 1 && libraryKinds[0] === "cardio"
                ? activeCardioIds
                : undefined
        }
        onPick={(item) => addCatalogPick(item.kind, item.id)}
        pickLabel="Add"
        pickDisabled={!catalogPickEnabled}
        weightCatalog={catalogExercises}
        stretchCatalog={catalogs.stretch}
        cardioCatalog={catalogs.cardio}
      >
        <form
          id={WORKOUT_COMPOSER_FORM_ID}
          onSubmit={(e) => void onSubmit(e)}
          className="card overflow-hidden pb-24 sm:pb-4"
        >
          <div className="border-b border-[var(--erv-outline-variant)] bg-[var(--erv-surface-variant)]/35 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="text-lg font-semibold text-heading">
                  {editingId ? "Edit Workout" : "Compose Workout"}
                </h3>
                <p className="mt-1 text-sm text-muted">
                  Start with a template or block, select the active segment, then add items from
                  the library.
                </p>
              </div>
              <span className="sun-chip">{activeSegmentLabel}</span>
            </div>
          </div>

          <div className="space-y-4 p-4">

          <label className="block space-y-1">
            <FieldLabel className="text-sm font-medium">Workout name</FieldLabel>
            <input
              className="input w-full"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Full session — warm-up to cooldown"
            />
          </label>

          {hasBaseline && segments.length > 0 ? (
            <div className="flex flex-wrap gap-2 items-center">
              <span className="text-xs text-muted">Load suggestions:</span>
              <button
                type="button"
                className="btn-ghost text-xs py-1 px-2"
                onClick={() => applyBaselineToEditor(0)}
              >
                Apply baseline loads
              </button>
              <button
                type="button"
                className="btn-ghost text-xs py-1 px-2"
                onClick={() => applyBaselineToEditor(progressIncrementKg)}
                title={`Uses profile progression (+${progressIncrementKg} kg) or last-week session when found`}
              >
                Apply baseline + progress (+{progressIncrementKg} kg)
              </button>
            </div>
          ) : null}

          <WorkoutPreviewCard
            name={name}
            segments={segments}
            exercises={catalogExercises}
            stretchCatalog={catalogs.stretch}
            cardioCatalog={catalogs.cardio}
            cardioRoutines={cardioRoutines}
            weightLoadUnit={weightLoadUnit}
          />

          <div className="grid gap-3 md:grid-cols-3">
            <div className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3">
              <p className="text-xs font-semibold text-heading">1. Shape The Arc</p>
              <p className="mt-1 text-xs text-muted">Use templates for common flows or build blocks manually.</p>
            </div>
            <div className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3">
              <p className="text-xs font-semibold text-heading">2. Fill The Segment</p>
              <p className="mt-1 text-xs text-muted">Pick the highlighted segment, then add catalog items.</p>
            </div>
            <div className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3">
              <p className="text-xs font-semibold text-heading">3. Publish And Run</p>
              <p className="mt-1 text-xs text-muted">Relay publish makes the workout available after Android sync.</p>
            </div>
          </div>

          <div className="space-y-4">
            <div className="space-y-2">
              <SectionHeader>Templates</SectionHeader>
              <div className="flex flex-wrap gap-2">
                {WORKOUT_SEGMENT_TEMPLATES.map((template) => (
                  <button
                    key={template.id}
                    type="button"
                    className={`btn-ghost text-sm ${pulseSegmentTypes ? "erv-pulse-border" : ""}`}
                    title={template.description}
                    onClick={() => addSegmentTemplate(template.id)}
                  >
                    {template.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <SectionHeader>Flow</SectionHeader>
              <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
                {segmentChoice("composite", "Flow Block", "Mix warm-up, rest, notes, mobility, and light cardio.", true)}
                {segmentChoice("cardio", "Cardio Block", "Steady effort or saved cardio routine prescriptions.")}
                {segmentChoice("interval", "Interval Block", "Work/recover rounds for conditioning finishers.")}
                {segmentChoice("mobility", "Mobility Block", "Cooldowns, joint prep, and flexibility work.")}
              </div>
            </div>
            <div className="space-y-2">
              <SectionHeader>Lifting</SectionHeader>
              <div className="grid gap-2 sm:grid-cols-3">
                {segmentChoice("straight_sets", "Straight Sets", "Classic exercise-by-exercise strength work.", true)}
                {segmentChoice("circuit", "Circuit", "Round-based training with shared rest rules.", true)}
                {segmentChoice("superset", "Superset", "Pair moves together for density and flow.", true)}
              </div>
            </div>

            {segments.length > 0 ? (
              <ReorderableList
                items={segments}
                getKey={(segment, index) => segment.id ?? String(index)}
                onReorder={(ordered) => {
                  setSegments(ordered);
                  setActiveSegmentIndex((prev) => {
                    if (prev == null) return null;
                    const activeId = segments[prev]?.id;
                    if (activeId == null) return prev;
                    const nextIndex = ordered.findIndex((s) => s.id === activeId);
                    return nextIndex >= 0 ? nextIndex : null;
                  });
                }}
                className="space-y-3"
                renderItem={(segment, index) => (
                  <WorkoutSegmentEditor
                    segment={segment}
                    segmentIndex={index}
                    isActive={activeSegmentIndexResolved === index}
                    catalogExercises={catalogExercises}
                    stretchCatalog={catalogs.stretch}
                    cardioCatalog={catalogs.cardio}
                    cardioRoutines={cardioRoutines}
                    trainingSnapshot={snapshot}
                    weightLoadUnit={weightLoadUnit}
                    onSelect={() => setActiveSegmentIndex(index)}
                    onUpdate={(updated) => updateSegment(index, updated)}
                    onRemove={() => removeSegment(index)}
                    onMoveUp={() => moveSegmentByIndex(index, index - 1)}
                    onMoveDown={() => moveSegmentByIndex(index, index + 1)}
                    canMoveUp={index > 0}
                    canMoveDown={index < segments.length - 1}
                  />
                )}
              />
            ) : null}
          </div>

          {!showComposerDock ? (
            <div className="flex flex-wrap gap-2">
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? "Publishing…" : editingId ? "Publish update" : "Publish to relay"}
              </button>
              {editingId ? (
                <button type="button" className="btn-ghost" onClick={resetForm}>
                  Cancel edit
                </button>
              ) : null}
            </div>
          ) : (
            <p className="text-xs text-muted">
              Use the publish panel on the right to save this workout to your relay.
            </p>
          )}
          </div>
        </form>
      </RoutineBuilderLayout>

      <WorkoutComposerDock
        visible={showComposerDock}
        formId={WORKOUT_COMPOSER_FORM_ID}
        saving={saving}
        editing={editingId != null}
        draftLabel={name}
        segmentCount={segments.length}
        itemCount={draftItemCount}
        onCancelEdit={resetForm}
      />
    </div>
  );
}
