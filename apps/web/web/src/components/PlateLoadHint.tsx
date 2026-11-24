import { useMemo, useState } from "react";
import { useEquipment } from "../lib/equipmentData";
import { lbToKg, type BodyWeightUnit } from "../lib/fitnessEquipment";
import {
  calculatePlateLoad,
  defaultBarForExercise,
  exercisePlateLoading,
  formatPlateList,
  nominalWeight,
  plateCalculatorBars,
  plateCalculatorPlates,
} from "../lib/plateCalculator";
import { weightLoadUnitSuffix } from "../lib/weightLoadUnit";

const BAR_STORAGE_PREFIX = "erv-plate-bar:";

function readRememberedBar(exerciseId: string): string | null {
  try {
    return window.localStorage.getItem(BAR_STORAGE_PREFIX + exerciseId);
  } catch {
    return null;
  }
}

function rememberBar(exerciseId: string, barId: string) {
  try {
    window.localStorage.setItem(BAR_STORAGE_PREFIX + exerciseId, barId);
  } catch {
    // Remembering the bar is a convenience only.
  }
}

/**
 * Plates per side under a barbell target weight, using your bars and plates. When the weight
 * cannot be loaded it names the nearest loadable weights, with one click to use either.
 */
export function PlateLoadHint({
  exercise,
  targetKg,
  weightLoadUnit,
  onUseWeightKg,
}: {
  exercise: { id: string; name: string; equipment: string } | null | undefined;
  targetKg: number | null | undefined;
  weightLoadUnit: BodyWeightUnit;
  onUseWeightKg: (kg: number) => void;
}) {
  const { equipment } = useEquipment();
  const loading = exercisePlateLoading(exercise);
  const { bars, assumed: barsAssumed } = useMemo(
    () => plateCalculatorBars(equipment, weightLoadUnit),
    [equipment, weightLoadUnit],
  );
  const { plates, assumed: platesAssumed } = useMemo(
    () => plateCalculatorPlates(equipment, weightLoadUnit),
    [equipment, weightLoadUnit],
  );
  const [pickedBarId, setPickedBarId] = useState<string | null>(() =>
    exercise ? readRememberedBar(exercise.id) : null,
  );

  if (!exercise || !loading || loading === "NONE" || targetKg == null || targetKg <= 0) return null;
  const bar = defaultBarForExercise(exercise, bars, pickedBarId);
  if (!bar) return null;

  const suffix = weightLoadUnitSuffix(weightLoadUnit);
  const target = nominalWeight(targetKg, weightLoadUnit);
  const result = calculatePlateLoad({ target, bar: bar.weight, plates, loading });
  const applyWeight = (displayWeight: number) =>
    onUseWeightKg(weightLoadUnit === "LB" ? lbToKg(displayWeight) : displayWeight);
  const assumedNote =
    barsAssumed || platesAssumed ? " Using standard gym plates until you add yours under Equipment." : "";

  return (
    <div className="col-span-full space-y-1 text-xs">
      <div className="flex flex-wrap items-center gap-2">
        {result.exact ? (
          <span className="font-medium">{formatPlateList(result.exact.plates, loading)}</span>
        ) : (
          <span className="text-error">
            You can't load {target} {suffix} on this bar with your plates.
          </span>
        )}
        {bars.length > 1 ? (
          <select
            className="input py-0.5 text-xs w-auto"
            aria-label="Bar"
            value={bar.id}
            onChange={(e) => {
              setPickedBarId(e.target.value);
              rememberBar(exercise.id, e.target.value);
            }}
          >
            {bars.map((b) => (
              <option key={b.id} value={b.id}>
                {b.label}
              </option>
            ))}
          </select>
        ) : (
          <span className="text-muted">on {bar.label}</span>
        )}
      </div>
      {!result.exact && (result.below || result.above) ? (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-muted">Nearest:</span>
          {[result.below, result.above].map((option) =>
            option ? (
              <button
                key={option.total}
                type="button"
                className="btn-ghost text-xs py-0.5 px-2"
                title={formatPlateList(option.plates, loading)}
                onClick={() => applyWeight(option.total)}
              >
                Use {option.total} {suffix}
              </button>
            ) : null,
          )}
        </div>
      ) : null}
      {assumedNote ? <span className="text-muted block">{assumedNote.trim()}</span> : null}
    </div>
  );
}
