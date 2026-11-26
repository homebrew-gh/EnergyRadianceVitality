import { FieldLabel } from "../components/FieldLabel";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ReorderableList } from "../components/ReorderableList";
import { RoutineBuilderLayout } from "../components/RoutineBuilderLayout";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { SavedRoutinesPanel } from "../components/SavedRoutinesPanel";
import { upsertRoutine } from "../lib/cardioTraining";
import {
  cleanedHoldOverrides,
  isRestorativeStretch,
  STRETCH_GAP_DEFAULT_SECONDS,
  STRETCH_GAP_MAX_SECONDS,
  STRETCH_GAP_MIN_SECONDS,
  STRETCH_HOLD_MAX_SECONDS,
  STRETCH_HOLD_MIN_SECONDS,
  STRETCH_SIDE_SWITCH_SECONDS,
  stretchGapFor,
  stretchHoldFor,
  stretchLabel,
  stretchRoutineMinutes,
  type StretchRoutine,
} from "../lib/stretchTraining";
import { useTraining } from "../lib/trainingData";

export function StretchRoutinesTab() {
  const {
    stretchRoutines,
    catalogs,
    exercises,
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
  /** Per-stretch hold times as typed; a missing or blank entry uses the routine's hold. */
  const [holdOverrides, setHoldOverrides] = useState<Record<string, string>>({});
  const [gapText, setGapText] = useState("");
  const [settleAndRest, setSettleAndRest] = useState(true);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const selectedSet = useMemo(() => new Set(draftIds), [draftIds]);
  const catalog = catalogs.stretch;

  const resetForm = () => {
    setEditingId(null);
    setName("");
    setDraftIds([]);
    setHoldSeconds(30);
    setHoldOverrides({});
    setGapText("");
    setSettleAndRest(true);
    setFormError(null);
  };

  const hasRestorative = draftIds.some((id) => isRestorativeStretch(catalog.find((e) => e.id === id)));
  const clampHold = (n: number) =>
    Math.min(STRETCH_HOLD_MAX_SECONDS, Math.max(STRETCH_HOLD_MIN_SECONDS, Math.round(n)));
  const defaultHold = clampHold(Number.isFinite(holdSeconds) && holdSeconds > 0 ? holdSeconds : 30);
  const parsedOverrides = useMemo(() => {
    const out: Record<string, number> = {};
    for (const [id, text] of Object.entries(holdOverrides)) {
      const n = Number(text);
      if (text.trim() !== "" && Number.isFinite(n) && n > 0) out[id] = n;
    }
    return out;
  }, [holdOverrides]);
  const parsedGap = (() => {
    const n = Number(gapText);
    if (gapText.trim() === "" || !Number.isFinite(n)) return null;
    return Math.min(STRETCH_GAP_MAX_SECONDS, Math.max(STRETCH_GAP_MIN_SECONDS, Math.round(n)));
  })();

  const draftRoutine: StretchRoutine = {
    id: editingId ?? "draft",
    name,
    stretchIds: draftIds,
    holdSecondsPerStretch: defaultHold,
    holdSecondsByStretchId: cleanedHoldOverrides(parsedOverrides, draftIds, defaultHold),
    gapSeconds: parsedGap != null && parsedGap !== STRETCH_GAP_DEFAULT_SECONDS ? parsedGap : null,
    settleAndRest: settleAndRest && hasRestorative,
  };

  const addStretch = (id: string) => {
    if (draftIds.includes(id)) return;
    setDraftIds((prev) => (prev.includes(id) ? prev : [...prev, id]));
    // Restorative poses come with a longer suggested hold.
    const suggested = catalog.find((e) => e.id === id)?.defaultHoldSeconds;
    if (typeof suggested === "number" && suggested > 0) {
      setHoldOverrides((prev) => (prev[id] != null ? prev : { ...prev, [id]: String(suggested) }));
    }
  };

  const removeStretch = (id: string) => {
    setDraftIds((prev) => prev.filter((x) => x !== id));
    setHoldOverrides((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
  };

  const startEdit = (routine: StretchRoutine) => {
    setEditingId(routine.id);
    setName(routine.name);
    setDraftIds([...routine.stretchIds]);
    setHoldSeconds(routine.holdSecondsPerStretch);
    setHoldOverrides(
      Object.fromEntries(
        Object.entries(routine.holdSecondsByStretchId ?? {}).map(([id, sec]) => [id, String(sec)]),
      ),
    );
    setGapText(routine.gapSeconds != null ? String(routine.gapSeconds) : "");
    setSettleAndRest(routine.settleAndRest === true);
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
      ...draftRoutine,
      id: editingId ?? crypto.randomUUID(),
      name: trimmed,
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
          <Link to="/app/exercises" className="underline text-heading">
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
        weightCatalog={exercises}
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
            Order stretches for the guided player. Drag to reorder, then publish.
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
            <label className="label" htmlFor="stretch-gap">
              <FieldLabel>Gap between stretches (seconds)</FieldLabel>
            </label>
            <input
              id="stretch-gap"
              type="number"
              min={STRETCH_GAP_MIN_SECONDS}
              max={STRETCH_GAP_MAX_SECONDS}
              className="input"
              value={gapText}
              placeholder={String(STRETCH_GAP_DEFAULT_SECONDS)}
              onChange={(e) => setGapText(e.target.value)}
            />
            <p className="text-xs text-muted mt-1">
              Time to move into the next stretch. Switching sides stays {STRETCH_SIDE_SWITCH_SECONDS} s.
            </p>
          </div>
          <div>
            <p className="label">
              <FieldLabel>{`Routine order (${draftIds.length})`}</FieldLabel>
            </p>
            {draftIds.length > 0 ? (
              <p className="text-xs text-muted mb-2">
                {`About ${stretchRoutineMinutes(draftRoutine, catalog)} min. Leave a stretch's hold blank to use ${defaultHold} s.`}
              </p>
            ) : null}
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
                    <span className="flex-1">
                      {stretchLabel(id, catalog)}
                      {catalog.find((e) => e.id === id)?.requiresBothSides ? (
                        <span className="text-xs text-muted"> · each side</span>
                      ) : null}
                    </span>
                    <input
                      type="number"
                      min={STRETCH_HOLD_MIN_SECONDS}
                      max={STRETCH_HOLD_MAX_SECONDS}
                      className="input w-20 py-0.5 text-sm"
                      aria-label={`Hold seconds for ${stretchLabel(id, catalog)}`}
                      placeholder={String(defaultHold)}
                      value={holdOverrides[id] ?? ""}
                      onChange={(e) => {
                        const v = e.target.value;
                        setHoldOverrides((prev) => {
                          const next = { ...prev };
                          if (v.trim() === "") delete next[id];
                          else next[id] = v;
                          return next;
                        });
                      }}
                    />
                    <span className="text-xs text-muted">s</span>
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
          {hasRestorative ? (
            <label className="flex items-start gap-2 text-sm" htmlFor="stretch-settle">
              <input
                id="stretch-settle"
                type="checkbox"
                className="mt-1"
                checked={settleAndRest}
                onChange={(e) => setSettleAndRest(e.target.checked)}
              />
              <span>
                <FieldLabel>Settle in and rest</FieldLabel>
                <span className="block text-xs text-muted">
                  A minute of slow breathing before the first pose, and a minute of rest with a soft
                  bell at the end.
                </span>
              </span>
            </label>
          ) : null}
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
          onReload={() => void reload(true)}
          getId={(r) => r.id}
          getName={(r) => r.name}
          getDetail={(r) => {
            const holds = r.stretchIds.map((id) => stretchHoldFor(r, id));
            const lo = Math.min(...holds);
            const hi = Math.max(...holds);
            const holdLabel = holds.length === 0 ? `${r.holdSecondsPerStretch}s` : lo === hi ? `${lo}s` : `${lo}–${hi}s`;
            const gap = stretchGapFor(r);
            const gapLabel = gap !== STRETCH_GAP_DEFAULT_SECONDS ? ` · ${gap}s gaps` : "";
            return `About ${stretchRoutineMinutes(r, catalog)} min · ${holdLabel} holds${gapLabel} · ${r.stretchIds
              .map((id) => stretchLabel(id, catalog))
              .join(" · ")}`;
          }}
          onEdit={startEdit}
          onDelete={(r) => void onDelete(r)}
        />
      </RoutineBuilderLayout>
    </div>
  );
}
