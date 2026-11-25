import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { Link } from "react-router-dom";
import type { LibraryItemKind } from "../components/LibrarySidebar";
import { RoutineBuilderLayout } from "../components/RoutineBuilderLayout";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { ReorderableList } from "../components/ReorderableList";
import { SavedRoutinesPanel } from "../components/SavedRoutinesPanel";
import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { WorkoutImportPreviewDialog } from "../components/WorkoutImportPreviewDialog";
import { mergeImportedWorkouts } from "../lib/workoutImport";
import { WorkoutPreviewCard } from "../components/WorkoutPreviewCard";
import { WorkoutComposerDock } from "../components/WorkoutComposerDock";
import { WorkoutSegmentEditor } from "../components/WorkoutSegmentEditor";
import { useEquipment } from "../lib/equipmentData";
import { useTrainingProfile } from "../lib/trainingProfileData";
import { useTrainingHistory } from "../lib/trainingHistoryData";
import { useTraining } from "../lib/trainingData";
import { buildTrainingSnapshot, snapshotHasData } from "../lib/trainingSnapshot";
import type { WeightExercisePickerFilter } from "../lib/weightExerciseAvailability";
import {
  formatWeightLoadNumber,
  useWeightLoadUnit,
  weightLoadUnitSuffix,
} from "../lib/weightLoadUnit";
import { useAuth } from "../lib/auth";
import { useUnsavedChangesWarning } from "../hooks/useUnsavedChangesWarning";
import {
  clearStoredWorkoutDraft,
  readStoredWorkoutDraft,
  readWorkoutDrafts,
  workoutDraftFingerprint,
  workoutLibraryRows,
  workoutPublishStateLabel,
  writeStoredWorkoutDraft,
  writeWorkoutDrafts,
  type WorkoutDrafts,
  type WorkoutLibraryRow,
  type WorkoutPublishState,
} from "../lib/workoutDraftStorage";
import {
  applyBaselineLoadsToWorkout,
  applyTargetWeightToPrescription,
  duplicateWorkoutWithProgression,
  progressionIncrementKg,
  suggestedTargetWeightKg,
} from "../lib/workoutPrescriptionHints";
import {
  defaultCardioModeForSegment,
  defaultRestPolicy,
  defaultSegmentTitle,
  newCardioItem,
  newMobilityItem,
  newWeightItem,
  segmentIsEmpty,
  segmentItems,
  segmentKindHint,
  segmentKindLabel,
  segmentLibraryKinds,
  upsertWorkout,
  duplicateWorkout,
  weightItems,
  type Workout,
  type WorkoutSegment,
  type WorkoutSegmentKind,
} from "../lib/workoutTraining";
import {
  instantiateSegmentTemplate,
  WORKOUT_SEGMENT_TEMPLATES,
} from "../lib/segmentTemplates";
import { PageHeader } from "../components/PageHeader";
import {
  canRedo,
  canUndo,
  initialWorkoutEditorState,
  withStableIds,
  workoutEditorReducer,
} from "../lib/workoutEditor";

const WORKOUT_COMPOSER_FORM_ID = "workout-composer-form";

function newSegment(kind: WorkoutSegmentKind): WorkoutSegment {
  const base: WorkoutSegment = {
    id: crypto.randomUUID(),
    kind,
    title: defaultSegmentTitle(kind),
    items: [],
  };
  if (kind === "circuit" || kind === "superset") {
    return {
      ...base,
      rounds: 3,
      restPolicy: defaultRestPolicy(),
    };
  }
  return base;
}

const EMPTY_DRAFT_FINGERPRINT = workoutDraftFingerprint("", []);
const UNPUBLISHED_DRAFT_MESSAGE =
  "This workout has changes that are not published yet. They stay in this browser, but your phone won't see them until you publish.";

function copySegments(segments: WorkoutSegment[]): WorkoutSegment[] {
  return segments.map((segment) => ({ ...segment, items: [...(segment.items ?? [])] }));
}

function formatPublishedAt(epochSeconds: number | undefined): string {
  if (!epochSeconds) return "";
  return new Date(epochSeconds * 1000).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function publishStateChipClass(state: WorkoutPublishState): string {
  return state === "published"
    ? "rounded-pill bg-[var(--erv-primary-container)] px-2 py-0.5 text-[11px] font-semibold text-heading"
    : "rounded-pill border border-[var(--erv-error)] px-2 py-0.5 text-[11px] font-semibold text-[var(--erv-error)]";
}

export function WorkoutsTab() {
  const {
    workouts,
    exercises,
    catalogs,
    cardioRoutines,
    loading,
    saving,
    error,
    lastEventId,
    reload,
    saveWorkouts,
  } = useTraining();
  const { profile } = useTrainingProfile();
  const { gymMembership, equipment, enabledWeightExercisePackIds } = useEquipment();
  const { weightLogs, cardioLogs, lastLoadedAt } = useTrainingHistory();
  const [weightLoadUnit] = useWeightLoadUnit();
  const { status: authStatus } = useAuth();
  const draftAccountKey = authStatus?.npub ?? "default";

  const snapshot = useMemo(
    () =>
      buildTrainingSnapshot({
        weightLogs,
        cardioLogs,
        exercises,
        computedAtMs: lastLoadedAt ?? Date.now(),
      }),
    [weightLogs, cardioLogs, exercises, lastLoadedAt],
  );
  const hasBaseline = snapshotHasData(snapshot);
  const progressIncrementKg = progressionIncrementKg(profile.progressionStyle, weightLoadUnit);
  const progressIncrementLabel = `+${formatWeightLoadNumber(progressIncrementKg, weightLoadUnit)} ${weightLoadUnitSuffix(weightLoadUnit)}`;

  /** Published workout open in the editor, or null for a workout that was never published. */
  const [editingId, setEditingId] = useState<string | null>(null);
  /** Id a never-published workout's draft is kept under; it becomes the workout id on publish. */
  const [newDraftId, setNewDraftId] = useState<string>(() => crypto.randomUUID());
  const draftId = editingId ?? newDraftId;
  // Unpublished versions of every workout changed in this browser, tied to the account they belong to.
  const [draftStore, setDraftStore] = useState<{ account: string; drafts: WorkoutDrafts }>(() => ({
    account: draftAccountKey,
    drafts: readWorkoutDrafts(draftAccountKey),
  }));
  const drafts = draftStore.account === draftAccountKey ? draftStore.drafts : {};
  const updateDrafts = (change: (prev: WorkoutDrafts) => WorkoutDrafts) =>
    setDraftStore((prev) => ({ account: prev.account, drafts: change(prev.drafts) }));
  const removeDraft = (id: string) =>
    updateDrafts((prev) => {
      if (!(id in prev)) return prev;
      const next = { ...prev };
      delete next[id];
      return next;
    });
  // The open draft: its name and segments, the segment the library adds to, and undo history.
  const [editor, dispatch] = useReducer(workoutEditorReducer, undefined, () =>
    initialWorkoutEditorState(),
  );
  const { name, segments } = editor.draft;
  const setName = (next: string) => dispatch({ type: "rename", name: next, atMs: Date.now() });
  /** Open [nextSegments] as a fresh draft (no undo history); returns them with ids filled in. */
  const loadDraft = useCallback((nextName: string, nextSegments: WorkoutSegment[], scope: string) => {
    const withIds = withStableIds(copySegments(nextSegments), scope);
    dispatch({ type: "load", name: nextName, segments: withIds });
    return withIds;
  }, []);
  const [importOpen, setImportOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  /** Short note shown in the composer when a library pick was not added. */
  const [pickNotice, setPickNotice] = useState<string | null>(null);
  const [weightPickerFilter, setWeightPickerFilter] =
    useState<WeightExercisePickerFilter>("ALL");
  const alertsRef = useRef<HTMLDivElement>(null);
  /** What the draft looked like when it was last opened, published or reset. */
  const [draftBaseline, setDraftBaseline] = useState(EMPTY_DRAFT_FINGERPRINT);
  const [draftRestored, setDraftRestored] = useState(false);
  const draftDirty = workoutDraftFingerprint(name, segments) !== draftBaseline;
  useUnsavedChangesWarning(draftDirty, UNPUBLISHED_DRAFT_MESSAGE);

  useEffect(() => {
    setDraftStore((prev) =>
      prev.account === draftAccountKey
        ? prev
        : { account: draftAccountKey, drafts: readWorkoutDrafts(draftAccountKey) },
    );
  }, [draftAccountKey]);

  useEffect(() => {
    writeWorkoutDrafts(draftStore.account, draftStore.drafts);
  }, [draftStore]);

  // Bring back an unpublished draft kept in this browser (after a reload or closed tab).
  useEffect(() => {
    setDraftRestored(false);
    const stored = readStoredWorkoutDraft(draftAccountKey);
    if (stored) {
      setEditingId(stored.editingId);
      if (stored.editingId == null && stored.draftId) setNewDraftId(stored.draftId);
      loadDraft(stored.name, stored.segments, stored.editingId ?? stored.draftId ?? "draft");
      setDraftBaseline(stored.baseline);
      setSuccess("Restored your unpublished workout draft from this browser.");
    }
    setDraftRestored(true);
  }, [draftAccountKey, loadDraft]);

  // Keep the draft in this browser while it has unpublished changes: the open one (to reopen it
  // after a reload) and in the drafts list (so switching workouts keeps it).
  useEffect(() => {
    if (!draftRestored) return;
    if (!draftDirty) {
      clearStoredWorkoutDraft(draftAccountKey);
      removeDraft(draftId);
      return;
    }
    const savedAtMs = Date.now();
    writeStoredWorkoutDraft(draftAccountKey, {
      editingId,
      draftId,
      name,
      segments,
      baseline: draftBaseline,
      savedAtMs,
    });
    updateDrafts((prev) => ({ ...prev, [draftId]: { id: draftId, name, segments, savedAtMs } }));
  }, [draftRestored, draftDirty, draftAccountKey, editingId, draftId, name, segments, draftBaseline]);

  const libraryRows = useMemo(() => workoutLibraryRows(workouts, drafts), [workouts, drafts]);
  const publishedWorkout = editingId ? workouts.find((w) => w.id === editingId) ?? null : null;
  const publishState: WorkoutPublishState =
    publishedWorkout == null ? "draft" : draftDirty ? "unpublished_changes" : "published";
  const hasDraftContent = name.trim().length > 0 || segments.length > 0;

  const catalogExercises = exercises;
  const activeSegment =
    segments.find((segment) => segment.id === editor.activeSegmentId) ?? null;
  const catalogPickEnabled = activeSegment != null;
  const pulseSegmentTypes = segments.length === 0;
  const libraryKinds = useMemo((): LibraryItemKind[] => {
    if (!activeSegment) return ["weight"];
    return segmentLibraryKinds(activeSegment.kind);
  }, [activeSegment?.kind]);
  const selectionKind: LibraryItemKind | undefined =
    libraryKinds.length === 1 ? libraryKinds[0] : undefined;

  const activeWeightIds = useMemo(
    () => new Set(activeSegment ? weightItems(activeSegment).map((i) => i.exerciseId) : []),
    [activeSegment],
  );
  const activeStretchIds = useMemo(
    () =>
      new Set(
        activeSegment
          ? segmentItems(activeSegment)
              .filter((i) => i.type === "mobility")
              .map((i) => i.mobility.catalogId)
          : [],
      ),
    [activeSegment],
  );
  const activeCardioIds = useMemo(
    () =>
      new Set(
        activeSegment
          ? segmentItems(activeSegment)
              .filter((i) => i.type === "cardio")
              .map((i) => i.cardio.activity)
          : [],
      ),
    [activeSegment],
  );

  const resetForm = () => {
    setEditingId(null);
    setNewDraftId(crypto.randomUUID());
    loadDraft("", [], "draft");
    setFormError(null);
    setDraftBaseline(EMPTY_DRAFT_FINGERPRINT);
  };

  /** Note that the open workout's edits stay as a draft when another one is opened. */
  const keptDraftNotice = (): string | null => {
    if (!draftDirty) return null;
    const label = name.trim() ? `"${name.trim()}"` : "your untitled workout";
    return `Your changes to ${label} are kept as a draft in this browser.`;
  };

  /** Close the editor and start a new workout; the open one stays in the list as a draft. */
  const startNewWorkout = () => {
    const notice = keptDraftNotice();
    resetForm();
    setSuccess(notice);
  };

  /** Open a workout from the list: its draft when this browser has one, else the published version. */
  const openWorkout = (row: WorkoutLibraryRow, notice: string | null = keptDraftNotice()) => {
    const source = row.draft ?? row.published;
    if (!source) return;
    setEditingId(row.published ? row.id : null);
    if (!row.published) setNewDraftId(row.id);
    loadDraft(source.name, source.segments, row.id);
    // Ids are filled in the same way as the draft's, so an unchanged workout is not "changed".
    setDraftBaseline(
      row.published
        ? workoutDraftFingerprint(
            row.published.name,
            withStableIds(copySegments(row.published.segments), row.id),
          )
        : EMPTY_DRAFT_FINGERPRINT,
    );
    setFormError(null);
    setSuccess(notice);
  };

  const startEdit = (row: WorkoutLibraryRow) => {
    if (row.id === draftId) return;
    openWorkout(row);
  };

  /** Throw away this browser's unpublished changes to the open workout. */
  const discardChanges = () => {
    const label = name.trim() ? `"${name.trim()}"` : "this workout";
    const detail = publishedWorkout
      ? "The published version stays on the relay and opens again."
      : "It was never published, so the draft is deleted.";
    if (!window.confirm(`Discard the unpublished changes to ${label}? ${detail}`)) return;
    removeDraft(draftId);
    clearStoredWorkoutDraft(draftAccountKey);
    if (publishedWorkout) {
      openWorkout(
        { id: publishedWorkout.id, name: publishedWorkout.name, segments: publishedWorkout.segments, state: "published", published: publishedWorkout, draft: null },
        "Discarded the unpublished changes.",
      );
    } else {
      resetForm();
      setSuccess("Deleted the draft.");
    }
  };

  const onDelete = async (row: WorkoutLibraryRow) => {
    const label = row.name.trim() || "Untitled workout";
    if (!row.published) {
      if (!window.confirm(`Delete the draft "${label}"? It was never published.`)) return;
      removeDraft(row.id);
      if (draftId === row.id) {
        clearStoredWorkoutDraft(draftAccountKey);
        resetForm();
      }
      setFormError(null);
      setSuccess(`Deleted the draft "${label}".`);
      return;
    }
    if (
      !window.confirm(
        `Delete "${row.published.name}"? This publishes an updated library to the relay.`,
      )
    ) {
      return;
    }
    setFormError(null);
    setSuccess(null);
    try {
      await saveWorkouts(workouts.filter((w) => w.id !== row.id));
      removeDraft(row.id);
      if (draftId === row.id) {
        clearStoredWorkoutDraft(draftAccountKey);
        resetForm();
      }
      setSuccess(`Deleted "${row.published.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Delete failed.");
    }
  };

  const addSegment = (kind: WorkoutSegmentKind) => {
    dispatch({ type: "addSegment", segment: newSegment(kind) });
  };

  const addSegmentTemplate = (templateId: string) => {
    const template = WORKOUT_SEGMENT_TEMPLATES.find((t) => t.id === templateId);
    if (!template) return;
    dispatch({ type: "addSegment", segment: instantiateSegmentTemplate(template) });
  };

  const updateSegment = (segmentId: string, segment: WorkoutSegment) => {
    dispatch({ type: "updateSegment", segmentId, segment, atMs: Date.now() });
  };

  const undo = () => dispatch({ type: "undo" });
  const redo = () => dispatch({ type: "redo" });
  const undoAvailable = canUndo(editor);
  const redoAvailable = canRedo(editor);

  // Ctrl+Z (Cmd+Z on a Mac) undoes the last change to the draft; Ctrl+Shift+Z or Ctrl+Y redoes.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!(event.ctrlKey || event.metaKey) || event.altKey) return;
      const key = event.key.toLowerCase();
      const isUndo = key === "z" && !event.shiftKey;
      const isRedo = (key === "z" && event.shiftKey) || key === "y";
      if (!isUndo && !isRedo) return;
      if (importOpen) return;
      event.preventDefault();
      dispatch({ type: isUndo ? "undo" : "redo" });
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [importOpen]);

  useEffect(() => {
    if (!pickNotice) return;
    const timer = window.setTimeout(() => setPickNotice(null), 6000);
    return () => window.clearTimeout(timer);
  }, [pickNotice]);

  /** A library pick is its own undo step, never merged with typing in the segment. */
  const pickIntoSegment = (segmentId: string, segment: WorkoutSegment) => {
    dispatch({ type: "updateSegment", segmentId, segment, atMs: Date.now(), separate: true });
  };

  const addCatalogPick = (kind: LibraryItemKind, id: string) => {
    setPickNotice(null);
    const segment = activeSegment;
    if (!segment?.id) {
      setFormError("Add a segment first, then pick items from the library.");
      return;
    }
    const segmentId = segment.id;

    if (kind === "weight") {
      const exercise = catalogExercises.find((ex) => ex.id === id);
      if (weightItems(segment).some((item) => item.exerciseId === id)) {
        setPickNotice(
          `${exercise?.name ?? "That exercise"} is already in this segment. Add sets to it there, or select another segment.`,
        );
        return;
      }
      const item = newWeightItem(id, segment.kind, exercise);
      const suggested = suggestedTargetWeightKg(id, snapshot, 0, weightLoadUnit);
      if (suggested != null) {
        item.prescription = applyTargetWeightToPrescription(item.prescription, suggested);
      }
      pickIntoSegment(segmentId, {
        ...segment,
        items: [...segmentItems(segment), item],
      });
      return;
    }
    if (kind === "stretch") {
      if (activeStretchIds.has(id)) {
        setPickNotice("That stretch is already in this segment.");
        return;
      }
      pickIntoSegment(segmentId, {
        ...segment,
        items: [...segmentItems(segment), newMobilityItem(id)],
      });
      return;
    }
    if (kind === "cardio") {
      if (activeCardioIds.has(id)) {
        setPickNotice("That activity is already in this segment.");
        return;
      }
      pickIntoSegment(segmentId, {
        ...segment,
        items: [
          ...segmentItems(segment),
          newCardioItem(id, defaultCardioModeForSegment(segment.kind)),
        ],
      });
    }
  };

  const onDuplicateWithProgress = async (workout: Workout) => {
    setFormError(null);
    setSuccess(null);
    try {
      const copy = duplicateWorkoutWithProgression(workout, {
        snapshot,
        weightLogs,
        profile,
        computedAtMs: lastLoadedAt ?? Date.now(),
        weightLoadUnit,
      });
      await saveWorkouts(upsertWorkout(workouts, copy));
      setSuccess(`Duplicated "${workout.name}" with progressed loads as "${copy.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Duplicate with progress failed.");
    }
  };

  const applyBaselineToEditor = (incrementKg = 0) => {
    if (!hasBaseline) return;
    const draft: Workout = {
      id: editingId ?? "draft",
      name: name.trim() || "Draft",
      segments,
      sourceLabel: "Start9",
      lastModifiedEpochSeconds: 0,
      createdAtEpochSeconds: 0,
    };
    const updated = applyBaselineLoadsToWorkout(draft, snapshot, incrementKg, weightLoadUnit);
    dispatch({ type: "replaceSegments", segments: updated.segments });
    setSuccess(
      incrementKg > 0
        ? `Applied baseline loads + ${formatWeightLoadNumber(incrementKg, weightLoadUnit)} ${weightLoadUnitSuffix(weightLoadUnit)} progress to weight items.`
        : "Applied baseline loads from your training history.",
    );
  };

  const onDuplicate = async (workout: Workout) => {
    setFormError(null);
    setSuccess(null);
    try {
      const copy = duplicateWorkout(workout);
      await saveWorkouts(upsertWorkout(workouts, copy));
      setSuccess(`Duplicated "${workout.name}" as "${copy.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Duplicate failed.");
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSuccess(null);
    const trimmed = name.trim();
    if (!trimmed) {
      setFormError("Workout name is required.");
      return;
    }
    if (segments.length === 0) {
      setFormError("Add at least one segment.");
      return;
    }
    if (segments.some((segment) => segmentIsEmpty(segment))) {
      setFormError("Each segment needs at least one item.");
      return;
    }

    const now = Math.floor(Date.now() / 1000);
    const normalizedSegments = segments.map((segment) => ({
      ...segment,
      title: segment.title?.trim() || null,
    }));
    // Editing keeps what the form does not show (description, tags, created date, source).
    const existing = editingId ? workouts.find((w) => w.id === editingId) : undefined;
    const workout: Workout = existing
      ? {
          ...existing,
          name: trimmed,
          segments: normalizedSegments,
          sourceLabel: existing.sourceLabel ?? "Start9",
          createdAtEpochSeconds: existing.createdAtEpochSeconds ?? now,
          lastModifiedEpochSeconds: now,
        }
      : {
          id: editingId ?? newDraftId,
          name: trimmed,
          segments: normalizedSegments,
          sourceLabel: "Start9",
          lastModifiedEpochSeconds: now,
          createdAtEpochSeconds: now,
        };

    try {
      const wasPublished = publishedWorkout != null;
      await saveWorkouts(upsertWorkout(workouts, workout));
      // Keep the editor open on what was just published, with nothing left unpublished.
      removeDraft(workout.id);
      clearStoredWorkoutDraft(draftAccountKey);
      setEditingId(workout.id);
      const publishedSegments = loadDraft(trimmed, normalizedSegments, workout.id);
      setDraftBaseline(workoutDraftFingerprint(trimmed, publishedSegments));
      setSuccess(
        wasPublished
          ? "Workout updated on the relay. Open ERV on your phone and sync to use it."
          : "Workout published to the relay. Open ERV on your phone and sync to use it.",
      );
      alertsRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Save failed.");
    }
  };

  const draftItemCount = segments.reduce((total, segment) => total + segmentItems(segment).length, 0);
  const activeSegmentLabel = activeSegment
    ? `${segmentKindLabel(activeSegment.kind)}${activeSegment.title ? ` - ${activeSegment.title}` : ""}`
    : "Choose a segment";
  const baselineLabel = hasBaseline ? "Baseline loads ready" : "Sync logs for load hints";
  const showComposerDock = editingId != null || hasDraftContent;
  const publishStatusText =
    publishState === "published"
      ? `Published ${formatPublishedAt(publishedWorkout?.lastModifiedEpochSeconds)}. Your phone gets it on its next sync.`
      : publishState === "unpublished_changes"
        ? `Unpublished changes, saved in this browser. Your phone still has the version published ${formatPublishedAt(publishedWorkout?.lastModifiedEpochSeconds)}.`
        : "Draft, saved in this browser. Not published, so your phone won't see it until you publish.";

  const segmentChoice = (
    kind: WorkoutSegmentKind,
    title: string,
    description: string,
    pulse = false,
  ) => (
    <button
      type="button"
      className={`rounded-card border p-3 text-left transition hover:-translate-y-0.5 hover:shadow-card ${
        pulse && pulseSegmentTypes
          ? "erv-pulse-border bg-[var(--erv-primary-container)]/45"
          : "border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]"
      }`}
      title={segmentKindHint(kind)}
      onClick={() => addSegment(kind)}
    >
      <span className="block text-sm font-semibold text-heading">{title}</span>
      <span className="mt-1 block text-xs text-muted">{description}</span>
    </button>
  );

  return (
    <div className="space-y-5">
      <PageHeader
        title="Workouts"
        description="Build a workout here and publish it. Your phone picks it up on its next sync, ready to run."
      >
        <p className="text-xs text-muted">{baselineLabel}</p>
      </PageHeader>

      {catalogs.weight.length === 0 && catalogs.stretch.length === 0 ? (
        <p className="text-sm text-muted card p-3">
          Sync ERV on your phone to load full catalogs, or{" "}
          <Link to="/app/exercises" className="underline text-heading">
            edit the catalog
          </Link>
          . Cardio activities are available offline from built-in defaults.
        </p>
      ) : null}

      <div ref={alertsRef}>
        <RoutineFormAlerts
          error={error}
          formError={formError}
          success={success}
          lastEventId={lastEventId}
        />
      </div>

      <SavedRoutinesPanel
        title="Saved workouts"
        description="Drafts stay in this browser until you publish them. Published workouts reach your phone on its next sync."
        loading={loading}
        routines={libraryRows}
        editingId={hasDraftContent || editingId != null ? draftId : null}
        onReload={() => void reload(true)}
        getId={(row) => row.id}
        getName={(row) => row.name.trim() || "Untitled workout"}
        getDetail={(row) =>
          row.segments
            .map((s) => `${segmentKindLabel(s.kind)} (${segmentItems(s).length})`)
            .join(" · ")
        }
        getBadge={(row) => (
          <span className={`ml-2 ${publishStateChipClass(row.state)}`}>
            {workoutPublishStateLabel(row.state)}
          </span>
        )}
        canDuplicate={(row) => row.published != null}
        headerActions={
          <button
            type="button"
            className="btn-ghost text-sm"
            onClick={() => setImportOpen(true)}
            disabled={saving}
          >
            Import
          </button>
        }
        onEdit={startEdit}
        onDelete={(row) => void onDelete(row)}
        onDuplicate={(row) => {
          if (row.published) void onDuplicate(row.published);
        }}
        onDuplicateWithProgress={
          weightLogs.length > 0 || hasBaseline
            ? (row) => {
                if (row.published) void onDuplicateWithProgress(row.published);
              }
            : undefined
        }
      />

      {importOpen ? (
        <WorkoutImportPreviewDialog
          current={workouts}
          exercises={catalogExercises}
          stretchCatalog={catalogs.stretch}
          cardioCatalog={catalogs.cardio}
          weightLoadUnit={weightLoadUnit}
          saving={saving}
          onClose={() => setImportOpen(false)}
          onPublish={async (imported) => {
            await saveWorkouts(mergeImportedWorkouts(workouts, imported));
            const updated = imported.find((workout) => workout.id === editingId);
            if (updated) {
              const updatedSegments = loadDraft(updated.name, updated.segments, updated.id);
              setDraftBaseline(workoutDraftFingerprint(updated.name, updatedSegments));
            }
            setFormError(null);
            setSuccess(
              imported.length === 1
                ? `Published imported workout "${imported[0]?.name ?? "workout"}".`
                : `Published ${imported.length} imported workouts.`,
            );
            setImportOpen(false);
          }}
        />
      ) : null}

      <RoutineBuilderLayout
        sidebarKinds={libraryKinds}
        selectionKind={selectionKind}
        enableWeightEquipmentFilter={libraryKinds.includes("weight")}
        gymMembership={gymMembership}
        ownedEquipment={equipment}
        enabledWeightExercisePackIds={enabledWeightExercisePackIds}
        weightPickerFilter={weightPickerFilter}
        onWeightPickerFilterChange={setWeightPickerFilter}
        selectedIds={
          libraryKinds.length === 1 && libraryKinds[0] === "weight"
            ? activeWeightIds
            : libraryKinds.length === 1 && libraryKinds[0] === "stretch"
              ? activeStretchIds
              : libraryKinds.length === 1 && libraryKinds[0] === "cardio"
                ? activeCardioIds
                : undefined
        }
        onPick={(item) => addCatalogPick(item.kind, item.id)}
        pickLabel="Add"
        pickDisabled={!catalogPickEnabled}
        weightCatalog={catalogExercises}
        stretchCatalog={catalogs.stretch}
        cardioCatalog={catalogs.cardio}
        aside={
          showComposerDock ? (
            <WorkoutComposerDock
              visible
              formId={WORKOUT_COMPOSER_FORM_ID}
              saving={saving}
              editing={publishedWorkout != null}
              draftLabel={name}
              segmentCount={segments.length}
              itemCount={draftItemCount}
              publishState={publishState}
              onDiscardChanges={draftDirty ? discardChanges : undefined}
              onNewWorkout={startNewWorkout}
            />
          ) : undefined
        }
      >
        <form
          id={WORKOUT_COMPOSER_FORM_ID}
          onSubmit={(e) => void onSubmit(e)}
          className="card w-full overflow-hidden pb-24 lg:pb-4"
        >
          <div className="border-b border-[var(--erv-outline-variant)] bg-[var(--erv-surface-variant)]/35 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="text-lg font-semibold text-heading">
                  {editingId ? "Edit Workout" : "Compose Workout"}
                </h3>
                {hasDraftContent || publishedWorkout ? (
                  <p
                    className={`mt-1 flex flex-wrap items-center gap-2 text-xs ${
                      publishState === "published" ? "text-muted" : "font-semibold text-[var(--erv-error)]"
                    }`}
                    role="status"
                  >
                    <span className={publishStateChipClass(publishState)}>
                      {workoutPublishStateLabel(publishState)}
                    </span>
                    <span>{publishStatusText}</span>
                  </p>
                ) : null}
                <p className="mt-1 text-sm text-muted">
                  Start with a template or block, select the active segment, then add items from
                  the library.
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  className="btn-ghost px-3 py-1 text-xs"
                  onClick={undo}
                  disabled={!undoAvailable}
                  title="Undo (Ctrl+Z)"
                >
                  Undo
                </button>
                <button
                  type="button"
                  className="btn-ghost px-3 py-1 text-xs"
                  onClick={redo}
                  disabled={!redoAvailable}
                  title="Redo (Ctrl+Shift+Z)"
                >
                  Redo
                </button>
                <span className="sun-chip">{activeSegmentLabel}</span>
              </div>
            </div>
            {pickNotice ? (
              <p className="mt-2 text-sm text-[var(--erv-error)]" role="status">
                {pickNotice}
              </p>
            ) : null}
          </div>

          <div className="space-y-4 p-4">

          <label className="block space-y-1">
            <FieldLabel className="text-sm font-medium">Workout name</FieldLabel>
            <input
              className="input w-full"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Full session — warm-up to cooldown"
            />
          </label>

          {hasBaseline && segments.length > 0 ? (
            <div className="flex flex-wrap gap-2 items-center">
              <span className="text-xs text-muted">Load suggestions:</span>
              <button
                type="button"
                className="btn-ghost text-xs py-1 px-2"
                onClick={() => applyBaselineToEditor(0)}
              >
                Apply baseline loads
              </button>
              <button
                type="button"
                className="btn-ghost text-xs py-1 px-2"
                onClick={() => applyBaselineToEditor(progressIncrementKg)}
                title={`Uses profile progression (${progressIncrementLabel}) or last-week session when found`}
              >
                Apply baseline + progress ({progressIncrementLabel})
              </button>
            </div>
          ) : null}

          <WorkoutPreviewCard
            name={name}
            segments={segments}
            exercises={catalogExercises}
            stretchCatalog={catalogs.stretch}
            cardioCatalog={catalogs.cardio}
            cardioRoutines={cardioRoutines}
            weightLoadUnit={weightLoadUnit}
          />

          <div className="grid gap-3 md:grid-cols-3">
            <div className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3">
              <p className="text-xs font-semibold text-heading">1. Shape The Arc</p>
              <p className="mt-1 text-xs text-muted">Use templates for common flows or build blocks manually.</p>
            </div>
            <div className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3">
              <p className="text-xs font-semibold text-heading">2. Fill The Segment</p>
              <p className="mt-1 text-xs text-muted">Pick the highlighted segment, then add catalog items.</p>
            </div>
            <div className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3">
              <p className="text-xs font-semibold text-heading">3. Publish And Run</p>
              <p className="mt-1 text-xs text-muted">Relay publish makes the workout available after Android sync.</p>
            </div>
          </div>

          <div className="space-y-4">
            <div className="space-y-2">
              <SectionHeader>Templates</SectionHeader>
              <div className="flex flex-wrap gap-2">
                {WORKOUT_SEGMENT_TEMPLATES.map((template) => (
                  <button
                    key={template.id}
                    type="button"
                    className={`btn-ghost text-sm ${pulseSegmentTypes ? "erv-pulse-border" : ""}`}
                    title={template.description}
                    onClick={() => addSegmentTemplate(template.id)}
                  >
                    {template.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <SectionHeader>Flow</SectionHeader>
              <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
                {segmentChoice("composite", "Flow Block", "Mix warm-up, rest, notes, mobility, and light cardio.", true)}
                {segmentChoice("cardio", "Cardio Block", "Steady effort or saved cardio routine prescriptions.")}
                {segmentChoice("interval", "Interval Block", "Work/recover rounds for conditioning finishers.")}
                {segmentChoice("mobility", "Mobility Block", "Cooldowns, joint prep, and flexibility work.")}
              </div>
            </div>
            <div className="space-y-2">
              <SectionHeader>Lifting</SectionHeader>
              <div className="grid gap-2 sm:grid-cols-3">
                {segmentChoice("straight_sets", "Straight Sets", "Classic exercise-by-exercise strength work.", true)}
                {segmentChoice("circuit", "Circuit", "Round-based training with shared rest rules.", true)}
                {segmentChoice("superset", "Superset", "Pair moves together for density and flow.", true)}
              </div>
            </div>

            {segments.length > 0 ? (
              <ReorderableList
                items={segments}
                getKey={(segment, index) => segment.id ?? String(index)}
                onReorder={(ordered) => dispatch({ type: "replaceSegments", segments: ordered })}
                className="space-y-3"
                renderItem={(segment, index) => (
                  <WorkoutSegmentEditor
                    segment={segment}
                    segmentIndex={index}
                    isActive={segment.id === editor.activeSegmentId}
                    catalogExercises={catalogExercises}
                    stretchCatalog={catalogs.stretch}
                    cardioCatalog={catalogs.cardio}
                    cardioRoutines={cardioRoutines}
                    trainingSnapshot={snapshot}
                    weightLoadUnit={weightLoadUnit}
                    onSelect={() => dispatch({ type: "selectSegment", segmentId: segment.id ?? null })}
                    onUpdate={(updated) => updateSegment(segment.id!, updated)}
                    onRemove={() => dispatch({ type: "removeSegment", segmentId: segment.id! })}
                    onMoveUp={() => dispatch({ type: "moveSegment", from: index, to: index - 1 })}
                    onMoveDown={() => dispatch({ type: "moveSegment", from: index, to: index + 1 })}
                    canMoveUp={index > 0}
                    canMoveDown={index < segments.length - 1}
                  />
                )}
              />
            ) : null}
          </div>

          {!showComposerDock ? (
            <div className="flex flex-wrap gap-2">
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? "Publishing…" : editingId ? "Publish update" : "Publish to relay"}
              </button>
              {draftDirty ? (
                <button type="button" className="btn-ghost" onClick={discardChanges}>
                  Discard changes
                </button>
              ) : null}
            </div>
          ) : (
            <p className="text-xs text-muted">
              Use the publish panel on the right to save this workout to your relay.
            </p>
          )}
          </div>
        </form>
      </RoutineBuilderLayout>
    </div>
  );
}
