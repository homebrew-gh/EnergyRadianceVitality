import type { ReactNode } from "react";
import { useMemo, useState } from "react";
import {
  formatCategoryLabel,
  groupCardioActivities,
  groupStretchEntries,
  groupWeightExercises,
  type CardioCatalogActivity,
  type StretchCatalogEntry,
  type WeightCatalogExercise,
} from "../lib/catalog";

export type LibraryItemKind = "weight" | "stretch" | "cardio";

export type LibraryPick = {
  kind: LibraryItemKind;
  id: string;
  name: string;
  group: string;
};

type LibrarySidebarProps = {
  weightCatalog: WeightCatalogExercise[];
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
  /** Which silos appear in the sidebar. */
  kinds?: LibraryItemKind[];
  selectedIds?: ReadonlySet<string>;
  /** When set, only this silo's ids are considered for selection highlighting. */
  selectionKind?: LibraryItemKind;
  onPick: (item: LibraryPick) => void;
  pickLabel?: string;
  className?: string;
};

type FilterTab = "all" | LibraryItemKind;

export function LibrarySidebar({
  weightCatalog,
  stretchCatalog,
  cardioCatalog,
  kinds = ["weight", "stretch", "cardio"],
  selectedIds,
  selectionKind,
  onPick,
  pickLabel = "Add",
  className = "",
}: LibrarySidebarProps) {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<FilterTab>(
    kinds.length === 1 ? kinds[0]! : "all",
  );

  const visibleKinds = useMemo(() => new Set(kinds), [kinds]);

  const weightGroups = useMemo(() => {
    if (!visibleKinds.has("weight")) return [];
    const q = query.trim().toLowerCase();
    const filtered = q
      ? weightCatalog.filter(
          (ex) =>
            ex.name.toLowerCase().includes(q) ||
            ex.muscleGroup.toLowerCase().includes(q) ||
            ex.equipment.toLowerCase().includes(q),
        )
      : weightCatalog;
    return groupWeightExercises(filtered);
  }, [query, visibleKinds, weightCatalog]);

  const stretchGroups = useMemo(() => {
    if (!visibleKinds.has("stretch")) return [];
    const q = query.trim().toLowerCase();
    const filtered = q
      ? stretchCatalog.filter(
          (e) =>
            e.name.toLowerCase().includes(q) ||
            e.category.toLowerCase().includes(q) ||
            e.procedure.toLowerCase().includes(q),
        )
      : stretchCatalog;
    return groupStretchEntries(filtered);
  }, [query, stretchCatalog, visibleKinds]);

  const cardioGroups = useMemo(() => {
    if (!visibleKinds.has("cardio")) return [];
    const q = query.trim().toLowerCase();
    const filtered = q
      ? cardioCatalog.filter(
          (a) =>
            a.displayName.toLowerCase().includes(q) ||
            a.id.toLowerCase().includes(q) ||
            a.section.toLowerCase().includes(q),
        )
      : cardioCatalog;
    return groupCardioActivities(filtered);
  }, [cardioCatalog, query, visibleKinds]);

  const showWeight = filter === "all" || filter === "weight";
  const showStretch = filter === "all" || filter === "stretch";
  const showCardio = filter === "all" || filter === "cardio";

  const isSelected = (kind: LibraryItemKind, id: string) =>
    selectionKind != null && selectionKind !== kind
      ? false
      : (selectedIds?.has(id) ?? false);

  return (
    <aside
      className={`card flex flex-col min-h-0 ${className}`}
      aria-label="Exercise library"
    >
      <div className="p-3 border-b border-outline/30 space-y-2 shrink-0">
        <p className="text-sm font-semibold text-heading">Library</p>
        <input
          className="input text-sm"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search catalog…"
          aria-label="Search library"
        />
        {kinds.length > 1 ? (
          <div className="flex flex-wrap gap-1">
            {(["all", ...kinds] as FilterTab[]).map((tab) => (
              <button
                key={tab}
                type="button"
                className={
                  filter === tab ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"
                }
                onClick={() => setFilter(tab)}
              >
                {tab === "all" ? "All" : formatCategoryLabel(tab)}
              </button>
            ))}
          </div>
        ) : null}
      </div>

      <div className="flex-1 overflow-y-auto p-2 space-y-3 min-h-0 max-h-[min(70vh,520px)]">
        {showWeight && visibleKinds.has("weight")
          ? weightGroups.map((group) => (
              <SidebarGroup key={`w-${group.key}`} title={group.label}>
                {group.items.map((ex) => (
                  <SidebarRow
                    key={ex.id}
                    name={ex.name}
                    meta={ex.equipment}
                    selected={isSelected("weight", ex.id)}
                    onPick={() =>
                      onPick({
                        kind: "weight",
                        id: ex.id,
                        name: ex.name,
                        group: group.label,
                      })
                    }
                    pickLabel={pickLabel}
                  />
                ))}
              </SidebarGroup>
            ))
          : null}

        {showStretch && visibleKinds.has("stretch")
          ? stretchGroups.map((group) => (
              <SidebarGroup key={`s-${group.key}`} title={group.label}>
                {group.items.map((entry) => (
                  <SidebarRow
                    key={entry.id}
                    name={entry.name}
                    meta={entry.requiresBothSides ? "both sides" : undefined}
                    selected={isSelected("stretch", entry.id)}
                    onPick={() =>
                      onPick({
                        kind: "stretch",
                        id: entry.id,
                        name: entry.name,
                        group: group.label,
                      })
                    }
                    pickLabel={pickLabel}
                  />
                ))}
              </SidebarGroup>
            ))
          : null}

        {showCardio && visibleKinds.has("cardio")
          ? cardioGroups.map((group) => (
              <SidebarGroup key={`c-${group.key}`} title={group.label}>
                {group.items.map((activity) => (
                  <SidebarRow
                    key={activity.id}
                    name={activity.displayName}
                    meta={activity.id}
                    selected={isSelected("cardio", activity.id)}
                    onPick={() =>
                      onPick({
                        kind: "cardio",
                        id: activity.id,
                        name: activity.displayName,
                        group: group.label,
                      })
                    }
                    pickLabel={pickLabel}
                  />
                ))}
              </SidebarGroup>
            ))
          : null}

        {weightGroups.length === 0 &&
        stretchGroups.length === 0 &&
        cardioGroups.length === 0 ? (
          <p className="text-sm text-muted p-2">No matches. Sync catalogs from your phone or add entries in Catalog.</p>
        ) : null}
      </div>
    </aside>
  );
}

function SidebarGroup({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-wide text-muted px-1 mb-1">
        {title}
      </p>
      <div className="space-y-1">{children}</div>
    </div>
  );
}

function SidebarRow({
  name,
  meta,
  selected,
  onPick,
  pickLabel,
}: {
  name: string;
  meta?: string;
  selected: boolean;
  onPick: () => void;
  pickLabel: string;
}) {
  return (
    <div
      className={`flex items-center gap-2 rounded-card border px-2 py-1.5 text-sm ${
        selected
          ? "border-[var(--erv-primary)] bg-[var(--erv-primary-container)]"
          : "border-outline/30 bg-[var(--erv-input-bg)]"
      }`}
    >
      <span className="flex-1 min-w-0 truncate">{name}</span>
      {meta ? <span className="text-[10px] text-muted shrink-0">{meta}</span> : null}
      <button type="button" className="btn-ghost text-xs py-0.5 px-2 shrink-0" onClick={onPick}>
        {selected ? "✓" : pickLabel}
      </button>
    </div>
  );
}
