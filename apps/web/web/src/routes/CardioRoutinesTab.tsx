import { FieldLabel } from "../components/FieldLabel";
import { useState } from "react";
import { Link } from "react-router-dom";
import { ReorderableList } from "../components/ReorderableList";
import { RoutineBuilderLayout } from "../components/RoutineBuilderLayout";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { SavedRoutinesPanel } from "../components/SavedRoutinesPanel";
import {
  buildCardioRoutineFromDrafts,
  cardioRoutineSummary,
  newCardioStepDraft,
  routineToStepDrafts,
  upsertRoutine,
  type CardioModality,
  type CardioRoutine,
  type CardioStepDraft,
} from "../lib/cardioTraining";
import { useTraining } from "../lib/trainingData";

export function CardioRoutinesTab() {
  const {
    cardioRoutines,
    catalogs,
    exercises,
    loading,
    saving,
    error,
    lastEventId,
    reload,
    saveCardioRoutines,
  } = useTraining();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [notes, setNotes] = useState("");
  const [steps, setSteps] = useState<CardioStepDraft[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const catalog = catalogs.cardio;

  const resetForm = () => {
    setEditingId(null);
    setName("");
    setNotes("");
    setSteps([]);
    setFormError(null);
  };

  const addLeg = (activityId: string) => {
    setSteps((prev) => [...prev, newCardioStepDraft(activityId)]);
  };

  const updateStep = (index: number, patch: Partial<CardioStepDraft>) => {
    setSteps((prev) =>
      prev.map((step, i) => (i === index ? { ...step, ...patch } : step)),
    );
  };

  const removeStep = (index: number) => {
    setSteps((prev) => prev.filter((_, i) => i !== index));
  };

  const startEdit = (routine: CardioRoutine) => {
    setEditingId(routine.id);
    setName(routine.name);
    setNotes(routine.notes);
    setSteps(routineToStepDrafts(routine));
    setFormError(null);
    setSuccess(null);
  };

  const onDelete = async (routine: CardioRoutine) => {
    if (!window.confirm(`Delete "${routine.name}"? This publishes an updated list to the relay.`)) {
      return;
    }
    setFormError(null);
    setSuccess(null);
    try {
      await saveCardioRoutines(cardioRoutines.filter((r) => r.id !== routine.id));
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
    if (steps.length === 0) {
      setFormError("Add at least one activity leg from the library.");
      return;
    }

    const existing = editingId
      ? cardioRoutines.find((r) => r.id === editingId)
      : undefined;

    try {
      const routine = buildCardioRoutineFromDrafts({
        editingId,
        name: trimmed,
        notes: notes.trim(),
        steps,
        catalog,
        existing,
      });
      await saveCardioRoutines(upsertRoutine(cardioRoutines, routine));
      setSuccess(
        editingId
          ? "Cardio routine updated. Sync ERV to pick up changes."
          : "Cardio routine published. Sync ERV to see it under Cardio → Routines.",
      );
      resetForm();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Save failed.");
    }
  };

  const activityLabel = (activityId: string) =>
    catalog.find((a) => a.id === activityId)?.displayName ?? activityId;

  return (
    <div className="space-y-4">
      {catalog.length === 0 ? (
        <p className="text-sm text-muted card p-3">
          No cardio catalog on relay yet. Sync ERV on your phone or{" "}
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
        sidebarKinds={["cardio"]}
        selectionKind="cardio"
        pickLabel="Add leg"
        weightCatalog={exercises}
        stretchCatalog={catalogs.stretch}
        cardioCatalog={catalog}
        onPick={(item) => {
          if (item.kind === "cardio") addLeg(item.id);
        }}
      >
        <form className="card p-4 space-y-4" onSubmit={onSubmit}>
          <h3 className="font-semibold text-heading">
            {editingId ? "Edit cardio routine" : "New cardio routine"}
          </h3>
          <p className="text-sm text-muted">
            Build single- or multi-leg cardio templates. Drag legs to reorder.
            HIIT interval blocks are Phase 2. Publishes to{" "}
            <code className="text-xs">erv/cardio/routines</code>.
          </p>
          <div>
            <label className="label" htmlFor="cardio-routine-name">
              <FieldLabel>Name</FieldLabel>
            </label>
            <input
              id="cardio-routine-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Brick: bike → run"
              required
            />
          </div>
          <div>
            <p className="label">
              <FieldLabel>{`Activity legs (${steps.length})`}</FieldLabel>
            </p>
            {steps.length === 0 ? (
              <p className="text-sm text-muted">
                Add activities from the library sidebar. Each click adds a leg.
              </p>
            ) : (
              <ReorderableList
                items={steps}
                getKey={(step) => step.id}
                onReorder={setSteps}
                renderItem={(step, index) => (
                  <div className="flex-1 grid grid-cols-1 sm:grid-cols-[1fr_auto_auto_auto] gap-2 items-center min-w-0">
                    <div className="min-w-0">
                      <span className="text-muted mr-2">{index + 1}.</span>
                      <span className="font-medium">{activityLabel(step.activityId)}</span>
                    </div>
                    <select
                      className="input text-sm py-1"
                      value={step.modality}
                      onChange={(e) =>
                        updateStep(index, {
                          modality: e.target.value as CardioModality,
                        })
                      }
                      aria-label={`Modality for leg ${index + 1}`}
                    >
                      <option value="OUTDOOR">Outdoor</option>
                      <option value="INDOOR_TREADMILL">Indoor</option>
                    </select>
                    <input
                      type="number"
                      min={1}
                      className="input text-sm py-1 w-24"
                      placeholder="min"
                      value={step.targetDurationMinutes}
                      onChange={(e) =>
                        updateStep(index, {
                          targetDurationMinutes:
                            e.target.value === "" ? "" : Number(e.target.value),
                        })
                      }
                      aria-label={`Target minutes for leg ${index + 1}`}
                    />
                    <button
                      type="button"
                      className="btn-ghost text-xs py-0.5 px-2 justify-self-end"
                      onClick={() => removeStep(index)}
                    >
                      Remove
                    </button>
                  </div>
                )}
              />
            )}
          </div>
          <div>
            <label className="label" htmlFor="cardio-notes">
              <FieldLabel>Notes (optional)</FieldLabel>
            </label>
            <textarea
              id="cardio-notes"
              className="input min-h-[72px]"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
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
          routines={cardioRoutines}
          editingId={editingId}
          onReload={() => void reload(true)}
          getId={(r) => r.id}
          getName={(r) => r.name}
          getDetail={(r) => {
            const summary = cardioRoutineSummary(r);
            const duration =
              r.steps?.length && r.steps.length > 1
                ? r.steps
                    .sort((a, b) => a.orderIndex - b.orderIndex)
                    .map((s) =>
                      s.targetDurationMinutes
                        ? `${s.targetDurationMinutes}m`
                        : null,
                    )
                    .filter(Boolean)
                    .join(" → ")
                : r.targetDurationMinutes
                  ? `${r.targetDurationMinutes} min`
                  : null;
            return duration ? `${summary} · ${duration}` : summary;
          }}
          onEdit={startEdit}
          onDelete={(r) => void onDelete(r)}
        />
      </RoutineBuilderLayout>
    </div>
  );
}
