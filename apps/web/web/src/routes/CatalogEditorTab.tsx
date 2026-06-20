import { FieldLabel } from "../components/FieldLabel";
import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
  formatCategoryLabel,
  groupCardioActivities,
  groupStretchEntries,
  groupWeightExercises,
  isBuiltInStretchCatalogId,
  isBuiltInWeightCatalogId,
  newCustomStretchId,
  newCustomWeightExerciseId,
  WEIGHT_EQUIPMENT_OPTIONS,
  type CardioCatalogActivity,
  type StretchCatalogEntry,
  type WeightCatalogExercise,
} from "../lib/catalog";
import { useCatalogEditor } from "../lib/catalogEditorData";

type EditorTab = "weight" | "stretch" | "cardio";

type WeightDraft = WeightCatalogExercise;
type StretchDraft = StretchCatalogEntry;

export function CatalogEditorTab() {
  const editor = useCatalogEditor();
  const [tab, setTab] = useState<EditorTab>("weight");
  const [query, setQuery] = useState("");

  const weightGroups = useMemo(() => {
    const filtered = editor.weightExercises.filter((exercise) =>
      matchesQuery(query, exercise.name, exercise.muscleGroup, exercise.equipment),
    );
    return groupWeightExercises(filtered);
  }, [editor.weightExercises, query]);

  const stretchGroups = useMemo(() => {
    const filtered = editor.stretchEntries.filter((entry) =>
      matchesQuery(query, entry.name, entry.category, entry.procedure),
    );
    return groupStretchEntries(filtered);
  }, [editor.stretchEntries, query]);

  const cardioGroups = useMemo(() => {
    const filtered = editor.cardioActivities.filter((activity) =>
      matchesQuery(query, activity.displayName, activity.id, activity.section),
    );
    return groupCardioActivities(filtered);
  }, [editor.cardioActivities, query]);

  const [weightModal, setWeightModal] = useState<WeightDraft | null>(null);
  const [stretchModal, setStretchModal] = useState<StretchDraft | null>(null);
  const [cardioModal, setCardioModal] = useState<CardioCatalogActivity | null>(null);

  const onPublish = async () => {
    editor.clearSuccess();
    if (tab === "weight") await editor.publishWeightCatalog();
    else if (tab === "stretch") await editor.publishStretchCatalog();
    else await editor.publishCardioCatalog();
  };

  const dirty =
    tab === "weight"
      ? editor.weightDirty
      : tab === "stretch"
        ? editor.stretchDirty
        : editor.cardioDirty;

  const version =
    tab === "weight"
      ? editor.weightVersion
      : tab === "stretch"
        ? editor.stretchVersion
        : editor.cardioVersion;

  const count =
    tab === "weight"
      ? editor.weightExercises.length
      : tab === "stretch"
        ? editor.stretchEntries.length
        : editor.cardioActivities.length;

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <h2 className="text-2xl font-bold text-heading">Catalog editor</h2>
        <p className="text-sm text-muted">
          Browse and edit the synced exercise compendium by category. Changes publish to{" "}
          <code className="text-xs">erv/catalog/*</code> on your relay and sync to ERV on
          your phone.
        </p>
      </header>

      {editor.error ? (
        <p className="text-sm text-error card p-3" role="alert">
          {editor.error}
        </p>
      ) : null}
      {editor.success ? (
        <p
          className="text-sm card p-3 border-l-4 border-[var(--erv-success)]"
          role="status"
        >
          {editor.success}
          {editor.lastEventId ? (
            <span className="block text-xs text-muted mt-1 font-mono">
              event {editor.lastEventId.slice(0, 16)}…
            </span>
          ) : null}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <TabButton active={tab === "weight"} onClick={() => setTab("weight")}>
          Weight ({editor.weightExercises.length})
        </TabButton>
        <TabButton active={tab === "stretch"} onClick={() => setTab("stretch")}>
          Stretch ({editor.stretchEntries.length})
        </TabButton>
        <TabButton active={tab === "cardio"} onClick={() => setTab("cardio")}>
          Cardio ({editor.cardioActivities.length})
        </TabButton>
        <button
          type="button"
          className="btn-ghost text-sm ml-auto"
          onClick={() => void editor.reload()}
          disabled={editor.loading}
        >
          Refresh
        </button>
      </div>

      <div className="card p-4 space-y-3">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[200px]">
            <label className="label" htmlFor="catalog-search">
              <FieldLabel>Search</FieldLabel>
            </label>
            <input
              id="catalog-search"
              className="input"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Filter by name, category, equipment…"
            />
          </div>
          <div className="text-sm text-muted">
            {count} items
            {version != null ? ` · relay v${version}` : " · not on relay yet"}
            {dirty ? " · unsaved edits" : ""}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {tab === "weight" ? (
            <button
              type="button"
              className="btn-ghost text-sm"
              onClick={() =>
                setWeightModal({
                  id: newCustomWeightExerciseId(),
                  name: "",
                  muscleGroup: "chest",
                  pushOrPull: "push",
                  equipment: "dumbbell",
                })
              }
            >
              Add exercise
            </button>
          ) : null}
          {tab === "stretch" ? (
            <button
              type="button"
              className="btn-ghost text-sm"
              onClick={() =>
                setStretchModal({
                  id: newCustomStretchId(),
                  name: "",
                  category: "other",
                  requiresBothSides: false,
                  targetBodyParts: [],
                  procedure: "",
                })
              }
            >
              Add stretch
            </button>
          ) : null}
          <button
            type="button"
            className="btn-primary text-sm"
            disabled={editor.loading || editor.saving || !dirty}
            onClick={() => void onPublish()}
          >
            {editor.saving ? "Publishing…" : `Publish ${tab} catalog`}
          </button>
        </div>
      </div>

      {editor.loading ? (
        <p className="text-muted text-sm">Loading catalogs…</p>
      ) : tab === "weight" ? (
        weightGroups.length === 0 ? (
          <EmptyCatalogHint kind="weight" />
        ) : (
          weightGroups.map((group) => (
            <CategorySection
              key={group.key}
              title={group.label}
              count={group.items.length}
              onAdd={() =>
                setWeightModal({
                  id: newCustomWeightExerciseId(),
                  name: "",
                  muscleGroup: group.key,
                  pushOrPull: "push",
                  equipment: "dumbbell",
                })
              }
            >
              {group.items.map((exercise) => (
                <CatalogRow
                  key={exercise.id}
                  title={exercise.name}
                  subtitle={`${formatCategoryLabel(exercise.equipment)} · ${exercise.pushOrPull}`}
                  meta={isBuiltInWeightCatalogId(exercise.id) ? "built-in" : "custom"}
                  onEdit={() => setWeightModal({ ...exercise })}
                  onDelete={() => {
                    if (!confirm(`Remove “${exercise.name}” from the draft catalog?`)) return;
                    editor.setWeightExercises(
                      editor.weightExercises.filter((item) => item.id !== exercise.id),
                    );
                  }}
                />
              ))}
            </CategorySection>
          ))
        )
      ) : tab === "stretch" ? (
        stretchGroups.length === 0 ? (
          <EmptyCatalogHint kind="stretch" />
        ) : (
          stretchGroups.map((group) => (
            <CategorySection
              key={group.key}
              title={group.label}
              count={group.items.length}
              onAdd={() =>
                setStretchModal({
                  id: newCustomStretchId(),
                  name: "",
                  category: group.key,
                  requiresBothSides: false,
                  targetBodyParts: [],
                  procedure: "",
                })
              }
            >
              {group.items.map((entry) => (
                <CatalogRow
                  key={entry.id}
                  title={entry.name}
                  subtitle={
                    entry.requiresBothSides
                      ? "Both sides · " + entry.targetBodyParts.join(", ")
                      : entry.targetBodyParts.join(", ") || "—"
                  }
                  meta={isBuiltInStretchCatalogId(entry.id) ? "built-in" : "custom"}
                  onEdit={() => setStretchModal({ ...entry })}
                  onDelete={() => {
                    if (!confirm(`Remove “${entry.name}” from the draft catalog?`)) return;
                    editor.setStretchEntries(
                      editor.stretchEntries.filter((item) => item.id !== entry.id),
                    );
                  }}
                />
              ))}
            </CategorySection>
          ))
        )
      ) : cardioGroups.length === 0 ? (
        <EmptyCatalogHint kind="cardio" />
      ) : (
        cardioGroups.map((group) => (
          <CategorySection key={group.key} title={group.label} count={group.items.length}>
            {group.items.map((activity) => (
              <CatalogRow
                key={activity.id}
                title={activity.displayName}
                subtitle={activity.id}
                meta="enum"
                onEdit={() => setCardioModal({ ...activity })}
              />
            ))}
          </CategorySection>
        ))
      )}

      {weightModal ? (
        <WeightExerciseModal
          draft={weightModal}
          onClose={() => setWeightModal(null)}
          onSave={(next) => {
            const exists = editor.weightExercises.some((item) => item.id === next.id);
            editor.setWeightExercises(
              exists
                ? editor.weightExercises.map((item) => (item.id === next.id ? next : item))
                : [...editor.weightExercises, next],
            );
            setWeightModal(null);
          }}
        />
      ) : null}

      {stretchModal ? (
        <StretchEntryModal
          draft={stretchModal}
          onClose={() => setStretchModal(null)}
          onSave={(next) => {
            const exists = editor.stretchEntries.some((item) => item.id === next.id);
            editor.setStretchEntries(
              exists
                ? editor.stretchEntries.map((item) => (item.id === next.id ? next : item))
                : [...editor.stretchEntries, next],
            );
            setStretchModal(null);
          }}
        />
      ) : null}

      {cardioModal ? (
        <CardioActivityModal
          draft={cardioModal}
          onClose={() => setCardioModal(null)}
          onSave={(next) => {
            editor.setCardioActivities(
              editor.cardioActivities.map((item) =>
                item.id === next.id ? next : item,
              ),
            );
            setCardioModal(null);
          }}
        />
      ) : null}
    </div>
  );
}

function matchesQuery(query: string, ...fields: string[]): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return fields.some((field) => field.toLowerCase().includes(q));
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      className={active ? "btn-primary text-sm" : "btn-ghost text-sm"}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function CategorySection({
  title,
  count,
  onAdd,
  children,
}: {
  title: string;
  count: number;
  onAdd?: () => void;
  children: ReactNode;
}) {
  return (
    <details className="card group" open>
      <summary className="cursor-pointer list-none px-4 py-3 flex items-center justify-between gap-3">
        <div>
          <h3 className="font-semibold text-heading inline">{title}</h3>
          <span className="text-sm text-muted ml-2">{count}</span>
        </div>
        <div className="flex items-center gap-2">
          {onAdd ? (
            <button
              type="button"
              className="btn-ghost text-xs py-1 px-2"
              onClick={(e) => {
                e.preventDefault();
                onAdd();
              }}
            >
              Add
            </button>
          ) : null}
          <span className="text-muted text-xs group-open:rotate-180 transition-transform">
            ▾
          </span>
        </div>
      </summary>
      <div className="border-t border-outline/30 divide-y divide-outline/20">
        {children}
      </div>
    </details>
  );
}

function CatalogRow({
  title,
  subtitle,
  meta,
  onEdit,
  onDelete,
}: {
  title: string;
  subtitle: string;
  meta: string;
  onEdit: () => void;
  onDelete?: () => void;
}) {
  return (
    <div className="px-4 py-3 flex items-start gap-3">
      <div className="flex-1 min-w-0">
        <p className="font-medium text-heading truncate">{title}</p>
        <p className="text-sm text-muted truncate">{subtitle}</p>
      </div>
      <span className="text-[10px] uppercase tracking-wide text-muted border border-outline/40 rounded-pill px-2 py-0.5">
        {meta}
      </span>
      <div className="flex gap-1 shrink-0">
        <button type="button" className="btn-ghost text-xs py-1 px-2" onClick={onEdit}>
          Edit
        </button>
        {onDelete ? (
          <button type="button" className="btn-ghost text-xs py-1 px-2" onClick={onDelete}>
            Delete
          </button>
        ) : null}
      </div>
    </div>
  );
}

function EmptyCatalogHint({ kind }: { kind: EditorTab }) {
  return (
    <div className="card p-4 text-sm text-muted space-y-2">
      <p>
        {kind === "weight"
          ? "No weight exercises match your filter, or the relay catalog has not synced yet."
          : kind === "stretch"
            ? "No stretches on the relay yet. Sync ERV on your phone to bootstrap the stretch catalog, or add stretches here and publish."
            : "No cardio activities on the relay yet. Sync ERV on your phone to bootstrap the cardio catalog."}
      </p>
    </div>
  );
}

function ModalShell({
  title,
  onClose,
  onSubmit,
  children,
}: {
  title: string;
  onClose: () => void;
  onSubmit: (e: FormEvent) => void;
  children: ReactNode;
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40"
      role="dialog"
      aria-modal="true"
    >
      <div className="card w-full max-w-lg max-h-[90vh] overflow-y-auto p-4 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold text-heading">{title}</h3>
          <button type="button" className="btn-ghost text-sm py-1 px-2" onClick={onClose}>
            Close
          </button>
        </div>
        <form className="space-y-4" onSubmit={onSubmit}>
          {children}
          <div className="flex gap-2 justify-end">
            <button type="button" className="btn-ghost text-sm" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn-primary text-sm">
              Save to draft
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function WeightExerciseModal({
  draft,
  onClose,
  onSave,
}: {
  draft: WeightDraft;
  onClose: () => void;
  onSave: (next: WeightDraft) => void;
}) {
  const [form, setForm] = useState(draft);
  const isNew = !draft.name;

  return (
    <ModalShell
      title={isNew ? "Add weight exercise" : "Edit weight exercise"}
      onClose={onClose}
      onSubmit={(e) => {
        e.preventDefault();
        if (!form.name.trim()) return;
        onSave({ ...form, name: form.name.trim(), muscleGroup: form.muscleGroup.trim().toLowerCase() });
      }}
    >
      <Field label="Name">
        <input
          className="input"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />
      </Field>
      <Field label="Muscle group">
        <input
          className="input"
          value={form.muscleGroup}
          onChange={(e) => setForm({ ...form, muscleGroup: e.target.value })}
          placeholder="chest, back, legs…"
          required
        />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Push / pull">
          <select
            className="input"
            value={form.pushOrPull}
            onChange={(e) =>
              setForm({ ...form, pushOrPull: e.target.value as "push" | "pull" })
            }
          >
            <option value="push">Push</option>
            <option value="pull">Pull</option>
          </select>
        </Field>
        <Field label="Equipment">
          <select
            className="input"
            value={form.equipment}
            onChange={(e) => setForm({ ...form, equipment: e.target.value })}
          >
            {WEIGHT_EQUIPMENT_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {formatCategoryLabel(option)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="flex flex-wrap gap-4 text-sm">
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={form.hiitCapable ?? false}
            onChange={(e) => setForm({ ...form, hiitCapable: e.target.checked })}
          />
          HIIT capable
        </label>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={form.timePerSetCapable ?? false}
            onChange={(e) =>
              setForm({
                ...form,
                timePerSetCapable: e.target.checked,
                repPerSetCapable: e.target.checked ? form.repPerSetCapable ?? true : true,
              })
            }
          />
          Timed sets
        </label>
        {form.timePerSetCapable ? (
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={form.repPerSetCapable !== false}
              onChange={(e) => setForm({ ...form, repPerSetCapable: e.target.checked })}
            />
            Also allow rep-based sets
          </label>
        ) : null}
      </div>
      <p className="text-xs text-muted font-mono break-all">id: {form.id}</p>
    </ModalShell>
  );
}

function StretchEntryModal({
  draft,
  onClose,
  onSave,
}: {
  draft: StretchDraft;
  onClose: () => void;
  onSave: (next: StretchDraft) => void;
}) {
  const [form, setForm] = useState(draft);

  return (
    <ModalShell
      title={draft.name ? "Edit stretch" : "Add stretch"}
      onClose={onClose}
      onSubmit={(e) => {
        e.preventDefault();
        if (!form.name.trim()) return;
        onSave({
          ...form,
          name: form.name.trim(),
          category: form.category.trim().toLowerCase() || "other",
          targetBodyParts: form.targetBodyParts.filter(Boolean),
        });
      }}
    >
      <Field label="Name">
        <input
          className="input"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />
      </Field>
      <Field label="Category">
        <input
          className="input"
          value={form.category}
          onChange={(e) => setForm({ ...form, category: e.target.value })}
          placeholder="neck, legs, glutes…"
          required
        />
      </Field>
      <Field label="Target body parts (comma-separated)">
        <input
          className="input"
          value={form.targetBodyParts.join(", ")}
          onChange={(e) =>
            setForm({
              ...form,
              targetBodyParts: e.target.value
                .split(",")
                .map((part) => part.trim())
                .filter(Boolean),
            })
          }
        />
      </Field>
      <Field label="Procedure / cues">
        <textarea
          className="input min-h-[96px]"
          value={form.procedure}
          onChange={(e) => setForm({ ...form, procedure: e.target.value })}
        />
      </Field>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={form.requiresBothSides}
          onChange={(e) => setForm({ ...form, requiresBothSides: e.target.checked })}
        />
        Requires both sides
      </label>
      <p className="text-xs text-muted font-mono break-all">id: {form.id}</p>
    </ModalShell>
  );
}

function CardioActivityModal({
  draft,
  onClose,
  onSave,
}: {
  draft: CardioCatalogActivity;
  onClose: () => void;
  onSave: (next: CardioCatalogActivity) => void;
}) {
  const [form, setForm] = useState(draft);

  return (
    <ModalShell
      title="Edit cardio activity"
      onClose={onClose}
      onSubmit={(e) => {
        e.preventDefault();
        if (!form.displayName.trim()) return;
        onSave({ ...form, displayName: form.displayName.trim() });
      }}
    >
      <Field label="Display name">
        <input
          className="input"
          value={form.displayName}
          onChange={(e) => setForm({ ...form, displayName: e.target.value })}
          required
        />
      </Field>
      <p className="text-sm text-muted">
        Activity id <code className="text-xs">{form.id}</code> is fixed (matches the Android
        enum). Custom cardio types belong in the cardio routines master, not this catalog.
      </p>
      <div className="flex flex-wrap gap-4 text-sm">
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={form.offersHiitIntervalTemplate}
            onChange={(e) =>
              setForm({ ...form, offersHiitIntervalTemplate: e.target.checked })
            }
          />
          HIIT interval template
        </label>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={form.supportsTreadmillModality}
            onChange={(e) =>
              setForm({ ...form, supportsTreadmillModality: e.target.checked })
            }
          />
          Treadmill modality
        </label>
      </div>
    </ModalShell>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <FieldLabel className="label">{label}</FieldLabel>
      {children}
    </div>
  );
}
