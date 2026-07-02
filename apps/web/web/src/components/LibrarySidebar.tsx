import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import {
  formatCategoryLabel,
  groupCardioActivities,
  groupStretchEntries,
  groupWeightExercises,
  type CardioCatalogActivity,
  type StretchCatalogEntry,
  type WeightCatalogExercise,
} from "../lib/catalog";
import type { OwnedEquipmentItem } from "../lib/fitnessEquipment";
import {
  filterWeightExercisesForPicker,
  type WeightExercisePickerFilter,
} from "../lib/weightExerciseAvailability";
import { SectionHeader } from "./FieldLabel";

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
  /** When true, catalog rows are greyed out and pick buttons are disabled. */
  pickDisabled?: boolean;
  /** Equipment-aware weight exercise filter (W4). */
  enableWeightEquipmentFilter?: boolean;
  gymMembership?: boolean;
  ownedEquipment?: OwnedEquipmentItem[];
  enabledWeightExercisePackIds?: string[];
  weightPickerFilter?: WeightExercisePickerFilter;
  onWeightPickerFilterChange?: (filter: WeightExercisePickerFilter) => void;
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
  pickDisabled = false,
  enableWeightEquipmentFilter = false,
  gymMembership = false,
  ownedEquipment = [],
  enabledWeightExercisePackIds = [],
  weightPickerFilter: weightPickerFilterProp,
  onWeightPickerFilterChange,
  className = "",
}: LibrarySidebarProps) {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<FilterTab>(
    kinds.length === 1 ? kinds[0]! : "all",
  );
  const [internalWeightFilter, setInternalWeightFilter] =
    useState<WeightExercisePickerFilter>("HOME_READY");
  const weightPickerFilter = weightPickerFilterProp ?? internalWeightFilter;
  const setWeightPickerFilter = onWeightPickerFilterChange ?? setInternalWeightFilter;

  // When kinds narrows to one silo (mobility→stretch, cardio/interval→cardio, lifting→weight), sync filter.
  useEffect(() => {
    if (kinds.length === 1) {
      setFilter(kinds[0]!);
    } else if (filter !== "all" && !kinds.includes(filter)) {
      setFilter("all");
    }
  }, [kinds, filter]);

  const visibleKinds = useMemo(() => new Set(kinds), [kinds]);

  const filteredWeightCatalog = useMemo(() => {
    if (!enableWeightEquipmentFilter) return weightCatalog;
    return filterWeightExercisesForPicker(
      weightCatalog,
      weightPickerFilter,
      ownedEquipment,
      enabledWeightExercisePackIds,
    );
  }, [
    enableWeightEquipmentFilter,
    weightCatalog,
    weightPickerFilter,
    ownedEquipment,
    enabledWeightExercisePackIds,
  ]);

  const weightGroups = useMemo(() => {
    if (!visibleKinds.has("weight")) return [];
    const q = query.trim().toLowerCase();
    const filtered = q
      ? filteredWeightCatalog.filter(
          (ex) =>
            ex.name.toLowerCase().includes(q) ||
            ex.muscleGroup.toLowerCase().includes(q) ||
            ex.equipment.toLowerCase().includes(q),
        )
      : filteredWeightCatalog;
    return groupWeightExercises(filtered);
  }, [query, visibleKinds, filteredWeightCatalog]);

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

  const activeFilter: FilterTab = kinds.length === 1 ? kinds[0]! : filter;

  const showWeight = activeFilter === "all" || activeFilter === "weight";
  const showStretch = activeFilter === "all" || activeFilter === "stretch";
  const showCardio = activeFilter === "all" || activeFilter === "cardio";

  const isSelected = (kind: LibraryItemKind, id: string) =>
    selectionKind != null && selectionKind !== kind
      ? false
      : (selectedIds?.has(id) ?? false);

  const handlePick = (item: LibraryPick) => {
    onPick(item);
    setQuery("");
  };

  return (
    <aside
      className={`card flex flex-col min-h-0 overflow-hidden ${className}`}
      aria-label="Exercise library"
    >
      <div className="space-y-3 border-b border-[var(--erv-outline-variant)] bg-[var(--erv-surface-variant)]/35 p-4 shrink-0">
        <div>
          <p className="text-sm font-semibold text-heading">Training Library</p>
          <p className="text-xs text-muted">
            Pick a segment, then add matching moves from your catalog.
          </p>
        </div>
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
                  activeFilter === tab ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"
                }
                onClick={() => setFilter(tab)}
              >
                {tab === "all" ? "All" : formatCategoryLabel(tab)}
              </button>
            ))}
          </div>
        ) : null}
        {enableWeightEquipmentFilter && visibleKinds.has("weight") ? (
          <div className="space-y-1">
            <div className="flex flex-wrap gap-1">
              <button
                type="button"
                className={
                  weightPickerFilter === "ALL"
                    ? "btn-primary text-xs py-1 px-2"
                    : "btn-ghost text-xs py-1 px-2"
                }
                onClick={() => setWeightPickerFilter("ALL")}
              >
                {gymMembership ? "All / Gym" : "All"}
              </button>
              <button
                type="button"
                className={
                  weightPickerFilter === "HOME_READY"
                    ? "btn-primary text-xs py-1 px-2"
                    : "btn-ghost text-xs py-1 px-2"
                }
                onClick={() => setWeightPickerFilter("HOME_READY")}
              >
                Home-ready
              </button>
            </div>
            {weightPickerFilter === "HOME_READY" && ownedEquipment.length === 0 && !gymMembership ? (
              <p className="text-[11px] text-muted leading-snug">
                Home-ready uses your Equipment tab profile plus no-equipment bodyweight moves and
                exercises you add in the catalog.
              </p>
            ) : null}
          </div>
        ) : null}
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-4 min-h-0 max-h-[min(70vh,560px)]">
        {showWeight && visibleKinds.has("weight") && weightGroups.length === 0 ? (
          <p className="text-xs text-muted px-1">
            {enableWeightEquipmentFilter && weightPickerFilter === "HOME_READY"
              ? "No home-ready exercises match your equipment. Try All / Gym or update Equipment."
              : "No exercises match your search."}
          </p>
        ) : null}
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
                      handlePick({
                        kind: "weight",
                        id: ex.id,
                        name: ex.name,
                        group: group.label,
                      })
                    }
                    pickLabel={pickLabel}
                    pickDisabled={pickDisabled}
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
                      handlePick({
                        kind: "stretch",
                        id: entry.id,
                        name: entry.name,
                        group: group.label,
                      })
                    }
                    pickLabel={pickLabel}
                    pickDisabled={pickDisabled}
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
                    selected={isSelected("cardio", activity.id)}
                    onPick={() =>
                      handlePick({
                        kind: "cardio",
                        id: activity.id,
                        name: activity.displayName,
                        group: group.label,
                      })
                    }
                    pickLabel={pickLabel}
                    pickDisabled={pickDisabled}
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
      <SectionHeader className="text-[10px] font-semibold text-muted px-1 mb-1">
        {title}
      </SectionHeader>
      <div className="space-y-1.5">{children}</div>
    </div>
  );
}

function SidebarRow({
  name,
  meta,
  selected,
  onPick,
  pickLabel,
  pickDisabled,
}: {
  name: string;
  meta?: string;
  selected: boolean;
  onPick: () => void;
  pickLabel: string;
  pickDisabled: boolean;
}) {
  return (
    <div
      className={`flex items-center gap-2 rounded-card border px-3 py-2 text-sm transition ${
        pickDisabled ? "opacity-45" : ""
      } ${
        selected
          ? "border-[var(--erv-primary)] bg-[var(--erv-primary-container)]"
          : "border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] hover:border-[var(--erv-outline)] hover:shadow-sm"
      }`}
    >
      <span className="flex-1 min-w-0">
        <span className="block truncate font-medium text-heading">{name}</span>
        {meta ? <span className="block truncate text-[10px] text-muted">{meta}</span> : null}
      </span>
      <button
        type="button"
        className={selected ? "btn-primary text-xs py-0.5 px-2 shrink-0" : "btn-ghost text-xs py-0.5 px-2 shrink-0"}
        disabled={pickDisabled}
        onClick={onPick}
      >
        {selected ? "Added" : pickLabel}
      </button>
    </div>
  );
}
