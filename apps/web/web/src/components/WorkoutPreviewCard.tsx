import { useMemo } from "react";
import { SectionHeader } from "./FieldLabel";
import type {
  CardioCatalogActivity,
  StretchCatalogEntry,
  WeightCatalogExercise,
} from "../lib/catalog";
import type { CardioRoutine } from "../lib/cardioTraining";
import {
  buildWorkoutPreview,
  formatDuration,
} from "../lib/workoutPreview";
import type { WorkoutSegment } from "../lib/workoutTraining";

type WorkoutPreviewCardProps = {
  name: string;
  segments: WorkoutSegment[];
  exercises: WeightCatalogExercise[];
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
  cardioRoutines: CardioRoutine[];
};

export function WorkoutPreviewCard({
  name,
  segments,
  exercises,
  stretchCatalog,
  cardioCatalog,
  cardioRoutines,
}: WorkoutPreviewCardProps) {
  const preview = useMemo(
    () =>
      buildWorkoutPreview(segments, {
        exercises,
        stretchCatalog,
        cardioCatalog,
        cardioRoutines,
      }),
    [cardioCatalog, cardioRoutines, exercises, segments, stretchCatalog],
  );

  const hasDraft = preview.segmentCount > 0;
  const restMinutes = Math.round(preview.restSeconds / 60);

  return (
    <section className="rounded-[18px] border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-4 space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <SectionHeader>Workout preview</SectionHeader>
          <h4 className="mt-1 text-lg font-semibold text-heading">
            {name.trim() || "Untitled Session"}
          </h4>
          <p className="text-sm text-muted">
            Android-style read-through with an estimated time budget before you publish.
          </p>
        </div>
        <div className="rounded-[18px] bg-[var(--erv-primary-container)] px-4 py-3 text-right">
          <p className="text-xs text-muted">Estimated Duration</p>
          <p className="text-2xl font-bold text-heading tabular-nums">
            {formatDuration(preview.estimatedSeconds)}
          </p>
        </div>
      </div>

      <div className="grid gap-2 sm:grid-cols-4">
        <PreviewMetric label="Segments" value={String(preview.segmentCount)} />
        <PreviewMetric label="Items" value={String(preview.itemCount)} />
        <PreviewMetric
          label="Training Mix"
          value={`${preview.strengthItemCount}/${preview.cardioItemCount}/${preview.mobilityItemCount}`}
          detail="Lift / cardio / mobility"
        />
        <PreviewMetric
          label="Rest Budget"
          value={restMinutes > 0 ? `${restMinutes} min` : "Light"}
        />
      </div>

      {!hasDraft ? (
        <div className="rounded-card border border-dashed border-[var(--erv-outline-variant)] p-4 text-sm text-muted">
          Add a template or segment to see the live preview and duration estimate.
        </div>
      ) : (
        <ol className="space-y-3">
          {preview.segments.map((segment, index) => (
            <li
              key={segment.key}
              className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-surface)]/70 p-3"
            >
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <div>
                  <p className="text-sm font-semibold text-heading">
                    {index + 1}. {segment.title}
                  </p>
                  <p className="text-xs text-muted">
                    {segment.kindLabel}
                    {segment.rounds != null ? ` · ${segment.rounds} rounds` : ""}
                    {` · ${segment.itemCount} item${segment.itemCount === 1 ? "" : "s"}`}
                  </p>
                </div>
                <span className="sun-chip">{formatDuration(segment.estimatedSeconds)}</span>
              </div>

              {segment.items.length > 0 ? (
                <ul className="mt-3 space-y-2">
                  {segment.items.map((item) => (
                    <li
                      key={item.key}
                      className="grid gap-2 rounded-card bg-[var(--erv-input-bg)] px-3 py-2 text-sm sm:grid-cols-[5rem_1fr_auto]"
                    >
                      <span className="text-xs font-semibold text-muted">{item.kind}</span>
                      <span className="min-w-0">
                        <span className="block truncate font-medium text-heading">{item.title}</span>
                        <span className="block truncate text-xs text-muted">{item.detail}</span>
                      </span>
                      <span className="text-xs font-semibold text-muted">
                        {formatDuration(item.estimatedSeconds)}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-3 rounded-card border border-dashed border-[var(--erv-outline-variant)] p-3 text-sm text-muted">
                  Select this segment, then add moves from the library.
                </p>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function PreviewMetric({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail?: string;
}) {
  return (
    <div className="metric-card">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-xl font-bold text-heading tabular-nums">{value}</p>
      {detail ? <p className="text-[11px] text-muted">{detail}</p> : null}
    </div>
  );
}
