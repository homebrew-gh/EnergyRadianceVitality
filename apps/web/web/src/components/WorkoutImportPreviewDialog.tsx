import { useEffect, useId, useState } from "react";
import { FieldLabel, SectionHeader } from "./FieldLabel";
import type { CardioCatalogActivity, StretchCatalogEntry, WeightCatalogExercise } from "../lib/catalog";
import {
  parseWorkoutImportEnvelope,
  previewWorkoutImport,
  workoutImportHasErrors,
  type WorkoutImportChange,
  type WorkoutImportIssue,
  type WorkoutImportWorkoutPreview,
} from "../lib/workoutImport";
import type { Workout } from "../lib/workoutTraining";
import {
  formatWeightLoadNumber,
  weightLoadUnitSuffix,
} from "../lib/weightLoadUnit";
import type { BodyWeightUnit } from "../lib/fitnessEquipment";

type WorkoutImportPreviewDialogProps = {
  current: Workout[];
  exercises: WeightCatalogExercise[];
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
  weightLoadUnit: BodyWeightUnit;
  saving: boolean;
  onClose: () => void;
  onPublish: (workouts: Workout[]) => Promise<void>;
};

const PLACEHOLDER = `{
  "ervWorkoutImportVersion": 1,
  "workouts": [
    {
      "name": "Push day",
      "segments": [
        {
          "kind": "straight_sets",
          "title": "Main work",
          "items": [
            {
              "type": "weight",
              "exerciseId": "erv-weight-exercise-bench-v1",
              "prescription": { "setCount": 3, "targetReps": 8 }
            }
          ]
        }
      ]
    }
  ]
}`;

export function WorkoutImportPreviewDialog({
  current,
  exercises,
  stretchCatalog,
  cardioCatalog,
  weightLoadUnit,
  saving,
  onClose,
  onPublish,
}: WorkoutImportPreviewDialogProps) {
  const titleId = useId();
  const fieldId = useId();
  const [raw, setRaw] = useState("");
  const [issues, setIssues] = useState<WorkoutImportIssue[]>([]);
  const [imported, setImported] = useState<Workout[]>([]);
  const [previews, setPreviews] = useState<WorkoutImportWorkoutPreview[]>([]);
  const [previewed, setPreviewed] = useState(false);
  const [publishError, setPublishError] = useState<string | null>(null);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !saving) onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose, saving]);

  const catalogs = { exercises, stretchCatalog, cardioCatalog };
  const canPublish = previewed && imported.length > 0 && !workoutImportHasErrors(issues) && !saving;

  const onPreview = () => {
    const formatWeight = (kg: number) =>
      `${formatWeightLoadNumber(kg, weightLoadUnit)} ${weightLoadUnitSuffix(weightLoadUnit)}`;
    const parsed = parseWorkoutImportEnvelope(raw, catalogs);
    setIssues(parsed.issues);
    setImported(parsed.workouts);
    setPreviews(
      parsed.workouts.length > 0
        ? previewWorkoutImport(current, parsed.workouts, catalogs, formatWeight)
        : [],
    );
    setPreviewed(true);
    setPublishError(null);
  };

  const onConfirm = async () => {
    setPublishError(null);
    try {
      await onPublish(imported);
    } catch (error) {
      setPublishError(error instanceof Error ? error.message : "Could not publish the import.");
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !saving) onClose();
      }}
    >
      <div className="card w-full max-w-4xl max-h-[90vh] overflow-y-auto p-4 space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h3 id={titleId} className="text-lg font-semibold text-heading">
              Import workouts
            </h3>
            <p className="mt-1 text-sm text-muted">
              Paste an envelope, preview the diff, then publish. A matching id replaces the saved
              workout.
            </p>
          </div>
          <button type="button" className="btn-ghost text-sm py-1 px-2" onClick={onClose} disabled={saving}>
            Close
          </button>
        </div>

        <label className="block space-y-1" htmlFor={fieldId}>
          <FieldLabel className="text-sm font-medium">Workout envelope</FieldLabel>
          <textarea
            id={fieldId}
            className="input w-full min-h-40 font-mono text-xs"
            value={raw}
            placeholder={PLACEHOLDER}
            spellCheck={false}
            onChange={(event) => {
              setRaw(event.target.value);
              setPreviewed(false);
              setIssues([]);
              setImported([]);
              setPreviews([]);
              setPublishError(null);
            }}
          />
        </label>

        {exercises.length === 0 ? (
          <p className="text-sm text-muted">
            The weight catalog has not loaded yet. Lift ids will fail validation until you sync.
          </p>
        ) : null}

        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn-primary" onClick={onPreview} disabled={saving || !raw.trim()}>
            Preview import
          </button>
          <button type="button" className="btn-ghost" onClick={() => void onConfirm()} disabled={!canPublish}>
            {saving ? "Publishing…" : "Publish import"}
          </button>
        </div>

        {previewed && issues.length > 0 ? (
          <ul className="space-y-1 text-sm">
            {issues.map((issue, index) => (
              <li
                key={`${issue.path}-${index}`}
                className={issue.severity === "error" ? "text-error" : "text-muted"}
              >
                {issue.path ? <span className="font-mono text-xs">{issue.path}: </span> : null}
                {issue.message}
              </li>
            ))}
          </ul>
        ) : null}

        {publishError ? <p className="text-sm text-error">{publishError}</p> : null}

        {previews.map((preview) => (
          <WorkoutPreviewBlock key={preview.id} preview={preview} />
        ))}
      </div>
    </div>
  );
}

function WorkoutPreviewBlock({ preview }: { preview: WorkoutImportWorkoutPreview }) {
  const replacing = preview.action === "replace";
  return (
    <section className="space-y-3 rounded-card border border-[var(--erv-outline-variant)] p-3">
      <div>
        <h4 className="font-semibold text-heading">{preview.name}</h4>
        <p className="text-sm text-muted">
          {replacing
            ? `Replaces "${preview.existingName ?? preview.name}". Publishing writes the whole imported workout.`
            : "New workout. Publishing adds it to the library."}
        </p>
      </div>
      {preview.segments.map((segment, index) => (
        <div key={`${segment.currentKey ?? ""}-${segment.incomingKey ?? ""}-${index}`} className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold text-heading">{segment.heading}</p>
            {replacing ? <ChangeChip change={segment.change} /> : null}
          </div>
          {replacing ? (
            <div className="space-y-2">
              <div className="hidden gap-3 md:grid md:grid-cols-2">
                <SectionHeader>Current workout</SectionHeader>
                <SectionHeader>Imported workout</SectionHeader>
              </div>
              {segment.rows.length === 0 ? (
                <p className="text-sm text-muted">No exercises in this segment</p>
              ) : (
                segment.rows.map((row, rowIndex) => (
                  <div
                    key={`${row.currentKey ?? ""}-${row.incomingKey ?? ""}-${rowIndex}`}
                    className="grid gap-2 md:grid-cols-2"
                  >
                    <ExerciseCell
                      caption="Current workout"
                      label={row.currentLabel}
                      detail={row.currentDetail}
                      change={row.change}
                      emptyLabel="Not in the current workout"
                      showChange={row.currentLabel != null}
                    />
                    <ExerciseCell
                      caption="Imported workout"
                      label={row.incomingLabel}
                      detail={row.incomingDetail}
                      change={row.change}
                      emptyLabel="Not in the imported workout"
                      showChange={row.incomingLabel != null}
                    />
                  </div>
                ))
              )}
            </div>
          ) : (
            <ExerciseColumn
              rows={segment.rows}
              side="incoming"
              emptyLabel="No exercises in this segment"
              showChange={false}
            />
          )}
        </div>
      ))}
    </section>
  );
}

function ExerciseCell({
  caption,
  label,
  detail,
  change,
  emptyLabel,
  showChange,
}: {
  caption?: string;
  label: string | null;
  detail: string | null;
  change: WorkoutImportChange;
  emptyLabel: string;
  showChange: boolean;
}) {
  if (!label) {
    return (
      <div className="rounded-card border border-dashed border-[var(--erv-outline-variant)] p-2">
        {caption ? (
          <SectionHeader className="mb-1 text-xs font-semibold text-muted md:hidden">{caption}</SectionHeader>
        ) : null}
        <p className="text-sm text-muted">{emptyLabel}</p>
      </div>
    );
  }
  return (
    <div className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-2">
      {caption ? (
        <SectionHeader className="mb-1 text-xs font-semibold text-muted md:hidden">{caption}</SectionHeader>
      ) : null}
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium text-heading">{label}</p>
        {showChange ? <ChangeChip change={change} /> : null}
      </div>
      {detail ? <p className="mt-1 text-xs text-muted">{detail}</p> : null}
    </div>
  );
}

function ExerciseColumn({
  rows,
  side,
  emptyLabel,
  showChange = true,
}: {
  rows: WorkoutImportWorkoutPreview["segments"][number]["rows"];
  side: "current" | "incoming";
  emptyLabel: string;
  showChange?: boolean;
}) {
  const visible = rows.filter((row) => (side === "current" ? row.currentLabel : row.incomingLabel));
  if (visible.length === 0) {
    return <p className="text-sm text-muted">{emptyLabel}</p>;
  }
  return (
    <ul className="space-y-2">
      {visible.map((row, index) => {
        const label = side === "current" ? row.currentLabel : row.incomingLabel;
        const detail = side === "current" ? row.currentDetail : row.incomingDetail;
        const key = side === "current" ? row.currentKey : row.incomingKey;
        return (
          <li
            key={`${key ?? "row"}-${index}`}
            className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-2"
          >
            <div className="flex items-start justify-between gap-2">
              <p className="text-sm font-medium text-heading">{label}</p>
              {showChange ? <ChangeChip change={row.change} /> : null}
            </div>
            {detail ? <p className="mt-1 text-xs text-muted">{detail}</p> : null}
          </li>
        );
      })}
    </ul>
  );
}

function ChangeChip({ change }: { change: WorkoutImportChange }) {
  if (change === "same") return null;
  const label =
    change === "added" ? "Added" : change === "removed" ? "Removed" : "Changed";
  const className =
    change === "removed"
      ? "text-error"
      : "text-heading bg-[var(--erv-primary-container)]/50";
  return (
    <span className={`shrink-0 rounded-pill px-2 py-0.5 text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
