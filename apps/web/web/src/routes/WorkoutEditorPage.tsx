import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import clsx from "clsx";
import {
  closestCenter,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  pointerWithin,
  useSensor,
  useSensors,
  type CollisionDetection,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { LibraryItemKind } from "../components/LibrarySidebar";
import { WorkoutPreviewCard } from "../components/WorkoutPreviewCard";
import { LibraryPanel } from "../components/workoutEditor/LibraryPanel";
import { SegmentCard, type RowDragData, type SegmentEndDropData } from "../components/workoutEditor/SegmentCard";
import { lastLiftByExercise } from "../lib/lastLift";
import { applyWeight } from "../lib/liftRowFields";
import { itemLibraryKey, moveItem, moveItemBetween, segmentAcceptsItem, swapExercise } from "../lib/segmentRows";
import { useAuth } from "../lib/auth";
import { useEquipment } from "../lib/equipmentData";
import { phoneCompatibilityWarnings } from "../lib/phoneCompatibility";
import { emptySegmentForKind, instantiateSegmentTemplate, WORKOUT_SEGMENT_TEMPLATES } from "../lib/segmentTemplates";
import { useTraining } from "../lib/trainingData";
import { useTrainingHistory } from "../lib/trainingHistoryData";
import { buildTrainingSnapshot } from "../lib/trainingSnapshot";
import type { WeightExercisePickerFilter } from "../lib/weightExerciseAvailability";
import { exerciseLabel } from "../lib/weightTraining";
import { useWeightLoadUnit } from "../lib/weightLoadUnit";
import {
  clearStoredWorkoutDraft,
  readStoredWorkoutDraft,
  readWorkoutDrafts,
  workoutDraftFingerprint,
  workoutPublishStateLabel,
  writeWorkoutDrafts,
  type WorkoutDrafts,
  type WorkoutPublishState,
} from "../lib/workoutDraftStorage";
import {
  canRedo,
  canUndo,
  draftPublishProblem,
  initialWorkoutEditorState,
  withStableIds,
  workoutEditorReducer,
  workoutToPublish,
} from "../lib/workoutEditor";
import { applyTargetWeightToPrescription, suggestedTargetWeightKg } from "../lib/workoutPrescriptionHints";
import { buildWorkoutPreview, formatDuration } from "../lib/workoutPreview";
import {
  defaultCardioModeForSegment,
  newCardioItem,
  newMobilityItem,
  newWeightItem,
  segmentItems,
  segmentKindLabel,
  segmentLibraryKinds,
  upsertWorkout,
  type WorkoutItem,
  type WorkoutSegment,
  type WorkoutSegmentKind,
} from "../lib/workoutTraining";
import { cardioActivityLabel, stretchLabel } from "../lib/catalog";

const EMPTY_FINGERPRINT = workoutDraftFingerprint("", []);
const UNPUBLISHED_MESSAGE =
  "This workout has changes that are not published yet. They stay in this browser, but your phone won't see them until you publish.";

/** Segment kinds offered in the outline, lifting first. */
const ADD_SEGMENT_CHOICES: { kind: WorkoutSegmentKind; label: string }[] = [
  { kind: "straight_sets", label: "Straight Sets" },
  { kind: "superset", label: "Superset" },
  { kind: "circuit", label: "Circuit" },
  { kind: "composite", label: "Flow Block" },
  { kind: "cardio", label: "Cardio" },
  { kind: "interval", label: "Intervals" },
  { kind: "mobility", label: "Mobility" },
];

/** A picked item with no segment to go in starts one of the kind it belongs to. */
const SEGMENT_FOR_PICK: Record<LibraryItemKind, WorkoutSegmentKind> = {
  weight: "straight_sets",
  stretch: "mobility",
  cardio: "cardio",
};

function formatPublishedAt(epochSeconds: number | undefined): string {
  if (!epochSeconds) return "";
  return new Date(epochSeconds * 1000).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

/** Drag data for a segment in the outline. Rows can be dropped on it too, to move them there. */
type OutlineDragData = { type: "segment"; segmentId: string };
type DragData = RowDragData | SegmentEndDropData | OutlineDragData;

function dragData(entry: { data: { current?: unknown } } | null | undefined): DragData | null {
  return (entry?.data.current as DragData | undefined) ?? null;
}

/**
 * Rows land on rows, segment ends or outline segments; segments land only on segments. The
 * pointer decides when it is over something, the nearest target otherwise (and for the keyboard).
 */
const editorCollisions: CollisionDetection = (args) => {
  const activeType = dragData(args.active)?.type;
  const droppableContainers = args.droppableContainers.filter((container) => {
    const type = dragData(container)?.type;
    return activeType === "segment" ? type === "segment" : type != null;
  });
  const narrowed = { ...args, droppableContainers };
  const within = pointerWithin(narrowed);
  if (within.length > 0) return within;
  return closestCenter({
    ...narrowed,
    droppableContainers: droppableContainers.filter((c) => (dragData(c)?.type === "segment") === (activeType === "segment")),
  });
};

function localIsoDate(date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** One segment in the outline: click to go to it, drag the grip to reorder. */
function OutlineItem({
  segment,
  index,
  active,
  onSelect,
}: {
  segment: WorkoutSegment;
  index: number;
  active: boolean;
  onSelect: () => void;
}) {
  const id = `seg:${segment.id}`;
  const { setNodeRef, attributes, listeners, transform, transition, isDragging, isOver, active: dragging } = useSortable({
    id,
    data: { type: "segment", segmentId: segment.id! } satisfies OutlineDragData,
  });
  const rowOver = isOver && dragData(dragging)?.type === "row";
  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={clsx("flex items-stretch gap-0.5 rounded-lg", isDragging ? "opacity-40" : "", rowOver ? "ring-2 ring-[var(--erv-primary)]" : "")}
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        className="cursor-grab touch-none rounded-lg px-1 text-xs text-muted hover:bg-[var(--erv-surface-variant)] hover:text-heading active:cursor-grabbing"
        aria-label={`Move segment ${index + 1}`}
        title="Drag to reorder, or focus and use Space and the arrow keys"
      >
        ⠿
      </button>
      <button
        type="button"
        className={clsx(
          "min-w-0 flex-1 rounded-lg px-2 py-1.5 text-left text-sm",
          active ? "bg-[var(--erv-primary-container)] font-semibold text-heading" : "text-muted hover:bg-[var(--erv-surface-variant)]",
        )}
        onClick={onSelect}
      >
        <span className="block truncate">
          {index + 1}. {segment.title?.trim() || segmentKindLabel(segment.kind)}
        </span>
        <span className="block text-xs font-normal text-muted">
          {segmentKindLabel(segment.kind)} · {segmentItems(segment).length}
        </span>
      </button>
    </li>
  );
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable;
}

/** /app/workouts/new: a fresh id, so the new workout has its own address from the start. */
export function NewWorkoutRedirect() {
  const id = useMemo(() => crypto.randomUUID(), []);
  return <Navigate to={`/app/workouts/${id}/edit`} replace state={{ isNew: true }} />;
}

/**
 * The full-width workout editor: one workout at its own address, a fixed top bar with the
 * publish state, an outline of segments, lifting segments as tables, and the library as a
 * slide-over panel.
 */
export function WorkoutEditorPage() {
  const { workoutId = "" } = useParams();
  const { workouts, exercises, catalogs, cardioRoutines, loading, saving, error, saveWorkouts } = useTraining();
  const { equipment, enabledWeightExercisePackIds } = useEquipment();
  const { weightLogs, cardioLogs, lastLoadedAt } = useTrainingHistory();
  const [weightLoadUnit] = useWeightLoadUnit();
  const { status: authStatus } = useAuth();
  const accountKey = authStatus?.npub ?? "default";

  const snapshot = useMemo(
    () => buildTrainingSnapshot({ weightLogs, cardioLogs, exercises, computedAtMs: lastLoadedAt ?? Date.now() }),
    [weightLogs, cardioLogs, exercises, lastLoadedAt],
  );

  // Drafts kept in this browser, shared with the Workouts list.
  const [draftStore, setDraftStore] = useState<{ account: string; drafts: WorkoutDrafts }>(() => ({
    account: accountKey,
    drafts: readWorkoutDrafts(accountKey),
  }));
  useEffect(() => {
    setDraftStore((prev) => (prev.account === accountKey ? prev : { account: accountKey, drafts: readWorkoutDrafts(accountKey) }));
  }, [accountKey]);
  useEffect(() => {
    writeWorkoutDrafts(draftStore.account, draftStore.drafts);
  }, [draftStore]);
  const drafts = draftStore.account === accountKey ? draftStore.drafts : {};

  const published = workouts.find((w) => w.id === workoutId);
  const [editor, dispatch] = useReducer(workoutEditorReducer, undefined, () => initialWorkoutEditorState());
  const { name, segments } = editor.draft;
  const [baseline, setBaseline] = useState(EMPTY_FINGERPRINT);
  const [loadedId, setLoadedId] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);
  const [libraryOpen, setLibraryOpen] = useState(false);
  /** A new workout opens the library beside the name, with the cursor left in the name. */
  const [librarySearchFocus, setLibrarySearchFocus] = useState(true);
  useEffect(() => {
    if (!libraryOpen) setLibrarySearchFocus(true);
  }, [libraryOpen]);
  /** The row whose exercise the library is choosing a replacement for. */
  const [swapTarget, setSwapTarget] = useState<{ segmentId: string; itemId: string } | null>(null);
  useEffect(() => {
    if (!libraryOpen) setSwapTarget(null);
  }, [libraryOpen]);
  const [dragLabel, setDragLabel] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [weightFilter, setWeightFilter] = useState<WeightExercisePickerFilter>("ALL");
  const nameRef = useRef<HTMLInputElement>(null);

  // Open the workout: this browser's draft if there is one, else the published version.
  useEffect(() => {
    if (loadedId === workoutId) return;
    const draft = drafts[workoutId];
    if (!draft && loading) return;
    const source = draft ?? published;
    dispatch({
      type: "load",
      name: source?.name ?? "",
      segments: withStableIds(structuredClone(source?.segments ?? []), workoutId),
    });
    setBaseline(
      published
        ? workoutDraftFingerprint(published.name, withStableIds(structuredClone(published.segments), workoutId))
        : EMPTY_FINGERPRINT,
    );
    setLoadedId(workoutId);
    // The old Workouts page reopens its last open draft on its own; this page now owns this one.
    const stored = readStoredWorkoutDraft(accountKey);
    if (stored && (stored.editingId ?? stored.draftId) === workoutId) clearStoredWorkoutDraft(accountKey);
    setMessage(draft && published ? { tone: "ok", text: "Opened your unpublished changes from this browser." } : null);
    if (!source) {
      // Most workouts start from scratch: name first, with the library ready.
      setLibrarySearchFocus(false);
      setLibraryOpen(true);
      window.setTimeout(() => nameRef.current?.focus(), 0);
    }
  }, [workoutId, loadedId, drafts, published, loading, accountKey]);

  const loaded = loadedId === workoutId;
  const dirty = loaded && workoutDraftFingerprint(name, segments) !== baseline;
  // Unpublished changes are kept in this browser, so moving around the app needs no warning.
  // Closing the tab gets one, as a reminder that the phone does not have them yet.
  useEffect(() => {
    if (!dirty) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = UNPUBLISHED_MESSAGE;
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty]);

  // Keep unpublished changes in this browser; drop the draft once it matches what is published.
  useEffect(() => {
    if (!loaded) return;
    setDraftStore((prev) => {
      const has = workoutId in prev.drafts;
      if (!dirty) {
        if (!has) return prev;
        const next = { ...prev.drafts };
        delete next[workoutId];
        return { ...prev, drafts: next };
      }
      return { ...prev, drafts: { ...prev.drafts, [workoutId]: { id: workoutId, name, segments, savedAtMs: Date.now() } } };
    });
  }, [loaded, dirty, workoutId, name, segments]);

  const publishState: WorkoutPublishState = !published ? "draft" : dirty ? "unpublished_changes" : "published";
  const preview = useMemo(
    () =>
      buildWorkoutPreview(segments, {
        exercises,
        stretchCatalog: catalogs.stretch,
        cardioCatalog: catalogs.cardio,
        cardioRoutines,
      }),
    [segments, exercises, catalogs.stretch, catalogs.cardio, cardioRoutines],
  );
  const warnings = useMemo(
    () => phoneCompatibilityWarnings(segments, { exerciseName: (id) => exerciseLabel(id, exercises) }),
    [segments, exercises],
  );

  const lastLifts = useMemo(() => lastLiftByExercise(weightLogs), [weightLogs]);
  const todayIso = localIsoDate();

  const activeSegment = segments.find((s) => s.id === editor.activeSegmentId) ?? null;
  const swapSegment = swapTarget ? segments.find((s) => s.id === swapTarget.segmentId) ?? null : null;
  const swapItem = swapSegment ? segmentItems(swapSegment).find((item) => item.id === swapTarget?.itemId) ?? null : null;
  const swapping = swapItem && itemLibraryKey(swapItem) ? swapItem : null;
  const swapKind: LibraryItemKind | null =
    swapping?.type === "weight" ? "weight" : swapping?.type === "cardio" ? "cardio" : swapping?.type === "mobility" ? "stretch" : null;
  const libraryKinds: LibraryItemKind[] = swapKind
    ? [swapKind]
    : activeSegment
      ? segmentLibraryKinds(activeSegment.kind)
      : ["weight", "stretch", "cardio"];
  const alreadyInSegment = useMemo(() => {
    const ids = new Set<string>();
    for (const item of activeSegment ? segmentItems(activeSegment) : []) {
      if (item.type === "weight") ids.add(item.exerciseId);
      if (item.type === "mobility") ids.add(item.mobility.catalogId);
      if (item.type === "cardio") ids.add(item.cardio.activity);
    }
    return ids;
  }, [activeSegment]);

  const undo = useCallback(() => {
    // Apply whatever is half-typed in a cell first, so undo takes back that too.
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
    dispatch({ type: "undo" });
  }, []);
  const redo = useCallback(() => {
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
    dispatch({ type: "redo" });
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const mod = event.ctrlKey || event.metaKey;
      const key = event.key.toLowerCase();
      if (mod && !event.altKey && (key === "z" || key === "y")) {
        event.preventDefault();
        if (key === "z" && !event.shiftKey) undo();
        else redo();
        return;
      }
      if (!mod && event.key === "/" && !isTypingTarget(event.target)) {
        event.preventDefault();
        setLibraryOpen(true);
        return;
      }
      if (event.key === "Escape") {
        setPreviewOpen(false);
        if (!isTypingTarget(event.target)) setLibraryOpen(false);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [undo, redo]);

  const updateSegment = (segmentId: string, segment: WorkoutSegment, separate = false) =>
    dispatch({ type: "updateSegment", segmentId, segment, atMs: Date.now(), separate });

  const addSegment = (segment: WorkoutSegment) => {
    dispatch({ type: "addSegment", segment });
    window.setTimeout(
      () => document.getElementById(`segment-${segment.id}`)?.scrollIntoView({ behavior: "smooth", block: "start" }),
      50,
    );
  };

  const selectSegment = (segmentId: string | undefined, scroll = false) => {
    if (!segmentId) return;
    if (segmentId !== editor.activeSegmentId) dispatch({ type: "selectSegment", segmentId });
    if (scroll) {
      document.getElementById(`segment-${segmentId}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  };

  /** Add a library pick to the selected segment, or start a segment for it. Returns why not, if not. */
  const onPick = (kind: LibraryItemKind, id: string): string | null => {
    const exercise = exercises.find((ex) => ex.id === id) ?? null;
    if (swapping?.id && swapSegment?.id) {
      const pickKey = kind === "stretch" ? `stretch:${id}` : `${kind}:${id}`;
      if (pickKey === itemLibraryKey(swapping)) return "That is already in this row.";
      if (segmentItems(swapSegment).some((item) => itemLibraryKey(item) === pickKey)) {
        return "That is already in this segment.";
      }
      let replacement: WorkoutItem;
      if (swapping.type === "weight") {
        // Sets, reps and rest carry over; the weight is the new exercise's own, or blank.
        const suggested = suggestedTargetWeightKg(id, snapshot, 0, weightLoadUnit);
        const blank = applyWeight(swapping.prescription ?? {}, "", weightLoadUnit);
        const prescription = suggested != null ? applyTargetWeightToPrescription(blank, suggested) : blank;
        replacement = { ...swapExercise(swapping, id, exercise?.name?.trim() || null), prescription };
      } else if (swapping.type === "cardio") {
        // Same minutes, rounds and heart rate on a different activity.
        replacement = { ...swapping, title: null, cardio: { ...swapping.cardio, activity: id } };
      } else if (swapping.type === "mobility") {
        replacement = { ...swapping, title: null, mobility: { ...swapping.mobility, catalogId: id } };
      } else {
        return null;
      }
      const segmentId = swapSegment.id;
      const itemId = swapping.id;
      dispatch({
        type: "editSegments",
        edit: (list) =>
          list.map((s) =>
            s.id === segmentId ? { ...s, items: segmentItems(s).map((item) => (item.id === itemId ? replacement : item)) } : s,
          ),
      });
      setLibraryOpen(false);
      return null;
    }
    const target = activeSegment ?? emptySegmentForKind(SEGMENT_FOR_PICK[kind]);
    const items = segmentItems(target);
    if (kind === "weight") {
      if (items.some((item) => item.type === "weight" && item.exerciseId === id)) {
        return `${exercise?.name ?? "That exercise"} is already in this segment. Add sets to it there.`;
      }
    }
    if (kind === "stretch" && items.some((item) => item.type === "mobility" && item.mobility.catalogId === id)) {
      return "That stretch is already in this segment.";
    }
    if (kind === "cardio" && items.some((item) => item.type === "cardio" && item.cardio.activity === id)) {
      return "That activity is already in this segment.";
    }
    let item;
    if (kind === "weight") {
      item = newWeightItem(id, target.kind, exercise);
      const suggested = suggestedTargetWeightKg(id, snapshot, 0, weightLoadUnit);
      if (suggested != null) item.prescription = applyTargetWeightToPrescription(item.prescription, suggested);
    } else if (kind === "stretch") {
      item = newMobilityItem(id);
    } else {
      item = newCardioItem(id, defaultCardioModeForSegment(target.kind));
    }
    const next = { ...target, items: [...items, item] };
    if (activeSegment?.id) updateSegment(activeSegment.id, next, true);
    else addSegment(next);
    return null;
  };

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const rowLabel = (segmentId: string, itemId: string): string => {
    const item = segmentItems(segments.find((s) => s.id === segmentId) ?? { kind: "straight_sets", items: [] }).find(
      (i) => i.id === itemId,
    );
    if (!item) return "Row";
    if (item.type === "weight") return item.title?.trim() || exerciseLabel(item.exerciseId, exercises);
    if (item.type === "cardio") return item.title?.trim() || cardioActivityLabel(item.cardio.activity, catalogs.cardio);
    if (item.type === "mobility") return item.title?.trim() || stretchLabel(item.mobility.catalogId, catalogs.stretch);
    return item.type === "rest" ? "Rest" : item.type === "note" ? "Note" : "Item";
  };

  const onDragStart = ({ active }: DragStartEvent) => {
    const data = dragData(active);
    if (data?.type === "row") setDragLabel(rowLabel(data.segmentId, data.itemId));
    else if (data?.type === "segment") {
      const segment = segments.find((s) => s.id === data.segmentId);
      setDragLabel(segment ? segment.title?.trim() || segmentKindLabel(segment.kind) : "Segment");
    }
  };

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    setDragLabel(null);
    const from = dragData(active);
    const to = dragData(over);
    if (!from || !to) return;
    if (from.type === "segment") {
      if (to.type !== "segment") return;
      const a = segments.findIndex((s) => s.id === from.segmentId);
      const b = segments.findIndex((s) => s.id === to.segmentId);
      if (a >= 0 && b >= 0 && a !== b) dispatch({ type: "moveSegment", from: a, to: b });
      return;
    }
    if (from.type !== "row") return;
    const target = segments.find((s) => s.id === to.segmentId);
    if (!target?.id) return;
    const sameSegment = to.segmentId === from.segmentId;
    if (!sameSegment) {
      const moving = segmentItems(segments.find((s) => s.id === from.segmentId) ?? target).find((i) => i.id === from.itemId);
      if (!moving) return;
      if (!segmentAcceptsItem(target, moving)) {
        setMessage({
          tone: "error",
          text: `A ${segmentKindLabel(target.kind)} segment can't hold that row. Lifts go in lifting segments or a flow block, cardio in cardio or interval segments, stretches in mobility.`,
        });
        return;
      }
      const key = itemLibraryKey(moving);
      if (key && segmentItems(target).some((i) => itemLibraryKey(i) === key)) {
        setMessage({ tone: "error", text: `${rowLabel(from.segmentId, from.itemId)} is already in that segment.` });
        return;
      }
    }
    if (to.type === "row" && to.itemId === from.itemId) return;
    dispatch({
      type: "editSegments",
      edit: (list) => {
        const source = list.find((s) => s.id === from.segmentId);
        const dest = list.find((s) => s.id === to.segmentId);
        if (!source || !dest) return list;
        const fromIndex = segmentItems(source).findIndex((i) => i.id === from.itemId);
        if (fromIndex < 0) return list;
        const destItems = segmentItems(dest);
        const overIndex = to.type === "row" ? destItems.findIndex((i) => i.id === to.itemId) : destItems.length;
        if (source === dest) {
          const next = moveItem(source, fromIndex, Math.min(overIndex, destItems.length - 1));
          return next === source ? list : list.map((s) => (s === source ? next : s));
        }
        const moved = moveItemBetween(source, fromIndex, dest, overIndex);
        if (!moved) return list;
        return list.map((s) => (s === source ? moved.source : s === dest ? moved.target : s));
      },
    });
    if (!sameSegment) selectSegment(target.id);
  };

  const publish = async () => {
    setMessage(null);
    const problem = draftPublishProblem(editor.draft);
    if (problem) {
      setMessage({ tone: "error", text: problem });
      return;
    }
    const workout = workoutToPublish(editor.draft, workoutId, published, Math.floor(Date.now() / 1000));
    try {
      await saveWorkouts(upsertWorkout(workouts, workout));
      dispatch({ type: "load", name: workout.name, segments: workout.segments });
      setBaseline(workoutDraftFingerprint(workout.name, workout.segments));
      setMessage({
        tone: "ok",
        text: published
          ? "Published your changes. Your phone gets them on its next sync."
          : "Published. Your phone gets this workout on its next sync.",
      });
    } catch (err) {
      setMessage({ tone: "error", text: err instanceof Error ? err.message : "Publishing failed." });
    }
  };

  const discard = () => {
    const what = published ? "Go back to the published version?" : "Delete this unpublished workout?";
    if (!window.confirm(`${what} Your unpublished changes are thrown away.`)) return;
    dispatch({
      type: "load",
      name: published?.name ?? "",
      segments: withStableIds(structuredClone(published?.segments ?? []), workoutId),
    });
    setMessage({ tone: "ok", text: published ? "Back to the published version." : "Cleared." });
  };

  const stateChip =
    publishState === "published"
      ? "bg-[var(--erv-primary-container)] text-heading"
      : "border border-[var(--erv-error)] text-[var(--erv-error)]";
  const stateDetail =
    publishState === "published"
      ? `Published ${formatPublishedAt(published?.lastModifiedEpochSeconds)}`
      : publishState === "unpublished_changes"
        ? "Your phone still has the older version"
        : "Not on your phone yet";

  return (
    <div className={clsx("space-y-5", libraryOpen ? "xl:pr-[24rem]" : "")}>
      <div className="sticky top-0 z-20 -mx-4 -mt-5 border-b border-[var(--erv-outline-variant)] bg-[var(--erv-bg)]/95 px-4 py-3 backdrop-blur md:-mx-6 md:px-6">
        <div className="flex flex-wrap items-center gap-3">
          <Link to="/app/workouts" className="text-sm text-muted hover:text-heading">
            ← Workouts
          </Link>
          <input
            ref={nameRef}
            className="min-w-[12rem] flex-1 rounded-lg border border-transparent bg-transparent px-2 py-1 text-xl font-bold text-heading hover:border-[var(--erv-outline-variant)] focus:border-[var(--erv-primary)] focus:outline-none"
            value={name}
            placeholder="Name this workout"
            aria-label="Workout name"
            onChange={(e) => dispatch({ type: "rename", name: e.target.value, atMs: Date.now() })}
          />
          <button type="button" className="btn-primary text-sm" onClick={() => void publish()} disabled={saving || !loaded}>
            {saving ? "Publishing…" : published ? "Publish Changes" : "Publish"}
          </button>
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
          <span className={clsx("rounded-pill px-2 py-0.5 font-semibold", stateChip)}>{workoutPublishStateLabel(publishState)}</span>
          <span className={publishState === "published" ? "text-muted" : "text-[var(--erv-error)]"}>{stateDetail}</span>
          <span className="text-muted">·</span>
          <span className="text-muted">About {formatDuration(preview.estimatedSeconds)}</span>
          {warnings.length > 0 ? (
            <button
              type="button"
              className="rounded-pill border border-[var(--erv-error)] px-2 py-0.5 font-semibold text-[var(--erv-error)]"
              onClick={() => {
                const first = warnings[0]!;
                selectSegment(segments[first.segmentIndex]?.id, true);
              }}
              title={warnings.map((w) => w.message).join("\n")}
            >
              Phone Check: {warnings.length}
            </button>
          ) : (
            <span className="rounded-pill bg-[var(--erv-surface-variant)] px-2 py-0.5 text-muted">Phone Check OK</span>
          )}
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={undo} disabled={!canUndo(editor)} title="Undo (Ctrl+Z)">
              Undo
            </button>
            <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={redo} disabled={!canRedo(editor)} title="Redo (Ctrl+Shift+Z)">
              Redo
            </button>
            {dirty ? (
              <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={discard}>
                Discard Changes
              </button>
            ) : null}
            <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={() => setPreviewOpen(true)}>
              Preview
            </button>
            <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={() => setLibraryOpen((v) => !v)} title="Library (/)">
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

      {!loaded ? (
        <p className="text-sm text-muted">Loading workout…</p>
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={editorCollisions}
          onDragStart={onDragStart}
          onDragEnd={onDragEnd}
          onDragCancel={() => setDragLabel(null)}
        >
        <div className="grid gap-5 lg:grid-cols-[12rem_minmax(0,1fr)]">
          <nav className="space-y-4 lg:sticky lg:top-32 lg:self-start" aria-label="Workout outline">
            {segments.length > 0 ? (
              <SortableContext items={segments.map((segment) => `seg:${segment.id}`)} strategy={verticalListSortingStrategy}>
                <ol className="space-y-1">
                  {segments.map((segment, index) => (
                    <OutlineItem
                      key={segment.id}
                      segment={segment}
                      index={index}
                      active={segment.id === editor.activeSegmentId}
                      onSelect={() => selectSegment(segment.id, true)}
                    />
                  ))}
                </ol>
              </SortableContext>
            ) : null}
            <div className="space-y-1">
              <p className="px-2 text-xs font-semibold text-muted">Add Segment</p>
              {ADD_SEGMENT_CHOICES.map((choice) => (
                <button
                  key={choice.kind}
                  type="button"
                  className="block w-full rounded-lg px-2 py-1 text-left text-sm text-muted hover:bg-[var(--erv-surface-variant)] hover:text-heading"
                  onClick={() => addSegment(emptySegmentForKind(choice.kind))}
                >
                  + {choice.label}
                </button>
              ))}
              <select
                className="input mt-1 py-1 text-xs"
                value=""
                aria-label="Add a template"
                onChange={(e) => {
                  const template = WORKOUT_SEGMENT_TEMPLATES.find((t) => t.id === e.target.value);
                  if (template) addSegment(instantiateSegmentTemplate(template));
                }}
              >
                <option value="">+ From a template…</option>
                {WORKOUT_SEGMENT_TEMPLATES.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>
          </nav>

          <div className="min-w-0 space-y-4">
            {segments.length === 0 ? (
              <div className="card space-y-3 p-6">
                <p className="font-semibold text-heading">Start with a lift</p>
                <p className="text-sm text-muted">
                  Search the library and press Enter to add an exercise. It goes into a new Straight Sets segment. Or
                  add a segment from the outline.
                </p>
                <button type="button" className="btn-primary text-sm" onClick={() => setLibraryOpen(true)}>
                  Open the Library
                </button>
              </div>
            ) : null}
            {segments.map((segment, index) => {
              const segmentId = segment.id!;
              const segmentWarnings = warnings.filter((w) => w.segmentIndex === index);
              const moveUp = index > 0 ? () => dispatch({ type: "moveSegment", from: index, to: index - 1 }) : undefined;
              const moveDown =
                index < segments.length - 1 ? () => dispatch({ type: "moveSegment", from: index, to: index + 1 }) : undefined;
              return (
                <SegmentCard
                  key={segmentId}
                  segment={segment}
                  position={index + 1}
                  active={segmentId === editor.activeSegmentId}
                  exercises={exercises}
                  snapshot={snapshot}
                  weightLoadUnit={weightLoadUnit}
                  warnings={segmentWarnings}
                  onSelect={() => selectSegment(segmentId)}
                  onChange={(next, separate) => updateSegment(segmentId, next, separate)}
                  onRemove={() => dispatch({ type: "removeSegment", segmentId })}
                  onMoveUp={moveUp}
                  onMoveDown={moveDown}
                  onOpenLibrary={() => {
                    selectSegment(segmentId);
                    setSwapTarget(null);
                    setLibraryOpen(true);
                  }}
                  onEdit={(edit) =>
                    dispatch({ type: "editSegments", edit: (list) => list.map((s) => (s.id === segmentId ? edit(s) : s)) })
                  }
                  onSwapItem={(itemId) => {
                    selectSegment(segmentId);
                    setSwapTarget({ segmentId, itemId });
                    setLibrarySearchFocus(true);
                    setLibraryOpen(true);
                  }}
                  lastLifts={lastLifts}
                  todayIso={todayIso}
                  stretchCatalog={catalogs.stretch}
                  cardioCatalog={catalogs.cardio}
                  cardioRoutines={cardioRoutines}
                />
              );
            })}
          </div>
        </div>
        <DragOverlay dropAnimation={null}>
          {dragLabel ? (
            <div className="inline-block rounded-lg border border-[var(--erv-primary)] bg-[var(--erv-surface)] px-3 py-1.5 text-sm font-semibold text-heading shadow-xl">
              {dragLabel}
            </div>
          ) : null}
        </DragOverlay>
        </DndContext>
      )}

      <LibraryPanel
        open={libraryOpen}
        focusSearchOnOpen={librarySearchFocus}
        onClose={() => setLibraryOpen(false)}
        targetLabel={
          swapping
            ? `Swapping ${swapping.id ? rowLabel(swapTarget?.segmentId ?? "", swapping.id) : "this row"}`
            : `Adding to ${activeSegment ? activeSegment.title?.trim() || segmentKindLabel(activeSegment.kind) : "a new segment"}`
        }
        kinds={libraryKinds}
        weightCatalog={exercises}
        stretchCatalog={catalogs.stretch}
        cardioCatalog={catalogs.cardio}
        ownedEquipment={equipment}
        enabledWeightExercisePackIds={enabledWeightExercisePackIds}
        weightFilter={weightFilter}
        onWeightFilterChange={setWeightFilter}
        alreadyInSegment={alreadyInSegment}
        onPick={onPick}
      />

      {previewOpen ? (
        <div className="fixed inset-0 z-40 flex items-start justify-center overflow-y-auto bg-black/40 p-4" onClick={() => setPreviewOpen(false)}>
          <div className="w-full max-w-3xl space-y-2" onClick={(e) => e.stopPropagation()}>
            <div className="flex justify-end">
              <button type="button" className="btn-ghost bg-[var(--erv-surface)] text-sm" onClick={() => setPreviewOpen(false)}>
                Close Preview
              </button>
            </div>
            <WorkoutPreviewCard
              name={name}
              segments={segments}
              exercises={exercises}
              stretchCatalog={catalogs.stretch}
              cardioCatalog={catalogs.cardio}
              cardioRoutines={cardioRoutines}
              weightLoadUnit={weightLoadUnit}
            />
          </div>
        </div>
      ) : null}
    </div>
  );
}
