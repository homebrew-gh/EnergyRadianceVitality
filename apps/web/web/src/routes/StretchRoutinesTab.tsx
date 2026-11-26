import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { CellInput } from "../components/workoutEditor/CellInput";
import { LibraryPanel } from "../components/workoutEditor/LibraryPanel";
import { RowMenu, type RowMenuAction } from "../components/workoutEditor/RowMenu";
import type { StretchCatalogEntry } from "../lib/catalog";
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
import type { WeightExercisePickerFilter } from "../lib/weightExerciseAvailability";

const clampHold = (n: number) =>
  Math.min(STRETCH_HOLD_MAX_SECONDS, Math.max(STRETCH_HOLD_MIN_SECONDS, Math.round(n)));
const clampGap = (n: number) =>
  Math.min(STRETCH_GAP_MAX_SECONDS, Math.max(STRETCH_GAP_MIN_SECONDS, Math.round(n)));

/** A routine as the editor holds it, tidied the way it is published. */
function normalizedRoutine(r: StretchRoutine, catalog: StretchCatalogEntry[]): StretchRoutine {
  const hold = clampHold(r.holdSecondsPerStretch || 30);
  const hasRestorative = r.stretchIds.some((id) => isRestorativeStretch(catalog.find((e) => e.id === id)));
  const gap = r.gapSeconds == null ? null : clampGap(r.gapSeconds);
  return {
    id: r.id,
    name: r.name,
    stretchIds: r.stretchIds,
    holdSecondsPerStretch: hold,
    holdSecondsByStretchId: cleanedHoldOverrides(r.holdSecondsByStretchId ?? {}, r.stretchIds, hold),
    gapSeconds: gap != null && gap !== STRETCH_GAP_DEFAULT_SECONDS ? gap : null,
    settleAndRest: r.settleAndRest === true && hasRestorative,
  };
}

function fingerprint(r: StretchRoutine | null, catalog: StretchCatalogEntry[]): string {
  return r ? JSON.stringify(normalizedRoutine(r, catalog)) : "";
}

function holdSummary(r: StretchRoutine): string {
  const holds = r.stretchIds.map((id) => stretchHoldFor(r, id));
  if (holds.length === 0) return `${r.holdSecondsPerStretch}s`;
  const lo = Math.min(...holds);
  const hi = Math.max(...holds);
  return lo === hi ? `${lo}s` : `${lo}–${hi}s`;
}

/**
 * /app/routines/stretch: the stretch routines as a library like Workouts, and a routine editor
 * like the workout editor (name in the bar, publish state, a table of stretches with each hold,
 * and the library as a slide-over panel).
 */
export function StretchRoutinesTab() {
  const { stretchRoutines, catalogs, loading, saving, error, reload, saveStretchRoutines } = useTraining();
  const catalog = catalogs.stretch;
  const [editing, setEditing] = useState<{ routine: StretchRoutine; isNew: boolean } | null>(null);
  const [query, setQuery] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    const sorted = [...stretchRoutines].sort((a, b) => a.name.localeCompare(b.name));
    if (!q) return sorted;
    return sorted.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        r.stretchIds.some((id) => stretchLabel(id, catalog).toLowerCase().includes(q)),
    );
  }, [stretchRoutines, query, catalog]);

  if (editing) {
    return (
      <StretchRoutineEditor
        key={editing.routine.id}
        initial={editing.routine}
        isNew={editing.isNew}
        published={stretchRoutines.find((r) => r.id === editing.routine.id) ?? null}
        catalog={catalog}
        saving={saving}
        error={error}
        onBack={() => setEditing(null)}
        onPublish={async (routine) => {
          await saveStretchRoutines(upsertRoutine(stretchRoutines, routine));
        }}
      />
    );
  }

  const newRoutine = () => {
    setSuccess(null);
    setEditing({
      isNew: true,
      routine: { id: crypto.randomUUID(), name: "", stretchIds: [], holdSecondsPerStretch: 30, settleAndRest: true },
    });
  };

  const onDelete = async (routine: StretchRoutine) => {
    if (!window.confirm(`Delete "${routine.name}"? This publishes an updated list to the relay.`)) return;
    setFormError(null);
    setSuccess(null);
    try {
      await saveStretchRoutines(stretchRoutines.filter((r) => r.id !== routine.id));
      setSuccess(`Deleted "${routine.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Delete failed.");
    }
  };

  const actionsFor = (routine: StretchRoutine): RowMenuAction[] => [
    { label: "Edit", onSelect: () => setEditing({ routine, isNew: false }) },
    {
      label: "Duplicate",
      onSelect: () =>
        setEditing({ isNew: true, routine: { ...routine, id: crypto.randomUUID(), name: `${routine.name} (copy)` } }),
    },
    { label: "Delete", danger: true, onSelect: () => void onDelete(routine) },
  ];

  return (
    <div className="space-y-4">
      {catalog.length === 0 ? (
        <p className="card p-3 text-sm text-muted">
          No stretch catalog on relay yet. Sync ERV on your phone or{" "}
          <Link to="/app/exercises" className="text-heading underline">
            edit the catalog
          </Link>
          .
        </p>
      ) : null}

      <RoutineFormAlerts error={error} formError={formError} success={success} />

      <section className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-[var(--erv-outline-variant)] px-4 py-3">
          <input
            className="input max-w-xs"
            value={query}
            placeholder="Search stretch routines"
            aria-label="Search stretch routines"
            onChange={(e) => setQuery(e.target.value)}
          />
          <span className="text-xs text-muted">
            {stretchRoutines.length === 1 ? "1 routine" : `${stretchRoutines.length} routines`}
          </span>
          <div className="ml-auto flex items-center gap-2">
            <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={() => void reload(true)} disabled={loading}>
              {loading ? "Loading…" : "Reload"}
            </button>
            <button type="button" className="btn-primary text-sm" onClick={newRoutine}>
              New Stretch Routine
            </button>
          </div>
        </div>
        {loading && stretchRoutines.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted">Loading stretch routines…</p>
        ) : stretchRoutines.length === 0 ? (
          <div className="space-y-3 px-4 py-8 text-center">
            <p className="font-semibold text-heading">No stretch routines yet</p>
            <p className="text-sm text-muted">
              Build one here and play it on your phone under Stretching → Routines, with a guided timer and voice.
            </p>
            <button type="button" className="btn-primary text-sm" onClick={newRoutine}>
              New Stretch Routine
            </button>
          </div>
        ) : shown.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted">No stretch routine matches “{query.trim()}”.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-muted">
                <th className="px-4 py-2 font-medium">Routine</th>
                <th className="w-24 px-2 py-2 font-medium">Time</th>
                <th className="w-28 px-2 py-2 font-medium">Holds</th>
                <th className="w-24 px-2 py-2" aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {shown.map((r) => (
                <tr
                  key={r.id}
                  className="cursor-pointer border-t border-[var(--erv-outline-variant)] align-top hover:bg-[var(--erv-surface-variant)]/40"
                  onClick={(e) => {
                    if ((e.target as HTMLElement).closest("button, a")) return;
                    setEditing({ routine: r, isNew: false });
                  }}
                >
                  <td className="px-4 py-3">
                    <span className="font-semibold text-heading">{r.name.trim() || "Untitled routine"}</span>
                    <span className="block text-xs text-muted">
                      {r.stretchIds.length === 0
                        ? "Empty"
                        : r.stretchIds.map((id) => stretchLabel(id, catalog)).join(" · ")}
                    </span>
                  </td>
                  <td className="px-2 py-3 text-muted">About {stretchRoutineMinutes(r, catalog)} min</td>
                  <td className="px-2 py-3 text-muted">
                    {holdSummary(r)}
                    {stretchGapFor(r) !== STRETCH_GAP_DEFAULT_SECONDS ? (
                      <span className="block text-xs">{stretchGapFor(r)}s gaps</span>
                    ) : null}
                  </td>
                  <td className="whitespace-nowrap px-2 py-3 text-right">
                    <button
                      type="button"
                      className="mr-1 text-xs text-muted hover:text-heading"
                      onClick={() => setEditing({ routine: r, isNew: false })}
                    >
                      Open
                    </button>
                    <RowMenu label={`${r.name.trim() || "Routine"} actions`} actions={actionsFor(r)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

/** One stretch in the routine table: drag the grip to reorder. */
function StretchRow({
  id,
  index,
  count,
  entry,
  name,
  hold,
  defaultHold,
  onHold,
  onMove,
  onRemove,
}: {
  id: string;
  index: number;
  count: number;
  entry: StretchCatalogEntry | undefined;
  name: string;
  hold: number | undefined;
  defaultHold: number;
  onHold: (seconds: number | null) => void;
  onMove: (to: number) => void;
  onRemove: () => void;
}) {
  const { setNodeRef, attributes, listeners, transform, transition, isDragging } = useSortable({ id });
  const restorative = isRestorativeStretch(entry);
  return (
    <tr
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={clsx("border-t border-[var(--erv-outline-variant)]", isDragging ? "relative z-10 bg-[var(--erv-surface)] opacity-80 shadow-lg" : "")}
    >
      <td className="w-8 px-1 py-1 align-middle">
        <button
          type="button"
          {...attributes}
          {...listeners}
          className="cursor-grab touch-none rounded-lg px-1 text-xs text-muted hover:bg-[var(--erv-surface-variant)] hover:text-heading active:cursor-grabbing"
          aria-label={`Move ${name}`}
          title="Drag to reorder, or focus and use Space and the arrow keys"
        >
          ⠿
        </button>
      </td>
      <td className="w-6 py-1 text-xs text-muted">{index + 1}</td>
      <td className="px-2 py-2">
        <span className="font-medium text-heading">{name}</span>
        <span className="block text-xs text-muted">
          {[entry?.category, entry?.requiresBothSides ? "each side" : null, restorative ? "restorative" : null]
            .filter((x, i, a) => x && a.indexOf(x) === i)
            .join(" · ")}
        </span>
      </td>
      <td className="w-28 px-1 py-1">
        <div className="flex items-center gap-1">
          <CellInput
            value={hold != null ? String(hold) : ""}
            placeholder={String(defaultHold)}
            ariaLabel={`Hold seconds for ${name}`}
            inputMode="numeric"
            title="Blank uses the routine's hold"
            onCommit={(text) => {
              const t = text.trim();
              if (t === "") return onHold(null);
              const n = Number(t);
              if (Number.isFinite(n) && n > 0) onHold(clampHold(n));
            }}
          />
          <span className="text-xs text-muted">s</span>
        </div>
      </td>
      <td className="w-12 px-1 py-1 text-right">
        <RowMenu
          label={`${name} actions`}
          actions={[
            { label: "Move Up", onSelect: () => onMove(index - 1), disabled: index === 0 },
            { label: "Move Down", onSelect: () => onMove(index + 1), disabled: index === count - 1 },
            { label: "Use Routine Hold", onSelect: () => onHold(null), disabled: hold == null },
            { label: "Remove", danger: true, onSelect: onRemove },
          ]}
        />
      </td>
    </tr>
  );
}

function StretchRoutineEditor({
  initial,
  isNew,
  published,
  catalog,
  saving,
  error,
  onBack,
  onPublish,
}: {
  initial: StretchRoutine;
  isNew: boolean;
  published: StretchRoutine | null;
  catalog: StretchCatalogEntry[];
  saving: boolean;
  error: string | null;
  onBack: () => void;
  onPublish: (routine: StretchRoutine) => Promise<void>;
}) {
  const [draft, setDraft] = useState<StretchRoutine>(() => ({
    ...initial,
    holdSecondsByStretchId: { ...(initial.holdSecondsByStretchId ?? {}) },
  }));
  const [baseline, setBaseline] = useState(() => (isNew ? "" : fingerprint(published ?? initial, catalog)));
  const [libraryOpen, setLibraryOpen] = useState(isNew);
  const [weightFilter, setWeightFilter] = useState<WeightExercisePickerFilter>("ALL");
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);
  const [holdText, setHoldText] = useState(String(initial.holdSecondsPerStretch || 30));
  const [gapText, setGapText] = useState(initial.gapSeconds != null ? String(initial.gapSeconds) : "");

  const byId = useMemo(() => new Map(catalog.map((e) => [e.id, e])), [catalog]);
  const normalized = normalizedRoutine(draft, catalog);
  const dirty = fingerprint(draft, catalog) !== baseline;
  const hasRestorative = draft.stretchIds.some((id) => isRestorativeStretch(byId.get(id)));
  const isPublished = published != null;
  const state: "draft" | "unpublished_changes" | "published" = !isPublished
    ? "draft"
    : dirty
      ? "unpublished_changes"
      : "published";

  useEffect(() => {
    if (!dirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const update = (patch: Partial<StretchRoutine>) => setDraft((d) => ({ ...d, ...patch }));
  const setHold = (id: string, seconds: number | null) =>
    setDraft((d) => {
      const next = { ...(d.holdSecondsByStretchId ?? {}) };
      if (seconds == null) delete next[id];
      else next[id] = seconds;
      return { ...d, holdSecondsByStretchId: next };
    });
  const move = (from: number, to: number) =>
    setDraft((d) => (to < 0 || to >= d.stretchIds.length ? d : { ...d, stretchIds: arrayMove(d.stretchIds, from, to) }));
  const remove = (id: string) =>
    setDraft((d) => {
      const next = { ...(d.holdSecondsByStretchId ?? {}) };
      delete next[id];
      return { ...d, stretchIds: d.stretchIds.filter((x) => x !== id), holdSecondsByStretchId: next };
    });

  const onPick = (_kind: string, id: string): string | null => {
    if (draft.stretchIds.includes(id)) return "Already in this routine.";
    const suggested = byId.get(id)?.defaultHoldSeconds;
    setDraft((d) => ({
      ...d,
      stretchIds: [...d.stretchIds, id],
      holdSecondsByStretchId:
        typeof suggested === "number" && suggested > 0 && d.holdSecondsByStretchId?.[id] == null
          ? { ...(d.holdSecondsByStretchId ?? {}), [id]: clampHold(suggested) }
          : d.holdSecondsByStretchId,
    }));
    return null;
  };

  const onDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const from = draft.stretchIds.indexOf(String(active.id));
    const to = draft.stretchIds.indexOf(String(over.id));
    if (from >= 0 && to >= 0) move(from, to);
  };

  const publish = async () => {
    setMessage(null);
    const name = draft.name.trim();
    if (!name) {
      setMessage({ tone: "error", text: "Name this routine first." });
      return;
    }
    if (draft.stretchIds.length === 0) {
      setMessage({ tone: "error", text: "Add at least one stretch from the library." });
      return;
    }
    const routine = normalizedRoutine({ ...draft, name }, catalog);
    try {
      await onPublish(routine);
      setDraft(routine);
      setBaseline(fingerprint(routine, catalog));
      setMessage({ tone: "ok", text: "Published. Sync ERV on your phone to see it under Stretching → Routines." });
    } catch (err) {
      setMessage({ tone: "error", text: err instanceof Error ? err.message : "Publish failed." });
    }
  };

  const leave = () => {
    if (dirty && !window.confirm("Leave without publishing? Your changes here will be lost.")) return;
    onBack();
  };

  const minutes = stretchRoutineMinutes(normalized, catalog);
  const stateChip =
    state === "published"
      ? "bg-[var(--erv-primary-container)] text-heading"
      : "border border-[var(--erv-error)] text-[var(--erv-error)]";
  const stateLabel = state === "published" ? "Published" : state === "draft" ? "Draft" : "Unpublished Changes";
  const stateDetail =
    state === "published"
      ? "On your phone after a sync"
      : state === "draft"
        ? "Not on your phone yet"
        : "Your phone has the older version";

  return (
    <div className={clsx("space-y-5", libraryOpen ? "xl:pr-[24rem]" : "")}>
      <div className="sticky top-0 z-20 -mx-4 border-b border-[var(--erv-outline-variant)] bg-[var(--erv-bg)]/95 px-4 py-3 backdrop-blur md:-mx-6 md:px-6">
        <div className="flex flex-wrap items-center gap-3">
          <button type="button" className="text-sm text-muted hover:text-heading" onClick={leave}>
            ← Stretch Routines
          </button>
          <input
            className="min-w-[12rem] flex-1 rounded-lg border border-transparent bg-transparent px-2 py-1 text-xl font-bold text-heading hover:border-[var(--erv-outline-variant)] focus:border-[var(--erv-primary)] focus:outline-none"
            value={draft.name}
            placeholder="Name this routine"
            aria-label="Routine name"
            autoFocus={isNew}
            onChange={(e) => update({ name: e.target.value })}
          />
          <button type="button" className="btn-primary text-sm" onClick={() => void publish()} disabled={saving}>
            {saving ? "Publishing…" : isPublished ? "Publish Changes" : "Publish"}
          </button>
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
          <span className={clsx("rounded-pill px-2 py-0.5 font-semibold", stateChip)}>{stateLabel}</span>
          <span className={state === "published" ? "text-muted" : "text-[var(--erv-error)]"}>{stateDetail}</span>
          <span className="text-muted">·</span>
          <span className="text-muted">
            {draft.stretchIds.length === 1 ? "1 stretch" : `${draft.stretchIds.length} stretches`}
            {draft.stretchIds.length > 0 ? ` · About ${minutes} min · ${holdSummary(normalized)} holds` : ""}
          </span>
          <div className="ml-auto flex flex-wrap items-center gap-2">
            {dirty && isPublished ? (
              <button
                type="button"
                className="btn-ghost px-3 py-1 text-xs"
                onClick={() => {
                  setDraft({ ...published!, holdSecondsByStretchId: { ...(published!.holdSecondsByStretchId ?? {}) } });
                  setHoldText(String(published!.holdSecondsPerStretch || 30));
                  setGapText(published!.gapSeconds != null ? String(published!.gapSeconds) : "");
                }}
              >
                Discard Changes
              </button>
            ) : null}
            <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={() => setLibraryOpen((v) => !v)}>
              Library
            </button>
          </div>
        </div>
        {message ? (
          <p className={clsx("mt-2 text-sm", message.tone === "error" ? "text-[var(--erv-error)]" : "text-muted")} role="status">
            {message.text}
          </p>
        ) : null}
        {error ? <p className="mt-2 text-sm text-[var(--erv-error)]">{error}</p> : null}
      </div>

      <section className="card space-y-3 p-4">
        <SectionHeader>Timing</SectionHeader>
        <div className="grid gap-3 sm:grid-cols-3">
          <div>
            <label className="label" htmlFor="stretch-hold">
              <FieldLabel>Hold seconds per stretch</FieldLabel>
            </label>
            <input
              id="stretch-hold"
              type="number"
              min={STRETCH_HOLD_MIN_SECONDS}
              max={STRETCH_HOLD_MAX_SECONDS}
              className="input"
              value={holdText}
              onChange={(e) => {
                setHoldText(e.target.value);
                const n = Number(e.target.value);
                if (Number.isFinite(n) && n > 0) update({ holdSecondsPerStretch: clampHold(n) });
              }}
            />
            <p className="mt-1 text-xs text-muted">Each side, for two-sided stretches. A stretch can have its own below.</p>
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
              onChange={(e) => {
                setGapText(e.target.value);
                const t = e.target.value.trim();
                const n = Number(t);
                update({ gapSeconds: t === "" || !Number.isFinite(n) ? null : clampGap(n) });
              }}
            />
            <p className="mt-1 text-xs text-muted">Switching sides stays {STRETCH_SIDE_SWITCH_SECONDS} s.</p>
          </div>
          <div>
            {hasRestorative ? (
              <label className="flex items-start gap-2 text-sm" htmlFor="stretch-settle">
                <input
                  id="stretch-settle"
                  type="checkbox"
                  className="mt-1"
                  checked={draft.settleAndRest === true}
                  onChange={(e) => update({ settleAndRest: e.target.checked })}
                />
                <span>
                  <FieldLabel>Settle in and rest</FieldLabel>
                  <span className="block text-xs text-muted">
                    A minute of slow breathing before the first pose, and a minute of rest with a soft bell at the end.
                  </span>
                </span>
              </label>
            ) : (
              <p className="text-xs text-muted">
                Add a restorative pose to offer Settle In and Rest: a breathing minute at the start and a closing rest.
              </p>
            )}
          </div>
        </div>
      </section>

      {draft.stretchIds.length === 0 ? (
        <div className="card space-y-3 p-6">
          <p className="font-semibold text-heading">Start with a stretch</p>
          <p className="text-sm text-muted">
            Search the library and press Enter to add a stretch. Restorative poses come with a longer hold.
          </p>
          <button type="button" className="btn-primary text-sm" onClick={() => setLibraryOpen(true)}>
            Open the Library
          </button>
        </div>
      ) : (
        <section className="card overflow-hidden">
          <div className="flex items-center gap-2 border-b border-[var(--erv-outline-variant)] px-4 py-2">
            <SectionHeader>Stretches in order</SectionHeader>
            <button type="button" className="btn-ghost ml-auto px-3 py-1 text-xs" onClick={() => setLibraryOpen(true)}>
              + Add Stretch
            </button>
          </div>
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
            <SortableContext items={draft.stretchIds} strategy={verticalListSortingStrategy}>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-muted">
                    <th className="w-8" aria-label="Reorder" />
                    <th className="w-6" aria-label="Position" />
                    <th className="px-2 py-2 font-medium">Stretch</th>
                    <th className="w-28 px-2 py-2 font-medium">Hold</th>
                    <th className="w-12" aria-label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {draft.stretchIds.map((id, index) => (
                    <StretchRow
                      key={id}
                      id={id}
                      index={index}
                      count={draft.stretchIds.length}
                      entry={byId.get(id)}
                      name={stretchLabel(id, catalog)}
                      hold={draft.holdSecondsByStretchId?.[id]}
                      defaultHold={normalized.holdSecondsPerStretch}
                      onHold={(s) => setHold(id, s)}
                      onMove={(to) => move(index, to)}
                      onRemove={() => remove(id)}
                    />
                  ))}
                </tbody>
              </table>
            </SortableContext>
          </DndContext>
        </section>
      )}

      <LibraryPanel
        open={libraryOpen}
        onClose={() => setLibraryOpen(false)}
        targetLabel={`Adding to ${draft.name.trim() || "this routine"}`}
        kinds={["stretch"]}
        weightCatalog={[]}
        stretchCatalog={catalog}
        cardioCatalog={[]}
        ownedEquipment={[]}
        enabledWeightExercisePackIds={[]}
        weightFilter={weightFilter}
        onWeightFilterChange={setWeightFilter}
        alreadyInSegment={new Set(draft.stretchIds)}
        onPick={onPick}
        focusSearchOnOpen={!isNew}
      />
    </div>
  );
}
