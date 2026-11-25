import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import clsx from "clsx";
import { PageHeader } from "../components/PageHeader";
import { RoutineFormAlerts } from "../components/RoutineFormAlerts";
import { WorkoutImportPreviewDialog } from "../components/WorkoutImportPreviewDialog";
import { RowMenu, type RowMenuAction } from "../components/workoutEditor/RowMenu";
import { useWorkoutDrafts } from "../hooks/useWorkoutDrafts";
import { useTraining } from "../lib/trainingData";
import { useTrainingHistory } from "../lib/trainingHistoryData";
import { useTrainingProfile } from "../lib/trainingProfileData";
import { buildTrainingSnapshot, snapshotHasData } from "../lib/trainingSnapshot";
import { useWeightLoadUnit } from "../lib/weightLoadUnit";
import {
  workoutLibraryRows,
  workoutPublishStateLabel,
  type WorkoutLibraryRow,
} from "../lib/workoutDraftStorage";
import { mergeImportedWorkouts } from "../lib/workoutImport";
import { duplicateWorkoutWithProgression } from "../lib/workoutPrescriptionHints";
import { buildWorkoutPreview, formatDuration } from "../lib/workoutPreview";
import { duplicateWorkout, segmentItems, segmentKindLabel, type Workout } from "../lib/workoutTraining";

function formatPublishedAt(epochSeconds: number | undefined): string {
  if (!epochSeconds) return "";
  return new Date(epochSeconds * 1000).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function stateDetail(row: WorkoutLibraryRow): string {
  switch (row.state) {
    case "draft":
      return "Not on your phone yet";
    case "unpublished_changes":
      return "Your phone has the older version";
    case "published":
      return `Published ${formatPublishedAt(row.published?.lastModifiedEpochSeconds)}`;
  }
}

/** The row as a workout: this browser's version when it has unpublished changes. */
function rowWorkout(row: WorkoutLibraryRow): Workout {
  return { ...(row.published ?? { id: row.id }), id: row.id, name: row.name, segments: row.segments };
}

/**
 * /app/workouts: every workout, published or still a draft in this browser, with its publish
 * state. A workout opens in the editor; copies start as drafts there too.
 */
export function WorkoutsLibraryPage() {
  const navigate = useNavigate();
  const { workouts, exercises, catalogs, cardioRoutines, loading, saving, error, saveWorkouts, reload } = useTraining();
  const { weightLogs, cardioLogs, lastLoadedAt } = useTrainingHistory();
  const { profile } = useTrainingProfile();
  const [weightLoadUnit] = useWeightLoadUnit();
  const { drafts, updateDrafts, removeDraft } = useWorkoutDrafts();
  const [query, setQuery] = useState("");
  const [importOpen, setImportOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const snapshot = useMemo(
    () => buildTrainingSnapshot({ weightLogs, cardioLogs, exercises, computedAtMs: lastLoadedAt ?? Date.now() }),
    [weightLogs, cardioLogs, exercises, lastLoadedAt],
  );
  const canProgress = weightLogs.length > 0 || snapshotHasData(snapshot);

  const rows = useMemo(() => workoutLibraryRows(workouts, drafts), [workouts, drafts]);
  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? rows.filter((row) => row.name.toLowerCase().includes(q)) : rows;
  }, [rows, query]);

  const minutes = (row: WorkoutLibraryRow) =>
    formatDuration(
      buildWorkoutPreview(row.segments, {
        exercises,
        stretchCatalog: catalogs.stretch,
        cardioCatalog: catalogs.cardio,
        cardioRoutines,
      }).estimatedSeconds,
    );

  /** A copy starts as a draft in this browser and opens in the editor; nothing is published yet. */
  const openCopy = (copy: Workout) => {
    updateDrafts((prev) => ({
      ...prev,
      [copy.id]: { id: copy.id, name: copy.name, segments: copy.segments, savedAtMs: Date.now() },
    }));
    navigate(`/app/workouts/${copy.id}/edit`);
  };

  const onDelete = async (row: WorkoutLibraryRow) => {
    const label = row.name.trim() || "Untitled workout";
    setFormError(null);
    setSuccess(null);
    if (!row.published) {
      if (!window.confirm(`Delete the draft "${label}"? It was never published.`)) return;
      removeDraft(row.id);
      setSuccess(`Deleted the draft "${label}".`);
      return;
    }
    if (!window.confirm(`Delete "${row.published.name}"? It is removed from your phone on its next sync.`)) return;
    try {
      await saveWorkouts(workouts.filter((w) => w.id !== row.id));
      removeDraft(row.id);
      setSuccess(`Deleted "${row.published.name}".`);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Delete failed.");
    }
  };

  const actionsFor = (row: WorkoutLibraryRow): RowMenuAction[] => [
    { label: "Duplicate", onSelect: () => openCopy(duplicateWorkout(rowWorkout(row))) },
    ...(canProgress
      ? [
          {
            label: "Duplicate With Progress",
            onSelect: () =>
              openCopy(
                duplicateWorkoutWithProgression(rowWorkout(row), {
                  snapshot,
                  weightLogs,
                  profile,
                  computedAtMs: lastLoadedAt ?? Date.now(),
                  weightLoadUnit,
                }),
              ),
          },
        ]
      : []),
    { label: row.published ? "Delete" : "Delete Draft", onSelect: () => void onDelete(row), danger: true },
  ];

  return (
    <div className="space-y-5">
      <PageHeader
        title="Workouts"
        description="Build a workout and publish it. Your phone picks it up on its next sync, ready to run."
        actions={
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn-ghost text-sm" onClick={() => void reload(true)} disabled={loading}>
              Refresh
            </button>
            <button type="button" className="btn-ghost text-sm" onClick={() => setImportOpen(true)} disabled={saving}>
              Import
            </button>
            <Link to="/app/workouts/new" className="btn-primary text-sm">
              New Workout
            </Link>
          </div>
        }
      />

      {catalogs.weight.length === 0 && catalogs.stretch.length === 0 ? (
        <p className="card p-3 text-sm text-muted">
          Sync ERV on your phone to load full catalogs, or{" "}
          <Link to="/app/exercises" className="text-heading underline">
            edit the catalog
          </Link>
          . Cardio activities are available offline from built-in defaults.
        </p>
      ) : null}

      <RoutineFormAlerts error={error} formError={formError} success={success} />

      <section className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-[var(--erv-outline-variant)] px-4 py-3">
          <input
            className="input max-w-xs"
            value={query}
            placeholder="Search workouts"
            aria-label="Search workouts"
            onChange={(e) => setQuery(e.target.value)}
          />
          <span className="text-xs text-muted">
            {rows.length === 1 ? "1 workout" : `${rows.length} workouts`}
            {rows.some((row) => row.state !== "published")
              ? ` · ${rows.filter((row) => row.state !== "published").length} not published`
              : ""}
          </span>
        </div>
        {loading && rows.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted">Loading workouts…</p>
        ) : rows.length === 0 ? (
          <div className="space-y-3 px-4 py-8 text-center">
            <p className="font-semibold text-heading">No workouts yet</p>
            <p className="text-sm text-muted">Build your first one. It stays a draft in this browser until you publish it.</p>
            <Link to="/app/workouts/new" className="btn-primary inline-block text-sm">
              New Workout
            </Link>
          </div>
        ) : shown.length === 0 ? (
          <p className="px-4 py-6 text-sm text-muted">No workout matches “{query.trim()}”.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-muted">
                <th className="px-4 py-2 font-medium">Workout</th>
                <th className="w-28 px-2 py-2 font-medium">Time</th>
                <th className="w-64 px-2 py-2 font-medium">State</th>
                <th className="w-24 px-2 py-2" aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {shown.map((row) => (
                <tr
                  key={row.id}
                  className="cursor-pointer border-t border-[var(--erv-outline-variant)] align-top hover:bg-[var(--erv-surface-variant)]/40"
                  onClick={(e) => {
                    if ((e.target as HTMLElement).closest("button, a")) return;
                    navigate(`/app/workouts/${row.id}/edit`);
                  }}
                >
                  <td className="px-4 py-3">
                    <Link to={`/app/workouts/${row.id}/edit`} className="font-semibold text-heading hover:underline">
                      {row.name.trim() || "Untitled workout"}
                    </Link>
                    <span className="block text-xs text-muted">
                      {row.segments.length === 0
                        ? "Empty"
                        : row.segments.map((s) => `${s.title?.trim() || segmentKindLabel(s.kind)} (${segmentItems(s).length})`).join(" · ")}
                    </span>
                  </td>
                  <td className="px-2 py-3 text-muted">{minutes(row)}</td>
                  <td className="px-2 py-3">
                    <span
                      className={clsx(
                        "rounded-pill px-2 py-0.5 text-xs font-semibold",
                        row.state === "published"
                          ? "bg-[var(--erv-primary-container)] text-heading"
                          : "border border-[var(--erv-error)] text-[var(--erv-error)]",
                      )}
                    >
                      {workoutPublishStateLabel(row.state)}
                    </span>
                    <span className={clsx("mt-1 block text-xs", row.state === "published" ? "text-muted" : "text-[var(--erv-error)]")}>
                      {stateDetail(row)}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-2 py-3 text-right">
                    <Link to={`/app/workouts/${row.id}/edit`} className="mr-1 text-xs text-muted hover:text-heading">
                      Open
                    </Link>
                    <RowMenu label={`${row.name.trim() || "Workout"} actions`} actions={actionsFor(row)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {importOpen ? (
        <WorkoutImportPreviewDialog
          current={workouts}
          exercises={exercises}
          stretchCatalog={catalogs.stretch}
          cardioCatalog={catalogs.cardio}
          weightLoadUnit={weightLoadUnit}
          saving={saving}
          onClose={() => setImportOpen(false)}
          onPublish={async (imported) => {
            await saveWorkouts(mergeImportedWorkouts(workouts, imported));
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
    </div>
  );
}
