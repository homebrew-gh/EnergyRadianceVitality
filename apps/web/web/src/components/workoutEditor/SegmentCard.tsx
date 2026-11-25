import { Fragment, useState, type KeyboardEvent, type ReactNode } from "react";
import clsx from "clsx";
import { useDroppable } from "@dnd-kit/core";
import { SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { formatLastLift, lastLiftTopWeightKg, type LastLift } from "../../lib/lastLift";
import {
  applyCardioAmount,
  applyCardioHeartRate,
  applyCardioRest,
  applyCardioWork,
  applyMobilityHold,
  applyMobilityPerSide,
  CARDIO_MODE_LABELS,
  cardioAmountField,
  cardioHeartRateField,
  cardioMode,
  cardioRestField,
  cardioWorkField,
  mobilityHoldField,
  mobilityPerSideField,
  setCardioMode,
  type CardioMode,
} from "../../lib/cardioRowFields";
import {
  cardioActivityLabel,
  stretchLabel,
  type CardioCatalogActivity,
  type StretchCatalogEntry,
} from "../../lib/catalog";
import type { CardioRoutine } from "../../lib/cardioTraining";
import { copyRowDown, duplicateItemAt, moveItem } from "../../lib/segmentRows";
import type { BodyWeightUnit } from "../../lib/fitnessEquipment";
import type { PhoneWarning } from "../../lib/phoneCompatibility";
import type { TrainingSnapshot } from "../../lib/trainingSnapshot";
import { exerciseLabel, type WeightExercise } from "../../lib/weightTraining";
import { formatWeightLoadNumber, weightLoadUnitSuffix } from "../../lib/weightLoadUnit";
import { suggestedTargetWeightKg } from "../../lib/workoutPrescriptionHints";
import {
  applyReps,
  applyRest,
  applyRir,
  applySets,
  applyWeight,
  formatClock,
  parseClock,
  repsField,
  restField,
  rirField,
  setsField,
  weightField,
  type RowField,
} from "../../lib/liftRowFields";
import {
  defaultRestPolicy,
  exerciseSetLoggingStyle,
  exerciseSupportsTargetWeight,
  newNoteItem,
  newRestItem,
  segmentAllowsInlineNotes,
  segmentItems,
  segmentKindLabel,
  type WorkoutCardioPrescription,
  type WorkoutItem,
  type WorkoutMobilityPrescription,
  type WorkoutSegment,
  type WorkoutSegmentKind,
  type WorkoutWeightPrescription,
} from "../../lib/workoutTraining";
import { CardioDetails } from "./CardioDetails";
import { cardioDistanceUnitFor } from "../../lib/cardioDistance";
import { FieldLabel } from "../FieldLabel";
import { PlateLoadHint } from "../PlateLoadHint";
import { WeightPrescriptionFields } from "./WeightPrescriptionFields";
import { CellInput, ClockInput } from "./CellInput";
import { RowMenu, type RowMenuAction } from "./RowMenu";

type WeightItem = Extract<WorkoutItem, { type: "weight" }>;

/** Column headings for a segment's table, and what its add button says. */
function segmentColumns(
  kind: WorkoutSegmentKind,
  weightSuffix: string,
): { item: string; values: string[]; addLabel: string } {
  switch (kind) {
    case "cardio":
    case "interval":
      return { item: "Activity", values: ["Mode", "Minutes / Rounds", "Work", "Rest", "Heart Rate"], addLabel: "+ Add Activity" };
    case "mobility":
      return { item: "Stretch", values: ["Hold", "Per side", "", "", ""], addLabel: "+ Add Stretch" };
    case "composite":
      return { item: "Item", values: ["", "", "", "", ""], addLabel: "+ Add From Library" };
    default:
      return { item: "Exercise", values: ["Sets", "Reps", `Weight (${weightSuffix})`, "RIR", "Rest"], addLabel: "+ Add Exercise" };
  }
}

/** Straight sets, supersets and circuits: segments made of lifts. */
export function isLiftingSegment(segment: WorkoutSegment): boolean {
  return segment.kind === "straight_sets" || segment.kind === "superset" || segment.kind === "circuit";
}

function fallbackExercise(id: string): WeightExercise {
  return { id, name: id, muscleGroup: "", pushOrPull: "push", equipment: "" };
}

function Cell(props: {
  field: RowField;
  label: string;
  onCommit: (text: string) => void;
  onOpenDetails: () => void;
  inputMode?: "numeric" | "decimal" | "text";
  placeholder?: string;
  /** Small label above the cell, for mixed segments where one header can't name every column. */
  caption?: string;
}) {
  if (!props.caption) return <CellBody {...props} />;
  return (
    <div>
      <span className="block px-2 text-[11px] leading-tight text-muted">
        <ColumnLabel text={props.caption} />
      </span>
      <CellBody {...props} />
    </div>
  );
}

/** Title case for column labels, except a unit in brackets ("Weight (lb)") stays as written. */
function ColumnLabel({ text }: { text: string }) {
  if (!text) return null;
  const unit = text.match(/^(.*?)(\s*\(.*\))$/);
  if (!unit) return <FieldLabel>{text}</FieldLabel>;
  return (
    <>
      <FieldLabel>{unit[1]!}</FieldLabel>
      {unit[2]}
    </>
  );
}

function CellBody({
  field,
  label,
  onCommit,
  onOpenDetails,
  inputMode = "numeric",
  placeholder,
}: {
  field: RowField;
  label: string;
  onCommit: (text: string) => void;
  onOpenDetails: () => void;
  inputMode?: "numeric" | "decimal" | "text";
  placeholder?: string;
}) {
  if (!field.editable && !field.text) return <span className="block px-2 py-1.5 text-muted">–</span>;
  if (!field.editable) {
    return (
      <button
        type="button"
        className="w-full rounded-lg px-2 py-1.5 text-left text-sm text-muted italic hover:bg-[var(--erv-surface-variant)]"
        title={field.hint}
        onClick={onOpenDetails}
      >
        {field.text}
      </button>
    );
  }
  return (
    <CellInput
      value={field.text}
      ariaLabel={label}
      title={field.hint}
      placeholder={placeholder ?? (field.hint ? "0:00" : "–")}
      inputMode={inputMode}
      onCommit={onCommit}
    />
  );
}

/** Drag data for a table row, so a drop knows where the row came from. */
export type RowDragData = { type: "row"; segmentId: string; itemId: string };
/** Drag data for the space under a segment's rows: a row dropped here goes to the end. */
export type SegmentEndDropData = { type: "segment-end"; segmentId: string };

/** A table row that can be dragged by its handle and shows where a dragged row would land. */
function SortableRow({
  id,
  segmentId,
  index,
  className,
  children,
}: {
  id: string;
  segmentId: string;
  index: number;
  className?: string;
  children: (handle: { attributes: object; listeners: object | undefined }) => ReactNode;
}) {
  const { setNodeRef, attributes, listeners, isDragging, isOver, activeIndex, active } = useSortable({
    id,
    data: { type: "row", segmentId, itemId: id } satisfies RowDragData,
  });
  const target = isOver && active?.id !== id;
  // Moving down a segment lands after the row under the pointer; anything else lands before it.
  const below = target && activeIndex >= 0 && activeIndex < index;
  return (
    <tr
      ref={setNodeRef}
      data-row-index={index}
      className={clsx(
        className,
        isDragging ? "opacity-40" : "",
        target && !below ? "[&>td]:border-t-2 [&>td]:border-t-[var(--erv-primary)]" : "",
        below ? "[&>td]:border-b-2 [&>td]:border-b-[var(--erv-primary)]" : "",
      )}
    >
      {children({ attributes, listeners })}
    </tr>
  );
}

/** The grip in the first column: drag it to move the row, or focus it and use Space and arrows. */
function RowHandle({
  index,
  label,
  handle,
}: {
  index: number;
  label: string;
  handle: { attributes: object; listeners: object | undefined };
}) {
  return (
    <button
      type="button"
      {...handle.attributes}
      {...(handle.listeners ?? {})}
      tabIndex={-1}
      className="flex cursor-grab touch-none items-center gap-1 rounded px-1 py-0.5 text-xs text-muted hover:bg-[var(--erv-surface-variant)] hover:text-heading active:cursor-grabbing"
      aria-label={`Move ${label}`}
      title="Drag to move. Alt+↑ or Alt+↓ in a row moves it too."
    >
      <span aria-hidden>⠿</span>
      {index + 1}
    </button>
  );
}

export function SegmentCard({
  segment,
  position,
  active,
  exercises,
  snapshot,
  weightLoadUnit,
  warnings,
  onSelect,
  onChange,
  onEdit,
  onRemove,
  onMoveUp,
  onMoveDown,
  onOpenLibrary,
  onSwapItem,
  lastLifts,
  todayIso,
  stretchCatalog,
  cardioCatalog,
  cardioRoutines,
}: {
  segment: WorkoutSegment;
  /** 1-based position in the workout, for the heading. */
  position: number;
  active: boolean;
  exercises: WeightExercise[];
  snapshot: TrainingSnapshot | null;
  weightLoadUnit: BodyWeightUnit;
  warnings: PhoneWarning[];
  onSelect: () => void;
  /** `separate` marks a structural change (add or remove a row) as its own undo step. */
  onChange: (segment: WorkoutSegment, separate?: boolean) => void;
  /**
   * A structural change (row tools, moving rows) made to the segment as it is when it runs, so
   * text still being typed in a cell is kept. Its own undo step.
   */
  onEdit: (edit: (segment: WorkoutSegment) => WorkoutSegment) => void;
  onRemove: () => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
  onOpenLibrary: () => void;
  /** Open the library to pick a different exercise, activity or stretch for this row. */
  onSwapItem: (itemId: string) => void;
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
  cardioRoutines: CardioRoutine[];
  lastLifts: ReadonlyMap<string, LastLift>;
  /** yyyy-mm-dd, for "today" in the last-time line. */
  todayIso: string;
}) {
  const items = segmentItems(segment);
  const [openDetails, setOpenDetails] = useState<Set<string>>(() => new Set());
  const [showSettings, setShowSettings] = useState(false);
  const roundBased = segment.kind === "circuit" || segment.kind === "superset";
  const restPolicy = { ...defaultRestPolicy(), ...segment.restPolicy };
  const suffix = weightLoadUnitSuffix(weightLoadUnit);
  const lifting = isLiftingSegment(segment);
  /** A flow block mixes kinds, so its cells carry their own small labels instead of a header. */
  const captions = segment.kind === "composite";
  const columns = segmentColumns(segment.kind, suffix);
  const cardioName = (id: string) => cardioActivityLabel(id, cardioCatalog);
  const stretchName = (id: string) => stretchLabel(id, stretchCatalog);
  const exerciseFor = (id: string) => exercises.find((ex) => ex.id === id) ?? null;

  const toggleDetails = (itemId: string) =>
    setOpenDetails((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) next.delete(itemId);
      else next.add(itemId);
      return next;
    });

  const replaceItem = (index: number, item: WorkoutItem, separate = false) =>
    onChange({ ...segment, items: items.map((existing, i) => (i === index ? item : existing)) }, separate);
  const segmentId = segment.id!;
  const itemIds = items.map((item, index) => item.id ?? `${segmentId}:${index}`);
  const { setNodeRef: setEndDropRef, isOver: overEnd } = useDroppable({
    id: `end:${segmentId}`,
    data: { type: "segment-end", segmentId } satisfies SegmentEndDropData,
  });

  /** Row tools find the row by id when they run, since rows may have moved since this render. */
  const editRow = (itemId: string, edit: (s: WorkoutSegment, index: number) => WorkoutSegment) =>
    onEdit((current) => {
      const index = segmentItems(current).findIndex((item) => item.id === itemId);
      return index < 0 ? current : edit(current, index);
    });
  const removeRow = (itemId: string) =>
    editRow(itemId, (s, index) => ({ ...s, items: segmentItems(s).filter((_, i) => i !== index) }));
  const moveRow = (itemId: string, by: number) =>
    editRow(itemId, (s, index) => moveItem(s, index, index + by));
  const duplicateRow = (itemId: string) => {
    const newId = crypto.randomUUID();
    editRow(itemId, (s, index) => duplicateItemAt(s, index, newId));
  };
  const styleOf = (exerciseId: string) => exerciseSetLoggingStyle(exerciseFor(exerciseId) ?? fallbackExercise(exerciseId));
  const copyDown = (itemId: string) => editRow(itemId, (s, index) => copyRowDown(s, index, styleOf));

  /** Alt+↑ / Alt+↓ in a row moves it; whatever was being typed is applied first. */
  const onRowKeyDown = (event: KeyboardEvent<HTMLTableSectionElement>) => {
    if (!event.altKey || (event.key !== "ArrowUp" && event.key !== "ArrowDown")) return;
    const row = (event.target as HTMLElement).closest("tr[data-row-index]");
    const index = Number(row?.getAttribute("data-row-index"));
    const item = items[index];
    if (!item?.id) return;
    event.preventDefault();
    const focused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    focused?.blur();
    moveRow(item.id, event.key === "ArrowUp" ? -1 : 1);
    window.setTimeout(() => focused?.focus(), 0);
  };

  const rowActions = (itemId: string, index: number, extra: RowMenuAction[] = []): RowMenuAction[] => [
    ...extra,
    { label: "Duplicate", onSelect: () => duplicateRow(itemId) },
    { label: "Move Up", onSelect: () => moveRow(itemId, -1), disabled: index === 0 },
    { label: "Move Down", onSelect: () => moveRow(itemId, 1), disabled: index === items.length - 1 },
    { label: "Remove", onSelect: () => removeRow(itemId), danger: true },
  ];
  const setPrescription = (index: number, item: WeightItem, prescription: WorkoutWeightPrescription) =>
    replaceItem(index, { ...item, prescription }, true);

  const itemWarnings = (index: number) => warnings.filter((w) => w.itemIndex === index);
  const segmentWarnings = warnings.filter((w) => w.itemIndex == null);

  return (
    <section
      id={`segment-${segment.id}`}
      className={clsx(
        "card scroll-mt-24 overflow-hidden transition",
        active ? "ring-2 ring-[var(--erv-primary)]/60" : "",
      )}
      onFocusCapture={onSelect}
      onMouseDown={onSelect}
    >
      <header className="flex flex-wrap items-center gap-2 border-b border-[var(--erv-outline-variant)] bg-[var(--erv-surface-variant)]/35 px-4 py-2">
        <span className="text-xs font-semibold text-muted">
          {position}. {segmentKindLabel(segment.kind)}
          {roundBased ? ` · ${segment.rounds ?? 1} rounds` : ""}
        </span>
        <input
          className="min-w-[10rem] flex-1 rounded-lg border border-transparent bg-transparent px-2 py-1 font-semibold text-heading hover:border-[var(--erv-outline-variant)] focus:border-[var(--erv-primary)] focus:outline-none"
          value={segment.title ?? ""}
          placeholder={segmentKindLabel(segment.kind)}
          aria-label={`Segment ${position} title`}
          onChange={(e) => onChange({ ...segment, title: e.target.value === "" ? null : e.target.value })}
        />
        <div className="flex items-center gap-1 text-xs">
          <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => setShowSettings((v) => !v)}>
            {showSettings ? "Hide Settings" : "Settings"}
          </button>
          <button
            type="button"
            className="btn-ghost px-2 py-1 text-xs"
            onClick={onMoveUp}
            disabled={!onMoveUp}
            aria-label="Move segment up"
            title="Move segment up"
          >
            ↑
          </button>
          <button
            type="button"
            className="btn-ghost px-2 py-1 text-xs"
            onClick={onMoveDown}
            disabled={!onMoveDown}
            aria-label="Move segment down"
            title="Move segment down"
          >
            ↓
          </button>
          <button
            type="button"
            className="btn-ghost px-2 py-1 text-xs text-[var(--erv-error)]"
            onClick={onRemove}
            title="Remove segment (Ctrl+Z brings it back)"
          >
            Remove
          </button>
        </div>
      </header>

      {showSettings ? (
        <div className="grid grid-cols-2 gap-3 border-b border-[var(--erv-outline-variant)] px-4 py-3 md:grid-cols-4">
          {roundBased ? (
            <>
              <label className="block space-y-1 text-xs">
                <FieldLabel>Rounds</FieldLabel>
                <div className="rounded-lg border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]">
                  <CellInput
                    value={String(segment.rounds ?? 1)}
                    ariaLabel="Rounds"
                    inputMode="numeric"
                    onCommit={(text) => {
                      const rounds = Number.parseInt(text, 10);
                      if (Number.isFinite(rounds) && rounds >= 1) onChange({ ...segment, rounds }, true);
                    }}
                  />
                </div>
              </label>
              <ClockInput
                label="Rest between exercises"
                seconds={restPolicy.restBetweenItemsSeconds}
                onSeconds={(s) =>
                  onChange({ ...segment, restPolicy: { ...restPolicy, restBetweenItemsSeconds: s ?? undefined } }, true)
                }
              />
              <ClockInput
                label="Rest after round"
                seconds={restPolicy.restAfterRoundSeconds}
                onSeconds={(s) =>
                  onChange({ ...segment, restPolicy: { ...restPolicy, restAfterRoundSeconds: s ?? undefined } }, true)
                }
              />
            </>
          ) : null}
          <ClockInput
            label="Rest after segment"
            seconds={segment.restAfterSeconds}
            onSeconds={(s) => onChange({ ...segment, restAfterSeconds: s }, true)}
          />
        </div>
      ) : null}

      <div className="overflow-x-auto">
        <table className="w-full min-w-[40rem] text-sm">
          <thead>
            <tr className="text-left text-xs text-muted">
              <th className="w-12 px-2 py-2 font-medium">#</th>
              <th className="px-2 py-2 font-medium">{columns.item}</th>
              {columns.values.map((label, i) => (
                <th key={i} className={clsx("px-2 py-2 font-medium", i === 0 && !lifting ? "w-32" : "w-24")}>
                  <ColumnLabel text={label} />
                </th>
              ))}
              <th className="w-28 px-2 py-2" aria-label="Row actions" />
            </tr>
          </thead>
          <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
          <tbody onKeyDown={onRowKeyDown}>
            {items.map((item, index) => {
              const rowKey = itemIds[index]!;
              const rowWarnings = itemWarnings(index);
              if (item.type === "note") {
                return (
                  <SortableRow key={rowKey} id={rowKey} segmentId={segmentId} index={index} className="border-t border-[var(--erv-outline-variant)]">
                    {(handle) => (
                  <>
                    <td className="px-1 py-1"><RowHandle index={index} label="note" handle={handle} /></td>
                    <td colSpan={6} className="px-1 py-1">
                      <input
                        className="w-full rounded-lg border border-transparent bg-transparent px-2 py-1.5 text-sm italic hover:border-[var(--erv-outline-variant)] focus:border-[var(--erv-primary)] focus:outline-none"
                        value={item.text}
                        placeholder="Note for this point in the workout"
                        aria-label="Note"
                        onChange={(e) => replaceItem(index, { ...item, text: e.target.value })}
                      />
                    </td>
                    <td className="px-2 py-1 text-right">
                      <RowMenu label="Note actions" actions={rowActions(rowKey, index)} />
                    </td>
                  </>
                    )}
                  </SortableRow>
                );
              }
              if (item.type === "rest") {
                return (
                  <SortableRow key={rowKey} id={rowKey} segmentId={segmentId} index={index} className="border-t border-[var(--erv-outline-variant)]">
                    {(handle) => (
                  <>
                    <td className="px-1 py-1"><RowHandle index={index} label="rest" handle={handle} /></td>
                    <td className="px-2 py-1 text-muted">Rest</td>
                    {lifting ? <td colSpan={4} /> : null}
                    <td className="px-1 py-1">
                      <CellInput
                        value={formatClock(item.durationSeconds)}
                        ariaLabel="Rest length"
                        onCommit={(text) => {
                          const seconds = parseClock(text);
                          if (seconds != null) replaceItem(index, { ...item, durationSeconds: seconds }, true);
                        }}
                      />
                    </td>
                    {lifting ? null : <td colSpan={4} />}
                    <td className="px-2 py-1 text-right">
                      <RowMenu label="Rest actions" actions={rowActions(rowKey, index)} />
                    </td>
                  </>
                    )}
                  </SortableRow>
                );
              }
              if (item.type === "cardio") {
                const c = item.cardio;
                const name = item.title?.trim() || cardioName(c.activity);
                const detailsOpen = openDetails.has(rowKey);
                const setCardio = (cardio: WorkoutCardioPrescription, separate = true) =>
                  replaceItem(index, { ...item, cardio }, separate);
                const routine = c.cardioRoutineId ? cardioRoutines.find((r) => r.id === c.cardioRoutineId) : null;
                return (
                  <Fragment key={rowKey}>
                    <SortableRow id={rowKey} segmentId={segmentId} index={index} className="border-t border-[var(--erv-outline-variant)] align-top">
                      {(handle) => (
                    <>
                      <td className="px-1 py-2"><RowHandle index={index} label={name} handle={handle} /></td>
                      <td className="px-2 py-2">
                        <span className="block font-medium text-heading">{name}</span>
                        <span className="block text-xs text-muted">Cardio</span>
                      </td>
                      {c.cardioRoutineId ? (
                        <td colSpan={5} className="px-2 py-2 text-sm text-muted">
                          Runs your saved routine “{routine?.name ?? "missing routine"}”.{" "}
                          <button type="button" tabIndex={-1} className="underline hover:text-heading" onClick={() => toggleDetails(rowKey)}>
                            Change in details
                          </button>
                        </td>
                      ) : (
                        <>
                          <td className="px-1 py-1">
                            {captions ? (
                              <span className="block px-2 text-[11px] leading-tight text-muted">Mode</span>
                            ) : null}
                            <select
                              className="w-full rounded-lg border border-transparent bg-transparent px-1 py-1.5 text-sm hover:border-[var(--erv-outline-variant)] focus:border-[var(--erv-primary)] focus:outline-none"
                              value={cardioMode(c)}
                              aria-label={`${name} mode`}
                              onChange={(e) => setCardio(setCardioMode(c, e.target.value as CardioMode))}
                            >
                              {(Object.keys(CARDIO_MODE_LABELS) as CardioMode[]).map((mode) => (
                                <option key={mode} value={mode}>
                                  {CARDIO_MODE_LABELS[mode]}
                                </option>
                              ))}
                            </select>
                          </td>
                          <td className="px-1 py-1">
                            <Cell
                              field={cardioAmountField(c)}
                              label={`${name} ${cardioMode(c) === "steady" ? "minutes" : "rounds"}`}
                              caption={captions ? cardioAmountField(c).hint : undefined}
                              placeholder="–"
                              onCommit={(text) => setCardio(applyCardioAmount(c, text))}
                              onOpenDetails={() => toggleDetails(rowKey)}
                            />
                          </td>
                          <td className="px-1 py-1">
                            <Cell
                              field={cardioWorkField(c)}
                              label={`${name} work`}
                              caption={captions && cardioMode(c) !== "steady" ? "Work" : undefined}
                              inputMode="text"
                              placeholder="0:00"
                              onCommit={(text) => setCardio(applyCardioWork(c, text))}
                              onOpenDetails={() => toggleDetails(rowKey)}
                            />
                          </td>
                          <td className="px-1 py-1">
                            <Cell
                              field={cardioRestField(c)}
                              label={`${name} rest`}
                              caption={captions && cardioMode(c) === "sprint_intervals" ? "Rest" : undefined}
                              inputMode="text"
                              placeholder="0:00"
                              onCommit={(text) => setCardio(applyCardioRest(c, text))}
                              onOpenDetails={() => toggleDetails(rowKey)}
                            />
                          </td>
                          <td className="px-1 py-1">
                            <Cell
                              field={cardioHeartRateField(c)}
                              label={`${name} heart rate`}
                              caption={captions ? "Heart Rate" : undefined}
                              inputMode="text"
                              placeholder="–"
                              onCommit={(text) => setCardio(applyCardioHeartRate(c, text))}
                              onOpenDetails={() => toggleDetails(rowKey)}
                            />
                          </td>
                        </>
                      )}
                      <td className="whitespace-nowrap px-2 py-2 text-right text-xs">
                        <button
                          type="button"
                          tabIndex={-1}
                          className={clsx("mr-2 hover:text-heading", detailsOpen ? "font-semibold text-heading" : "text-muted")}
                          onClick={() => toggleDetails(rowKey)}
                          aria-expanded={detailsOpen}
                        >
                          Details
                        </button>
                        <RowMenu
                          label={`${name} actions`}
                          actions={rowActions(rowKey, index, [{ label: "Change Activity…", onSelect: () => onSwapItem(rowKey) }])}
                        />
                      </td>
                    </>
                      )}
                    </SortableRow>
                    {itemWarnings(index).length > 0 ? (
                      <tr>
                        <td />
                        <td colSpan={7} className="px-2 pb-2 text-xs text-[var(--erv-error)]">
                          {itemWarnings(index).map((w) => (
                            <p key={w.key}>Phone: {w.message}</p>
                          ))}
                        </td>
                      </tr>
                    ) : null}
                    {detailsOpen ? (
                      <tr>
                        <td />
                        <td colSpan={7} className="px-2 pb-3">
                          <CardioDetails
                            cardio={c}
                            cardioRoutines={cardioRoutines}
                            distanceUnit={cardioDistanceUnitFor(weightLoadUnit)}
                            onChange={(cardio, separate) => setCardio(cardio, separate)}
                          />
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                );
              }
              if (item.type === "mobility") {
                const m = item.mobility;
                const name = item.title?.trim() || stretchName(m.catalogId);
                const setMobility = (mobility: WorkoutMobilityPrescription) =>
                  replaceItem(index, { ...item, mobility }, true);
                return (
                  <Fragment key={rowKey}>
                    <SortableRow id={rowKey} segmentId={segmentId} index={index} className="border-t border-[var(--erv-outline-variant)] align-top">
                      {(handle) => (
                    <>
                      <td className="px-1 py-2"><RowHandle index={index} label={name} handle={handle} /></td>
                      <td className="px-2 py-2">
                        <span className="block font-medium text-heading">{name}</span>
                        <span className="block text-xs text-muted">Stretch</span>
                      </td>
                      <td className="px-1 py-1">
                        <Cell
                          field={mobilityHoldField(m)}
                          label={`${name} hold`}
                          caption={captions ? "Hold" : undefined}
                          inputMode="text"
                          placeholder="0:00"
                          onCommit={(text) => setMobility(applyMobilityHold(m, text))}
                          onOpenDetails={() => undefined}
                        />
                      </td>
                      <td className="px-1 py-1">
                        <Cell
                          field={mobilityPerSideField(m)}
                          label={`${name} hold per side`}
                          caption={captions ? "Per side" : undefined}
                          inputMode="text"
                          placeholder="–"
                          onCommit={(text) => setMobility(applyMobilityPerSide(m, text))}
                          onOpenDetails={() => undefined}
                        />
                      </td>
                      <td colSpan={3} />
                      <td className="whitespace-nowrap px-2 py-2 text-right text-xs">
                        <RowMenu
                          label={`${name} actions`}
                          actions={rowActions(rowKey, index, [{ label: "Change Stretch…", onSelect: () => onSwapItem(rowKey) }])}
                        />
                      </td>
                    </>
                      )}
                    </SortableRow>
                    {itemWarnings(index).length > 0 ? (
                      <tr>
                        <td />
                        <td colSpan={7} className="px-2 pb-2 text-xs text-[var(--erv-error)]">
                          {itemWarnings(index).map((w) => (
                            <p key={w.key}>Phone: {w.message}</p>
                          ))}
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                );
              }
              const exercise = exerciseFor(item.exerciseId);
              const style = exerciseSetLoggingStyle(exercise ?? fallbackExercise(item.exerciseId));
              const supportsWeight = exerciseSupportsTargetWeight(exercise ?? fallbackExercise(item.exerciseId));
              const p = item.prescription ?? {};
              const detailsOpen = openDetails.has(rowKey);
              const weight = weightField(p, weightLoadUnit);
              const name = item.title?.trim() || exerciseLabel(item.exerciseId, exercises);
              const last = lastLifts.get(item.exerciseId);
              const lastTopKg = last && supportsWeight && weight.editable ? lastLiftTopWeightKg(last) : null;
              const hasLiftBelow = items.slice(index + 1).some((other) => other.type === "weight");
              return (
                <Fragment key={rowKey}>
                  <SortableRow id={rowKey} segmentId={segmentId} index={index} className="border-t border-[var(--erv-outline-variant)] align-top">
                    {(handle) => (
                  <>
                    <td className="px-1 py-2"><RowHandle index={index} label={name} handle={handle} /></td>
                    <td className="px-2 py-2">
                      <span className="block font-medium text-heading">{name}</span>
                      {exercise?.equipment ? <span className="block text-xs text-muted">{exercise.equipment}</span> : null}
                      {(item.alternativeExerciseIds ?? []).length > 0 ? (
                        <span className="block text-xs text-muted">
                          or {(item.alternativeExerciseIds ?? []).map((id) => exerciseLabel(id, exercises)).join(", ")}
                        </span>
                      ) : null}
                      {last ? (
                        lastTopKg != null ? (
                          <button
                            type="button"
                            tabIndex={-1}
                            className="block text-left text-xs text-muted hover:text-heading hover:underline"
                            title={`Use ${formatWeightLoadNumber(lastTopKg, weightLoadUnit)} ${suffix} as the weight`}
                            onClick={() =>
                              setPrescription(index, item, applyWeight(p, formatWeightLoadNumber(lastTopKg, weightLoadUnit), weightLoadUnit))
                            }
                          >
                            Last: {formatLastLift(last, weightLoadUnit, todayIso)}
                          </button>
                        ) : (
                          <span className="block text-xs text-muted">Last: {formatLastLift(last, weightLoadUnit, todayIso)}</span>
                        )
                      ) : null}
                    </td>
                    <td className="px-1 py-1">
                      <Cell
                        field={setsField(p)}
                        label={`${name} sets`}
                        caption={captions ? "Sets" : undefined}
                        onCommit={(text) => setPrescription(index, item, applySets(p, text))}
                        onOpenDetails={() => toggleDetails(rowKey)}
                      />
                    </td>
                    <td className="px-1 py-1">
                      <Cell
                        field={repsField(p, style)}
                        label={`${name} reps`}
                        caption={captions ? "Reps" : undefined}
                        inputMode="text"
                        onCommit={(text) => setPrescription(index, item, applyReps(p, text, style))}
                        onOpenDetails={() => toggleDetails(rowKey)}
                      />
                    </td>
                    <td className="px-1 py-1">
                      {supportsWeight ? (
                        <Cell
                          field={weight}
                          label={`${name} weight`}
                          caption={captions ? `Weight (${suffix})` : undefined}
                          inputMode="decimal"
                          onCommit={(text) => setPrescription(index, item, applyWeight(p, text, weightLoadUnit))}
                          onOpenDetails={() => toggleDetails(rowKey)}
                        />
                      ) : (
                        <span className="block px-2 py-1.5 text-muted">–</span>
                      )}
                    </td>
                    <td className="px-1 py-1">
                      <Cell
                        field={rirField(p)}
                        label={`${name} RIR`}
                        caption={captions ? "RIR" : undefined}
                        onCommit={(text) => setPrescription(index, item, applyRir(p, text))}
                        onOpenDetails={() => toggleDetails(rowKey)}
                      />
                    </td>
                    <td className="px-1 py-1">
                      <Cell
                        field={restField(p)}
                        label={`${name} rest between sets`}
                        caption={captions ? "Rest" : undefined}
                        inputMode="text"
                        onCommit={(text) => setPrescription(index, item, applyRest(p, text))}
                        onOpenDetails={() => toggleDetails(rowKey)}
                      />
                    </td>
                    <td className="whitespace-nowrap px-2 py-2 text-right text-xs">
                      <button
                        type="button"
                        tabIndex={-1}
                        className={clsx("mr-2 hover:text-heading", detailsOpen ? "font-semibold text-heading" : "text-muted")}
                        onClick={() => toggleDetails(rowKey)}
                        aria-expanded={detailsOpen}
                      >
                        Details
                      </button>
                      <RowMenu
                        label={`${name} actions`}
                        actions={rowActions(rowKey, index, [
                          { label: "Swap Exercise…", onSelect: () => onSwapItem(rowKey) },
                          {
                            label: "Copy Sets, Reps, RIR and Rest Down",
                            onSelect: () => copyDown(rowKey),
                            disabled: !hasLiftBelow,
                          },
                        ])}
                      />
                    </td>
                  </>
                    )}
                  </SortableRow>
                  {supportsWeight && weight.editable && (p.targetWeightKg ?? 0) > 0 ? (
                    <tr>
                      <td />
                      <td colSpan={7} className="px-2 pb-2">
                        <PlateLoadHint
                          key={exercise?.id ?? "none"}
                          exercise={exercise}
                          targetKg={p.targetWeightKg}
                          weightLoadUnit={weightLoadUnit}
                          onUseWeightKg={(kg) => setPrescription(index, item, { ...p, targetWeightKg: kg })}
                        />
                      </td>
                    </tr>
                  ) : null}
                  {rowWarnings.length > 0 ? (
                    <tr>
                      <td />
                      <td colSpan={7} className="px-2 pb-2 text-xs text-[var(--erv-error)]">
                        {rowWarnings.map((w) => (
                          <p key={w.key}>Phone: {w.message}</p>
                        ))}
                      </td>
                    </tr>
                  ) : null}
                  {detailsOpen ? (
                    <tr>
                      <td />
                      <td colSpan={7} className="px-2 pb-3">
                        <div className="space-y-3 rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-surface)] p-3">
                          <WeightPrescriptionFields
                            prescription={p}
                            compact={false}
                            weightLoadUnit={weightLoadUnit}
                            suggestedTargetKg={suggestedTargetWeightKg(item.exerciseId, snapshot, 0, weightLoadUnit)}
                            loggingStyle={style}
                            supportsTargetWeight={supportsWeight}
                            exercise={exercise}
                            onChange={(next) => replaceItem(index, { ...item, prescription: next })}
                          />
                          <AlternativesField
                            item={item}
                            exercises={exercises}
                            onChange={(alternativeExerciseIds) =>
                              replaceItem(index, { ...item, alternativeExerciseIds }, true)
                            }
                          />
                        </div>
                      </td>
                    </tr>
                  ) : null}
                </Fragment>
              );
            })}
          </tbody>
          </SortableContext>
        </table>
      </div>

      {segmentWarnings.length > 0 ? (
        <div className="px-4 pb-2 text-xs text-[var(--erv-error)]">
          {segmentWarnings.map((w) => (
            <p key={w.key}>Phone: {w.message}</p>
          ))}
        </div>
      ) : null}

      <footer
        ref={setEndDropRef}
        className={clsx(
          "flex flex-wrap items-center gap-2 border-t border-[var(--erv-outline-variant)] px-4 py-2",
          overEnd ? "bg-[var(--erv-primary-container)]/50" : "",
        )}
      >
        <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={onOpenLibrary}>
          {columns.addLabel}
        </button>
        {segmentAllowsInlineNotes(segment.kind) ? (
          <>
            <button
              type="button"
              className="btn-ghost px-3 py-1 text-xs"
              onClick={() => onChange({ ...segment, items: [...items, newRestItem()] }, true)}
            >
              + Rest
            </button>
            <button
              type="button"
              className="btn-ghost px-3 py-1 text-xs"
              onClick={() => onChange({ ...segment, items: [...items, newNoteItem()] }, true)}
            >
              + Note
            </button>
          </>
        ) : null}
        {items.length === 0 ? (
          <span className="text-xs text-muted">Empty. Press / or {columns.addLabel} to pick from your library.</span>
        ) : null}
      </footer>
    </section>
  );
}

function AlternativesField({
  item,
  exercises,
  onChange,
}: {
  item: WeightItem;
  exercises: WeightExercise[];
  onChange: (ids: string[]) => void;
}) {
  const current = item.alternativeExerciseIds ?? [];
  return (
    <label className="block space-y-1 text-xs">
      <FieldLabel>Alternative exercises (optional, pick at run)</FieldLabel>
      <select
        className="input w-full max-w-md"
        value=""
        onChange={(e) => {
          const id = e.target.value;
          if (!id || id === item.exerciseId || current.includes(id)) return;
          onChange([...current, id]);
        }}
      >
        <option value="">Add alternative…</option>
        {exercises
          .filter((ex) => ex.id !== item.exerciseId && !current.includes(ex.id))
          .map((ex) => (
            <option key={ex.id} value={ex.id}>
              {ex.name}
            </option>
          ))}
      </select>
      {current.length > 0 ? (
        <div className="flex flex-wrap gap-1 pt-1">
          {current.map((altId) => (
            <button
              key={altId}
              type="button"
              className="btn-ghost px-2 py-0.5 text-xs"
              onClick={() => onChange(current.filter((id) => id !== altId))}
            >
              {exerciseLabel(altId, exercises)} ×
            </button>
          ))}
        </div>
      ) : null}
    </label>
  );
}
