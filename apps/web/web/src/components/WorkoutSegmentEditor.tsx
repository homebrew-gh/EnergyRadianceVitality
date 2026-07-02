import { useState } from "react";
import type { CardioCatalogActivity } from "../lib/catalog";
import type { CardioRoutine } from "../lib/cardioTraining";
import { formatWeightKg } from "../lib/trainingHistory";
import type { BodyWeightUnit } from "../lib/fitnessEquipment";
import {
  formatWeightLoadNumber,
  parseWeightInputToKg,
  weightLoadUnitSuffix,
} from "../lib/weightLoadUnit";
import type { TrainingSnapshot } from "../lib/trainingSnapshot";
import {
  applyTargetWeightToPrescription,
  suggestedTargetWeightKg,
} from "../lib/workoutPrescriptionHints";
import { stretchLabel } from "../lib/stretchTraining";
import { exerciseLabel, type WeightExercise } from "../lib/weightTraining";
import { ReorderableList } from "./ReorderableList";
import {
  defaultRestPolicy,
  ensurePrescriptionSets,
  exerciseSetLoggingStyle,
  mobilityItemSummary,
  cardioItemSummary,
  readOptionalNonNegativeInt,
  readOptionalPositiveInt,
  prescriptionSummary,
  prescriptionUsesPerSide,
  segmentAllowsInlineNotes,
  segmentItems,
  segmentKindLabel,
  syncPrescriptionSetCount,
  updateWeightItemPrescription,
  updateWorkoutItem,
  workoutItemKey,
  exerciseSupportsTargetWeight,
  type WorkoutItem,
  type WorkoutSegment,
  type WorkoutWeightPrescription,
  type WorkoutPrescriptionSet,
  type WorkoutPrescriptionSetSide,
  type WorkoutCardioLogField,
  type WeightSetLoggingStyle,
} from "../lib/workoutTraining";
import { FieldLabel, SectionHeader } from "../components/FieldLabel";

const CARDIO_LOG_FIELD_OPTIONS: { value: WorkoutCardioLogField; label: string }[] = [
  { value: "INCLINE", label: "Incline" },
  { value: "SPEED", label: "Speed" },
  { value: "DISTANCE", label: "Distance" },
  { value: "NOTES", label: "Notes" },
];

type WorkoutSegmentEditorProps = {
  segment: WorkoutSegment;
  segmentIndex: number;
  isActive: boolean;
  catalogExercises: WeightExercise[];
  stretchCatalog: { id: string; name: string }[];
  cardioCatalog: CardioCatalogActivity[];
  cardioRoutines: CardioRoutine[];
  trainingSnapshot?: TrainingSnapshot | null;
  weightLoadUnit?: BodyWeightUnit;
  onSelect: () => void;
  onUpdate: (segment: WorkoutSegment) => void;
  onRemove: () => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
  canMoveUp: boolean;
  canMoveDown: boolean;
};

function WeightPrescriptionFields({
  prescription,
  loggingStyle,
  onChange,
  compact = false,
  suggestedTargetKg,
  supportsTargetWeight = loggingStyle !== "time_only",
  weightLoadUnit = "LB",
}: {
  prescription: WorkoutWeightPrescription;
  loggingStyle: WeightSetLoggingStyle;
  onChange: (next: WorkoutWeightPrescription) => void;
  compact?: boolean;
  suggestedTargetKg?: number | null;
  supportsTargetWeight?: boolean;
  weightLoadUnit?: BodyWeightUnit;
}) {
  const [showPerSetEditor, setShowPerSetEditor] = useState(() => {
    const sets = prescription.sets ?? [];
    if (sets.length < 2) return false;
    const reps = (s: WorkoutPrescriptionSet) => s.reps ?? s.targetReps ?? null;
    const load = (s: WorkoutPrescriptionSet) => s.targetWeightKg ?? s.weightKg ?? null;
    const rir = (s: WorkoutPrescriptionSet) => s.rir ?? null;
    const first = sets[0]!;
    return sets.some(
      (s) => reps(s) !== reps(first) || load(s) !== load(first) || rir(s) !== rir(first),
    );
  });
  const gridClass = compact
    ? "grid grid-cols-2 sm:grid-cols-3 gap-2"
    : "grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2";

  const isMaxReps = prescription.mode === "max_reps";
  const timedPrescription =
    loggingStyle === "time_only" ||
    (loggingStyle === "reps_or_time" && prescription.mode === "time_based");
  const perSide = prescriptionUsesPerSide(prescription);

  const field = (
    label: string,
    value: number | null | undefined,
    onValue: (next: number | null) => void,
    opts?: { min?: number; positive?: boolean },
  ) => (
    <label className="text-xs space-y-1">
      <FieldLabel>{label}</FieldLabel>
      <input
        className="input w-full"
        type="number"
        min={opts?.min ?? 0}
        value={value ?? ""}
        onChange={(e) => {
          onValue(
            opts?.positive
              ? readOptionalPositiveInt(e.target.value, value)
              : readOptionalNonNegativeInt(e.target.value, value),
          );
        }}
      />
    </label>
  );

  const loadSuffix = weightLoadUnitSuffix(weightLoadUnit);

  const weightField = (
    label: string,
    kgValue: number | null | undefined,
    onKgValue: (next: number | null) => void,
    ghostHint?: string | null,
  ) => {
    const displayValue =
      kgValue != null ? formatWeightLoadNumber(kgValue, weightLoadUnit) : "";
    return (
      <label className="text-xs space-y-1">
        <FieldLabel>{label}</FieldLabel>
        <input
          className="input w-full"
          type="number"
          min={0}
          step="any"
          placeholder={ghostHint ?? undefined}
          value={displayValue}
          onChange={(e) => {
            const raw = e.target.value.trim();
            if (!raw) {
              onKgValue(null);
              return;
            }
            const kg = parseWeightInputToKg(raw, weightLoadUnit);
            onKgValue(kg ?? kgValue ?? null);
          }}
        />
        {ghostHint && (kgValue == null || kgValue === 0) ? (
          <span className="text-[10px] text-muted block">{ghostHint}</span>
        ) : null}
      </label>
    );
  };

  const weightGhostHint =
    suggestedTargetKg != null && (prescription.targetWeightKg == null || prescription.targetWeightKg === 0)
      ? `Baseline: ${formatWeightKg(suggestedTargetKg, weightLoadUnit)}`
      : null;

  const updateSetAt = (index: number, patch: Partial<WorkoutPrescriptionSet>) => {
    const sets = ensurePrescriptionSets(prescription);
    sets[index] = { ...sets[index], ...patch };
    onChange({ ...prescription, sets, setCount: sets.length });
  };

  const setCountValue = prescription.setCount ?? prescription.sets?.length ?? 3;

  return (
    <div className="space-y-2">
      {loggingStyle !== "time_only" ? (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className={`btn-ghost text-xs py-1 px-2 ${
              !isMaxReps && prescription.mode !== "time_based" ? "erv-pulse-border" : ""
            }`}
            onClick={() =>
              onChange({
                ...prescription,
                mode: "straight",
                durationSeconds: null,
              })
            }
          >
            Straight
          </button>
          <button
            type="button"
            className={`btn-ghost text-xs py-1 px-2 ${isMaxReps ? "erv-pulse-border" : ""}`}
            onClick={() =>
              onChange({
                ...prescription,
                mode: "max_reps",
                targetReps: null,
                repRangeMin: null,
                repRangeMax: null,
                durationSeconds: null,
              })
            }
          >
            Max reps
          </button>
          {loggingStyle === "reps_or_time" ? (
            <button
              type="button"
              className={`btn-ghost text-xs py-1 px-2 ${
                prescription.mode === "time_based" ? "erv-pulse-border" : ""
              }`}
              onClick={() =>
                onChange({
                  ...prescription,
                  mode: "time_based",
                  targetReps: null,
                  repRangeMin: null,
                  repRangeMax: null,
                  durationSeconds: prescription.durationSeconds ?? 45,
                })
              }
            >
              Time
            </button>
          ) : null}
        </div>
      ) : null}
      <div className={gridClass}>
        {field("Sets", setCountValue, (setCount) => {
          if (setCount == null) return;
          onChange(syncPrescriptionSetCount(prescription, setCount));
        }, { positive: true })}
        {timedPrescription
          ? field(
              loggingStyle === "time_only" ? "Target duration (s)" : "Target hold (s)",
              prescription.durationSeconds ?? null,
              (durationSeconds) =>
                onChange({
                  ...prescription,
                  durationSeconds,
                  mode: "time_based",
                }),
              { positive: true },
            )
          : null}
        {!timedPrescription && !isMaxReps && !perSide && !showPerSetEditor
          ? field("Target reps", prescription.targetReps ?? null, (targetReps) =>
              onChange({ ...prescription, targetReps }),
              { positive: true },
            )
          : null}
        {!timedPrescription && !isMaxReps && !perSide && !showPerSetEditor
          ? field("Reps min", prescription.repRangeMin ?? null, (repRangeMin) =>
              onChange({ ...prescription, repRangeMin }),
              { positive: true },
            )
          : null}
        {!timedPrescription && !isMaxReps && !perSide && !showPerSetEditor
          ? field("Reps max", prescription.repRangeMax ?? null, (repRangeMax) =>
              onChange({ ...prescription, repRangeMax }),
              { positive: true },
            )
          : null}
        {supportsTargetWeight && !isMaxReps && !showPerSetEditor
          ? weightField(
              `Target weight (${loadSuffix})`,
              prescription.targetWeightKg ?? null,
              (targetWeightKg) => onChange({ ...prescription, targetWeightKg }),
              weightGhostHint,
            )
          : null}
        {!timedPrescription && !isMaxReps
          ? field("Target RIR", prescription.targetRir ?? null, (targetRir) =>
              onChange({ ...prescription, targetRir }),
            )
          : null}
        {weightGhostHint ? (
          <div className="col-span-full">
            <button
              type="button"
              className="btn-ghost text-xs py-0.5 px-2"
              onClick={() =>
                onChange(
                  applyTargetWeightToPrescription(prescription, suggestedTargetKg!) ?? prescription,
                )
              }
            >
              Use baseline load ({formatWeightKg(suggestedTargetKg!, weightLoadUnit)})
            </button>
          </div>
        ) : null}
        {field(
          "Rest between sets (s)",
          prescription.restBetweenSetsSeconds ?? null,
          (restBetweenSetsSeconds) => onChange({ ...prescription, restBetweenSetsSeconds }),
        )}
        {field(
          "Rest after exercise (s)",
          prescription.restAfterExerciseSeconds ?? null,
          (restAfterExerciseSeconds) => onChange({ ...prescription, restAfterExerciseSeconds }),
        )}
      </div>
      {!timedPrescription && !isMaxReps ? (
        <div className="flex flex-wrap items-center gap-3 text-xs">
          <label className="inline-flex items-center gap-1">
            <input
              type="checkbox"
              checked={perSide}
              onChange={(e) => {
                if (!e.target.checked) {
                  const sets = (prescription.sets ?? []).map((s) => ({
                    ...s,
                    repsPerSide: null,
                    side: null,
                  }));
                  onChange({ ...prescription, sets });
                  return;
                }
                const sets = syncPrescriptionSetCount(prescription, setCountValue).sets ?? [];
                sets[0] = {
                  ...sets[0],
                  repsPerSide: sets[0]?.repsPerSide ?? prescription.targetReps ?? 10,
                  side: (sets[0]?.side as WorkoutPrescriptionSetSide | null) ?? "each",
                };
                onChange({ ...prescription, sets, setCount: sets.length });
              }}
            />
            Per-side reps
          </label>
          {!perSide ? (
            <button
              type="button"
              className="btn-ghost text-xs py-0.5 px-2"
              onClick={() => setShowPerSetEditor((v) => !v)}
            >
              {showPerSetEditor ? "Hide per-set editor" : "Edit individual sets"}
            </button>
          ) : null}
        </div>
      ) : null}
      {perSide ? (
        <div className="grid grid-cols-2 gap-2">
          {field("Reps per side", prescription.sets?.[0]?.repsPerSide ?? null, (repsPerSide) => {
            const sets = syncPrescriptionSetCount(prescription, setCountValue).sets ?? [];
            sets[0] = {
              ...sets[0],
              repsPerSide,
              side: sets[0]?.side ?? "each",
            };
            onChange({ ...prescription, sets, setCount: sets.length });
          }, { positive: true })}
          <label className="text-xs space-y-1">
            <FieldLabel>Side</FieldLabel>
            <select
              className="input w-full"
              value={prescription.sets?.[0]?.side ?? "each"}
              onChange={(e) => {
                const sets = syncPrescriptionSetCount(prescription, setCountValue).sets ?? [];
                sets[0] = {
                  ...sets[0],
                  side: e.target.value as WorkoutPrescriptionSetSide,
                };
                onChange({ ...prescription, sets, setCount: sets.length });
              }}
            >
              <option value="each">Each side</option>
              <option value="left">Left</option>
              <option value="right">Right</option>
              <option value="alternating">Alternating</option>
            </select>
          </label>
        </div>
      ) : null}
      {showPerSetEditor && !perSide && !isMaxReps && !timedPrescription ? (
        <div className="space-y-2 rounded-card border border-outline/30 p-2">
          <SectionHeader className="text-xs font-medium text-muted">
            Per-set targets
          </SectionHeader>
          {ensurePrescriptionSets(prescription).map((set, index) => (
            <div key={index} className="grid grid-cols-5 gap-2 items-end">
              <span className="text-xs text-muted pb-2">Set {index + 1}</span>
              {field("Reps", set.reps ?? set.targetReps ?? null, (reps) =>
                updateSetAt(index, { reps, targetReps: reps }),
                { positive: true },
              )}
              {weightField(
                `Weight (${loadSuffix})`,
                set.targetWeightKg ?? set.weightKg ?? null,
                (weightKg) => updateSetAt(index, { weightKg, targetWeightKg: weightKg }),
              )}
              {field("RIR", set.rir ?? null, (rir) => updateSetAt(index, { rir }))}
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function itemTypeLabel(item: WorkoutItem): string {
  switch (item.type) {
    case "weight":
      return "Lift";
    case "cardio":
      return "Cardio";
    case "mobility":
      return "Stretch";
    case "note":
      return "Note";
    case "rest":
      return "Rest";
  }
}

export function WorkoutSegmentEditor({
  segment,
  segmentIndex,
  isActive,
  catalogExercises,
  stretchCatalog,
  cardioCatalog,
  cardioRoutines,
  trainingSnapshot,
  weightLoadUnit = "LB",
  onSelect,
  onUpdate,
  onRemove,
  onMoveUp,
  onMoveDown,
  canMoveUp,
  canMoveDown,
}: WorkoutSegmentEditorProps) {
  const [expandedItemId, setExpandedItemId] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState(false);
  const isRoundRobin = segment.kind === "circuit" || segment.kind === "superset";
  const items = segmentItems(segment);
  const restPolicy = { ...defaultRestPolicy(), ...segment.restPolicy };
  const allowNotes = segmentAllowsInlineNotes(segment.kind);

  const updateRestPolicy = (patch: Partial<typeof restPolicy>) => {
    onUpdate({
      ...segment,
      restPolicy: { ...restPolicy, ...patch },
    });
  };

  const removeItem = (item: WorkoutItem) => {
    onUpdate({
      ...segment,
      items: items.filter((entry) => entry !== item),
    });
  };

  const itemTitle = (item: WorkoutItem): string => {
    switch (item.type) {
      case "weight": {
        const altCount = item.alternativeExerciseIds?.length ?? 0;
        const base =
          item.title?.trim() ||
          exerciseLabel(item.exerciseId, catalogExercises);
        return altCount > 0 ? `${base} (+${altCount} alt)` : base;
      }
      case "cardio": {
        const label =
          cardioCatalog.find((a) => a.id === item.cardio.activity)?.displayName ??
          item.cardio.activity;
        return cardioItemSummary(item, label);
      }
      case "mobility":
        return mobilityItemSummary(item, stretchLabel(item.mobility.catalogId, stretchCatalog));
      case "note":
        return item.text.trim() ? item.text.trim().slice(0, 48) : "Coach note";
      case "rest":
        return `Rest ${item.durationSeconds}s`;
    }
  };

  const itemDetail = (item: WorkoutItem): string => {
    switch (item.type) {
      case "weight":
        return prescriptionSummary(
          item.prescription,
          (kg) => `${formatWeightLoadNumber(kg, weightLoadUnit)} ${weightLoadUnitSuffix(weightLoadUnit)}`,
        );
      case "cardio":
      case "mobility":
      case "rest":
        return itemTypeLabel(item);
      case "note":
        return "Coach note";
    }
  };

  return (
    <div
      className={`rounded-[18px] border p-3 space-y-3 transition ${
        isActive
          ? "border-[var(--erv-primary)] bg-[var(--erv-primary-container)]/25 shadow-card"
          : "border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]"
      }`}
    >
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          className="text-left text-sm font-semibold text-heading"
          onClick={onSelect}
        >
          {segmentIndex + 1}. {segmentKindLabel(segment.kind)}
          {segment.title ? ` · ${segment.title}` : ""}
          {collapsed ? ` · ${items.length} item(s)` : ""}
        </button>
        <button
          type="button"
          className="btn-ghost text-xs py-0.5 px-2"
          onClick={() => setCollapsed((v) => !v)}
        >
          {collapsed ? "Expand" : "Collapse"}
        </button>
        <div className="ml-auto flex gap-1">
          {onMoveUp ? (
            <button
              type="button"
              className="btn-ghost text-xs py-0.5 px-2"
              disabled={!canMoveUp}
              onClick={onMoveUp}
            >
              Up
            </button>
          ) : null}
          {onMoveDown ? (
            <button
              type="button"
              className="btn-ghost text-xs py-0.5 px-2"
              disabled={!canMoveDown}
              onClick={onMoveDown}
            >
              Down
            </button>
          ) : null}
          <button type="button" className="btn-ghost text-xs py-0.5 px-2" onClick={onRemove}>
            Remove
          </button>
        </div>
      </div>

      {!collapsed ? (
        <>
      <label className="block text-xs space-y-1">
        <FieldLabel>Segment title</FieldLabel>
        <input
          className="input w-full text-sm"
          value={segment.title ?? ""}
          onChange={(e) => {
            const raw = e.target.value;
            onUpdate({ ...segment, title: raw === "" ? null : raw });
          }}
          onBlur={(e) => {
            const trimmed = e.target.value.trim();
            if (trimmed !== (segment.title ?? "")) {
              onUpdate({ ...segment, title: trimmed || null });
            }
          }}
          placeholder={segmentKindLabel(segment.kind)}
        />
      </label>

      {isRoundRobin ? (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-2">
          <label className="text-xs space-y-1">
            <FieldLabel>Rounds</FieldLabel>
            <input
              className="input w-full"
              type="number"
              min={1}
              value={segment.rounds ?? ""}
              onChange={(e) =>
                onUpdate({
                  ...segment,
                  rounds:
                    readOptionalPositiveInt(e.target.value, segment.rounds) ?? undefined,
                })
              }
            />
          </label>
          <label className="text-xs space-y-1">
            <FieldLabel>Rest between exercises (s)</FieldLabel>
            <input
              className="input w-full"
              type="number"
              min={0}
              value={restPolicy.restBetweenItemsSeconds ?? ""}
              onChange={(e) =>
                updateRestPolicy({
                  restBetweenItemsSeconds:
                    readOptionalNonNegativeInt(
                      e.target.value,
                      restPolicy.restBetweenItemsSeconds,
                    ) ?? undefined,
                })
              }
            />
          </label>
          <label className="text-xs space-y-1">
            <FieldLabel>Rest after round (s)</FieldLabel>
            <input
              className="input w-full"
              type="number"
              min={0}
              value={restPolicy.restAfterRoundSeconds ?? ""}
              onChange={(e) =>
                updateRestPolicy({
                  restAfterRoundSeconds:
                    readOptionalNonNegativeInt(
                      e.target.value,
                      restPolicy.restAfterRoundSeconds,
                    ) ?? undefined,
                })
              }
            />
          </label>
          <label className="text-xs space-y-1">
            <FieldLabel>Rest after segment (s)</FieldLabel>
            <input
              className="input w-full"
              type="number"
              min={0}
              value={segment.restAfterSeconds ?? ""}
              onChange={(e) => {
                onUpdate({
                  ...segment,
                  restAfterSeconds: readOptionalNonNegativeInt(
                    e.target.value,
                    segment.restAfterSeconds,
                  ),
                });
              }}
            />
          </label>
        </div>
      ) : (
        <label className="text-xs space-y-1 block max-w-xs">
          <FieldLabel>Rest after segment (s)</FieldLabel>
          <input
            className="input w-full"
            type="number"
            min={0}
            value={segment.restAfterSeconds ?? ""}
            onChange={(e) => {
              onUpdate({
                ...segment,
                restAfterSeconds: readOptionalNonNegativeInt(
                  e.target.value,
                  segment.restAfterSeconds,
                ),
              });
            }}
          />
        </label>
      )}

      {allowNotes ? (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="btn-ghost text-xs py-1 px-2"
            onClick={() => onUpdate({ ...segment, items: [...items, { type: "note", id: crypto.randomUUID(), text: "" }] })}
          >
            + Note
          </button>
          <button
            type="button"
            className="btn-ghost text-xs py-1 px-2"
            onClick={() =>
              onUpdate({
                ...segment,
                items: [...items, { type: "rest", id: crypto.randomUUID(), durationSeconds: 60 }],
              })
            }
          >
            + Rest
          </button>
        </div>
      ) : null}

      {items.length === 0 ? null : (
        <ReorderableList
          items={items}
          getKey={(item) => workoutItemKey(item)}
          onReorder={(ordered) => onUpdate({ ...segment, items: ordered })}
          renderItem={(item, itemIndex) => {
            const itemKey = workoutItemKey(item);
            const expanded = expandedItemId === itemKey;
            return (
              <div className="flex flex-col gap-2 w-full min-w-0">
                <div className="flex items-start gap-2 w-full">
                  <span className="text-muted w-5 shrink-0 pt-1">{itemIndex + 1}.</span>
                  <div className="flex-1 min-w-0">
                    <button
                      type="button"
                      className="text-left w-full"
                      onClick={() => setExpandedItemId(expanded ? null : itemKey)}
                    >
                      <FieldLabel className="text-[10px] text-muted block">
                        {itemTypeLabel(item)}
                      </FieldLabel>
                      <span className="font-medium block truncate">{itemTitle(item)}</span>
                      {item.type === "weight" ? (
                        <span className="text-xs text-muted block">
                          {itemDetail(item)}
                          {expanded ? " · hide" : " · edit"}
                        </span>
                      ) : null}
                    </button>
                    {expanded ? (
                      <div className="mt-2 space-y-2 rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-surface)] p-3">
                        {item.type === "weight" ? (
                          <>
                          <WeightPrescriptionFields
                            prescription={item.prescription ?? {}}
                            compact
                            weightLoadUnit={weightLoadUnit}
                            suggestedTargetKg={suggestedTargetWeightKg(
                              item.exerciseId,
                              trainingSnapshot,
                            )}
                            loggingStyle={exerciseSetLoggingStyle(
                              catalogExercises.find((ex) => ex.id === item.exerciseId) ?? {
                                id: item.exerciseId,
                                name: item.exerciseId,
                                muscleGroup: "",
                                pushOrPull: "push",
                                equipment: "",
                              },
                            )}
                            supportsTargetWeight={exerciseSupportsTargetWeight(
                              catalogExercises.find((ex) => ex.id === item.exerciseId) ?? {
                                id: item.exerciseId,
                                name: item.exerciseId,
                                muscleGroup: "",
                                pushOrPull: "push",
                                equipment: "",
                              },
                            )}
                            onChange={(next) =>
                              onUpdate(updateWeightItemPrescription(segment, itemKey, next))
                            }
                          />
                          <label className="text-xs space-y-1 block">
                            <FieldLabel>Alternative exercises (optional, pick at run)</FieldLabel>
                            <select
                              className="input w-full"
                              value=""
                              onChange={(e) => {
                                const id = e.target.value;
                                if (!id || id === item.exerciseId) return;
                                const current = item.alternativeExerciseIds ?? [];
                                if (current.includes(id)) return;
                                onUpdate(
                                  updateWorkoutItem(segment, itemKey, {
                                    ...item,
                                    alternativeExerciseIds: [...current, id],
                                  }),
                                );
                              }}
                            >
                              <option value="">Add alternative…</option>
                              {catalogExercises
                                .filter(
                                  (ex) =>
                                    ex.id !== item.exerciseId &&
                                    !(item.alternativeExerciseIds ?? []).includes(ex.id),
                                )
                                .map((ex) => (
                                  <option key={ex.id} value={ex.id}>
                                    {ex.name}
                                  </option>
                                ))}
                            </select>
                            {(item.alternativeExerciseIds ?? []).length > 0 ? (
                              <div className="flex flex-wrap gap-1 pt-1">
                                {(item.alternativeExerciseIds ?? []).map((altId) => (
                                  <button
                                    key={altId}
                                    type="button"
                                    className="btn-ghost text-xs py-0.5 px-2"
                                    onClick={() =>
                                      onUpdate(
                                        updateWorkoutItem(segment, itemKey, {
                                          ...item,
                                          alternativeExerciseIds: (
                                            item.alternativeExerciseIds ?? []
                                          ).filter((id) => id !== altId),
                                        }),
                                      )
                                    }
                                  >
                                    {exerciseLabel(altId, catalogExercises)} ×
                                  </button>
                                ))}
                              </div>
                            ) : null}
                          </label>
                          </>
                        ) : null}
                        {item.type === "cardio" ? (
                          <div className="space-y-2">
                            <label className="text-xs space-y-1 block">
                              <FieldLabel>Saved cardio routine (optional)</FieldLabel>
                              <select
                                className="input w-full"
                                value={item.cardio.cardioRoutineId ?? ""}
                                onChange={(e) => {
                                  const routineId = e.target.value.trim();
                                  if (!routineId) {
                                    onUpdate(
                                      updateWorkoutItem(segment, itemKey, {
                                        ...item,
                                        cardio: {
                                          ...item.cardio,
                                          cardioRoutineId: null,
                                        },
                                      }),
                                    );
                                    return;
                                  }
                                  const routine = cardioRoutines.find((r) => r.id === routineId);
                                  const activity =
                                    routine?.activity.builtin ??
                                    routine?.steps[0]?.activity.builtin ??
                                    item.cardio.activity;
                                  onUpdate(
                                    updateWorkoutItem(segment, itemKey, {
                                      ...item,
                                      cardio: {
                                        ...item.cardio,
                                        activity: activity ?? item.cardio.activity,
                                        mode: "steady",
                                        cardioRoutineId: routineId,
                                        targetMinutes:
                                          routine?.targetDurationMinutes ??
                                          item.cardio.targetMinutes ??
                                          20,
                                      },
                                    }),
                                  );
                                }}
                              >
                                <option value="">Inline prescription</option>
                                {cardioRoutines.map((routine) => (
                                  <option key={routine.id} value={routine.id}>
                                    {routine.name}
                                  </option>
                                ))}
                              </select>
                            </label>
                            {item.cardio.cardioRoutineId ? (
                              <p className="text-xs text-muted">
                                Launch uses your saved routine. Inline interval fields are ignored
                                at run time.
                              </p>
                            ) : (
                              <>
                            <label className="text-xs space-y-1 block">
                              <FieldLabel>Cardio mode</FieldLabel>
                              <select
                                className="input w-full"
                                value={item.cardio.mode ?? "steady"}
                                onChange={(e) => {
                                  const mode = e.target
                                    .value as NonNullable<typeof item.cardio.mode>;
                                  const base = { ...item.cardio };
                                  const cardio =
                                    mode === "sprint_intervals"
                                      ? {
                                          ...base,
                                          mode,
                                          rounds: item.cardio.rounds ?? 10,
                                          workSeconds: item.cardio.workSeconds ?? 60,
                                          restSeconds: item.cardio.restSeconds ?? 60,
                                        }
                                      : mode === "interval_template"
                                        ? {
                                            ...base,
                                            mode,
                                            outerRounds: item.cardio.outerRounds ?? 3,
                                            legs: item.cardio.legs?.length
                                              ? item.cardio.legs
                                              : [{ workSeconds: 240, restSeconds: 240 }],
                                          }
                                        : {
                                            ...base,
                                            mode: "steady" as const,
                                            targetMinutes: item.cardio.targetMinutes ?? 10,
                                          };
                                  onUpdate(
                                    updateWorkoutItem(segment, itemKey, {
                                      ...item,
                                      cardio,
                                    }),
                                  );
                                }}
                              >
                                <option value="steady">Steady</option>
                                <option value="sprint_intervals">Sprint intervals</option>
                                <option value="interval_template">Interval template</option>
                              </select>
                            </label>
                            {(item.cardio.mode ?? "steady") === "steady" ? (
                              <div className="space-y-2">
                                <div className="grid grid-cols-2 gap-2">
                                  <label className="text-xs space-y-1">
                                    <FieldLabel>Target minutes</FieldLabel>
                                    <input
                                      className="input w-full"
                                      type="number"
                                      min={1}
                                      value={item.cardio.targetMinutes ?? ""}
                                      onChange={(e) =>
                                        onUpdate(
                                          updateWorkoutItem(segment, itemKey, {
                                            ...item,
                                            cardio: {
                                              ...item.cardio,
                                              targetMinutes:
                                                readOptionalPositiveInt(
                                                  e.target.value,
                                                  item.cardio.targetMinutes,
                                                ) ?? undefined,
                                            },
                                          }),
                                        )
                                      }
                                    />
                                  </label>
                                  <label className="text-xs space-y-1">
                                    <FieldLabel>Zone label</FieldLabel>
                                    <input
                                      className="input w-full"
                                      value={item.cardio.hrZoneLabel ?? ""}
                                      placeholder="Zone 2"
                                      onChange={(e) =>
                                        onUpdate(
                                          updateWorkoutItem(segment, itemKey, {
                                            ...item,
                                            cardio: {
                                              ...item.cardio,
                                              hrZoneLabel:
                                                e.target.value === ""
                                                  ? null
                                                  : e.target.value,
                                            },
                                          }),
                                        )
                                      }
                                      onBlur={(e) => {
                                        const trimmed = e.target.value.trim();
                                        if (trimmed !== (item.cardio.hrZoneLabel ?? "")) {
                                          onUpdate(
                                            updateWorkoutItem(segment, itemKey, {
                                              ...item,
                                              cardio: {
                                                ...item.cardio,
                                                hrZoneLabel: trimmed || null,
                                              },
                                            }),
                                          );
                                        }
                                      }}
                                    />
                                  </label>
                                </div>
                                <div className="grid grid-cols-3 gap-2">
                                  <label className="text-xs space-y-1">
                                    <FieldLabel>Target HR (bpm)</FieldLabel>
                                    <input
                                      className="input w-full"
                                      type="number"
                                      min={0}
                                      value={item.cardio.hrTargetBpm ?? ""}
                                      onChange={(e) => {
                                        onUpdate(
                                          updateWorkoutItem(segment, itemKey, {
                                            ...item,
                                            cardio: {
                                              ...item.cardio,
                                              hrTargetBpm: readOptionalNonNegativeInt(
                                                e.target.value,
                                                item.cardio.hrTargetBpm,
                                              ),
                                            },
                                          }),
                                        );
                                      }}
                                    />
                                  </label>
                                  <label className="text-xs space-y-1">
                                    <FieldLabel>HR min (bpm)</FieldLabel>
                                    <input
                                      className="input w-full"
                                      type="number"
                                      min={0}
                                      value={item.cardio.hrTargetMinBpm ?? ""}
                                      onChange={(e) => {
                                        onUpdate(
                                          updateWorkoutItem(segment, itemKey, {
                                            ...item,
                                            cardio: {
                                              ...item.cardio,
                                              hrTargetMinBpm: readOptionalNonNegativeInt(
                                                e.target.value,
                                                item.cardio.hrTargetMinBpm,
                                              ),
                                            },
                                          }),
                                        );
                                      }}
                                    />
                                  </label>
                                  <label className="text-xs space-y-1">
                                    <FieldLabel>HR max (bpm)</FieldLabel>
                                    <input
                                      className="input w-full"
                                      type="number"
                                      min={0}
                                      value={item.cardio.hrTargetMaxBpm ?? ""}
                                      onChange={(e) => {
                                        onUpdate(
                                          updateWorkoutItem(segment, itemKey, {
                                            ...item,
                                            cardio: {
                                              ...item.cardio,
                                              hrTargetMaxBpm: readOptionalNonNegativeInt(
                                                e.target.value,
                                                item.cardio.hrTargetMaxBpm,
                                              ),
                                            },
                                          }),
                                        );
                                      }}
                                    />
                                  </label>
                                </div>
                                <fieldset className="text-xs space-y-1">
                                  <legend className="font-medium">
                                    <FieldLabel>Log prompts at run time</FieldLabel>
                                  </legend>
                                  <div className="flex flex-wrap gap-3 pt-1">
                                    {CARDIO_LOG_FIELD_OPTIONS.map((opt) => {
                                      const selected =
                                        item.cardio.logFields?.includes(opt.value) ?? false;
                                      return (
                                        <label key={opt.value} className="inline-flex items-center gap-1">
                                          <input
                                            type="checkbox"
                                            checked={selected}
                                            onChange={(e) => {
                                              const current = item.cardio.logFields ?? [];
                                              const next = e.target.checked
                                                ? [...current, opt.value]
                                                : current.filter((f) => f !== opt.value);
                                              onUpdate(
                                                updateWorkoutItem(segment, itemKey, {
                                                  ...item,
                                                  cardio: {
                                                    ...item.cardio,
                                                    logFields:
                                                      next.length > 0 ? next : undefined,
                                                  },
                                                }),
                                              );
                                            }}
                                          />
                                          {opt.label}
                                        </label>
                                      );
                                    })}
                                  </div>
                                </fieldset>
                              </div>
                            ) : null}
                            {(item.cardio.mode ?? "steady") === "sprint_intervals" ? (
                              <div className="grid grid-cols-3 gap-2">
                                <label className="text-xs space-y-1">
                                  <FieldLabel>Rounds</FieldLabel>
                                  <input
                                    className="input w-full"
                                    type="number"
                                    min={1}
                                    value={item.cardio.rounds ?? ""}
                                    onChange={(e) =>
                                      onUpdate(
                                        updateWorkoutItem(segment, itemKey, {
                                          ...item,
                                          cardio: {
                                            ...item.cardio,
                                            rounds:
                                              readOptionalPositiveInt(
                                                e.target.value,
                                                item.cardio.rounds,
                                              ) ?? undefined,
                                          },
                                        }),
                                      )
                                    }
                                  />
                                </label>
                                <label className="text-xs space-y-1">
                                  <FieldLabel>Work (s)</FieldLabel>
                                  <input
                                    className="input w-full"
                                    type="number"
                                    min={1}
                                    value={item.cardio.workSeconds ?? ""}
                                    onChange={(e) =>
                                      onUpdate(
                                        updateWorkoutItem(segment, itemKey, {
                                          ...item,
                                          cardio: {
                                            ...item.cardio,
                                            workSeconds:
                                              readOptionalPositiveInt(
                                                e.target.value,
                                                item.cardio.workSeconds,
                                              ) ?? undefined,
                                          },
                                        }),
                                      )
                                    }
                                  />
                                </label>
                                <label className="text-xs space-y-1">
                                  <FieldLabel>Rest (s)</FieldLabel>
                                  <input
                                    className="input w-full"
                                    type="number"
                                    min={0}
                                    value={item.cardio.restSeconds ?? ""}
                                    onChange={(e) =>
                                      onUpdate(
                                        updateWorkoutItem(segment, itemKey, {
                                          ...item,
                                          cardio: {
                                            ...item.cardio,
                                            restSeconds:
                                              readOptionalNonNegativeInt(
                                                e.target.value,
                                                item.cardio.restSeconds,
                                              ) ?? undefined,
                                          },
                                        }),
                                      )
                                    }
                                  />
                                </label>
                              </div>
                            ) : null}
                            {(item.cardio.mode ?? "steady") === "interval_template" ? (
                              <div className="grid grid-cols-3 gap-2">
                                <label className="text-xs space-y-1">
                                  <FieldLabel>Outer rounds</FieldLabel>
                                  <input
                                    className="input w-full"
                                    type="number"
                                    min={1}
                                    value={item.cardio.outerRounds ?? ""}
                                    onChange={(e) =>
                                      onUpdate(
                                        updateWorkoutItem(segment, itemKey, {
                                          ...item,
                                          cardio: {
                                            ...item.cardio,
                                            outerRounds:
                                              readOptionalPositiveInt(
                                                e.target.value,
                                                item.cardio.outerRounds,
                                              ) ?? undefined,
                                          },
                                        }),
                                      )
                                    }
                                  />
                                </label>
                                <label className="text-xs space-y-1">
                                  <FieldLabel>Work (s)</FieldLabel>
                                  <input
                                    className="input w-full"
                                    type="number"
                                    min={1}
                                    value={
                                      (item.cardio.legs?.[0]?.workSeconds ?? 0) > 0
                                        ? item.cardio.legs?.[0]?.workSeconds
                                        : ""
                                    }
                                    onChange={(e) => {
                                      const leg = item.cardio.legs?.[0] ?? {
                                        workSeconds: 240,
                                        restSeconds: 240,
                                      };
                                      onUpdate(
                                        updateWorkoutItem(segment, itemKey, {
                                          ...item,
                                          cardio: {
                                            ...item.cardio,
                                            legs: [
                                              {
                                                ...leg,
                                                workSeconds:
                                                  readOptionalPositiveInt(
                                                    e.target.value,
                                                    leg.workSeconds,
                                                  ) ?? 0,
                                              },
                                            ],
                                          },
                                        }),
                                      );
                                    }}
                                  />
                                </label>
                                <label className="text-xs space-y-1">
                                  <FieldLabel>Rest (s)</FieldLabel>
                                  <input
                                    className="input w-full"
                                    type="number"
                                    min={0}
                                    value={item.cardio.legs?.[0]?.restSeconds ?? ""}
                                    onChange={(e) => {
                                      const leg = item.cardio.legs?.[0] ?? {
                                        workSeconds: 240,
                                        restSeconds: 240,
                                      };
                                      onUpdate(
                                        updateWorkoutItem(segment, itemKey, {
                                          ...item,
                                          cardio: {
                                            ...item.cardio,
                                            legs: [
                                              {
                                                ...leg,
                                                restSeconds:
                                                  readOptionalNonNegativeInt(
                                                    e.target.value,
                                                    leg.restSeconds,
                                                  ) ?? undefined,
                                              },
                                            ],
                                          },
                                        }),
                                      );
                                    }}
                                  />
                                </label>
                              </div>
                            ) : null}
                              </>
                            )}
                          </div>
                        ) : null}
                        {item.type === "mobility" ? (
                          <div className="grid grid-cols-2 gap-2">
                            <label className="text-xs space-y-1">
                              <FieldLabel>Hold (s)</FieldLabel>
                              <input
                                className="input w-full"
                                type="number"
                                min={1}
                                value={item.mobility.holdSeconds ?? ""}
                                onChange={(e) =>
                                  onUpdate(
                                    updateWorkoutItem(segment, itemKey, {
                                      ...item,
                                      mobility: {
                                        ...item.mobility,
                                        holdSeconds:
                                          readOptionalPositiveInt(
                                            e.target.value,
                                            item.mobility.holdSeconds,
                                          ) ?? undefined,
                                      },
                                    }),
                                  )
                                }
                              />
                            </label>
                            <label className="text-xs space-y-1">
                              <FieldLabel>Hold per side (s)</FieldLabel>
                              <input
                                className="input w-full"
                                type="number"
                                min={0}
                                value={item.mobility.holdSecondsPerSide ?? ""}
                                onChange={(e) => {
                                  onUpdate(
                                    updateWorkoutItem(segment, itemKey, {
                                      ...item,
                                      mobility: {
                                        ...item.mobility,
                                        holdSecondsPerSide: readOptionalPositiveInt(
                                          e.target.value,
                                          item.mobility.holdSecondsPerSide,
                                        ),
                                      },
                                    }),
                                  );
                                }}
                              />
                            </label>
                          </div>
                        ) : null}
                        {item.type === "note" ? (
                          <textarea
                            className="input w-full text-sm min-h-[4rem]"
                            value={item.text}
                            onChange={(e) =>
                              onUpdate(
                                updateWorkoutItem(segment, itemKey, { ...item, text: e.target.value }),
                              )
                            }
                            placeholder="Coach cue or reminder"
                          />
                        ) : null}
                        {item.type === "rest" ? (
                          <label className="text-xs space-y-1 block max-w-xs">
                            <FieldLabel>Duration (s)</FieldLabel>
                            <input
                              className="input w-full"
                              type="number"
                              min={1}
                              value={item.durationSeconds > 0 ? item.durationSeconds : ""}
                              onChange={(e) =>
                                onUpdate(
                                  updateWorkoutItem(segment, itemKey, {
                                    ...item,
                                    durationSeconds:
                                      readOptionalPositiveInt(
                                        e.target.value,
                                        item.durationSeconds,
                                      ) ?? 0,
                                  }),
                                )
                              }
                            />
                          </label>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                  <button
                    type="button"
                    className="btn-ghost text-xs py-0.5 px-2 shrink-0"
                    onClick={() => removeItem(item)}
                  >
                    Remove
                  </button>
                </div>
              </div>
            );
          }}
        />
      )}
        </>
      ) : null}
    </div>
  );
}
