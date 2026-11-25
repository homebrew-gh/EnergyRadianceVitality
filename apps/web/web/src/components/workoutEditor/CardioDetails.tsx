import type { CardioRoutine } from "../../lib/cardioTraining";
import { cardioMode } from "../../lib/cardioRowFields";
import { formatClock, parseClock } from "../../lib/liftRowFields";
import {
  addIntervalLeg,
  removeIntervalLeg,
  updateIntervalLeg,
  DEFAULT_INTERVAL_LEG,
  type WorkoutCardioLogField,
  type WorkoutCardioPrescription,
} from "../../lib/workoutTraining";
import { FieldLabel } from "../FieldLabel";
import { CellInput } from "./CellInput";

const LOG_FIELDS: { value: WorkoutCardioLogField; label: string }[] = [
  { value: "INCLINE", label: "Incline" },
  { value: "SPEED", label: "Speed" },
  { value: "DISTANCE", label: "Distance" },
  { value: "NOTES", label: "Notes" },
];

/**
 * Everything about a cardio row that doesn't fit its cells: a saved routine to run instead,
 * the zone name, what to log, and the legs of an interval pattern.
 */
export function CardioDetails({
  cardio,
  cardioRoutines,
  onChange,
}: {
  cardio: WorkoutCardioPrescription;
  cardioRoutines: CardioRoutine[];
  /** `separate` false lets quick typing (a zone name) merge into one undo step. */
  onChange: (cardio: WorkoutCardioPrescription, separate?: boolean) => void;
}) {
  const mode = cardioMode(cardio);
  const legs = cardio.legs?.length ? cardio.legs : [DEFAULT_INTERVAL_LEG];

  return (
    <div className="space-y-3 rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-surface)] p-3">
      <div className="grid gap-3 md:grid-cols-2">
        <label className="block space-y-1 text-xs">
          <FieldLabel>Saved cardio routine</FieldLabel>
          <select
            className="input w-full"
            value={cardio.cardioRoutineId ?? ""}
            onChange={(e) => {
              const routineId = e.target.value.trim();
              if (!routineId) {
                onChange({ ...cardio, cardioRoutineId: null });
                return;
              }
              const routine = cardioRoutines.find((r) => r.id === routineId);
              const activity = routine?.activity.builtin ?? routine?.steps[0]?.activity.builtin ?? cardio.activity;
              onChange({
                ...cardio,
                activity: activity ?? cardio.activity,
                mode: "steady",
                cardioRoutineId: routineId,
                targetMinutes: routine?.targetDurationMinutes ?? cardio.targetMinutes ?? 20,
              });
            }}
          >
            <option value="">None, use this row</option>
            {cardioRoutines.map((routine) => (
              <option key={routine.id} value={routine.id}>
                {routine.name}
              </option>
            ))}
          </select>
          <span className="block text-muted">The phone runs the saved routine instead of this row's numbers.</span>
        </label>
        {mode === "steady" && !cardio.cardioRoutineId ? (
          <label className="block space-y-1 text-xs">
            <FieldLabel>Zone label</FieldLabel>
            <input
              className="input w-full"
              value={cardio.hrZoneLabel ?? ""}
              placeholder="Zone 2"
              onChange={(e) => onChange({ ...cardio, hrZoneLabel: e.target.value === "" ? null : e.target.value }, false)}
            />
          </label>
        ) : null}
      </div>

      {mode === "steady" && !cardio.cardioRoutineId ? (
        <fieldset className="text-xs">
          <legend className="text-muted">
            <FieldLabel>Log during the run</FieldLabel>
          </legend>
          <div className="flex flex-wrap gap-3 pt-1">
            {LOG_FIELDS.map((opt) => {
              const selected = cardio.logFields?.includes(opt.value) ?? false;
              return (
                <label key={opt.value} className="inline-flex items-center gap-1">
                  <input
                    type="checkbox"
                    checked={selected}
                    onChange={(e) => {
                      const current = cardio.logFields ?? [];
                      const next = e.target.checked ? [...current, opt.value] : current.filter((f) => f !== opt.value);
                      onChange({ ...cardio, logFields: next.length > 0 ? next : undefined });
                    }}
                  />
                  {opt.label}
                </label>
              );
            })}
          </div>
        </fieldset>
      ) : null}

      {mode === "interval_template" && !cardio.cardioRoutineId ? (
        <div className="space-y-2">
          <p className="text-xs text-muted">
            <FieldLabel>Legs</FieldLabel> (the whole pattern repeats {cardio.outerRounds ?? 1}×)
          </p>
          <table className="w-full max-w-2xl text-sm">
            <thead>
              <tr className="text-left text-xs text-muted">
                <th className="w-8 px-1 font-medium">#</th>
                <th className="px-1 font-medium">Label</th>
                <th className="w-24 px-1 font-medium">Work</th>
                <th className="w-24 px-1 font-medium">Rest</th>
                <th className="w-24 px-1 font-medium">
                  <FieldLabel>Heart rate</FieldLabel>
                </th>
                <th className="w-16" />
              </tr>
            </thead>
            <tbody>
              {legs.map((leg, i) => (
                <tr key={i} className="border-t border-[var(--erv-outline-variant)]">
                  <td className="px-1 text-xs text-muted">{i + 1}</td>
                  <td className="px-1">
                    <CellInput
                      value={leg.label ?? ""}
                      ariaLabel={`Leg ${i + 1} label`}
                      placeholder="Work leg"
                      inputMode="text"
                      onCommit={(text) =>
                        onChange({ ...cardio, legs: updateIntervalLeg(cardio.legs, i, { label: text.trim() || null }) })
                      }
                    />
                  </td>
                  <td className="px-1">
                    <ClockCell
                      label={`Leg ${i + 1} work`}
                      seconds={leg.workSeconds}
                      onSeconds={(s) => s && onChange({ ...cardio, legs: updateIntervalLeg(cardio.legs, i, { workSeconds: s }) })}
                    />
                  </td>
                  <td className="px-1">
                    <ClockCell
                      label={`Leg ${i + 1} rest`}
                      seconds={leg.restSeconds}
                      onSeconds={(s) => onChange({ ...cardio, legs: updateIntervalLeg(cardio.legs, i, { restSeconds: s ?? 0 }) })}
                    />
                  </td>
                  <td className="px-1">
                    <CellInput
                      value={leg.hrTargetBpm ? String(leg.hrTargetBpm) : ""}
                      ariaLabel={`Leg ${i + 1} heart rate`}
                      placeholder="–"
                      onCommit={(text) => {
                        const t = text.trim();
                        if (t && !/^\d{2,3}$/.test(t)) return;
                        onChange({ ...cardio, legs: updateIntervalLeg(cardio.legs, i, { hrTargetBpm: t ? Number(t) : null }) });
                      }}
                    />
                  </td>
                  <td className="px-1 text-right">
                    <button
                      type="button"
                      className="text-xs text-muted hover:text-[var(--erv-error)] disabled:opacity-40"
                      disabled={legs.length <= 1}
                      onClick={() => onChange({ ...cardio, legs: removeIntervalLeg(cardio.legs, i) })}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button type="button" className="btn-ghost px-3 py-1 text-xs" onClick={() => onChange({ ...cardio, legs: addIntervalLeg(cardio.legs) })}>
            + Add Leg
          </button>
        </div>
      ) : null}
    </div>
  );
}

function ClockCell({
  label,
  seconds,
  onSeconds,
}: {
  label: string;
  seconds: number | null | undefined;
  onSeconds: (seconds: number | null) => void;
}) {
  return (
    <CellInput
      value={seconds ? formatClock(seconds) : ""}
      ariaLabel={label}
      placeholder="0:00"
      inputMode="text"
      onCommit={(text) => {
        const parsed = parseClock(text);
        if (parsed !== undefined) onSeconds(parsed);
      }}
    />
  );
}
