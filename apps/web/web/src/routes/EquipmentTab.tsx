import { useState } from "react";
import { SectionHeader } from "../components/FieldLabel";
import {
  EquipmentEditorModal,
  editorStateForItem,
  editorStateForQuickAdd,
  type EquipmentEditorState,
} from "../components/equipment/EquipmentEditors";
import { useUnsavedChangesWarning } from "../hooks/useUnsavedChangesWarning";
import {
  displayEquipmentTitle,
  equipmentSummaryLine,
  QUICK_ADD_CATEGORIES,
  workoutModalityLabel,
  type BodyWeightUnit,
  type EquipmentCatalogKind,
  type OwnedEquipmentItem,
} from "../lib/fitnessEquipment";
import { useEquipment } from "../lib/equipmentData";
import { useTraining } from "../lib/trainingData";
import {
  WEIGHT_EXERCISE_PACKS,
  weightExercisePackExerciseCount,
} from "../lib/weightExercisePacks";

export function EquipmentTab() {
  const {
    gymMembership,
    equipment,
    enabledWeightExercisePackIds,
    loading,
    publishing,
    dirty,
    error,
    lastEventId,
    reload,
    setGymMembership,
    toggleExercisePack,
    upsertEquipmentItem,
    removeEquipmentItem,
    publish,
    discardChanges,
    clearError,
  } = useEquipment();
  const { exercises } = useTraining();

  const [weightUnit, setWeightUnit] = useState<BodyWeightUnit>("LB");
  const [editor, setEditor] = useState<EquipmentEditorState | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useUnsavedChangesWarning(dirty);

  const onPublish = async () => {
    clearError();
    setSuccess(null);
    try {
      await publish();
      setSuccess("Equipment profile pushed to your relay.");
    } catch {
      /* error surfaced via provider */
    }
  };

  const openQuickAdd = (kind: EquipmentCatalogKind) => {
    setEditor(editorStateForQuickAdd(kind));
  };

  const openEdit = (item: OwnedEquipmentItem) => {
    setEditor(editorStateForItem(item));
  };

  if (loading) {
    return <p className="text-sm text-muted">Loading equipment profile…</p>;
  }

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-2">
            <h2 className="text-2xl font-bold text-heading">Equipment & Gym</h2>
            <p className="text-sm text-muted max-w-2xl">
              Tell ERV what you have access to at home or in your gym. Edit your inventory here,
              then push one update to your relay so your phone stays in sync.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted">Weight display</span>
            <button
              type="button"
              className={weightUnit === "LB" ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"}
              onClick={() => setWeightUnit("LB")}
            >
              lb
            </button>
            <button
              type="button"
              className={weightUnit === "KG" ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"}
              onClick={() => setWeightUnit("KG")}
            >
              kg
            </button>
          </div>
        </div>
      </header>

      {dirty ? (
        <div
          className="card p-3 border-[var(--erv-secondary)]/40 bg-[var(--erv-primary-container)]/30 space-y-2"
          role="status"
        >
          <p className="text-sm font-medium text-heading">Unsaved changes</p>
          <p className="text-sm text-muted">
            Your edits are only on this page until you push them to your relay. Leave without
            pushing and your progress will be lost.
          </p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="btn-primary text-sm"
              onClick={() => void onPublish()}
              disabled={publishing}
            >
              {publishing ? "Pushing…" : "Push update to relay"}
            </button>
            <button
              type="button"
              className="btn-ghost text-sm"
              onClick={discardChanges}
              disabled={publishing}
            >
              Discard changes
            </button>
          </div>
        </div>
      ) : null}

      {error ? (
        <div className="text-sm text-error card p-3 space-y-2" role="alert">
          <p>{error}</p>
          {dirty ? (
            <button
              type="button"
              className="btn-ghost text-sm border-[var(--erv-error)]/40 text-[var(--erv-error)]"
              onClick={() => void onPublish()}
              disabled={publishing}
            >
              {publishing ? "Retrying…" : "Retry push"}
            </button>
          ) : null}
        </div>
      ) : null}

      {success ? (
        <div className="text-sm text-[var(--erv-success)] card p-3" role="status">
          {success}
          {lastEventId ? (
            <span className="block text-xs text-muted mt-1 font-mono">Event {lastEventId.slice(0, 16)}…</span>
          ) : null}
        </div>
      ) : null}

      <section className="card p-4 space-y-3">
        <h3 className="text-lg font-semibold text-heading">Gym membership</h3>
        <label className="flex items-start justify-between gap-4">
          <span className="text-sm">
            <span className="block font-medium text-heading">Gym membership</span>
            <span className="text-muted">
              I have access to a full gym (machines, racks, cable, etc.)
            </span>
          </span>
          <input
            type="checkbox"
            className="mt-1 h-5 w-5"
            checked={gymMembership}
            onChange={(e) => setGymMembership(e.target.checked)}
          />
        </label>
      </section>

      <section className="card p-4 space-y-3">
        <h3 className="text-lg font-semibold text-heading">Exercise packs</h3>
        <p className="text-sm text-muted">
          Turn on niche exercise libraries only when you have the specialty equipment. Disabled
          packs stay out of the main exercise list on your phone.
        </p>
        <ul className="space-y-3">
          {WEIGHT_EXERCISE_PACKS.map((pack) => {
            const enabled = enabledWeightExercisePackIds.includes(pack.id);
            const count = weightExercisePackExerciseCount(pack.id, exercises);
            return (
              <li key={pack.id} className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-medium text-heading">{pack.title}</p>
                  <p className="text-sm text-muted mt-1">
                    {pack.description} {count} exercises.
                  </p>
                </div>
                <input
                  type="checkbox"
                  className="mt-1 h-5 w-5 shrink-0"
                  checked={enabled}
                  onChange={(e) => toggleExercisePack(pack.id, e.target.checked)}
                  aria-label={`Enable ${pack.title} exercise pack`}
                />
              </li>
            );
          })}
        </ul>
      </section>

      <section className="card p-4 space-y-3">
        <h3 className="text-lg font-semibold text-heading">Quick add</h3>
        <p className="text-sm text-muted">
          Pick a category — a form opens with common options. Weights follow your display unit above.
        </p>
        <div className="flex flex-wrap gap-2">
          {QUICK_ADD_CATEGORIES.map(({ kind, label }) => {
            const hasItem = equipment.some((item) => item.catalogKind === kind);
            return (
              <button
                key={kind}
                type="button"
                className="btn-ghost text-sm"
                onClick={() => openQuickAdd(kind)}
              >
                {hasItem ? "✓ " : ""}
                {label}
              </button>
            );
          })}
        </div>
      </section>

      <section className="card p-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-lg font-semibold text-heading">Your inventory</h3>
          {!dirty ? (
            <button type="button" className="btn-ghost text-sm" onClick={() => void reload()}>
              Reload from relay
            </button>
          ) : null}
        </div>
        <p className="text-sm text-muted">
          Add anything else (bench, bike, cable attachments, etc.) — or edit items from quick add.
        </p>
        {equipment.length === 0 ? (
          <p className="text-sm text-muted">No equipment added yet.</p>
        ) : (
          <ul className="space-y-2">
            {equipment.map((item) => (
              <li key={item.id} className="card p-3 flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-heading">{displayEquipmentTitle(item, weightUnit)}</p>
                  {equipmentSummaryLine(item, weightUnit) ? (
                    <p className="text-sm text-muted mt-1">{equipmentSummaryLine(item, weightUnit)}</p>
                  ) : null}
                  {(item.modalities ?? []).length === 0 ? (
                    <p className="text-sm text-muted mt-1">
                      No workout tags — edit to choose cardio, strength, etc.
                    </p>
                  ) : (
                    <div className="flex flex-wrap gap-1.5 mt-2">
                      {(item.modalities ?? []).map((m) => (
                        <span
                          key={m}
                          className="text-xs px-2 py-0.5 rounded-lg bg-[var(--erv-primary-container)] text-[var(--erv-on-primary-container)]"
                        >
                          {workoutModalityLabel(m)}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex gap-2 shrink-0">
                  <button
                    type="button"
                    className="btn-ghost text-sm"
                    onClick={() => openEdit(item)}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className="btn-ghost text-sm text-[var(--erv-error)]"
                    onClick={() => removeEquipmentItem(item.id)}
                  >
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
        <button
          type="button"
          className="btn-primary text-sm"
          onClick={() =>
            setEditor({
              kind: "manual",
              existingId: null,
              nameDraft: "",
              modalities: ["WEIGHT_TRAINING"],
            })
          }
        >
          Add other equipment
        </button>
      </section>

      <section className="card p-4 space-y-3">
        <SectionHeader>Sync to relay</SectionHeader>
        <p className="text-sm text-muted">
          Push your gym membership, exercise packs, and inventory as one encrypted update
          (`erv/equipment`). Your Android app will merge this on the next sync.
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="btn-primary text-sm"
            onClick={() => void onPublish()}
            disabled={publishing || !dirty}
          >
            {publishing ? "Pushing…" : dirty ? "Push update to relay" : "No changes to push"}
          </button>
        </div>
      </section>

      {editor ? (
        <EquipmentEditorModal
          editor={editor}
          weightUnit={weightUnit}
          onClose={() => setEditor(null)}
          onSave={(item) => {
            upsertEquipmentItem(item);
            setEditor(null);
          }}
        />
      ) : null}
    </div>
  );
}
