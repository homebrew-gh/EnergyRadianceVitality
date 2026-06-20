import { FieldLabel } from "../components/FieldLabel";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ReorderableList } from "../components/ReorderableList";
import { RoutineBuilderLayout } from "../components/RoutineBuilderLayout";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { SavedRoutinesPanel } from "../components/SavedRoutinesPanel";
import { upsertRoutine } from "../lib/cardioTraining";
import { stretchLabel, type StretchRoutine } from "../lib/stretchTraining";
import { useTraining } from "../lib/trainingData";

export function StretchRoutinesTab() {
  const {
    stretchRoutines,
    catalogs,
    loading,
    saving,
    error,
    lastEventId,
    reload,
    saveStretchRoutines,
  } = useTraining();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [draftIds, setDraftIds] = useState<string[]>([]);
  const [name, setName] = useState("");
  const [holdSeconds, setHoldSeconds] = useState(30);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const selectedSet = useMemo(() => new Set(draftIds), [draftIds]);
  const catalog = catalogs.stretch;

  const resetForm = () => {
    setEditingId(null);
    setName("");
    setDraftIds([]);
    setHoldSeconds(30);
    setFormError(null);
  };

  const addStretch = (id: string) => {
    setDraftIds((prev) => (prev.includes(id) ? prev : [...prev, id]));
  };

  const removeStretch = (id: string) => {
    setDraftIds((prev) => prev.filter((x) => x !== id));
  };

  const startEdit = (routine: StretchRoutine) => {
    setEditingId(routine.id);
    setName(routine.name);
    setDraftIds([...routine.stretchIds]);
    setHoldSeconds(routine.holdSecondsPerStretch);
    setFormError(null);
    setSuccess(null);
  };

  const onDelete = async (routine: StretchRoutine) => {
    if (!window.confirm(`Delete "${routine.name}"? This publishes an updated list to the relay.`)) {
      return;
    }
    setFormError(null);
    setSuccess(null);
    try {
      await saveStretchRoutines(stretchRoutines.filter((r) => r.id !== routine.id));
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
    if (draftIds.length === 0) {
      setFormError("Add at least one stretch from the library.");
      return;
    }
    const routine: StretchRoutine = {
      id: editingId ?? crypto.randomUUID(),
      name: trimmed,
      stretchIds: draftIds,
      holdSecondsPerStretch: Math.max(5, holdSeconds),
    };
    try {
      await saveStretchRoutines(upsertRoutine(stretchRoutines, routine));
      setSuccess(
        editingId
          ? "Stretch routine updated. Sync ERV to pick up changes."
          : "Stretch routine published. Sync ERV to see it under Stretching → Routines.",
      );
      resetForm();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Save failed.");
    }
  };

  return (
    <div className="space-y-4">
      {catalog.length === 0 ? (
        <p className="text-sm text-muted card p-3">
          No stretch catalog on relay yet. Sync ERV on your phone or{" "}
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
        sidebarKinds={["stretch"]}
        selectionKind="stretch"
        selectedIds={selectedSet}
        weightCatalog={catalogs.weight}
        stretchCatalog={catalog}
        cardioCatalog={catalogs.cardio}
        onPick={(item) => {
          if (item.kind === "stretch") addStretch(item.id);
        }}
      >
        <form className="card p-4 space-y-4" onSubmit={onSubmit}>
          <h3 className="font-semibold text-heading">
            {editingId ? "Edit stretch routine" : "New stretch routine"}
          </h3>
          <p className="text-sm text-muted">
            Order stretches for the guided player. Drag to reorder. Publishes to{" "}
            <code className="text-xs">erv/stretching/routines</code>.
          </p>
          <div>
            <label className="label" htmlFor="stretch-routine-name">
              <FieldLabel>Name</FieldLabel>
            </label>
            <input
              id="stretch-routine-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Pre-workout mobility"
              required
            />
          </div>
          <div>
            <label className="label" htmlFor="stretch-hold">
              <FieldLabel>Hold seconds per stretch</FieldLabel>
            </label>
            <input
              id="stretch-hold"
              type="number"
              min={5}
              max={600}
              className="input"
              value={holdSeconds}
              onChange={(e) => setHoldSeconds(Number(e.target.value))}
            />
          </div>
          <div>
            <p className="label">
              <FieldLabel>{`Routine order (${draftIds.length})`}</FieldLabel>
            </p>
            {draftIds.length === 0 ? (
              <p className="text-sm text-muted">Add stretches from the sidebar.</p>
            ) : (
              <ReorderableList
                items={draftIds}
                getKey={(id) => id}
                onReorder={setDraftIds}
                renderItem={(id, index) => (
                  <>
                    <span className="text-muted w-5">{index + 1}.</span>
                    <span className="flex-1">{stretchLabel(id, catalog)}</span>
                    <button
                      type="button"
                      className="btn-ghost text-xs py-0.5 px-2"
                      onClick={() => removeStretch(id)}
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
          routines={stretchRoutines}
          editingId={editingId}
          onReload={() => void reload()}
          getId={(r) => r.id}
          getName={(r) => r.name}
          getDetail={(r) =>
            `${r.holdSecondsPerStretch}s per stretch · ${r.stretchIds
              .map((id) => stretchLabel(id, catalog))
              .join(" · ")}`
          }
          onEdit={startEdit}
          onDelete={(r) => void onDelete(r)}
        />
      </RoutineBuilderLayout>
    </div>
  );
}
