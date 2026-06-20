import { FieldLabel } from "../components/FieldLabel";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ReorderableList } from "../components/ReorderableList";
import { RoutineBuilderLayout } from "../components/RoutineBuilderLayout";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { SavedRoutinesPanel } from "../components/SavedRoutinesPanel";
import { upsertRoutine } from "../lib/cardioTraining";
import { useTraining } from "../lib/trainingData";
import { exerciseLabel, type WeightRoutine } from "../lib/weightTraining";

export function WeightRoutinesTab() {
  const {
    routines,
    exercises,
    catalogs,
    loading,
    saving,
    error,
    lastEventId,
    reload,
    saveWeightRoutines,
  } = useTraining();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [notes, setNotes] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const selectedSet = useMemo(() => new Set(selected), [selected]);
  const catalogExercises =
    catalogs.weight.length > 0 ? catalogs.weight : exercises;

  const resetForm = () => {
    setEditingId(null);
    setName("");
    setNotes("");
    setSelected([]);
    setFormError(null);
  };

  const addExercise = (id: string) => {
    setSelected((prev) => (prev.includes(id) ? prev : [...prev, id]));
  };

  const removeExercise = (id: string) => {
    setSelected((prev) => prev.filter((x) => x !== id));
  };

  const startEdit = (routine: WeightRoutine) => {
    setEditingId(routine.id);
    setName(routine.name);
    setNotes(routine.notes ?? "");
    setSelected([...routine.exerciseIds]);
    setFormError(null);
    setSuccess(null);
  };

  const onDelete = async (routine: WeightRoutine) => {
    if (!window.confirm(`Delete "${routine.name}"? This publishes an updated list to the relay.`)) {
      return;
    }
    setFormError(null);
    setSuccess(null);
    try {
      await saveWeightRoutines(routines.filter((r) => r.id !== routine.id));
      if (editingId === routine.id) resetForm();
      setSuccess(`Deleted "${routine.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Delete failed.");
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSuccess(null);
    const trimmed = name.trim();
    if (!trimmed) {
      setFormError("Routine name is required.");
      return;
    }
    if (selected.length === 0) {
      setFormError("Add at least one exercise from the library.");
      return;
    }

    const now = Math.floor(Date.now() / 1000);
    const routine: WeightRoutine = {
      id: editingId ?? crypto.randomUUID(),
      name: trimmed,
      exerciseIds: selected,
      notes: notes.trim() || null,
      lastModifiedEpochSeconds: now,
    };

    try {
      await saveWeightRoutines(upsertRoutine(routines, routine));
      setSuccess(
        editingId
          ? "Routine updated. Sync ERV to pick up changes."
          : "Routine published. Open ERV on your phone and sync to see it under Weight Training → Routines.",
      );
      resetForm();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Save failed.");
    }
  };

  return (
    <div className="space-y-4">
      {catalogs.weight.length === 0 ? (
        <p className="text-sm text-muted card p-3">
          Sync ERV on your phone to load the full exercise catalog, or{" "}
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

      <RoutineBuilderLayout
        sidebarKinds={["weight"]}
        selectionKind="weight"
        selectedIds={selectedSet}
        pickLabel="Add"
        weightCatalog={catalogExercises}
        stretchCatalog={catalogs.stretch}
        cardioCatalog={catalogs.cardio}
        onPick={(item) => {
          if (item.kind === "weight") addExercise(item.id);
        }}
      >
        <form className="card p-4 space-y-4" onSubmit={onSubmit}>
          <h3 className="font-semibold text-heading">
            {editingId ? "Edit weight routine" : "New weight routine"}
          </h3>
          <p className="text-sm text-muted">
            Add exercises from the library, drag to reorder, then publish to{" "}
            <code className="text-xs">erv/weight/routines</code>.
          </p>
          <div>
            <label className="label" htmlFor="routine-name">
              <FieldLabel>Name</FieldLabel>
            </label>
            <input
              id="routine-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Push day A"
              required
            />
          </div>
          <div>
            <label className="label" htmlFor="routine-notes">
              <FieldLabel>Notes (optional)</FieldLabel>
            </label>
            <textarea
              id="routine-notes"
              className="input min-h-[72px]"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>
          <div>
            <p className="label">
              <FieldLabel>{`Exercises (${selected.length})`}</FieldLabel>
            </p>
            {selected.length === 0 ? (
              <p className="text-sm text-muted">Use the sidebar to add exercises.</p>
            ) : (
              <ReorderableList
                items={selected}
                getKey={(id) => id}
                onReorder={setSelected}
                renderItem={(id, index) => (
                  <>
                    <span className="text-muted w-5">{index + 1}.</span>
                    <span className="flex-1">{exerciseLabel(id, exercises)}</span>
                    <button
                      type="button"
                      className="btn-ghost text-xs py-0.5 px-2"
                      onClick={() => removeExercise(id)}
                    >
                      Remove
                    </button>
                  </>
                )}
              />
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="submit" className="btn-primary" disabled={saving || loading}>
              {saving ? "Publishing…" : editingId ? "Save changes" : "Publish to relay"}
            </button>
            {editingId ? (
              <button type="button" className="btn-ghost" onClick={resetForm}>
                Cancel edit
              </button>
            ) : null}
          </div>
        </form>

        <SavedRoutinesPanel
          loading={loading}
          routines={routines}
          editingId={editingId}
          onReload={() => void reload()}
          getId={(r) => r.id}
          getName={(r) => r.name}
          getDetail={(r) =>
            r.exerciseIds.map((id) => exerciseLabel(id, exercises)).join(" · ")
          }
          onEdit={startEdit}
          onDelete={(r) => void onDelete(r)}
        />
      </RoutineBuilderLayout>
    </div>
  );
}
