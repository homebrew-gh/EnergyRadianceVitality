import { useState } from "react";
import { formatWeightKg } from "../../lib/trainingHistory";
import type { BodyWeightUnit } from "../../lib/fitnessEquipment";
import {
  formatWeightLoadNumber,
  parseWeightInputToKg,
  weightLoadUnitSuffix,
} from "../../lib/weightLoadUnit";
import { applyTargetWeightToPrescription } from "../../lib/workoutPrescriptionHints";
import type { WeightExercise } from "../../lib/weightTraining";
import { PlateLoadHint } from "../PlateLoadHint";
import {
  DEFAULT_TIMED_PREP_SECONDS,
  ensurePrescriptionSets,
  readOptionalNonNegativeInt,
  readOptionalPositiveInt,
  prescriptionSetTargets,
  prescriptionUsesPerSide,
  syncPrescriptionSetCount,
  type WorkoutWeightPrescription,
  type WorkoutPrescriptionSet,
  type WorkoutPrescriptionSetSide,
  type WeightSetLoggingStyle,
} from "../../lib/workoutTraining";
import { FieldLabel, SectionHeader } from "../FieldLabel";

/** Per-set targets for one lift: the set detail editor behind a weight row's settings. */
export function WeightPrescriptionFields({
  prescription,
  loggingStyle,
  onChange,
  compact = false,
  suggestedTargetKg,
  supportsTargetWeight = loggingStyle !== "time_only",
  weightLoadUnit = "LB",
  exercise,
}: {
  prescription: WorkoutWeightPrescription;
  loggingStyle: WeightSetLoggingStyle;
  onChange: (next: WorkoutWeightPrescription) => void;
  compact?: boolean;
  suggestedTargetKg?: number | null;
  supportsTargetWeight?: boolean;
  weightLoadUnit?: BodyWeightUnit;
  /** The lift, so barbell exercises can show plates per side. */
  exercise?: WeightExercise | null;
}) {
  const [showPerSetEditor, setShowPerSetEditor] = useState(() => {
    const sets = prescription.sets ?? [];
    if (sets.length < 2) return false;
    const reps = (s: WorkoutPrescriptionSet) => prescriptionSetTargets(s).reps;
    const load = (s: WorkoutPrescriptionSet) => prescriptionSetTargets(s).weightKg;
    const rir = (s: WorkoutPrescriptionSet) => s.rir ?? null;
    const first = sets[0]!;
    return sets.some(
      (s) =>
        reps(s) !== reps(first) ||
        load(s) !== load(first) ||
        rir(s) !== rir(first) ||
        s.warmup === true,
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
                  timedPrepSeconds: prescription.timedPrepSeconds ?? DEFAULT_TIMED_PREP_SECONDS,
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
        {timedPrescription
          ? field(
              "Start countdown (s)",
              prescription.timedPrepSeconds ?? null,
              (timedPrepSeconds) =>
                onChange({
                  ...prescription,
                  timedPrepSeconds,
                  mode: "time_based",
                }),
              { min: 0 },
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
        {supportsTargetWeight && !isMaxReps && !showPerSetEditor ? (
          <PlateLoadHint
            key={exercise?.id ?? "none"}
            exercise={exercise}
            targetKg={prescription.targetWeightKg}
            weightLoadUnit={weightLoadUnit}
            onUseWeightKg={(targetWeightKg) => onChange({ ...prescription, targetWeightKg })}
          />
        ) : null}
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
              <div className="text-xs pb-2 space-y-1">
                <span className="text-muted block">{set.warmup ? "Warm-up" : `Set ${index + 1}`}</span>
                <label className="inline-flex items-center gap-1" title="Warm-ups stay out of records and volume">
                  <input
                    type="checkbox"
                    checked={set.warmup === true}
                    onChange={(e) => updateSetAt(index, { warmup: e.target.checked ? true : null })}
                  />
                  <FieldLabel>Warm-up</FieldLabel>
                </label>
              </div>
              {field("Reps", prescriptionSetTargets(set).reps, (reps) =>
                updateSetAt(index, { reps, targetReps: reps }),
                { positive: true },
              )}
              {weightField(
                `Weight (${loadSuffix})`,
                prescriptionSetTargets(set).weightKg,
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
