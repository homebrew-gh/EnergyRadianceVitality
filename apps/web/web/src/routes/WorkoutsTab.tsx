import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import type { LibraryItemKind } from "../components/LibrarySidebar";
import { RoutineBuilderLayout } from "../components/RoutineBuilderLayout";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { ReorderableList } from "../components/ReorderableList";
import { SavedRoutinesPanel } from "../components/SavedRoutinesPanel";
import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { WorkoutSegmentEditor } from "../components/WorkoutSegmentEditor";
import { useTraining } from "../lib/trainingData";
import {
  defaultCardioModeForSegment,
  defaultRestPolicy,
  defaultSegmentTitle,
  newCardioItem,
  newMobilityItem,
  newWeightItem,
  segmentIsEmpty,
  segmentItems,
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

  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [segments, setSegments] = useState<WorkoutSegment[]>([]);
  const [activeSegmentIndex, setActiveSegmentIndex] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const catalogExercises = catalogs.weight.length > 0 ? catalogs.weight : exercises;
  const activeSegment =
    activeSegmentIndex != null ? segments[activeSegmentIndex] ?? null : null;
  const catalogPickEnabled = activeSegmentIndex != null;
  const pulseSegmentTypes = segments.length === 0;
  const libraryKinds = activeSegment
    ? segmentLibraryKinds(activeSegment.kind)
    : (["weight"] as LibraryItemKind[]);
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
    if (activeSegmentIndex == null) {
      setFormError("Add a segment first, then pick items from the library.");
      return;
    }
    const segment = segments[activeSegmentIndex];
    if (!segment) return;

    if (kind === "weight") {
      if (weightItems(segment).some((item) => item.exerciseId === id)) return;
      const exercise = catalogExercises.find((ex) => ex.id === id);
      updateSegment(activeSegmentIndex, {
        ...segment,
        items: [...segmentItems(segment), newWeightItem(id, segment.kind, exercise)],
      });
      return;
    }
    if (kind === "stretch") {
      if (activeStretchIds.has(id)) return;
      updateSegment(activeSegmentIndex, {
        ...segment,
        items: [...segmentItems(segment), newMobilityItem(id)],
      });
      return;
    }
    if (kind === "cardio") {
      if (activeCardioIds.has(id)) return;
      updateSegment(activeSegmentIndex, {
        ...segment,
        items: [
          ...segmentItems(segment),
          newCardioItem(id, defaultCardioModeForSegment(segment.kind)),
        ],
      });
    }
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
    const workout: Workout = {
      id: editingId ?? crypto.randomUUID(),
      name: trimmed,
      segments,
      sourceLabel: "Start9",
      lastModifiedEpochSeconds: now,
      createdAtEpochSeconds: now,
    };

    try {
      await saveWorkouts(upsertWorkout(workouts, workout));
      setSuccess(
        editingId
          ? "Workout updated on the relay. Open ERV on your phone and sync to use it."
          : "Workout published to the relay. Open ERV on your phone and sync to use it.",
      );
      resetForm();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Save failed.");
    }
  };

  const segmentButton = (kind: WorkoutSegmentKind, label: string, pulse = false) => (
    <button
      type="button"
      className={`btn-ghost text-sm ${pulse && pulseSegmentTypes ? "erv-pulse-border" : ""}`}
      onClick={() => addSegment(kind)}
    >
      {label}
    </button>
  );

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-heading">Workout builder</h2>
        <p className="text-sm text-muted mt-1">
          Build full warm-up through cooldown flows — lifts, cardio, and stretching in one
          storyboard. Publish to your relay; run on Android after sync.{" "}
          <code className="text-xs">{WORKOUTS_LIBRARY_D_TAG}</code>
        </p>
      </div>

      {catalogs.weight.length === 0 && catalogs.stretch.length === 0 && catalogs.cardio.length === 0 ? (
        <p className="text-sm text-muted card p-3">
          Sync ERV on your phone to load catalogs, or{" "}
          <Link to="/app/catalog" className="underline text-heading">
            edit the catalog
          </Link>
          .
        </p>
      ) : null}

      <RoutineFormAlerts
        error={error}
        formError={formError}
        success={success}
        lastEventId={lastEventId}
      />

      <SavedRoutinesPanel
        title="Saved workouts"
        loading={loading}
        routines={workouts}
        editingId={editingId}
        onReload={() => void reload()}
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
      />

      <RoutineBuilderLayout
        sidebarKinds={libraryKinds}
        selectionKind={selectionKind}
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
        pickLabel="ADD"
        pickDisabled={!catalogPickEnabled}
        weightCatalog={catalogExercises}
        stretchCatalog={catalogs.stretch}
        cardioCatalog={catalogs.cardio}
      >
        <form onSubmit={(e) => void onSubmit(e)} className="card p-4 space-y-4">
          <h3 className="text-lg font-semibold text-heading">
            {editingId ? "Edit workout" : "Compose workout"}
          </h3>
          <p className="text-sm text-muted">
            Add a Flow block for mixed warm-up or cooldown, then lifting segments. Select a
            segment to enable the library.
          </p>

          <label className="block space-y-1">
            <FieldLabel className="text-sm font-medium">Workout name</FieldLabel>
            <input
              className="input w-full"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Full session — warm-up to cooldown"
            />
          </label>

          <div className="space-y-3">
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
              <div className="flex flex-wrap gap-2">
                {segmentButton("composite", "+ Flow block", true)}
                {segmentButton("cardio", "+ Cardio block")}
                {segmentButton("interval", "+ Interval block")}
                {segmentButton("mobility", "+ Mobility block")}
              </div>
            </div>
            <div className="space-y-2">
              <SectionHeader>Lifting</SectionHeader>
              <div className="flex flex-wrap gap-2">
                {segmentButton("straight_sets", "+ Straight sets", true)}
                {segmentButton("circuit", "+ Circuit", true)}
                {segmentButton("superset", "+ Superset", true)}
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
                    isActive={activeSegmentIndex === index}
                    catalogExercises={catalogExercises}
                    stretchCatalog={catalogs.stretch}
                    cardioCatalog={catalogs.cardio}
                    cardioRoutines={cardioRoutines}
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
        </form>
      </RoutineBuilderLayout>
    </div>
  );
}
