import { Fragment, useState } from "react";
import clsx from "clsx";
import type { BodyWeightUnit } from "../../lib/fitnessEquipment";
import type { PhoneWarning } from "../../lib/phoneCompatibility";
import type { TrainingSnapshot } from "../../lib/trainingSnapshot";
import { exerciseLabel, type WeightExercise } from "../../lib/weightTraining";
import { weightLoadUnitSuffix } from "../../lib/weightLoadUnit";
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
  type WorkoutItem,
  type WorkoutSegment,
  type WorkoutWeightPrescription,
} from "../../lib/workoutTraining";
import { FieldLabel } from "../FieldLabel";
import { PlateLoadHint } from "../PlateLoadHint";
import { WeightPrescriptionFields } from "../WorkoutSegmentEditor";
import { CellInput } from "./CellInput";

type WeightItem = Extract<WorkoutItem, { type: "weight" }>;

/** Lifting segments get the table; the rest still use the older editor until step 4. */
export function isLiftingSegment(segment: WorkoutSegment): boolean {
  return segment.kind === "straight_sets" || segment.kind === "superset" || segment.kind === "circuit";
}

function fallbackExercise(id: string): WeightExercise {
  return { id, name: id, muscleGroup: "", pushOrPull: "push", equipment: "" };
}

function ClockInput({
  label,
  seconds,
  onSeconds,
}: {
  label: string;
  seconds: number | null | undefined;
  onSeconds: (next: number | null) => void;
}) {
  return (
    <label className="block space-y-1 text-xs">
      <FieldLabel>{label}</FieldLabel>
      <div className="rounded-lg border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]">
        <CellInput
          value={formatClock(seconds)}
          placeholder="0:00"
          ariaLabel={label}
          onCommit={(text) => {
            const next = parseClock(text);
            if (next !== undefined) onSeconds(next);
          }}
        />
      </div>
    </label>
  );
}

function Cell({
  field,
  label,
  onCommit,
  onOpenDetails,
  inputMode = "numeric",
}: {
  field: RowField;
  label: string;
  onCommit: (text: string) => void;
  onOpenDetails: () => void;
  inputMode?: "numeric" | "decimal" | "text";
}) {
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
      placeholder={field.hint ? "0:00" : "–"}
      inputMode={inputMode}
      onCommit={onCommit}
    />
  );
}

export function LiftSegmentCard({
  segment,
  position,
  active,
  exercises,
  snapshot,
  weightLoadUnit,
  warnings,
  onSelect,
  onChange,
  onRemove,
  onMoveUp,
  onMoveDown,
  onOpenLibrary,
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
  onRemove: () => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
  onOpenLibrary: () => void;
}) {
  const items = segmentItems(segment);
  const [openDetails, setOpenDetails] = useState<Set<string>>(() => new Set());
  const [showSettings, setShowSettings] = useState(false);
  const roundBased = segment.kind === "circuit" || segment.kind === "superset";
  const restPolicy = { ...defaultRestPolicy(), ...segment.restPolicy };
  const suffix = weightLoadUnitSuffix(weightLoadUnit);
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
  const removeItem = (index: number) =>
    onChange({ ...segment, items: items.filter((_, i) => i !== index) }, true);
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
              <th className="w-8 px-2 py-2 font-medium">#</th>
              <th className="px-2 py-2 font-medium">Exercise</th>
              <th className="w-16 px-2 py-2 font-medium">Sets</th>
              <th className="w-24 px-2 py-2 font-medium">Reps</th>
              <th className="w-24 px-2 py-2 font-medium">Weight ({suffix})</th>
              <th className="w-16 px-2 py-2 font-medium">RIR</th>
              <th className="w-20 px-2 py-2 font-medium">Rest</th>
              <th className="w-28 px-2 py-2" aria-label="Row actions" />
            </tr>
          </thead>
          <tbody>
            {items.map((item, index) => {
              const rowKey = item.id ?? `${segment.id}:${index}`;
              const rowWarnings = itemWarnings(index);
              if (item.type === "note") {
                return (
                  <tr key={rowKey} className="border-t border-[var(--erv-outline-variant)]">
                    <td className="px-2 py-1 text-xs text-muted">{index + 1}</td>
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
                      <button type="button" tabIndex={-1} className="text-xs text-muted hover:text-[var(--erv-error)]" onClick={() => removeItem(index)}>
                        Remove
                      </button>
                    </td>
                  </tr>
                );
              }
              if (item.type === "rest") {
                return (
                  <tr key={rowKey} className="border-t border-[var(--erv-outline-variant)]">
                    <td className="px-2 py-1 text-xs text-muted">{index + 1}</td>
                    <td className="px-2 py-1 text-muted">Rest</td>
                    <td colSpan={4} />
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
                    <td className="px-2 py-1 text-right">
                      <button type="button" tabIndex={-1} className="text-xs text-muted hover:text-[var(--erv-error)]" onClick={() => removeItem(index)}>
                        Remove
                      </button>
                    </td>
                  </tr>
                );
              }
              if (item.type !== "weight") {
                return (
                  <tr key={rowKey} className="border-t border-[var(--erv-outline-variant)]">
                    <td className="px-2 py-1 text-xs text-muted">{index + 1}</td>
                    <td colSpan={7} className="px-2 py-1.5 text-muted">
                      {item.type === "cardio" ? "Cardio" : "Mobility"} item. Edit it in the old editor for now.
                    </td>
                  </tr>
                );
              }
              const exercise = exerciseFor(item.exerciseId);
              const style = exerciseSetLoggingStyle(exercise ?? fallbackExercise(item.exerciseId));
              const supportsWeight = exerciseSupportsTargetWeight(exercise ?? fallbackExercise(item.exerciseId));
              const p = item.prescription ?? {};
              const detailsOpen = openDetails.has(rowKey);
              const weight = weightField(p, weightLoadUnit);
              const name = item.title?.trim() || exerciseLabel(item.exerciseId, exercises);
              return (
                <Fragment key={rowKey}>
                  <tr className="border-t border-[var(--erv-outline-variant)] align-top">
                    <td className="px-2 py-2.5 text-xs text-muted">{index + 1}</td>
                    <td className="px-2 py-2">
                      <span className="block font-medium text-heading">{name}</span>
                      {exercise?.equipment ? <span className="block text-xs text-muted">{exercise.equipment}</span> : null}
                      {(item.alternativeExerciseIds ?? []).length > 0 ? (
                        <span className="block text-xs text-muted">
                          or {(item.alternativeExerciseIds ?? []).map((id) => exerciseLabel(id, exercises)).join(", ")}
                        </span>
                      ) : null}
                    </td>
                    <td className="px-1 py-1">
                      <Cell
                        field={setsField(p)}
                        label={`${name} sets`}
                        onCommit={(text) => setPrescription(index, item, applySets(p, text))}
                        onOpenDetails={() => toggleDetails(rowKey)}
                      />
                    </td>
                    <td className="px-1 py-1">
                      <Cell
                        field={repsField(p, style)}
                        label={`${name} reps`}
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
                        onCommit={(text) => setPrescription(index, item, applyRir(p, text))}
                        onOpenDetails={() => toggleDetails(rowKey)}
                      />
                    </td>
                    <td className="px-1 py-1">
                      <Cell
                        field={restField(p)}
                        label={`${name} rest between sets`}
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
                      <button
                        type="button"
                        tabIndex={-1}
                        className="text-muted hover:text-[var(--erv-error)]"
                        onClick={() => removeItem(index)}
                        title="Remove (Ctrl+Z brings it back)"
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
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
        </table>
      </div>

      {segmentWarnings.length > 0 ? (
        <div className="px-4 pb-2 text-xs text-[var(--erv-error)]">
          {segmentWarnings.map((w) => (
            <p key={w.key}>Phone: {w.message}</p>
          ))}
        </div>
      ) : null}

      <footer className="flex flex-wrap items-center gap-2 border-t border-[var(--erv-outline-variant)] px-4 py-2">
        <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={onOpenLibrary}>
          + Add Exercise
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
          <span className="text-xs text-muted">Empty. Press / or + Add Exercise to pick from your library.</span>
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
