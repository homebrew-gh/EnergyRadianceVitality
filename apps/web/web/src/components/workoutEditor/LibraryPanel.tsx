import { useEffect, useMemo, useRef, useState } from "react";
import clsx from "clsx";
import type { CardioCatalogActivity, StretchCatalogEntry, WeightCatalogExercise } from "../../lib/catalog";
import type { OwnedEquipmentItem } from "../../lib/fitnessEquipment";
import {
  filterWeightExercisesForPicker,
  type WeightExercisePickerFilter,
} from "../../lib/weightExerciseAvailability";
import type { LibraryItemKind } from "../LibrarySidebar";
import { NavIcon } from "../NavIcon";

type Row = { kind: LibraryItemKind; id: string; name: string; detail: string };

const KIND_LABELS: Record<LibraryItemKind, string> = {
  weight: "Lifts",
  stretch: "Stretches",
  cardio: "Cardio",
};

/** Most rows shown at once; searching narrows the list. */
const MAX_ROWS = 80;

/**
 * The exercise library as a panel that slides over the editor. Search stays as you left it,
 * Enter adds the highlighted result (the top one unless you arrow down), and the panel stays
 * open so you can add several in a row.
 */
export function LibraryPanel({
  open,
  onClose,
  targetLabel,
  kinds,
  weightCatalog,
  stretchCatalog,
  cardioCatalog,
  ownedEquipment,
  enabledWeightExercisePackIds,
  weightFilter,
  onWeightFilterChange,
  alreadyInSegment,
  onPick,
  focusSearchOnOpen = true,
}: {
  open: boolean;
  /** False when something else should keep focus as the panel opens (a new workout's name). */
  focusSearchOnOpen?: boolean;
  onClose: () => void;
  /** What a pick does, e.g. "Adding to Main Lifts" or "Swapping Bench Press". */
  targetLabel: string;
  kinds: LibraryItemKind[];
  weightCatalog: WeightCatalogExercise[];
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
  ownedEquipment: OwnedEquipmentItem[];
  enabledWeightExercisePackIds: string[];
  weightFilter: WeightExercisePickerFilter;
  onWeightFilterChange: (filter: WeightExercisePickerFilter) => void;
  alreadyInSegment: ReadonlySet<string>;
  /** Returns a note to show when the pick was not added. */
  onPick: (kind: LibraryItemKind, id: string) => string | null;
}) {
  const [query, setQuery] = useState("");
  const [kindFilter, setKindFilter] = useState<LibraryItemKind | "all">("all");
  const [highlight, setHighlight] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const [added, setAdded] = useState<string | null>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    if (open && focusSearchOnOpen) window.setTimeout(() => searchRef.current?.focus(), 0);
    // Only when the panel opens, not when the caller changes its mind while it is open.
  }, [open]);

  useEffect(() => {
    if (kindFilter !== "all" && !kinds.includes(kindFilter)) setKindFilter("all");
  }, [kinds, kindFilter]);

  const rows = useMemo((): Row[] => {
    const q = query.trim().toLowerCase();
    const matches = (...fields: string[]) => !q || fields.some((f) => f.toLowerCase().includes(q));
    const shown = (kind: LibraryItemKind) =>
      kinds.includes(kind) && (kindFilter === "all" || kindFilter === kind);
    const out: Row[] = [];
    if (shown("weight")) {
      const visible = filterWeightExercisesForPicker(
        weightCatalog,
        weightFilter,
        ownedEquipment,
        enabledWeightExercisePackIds,
      );
      for (const ex of visible) {
        if (matches(ex.name, ex.muscleGroup, ex.equipment)) {
          out.push({ kind: "weight", id: ex.id, name: ex.name, detail: [ex.muscleGroup, ex.equipment].filter(Boolean).join(" · ") });
        }
      }
    }
    if (shown("stretch")) {
      for (const st of stretchCatalog) {
        if (matches(st.name, st.category)) out.push({ kind: "stretch", id: st.id, name: st.name, detail: st.category });
      }
    }
    if (shown("cardio")) {
      for (const ac of cardioCatalog) {
        if (matches(ac.displayName, ac.section)) out.push({ kind: "cardio", id: ac.id, name: ac.displayName, detail: ac.section });
      }
    }
    // Names that start with the search come first.
    if (q) {
      out.sort((a, b) => Number(!a.name.toLowerCase().startsWith(q)) - Number(!b.name.toLowerCase().startsWith(q)));
    }
    return out.slice(0, MAX_ROWS);
  }, [query, kinds, kindFilter, weightCatalog, weightFilter, ownedEquipment, enabledWeightExercisePackIds, stretchCatalog, cardioCatalog]);

  useEffect(() => setHighlight(0), [query, kindFilter, weightFilter]);

  useEffect(() => {
    listRef.current?.querySelector(`[data-row="${highlight}"]`)?.scrollIntoView({ block: "nearest" });
  }, [highlight]);

  const pick = (row: Row) => {
    const problem = onPick(row.kind, row.id);
    setNotice(problem);
    setAdded(problem ? null : row.name);
    if (!problem) {
      setQuery("");
      searchRef.current?.focus();
    }
  };

  if (!open) return null;

  return (
    <aside
      className="fixed inset-y-0 right-0 z-30 flex w-full max-w-sm flex-col border-l border-[var(--erv-outline-variant)] bg-[var(--erv-surface)] shadow-2xl"
      aria-label="Exercise library"
    >
      <div className="flex items-start justify-between gap-2 border-b border-[var(--erv-outline-variant)] px-4 py-3">
        <div className="min-w-0">
          <h2 className="font-semibold text-heading">Library</h2>
          <p className="truncate text-xs text-muted">
            {targetLabel}
          </p>
        </div>
        <button
          type="button"
          className="rounded-lg p-1.5 text-muted hover:bg-[var(--erv-surface-variant)] hover:text-heading"
          onClick={onClose}
          aria-label="Close library"
          title="Close (Esc)"
        >
          <NavIcon name="close" />
        </button>
      </div>

      <div className="space-y-2 border-b border-[var(--erv-outline-variant)] px-4 py-3">
        <input
          ref={searchRef}
          className="input"
          value={query}
          placeholder="Search, then Enter to add"
          aria-label="Search the library"
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setHighlight((h) => Math.min(h + 1, rows.length - 1));
            } else if (e.key === "ArrowUp") {
              e.preventDefault();
              setHighlight((h) => Math.max(h - 1, 0));
            } else if (e.key === "Enter") {
              e.preventDefault();
              // The highlight can point past a list that just got shorter.
              const row = rows[Math.min(highlight, rows.length - 1)];
              if (row) pick(row);
            } else if (e.key === "Escape") {
              e.preventDefault();
              onClose();
            }
          }}
        />
        <div className="flex flex-wrap gap-1">
          {kinds.length > 1
            ? (["all", ...kinds] as const).map((k) => (
                <button
                  key={k}
                  type="button"
                  className={clsx("rounded-pill px-3 py-1 text-xs", kindFilter === k ? "bg-[var(--erv-primary-container)] font-semibold" : "text-muted hover:bg-[var(--erv-surface-variant)]")}
                  onClick={() => setKindFilter(k)}
                >
                  {k === "all" ? "All" : KIND_LABELS[k]}
                </button>
              ))
            : null}
          {kinds.includes("weight") ? (
            <button
              type="button"
              className={clsx("ml-auto rounded-pill px-3 py-1 text-xs", weightFilter === "HOME_READY" ? "bg-[var(--erv-primary-container)] font-semibold" : "text-muted hover:bg-[var(--erv-surface-variant)]")}
              onClick={() => onWeightFilterChange(weightFilter === "HOME_READY" ? "ALL" : "HOME_READY")}
              title="Only lifts your equipment can do"
            >
              My Equipment
            </button>
          ) : null}
        </div>
        {notice ? <p className="text-xs text-[var(--erv-error)]" role="status">{notice}</p> : null}
        {!notice && added ? <p className="text-xs text-muted" role="status">Added {added}.</p> : null}
      </div>

      <ul ref={listRef} className="flex-1 overflow-y-auto py-1" role="listbox" aria-label="Results">
        {rows.length === 0 ? (
          <li className="px-4 py-6 text-center text-sm text-muted">Nothing matches. Add exercises under Exercises.</li>
        ) : (
          rows.map((row, index) => {
            const inSegment = alreadyInSegment.has(row.id);
            return (
              <li key={`${row.kind}:${row.id}`} data-row={index} role="option" aria-selected={index === highlight}>
                <button
                  type="button"
                  className={clsx(
                    "flex w-full items-center justify-between gap-2 px-4 py-2 text-left",
                    index === highlight ? "bg-[var(--erv-primary-container)]/60" : "hover:bg-[var(--erv-surface-variant)]",
                  )}
                  // Only a real mouse move takes the highlight, not the list redrawing under a resting pointer.
                  onMouseMove={() => {
                    if (highlight !== index) setHighlight(index);
                  }}
                  onClick={() => pick(row)}
                >
                  <span className="min-w-0">
                    <span className="block truncate text-sm text-heading">{row.name}</span>
                    <span className="block truncate text-xs text-muted">{row.detail}</span>
                  </span>
                  {inSegment ? <span className="shrink-0 text-xs text-muted">In segment</span> : null}
                </button>
              </li>
            );
          })
        )}
      </ul>
    </aside>
  );
}
