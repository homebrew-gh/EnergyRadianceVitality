import { useMemo, useState } from "react";
import { FieldLabel, SectionHeader } from "./FieldLabel";
import type { BodyWeightUnit } from "../lib/fitnessEquipment";
import {
  CUSTOM_CARDIO_DISTANCES,
  CUSTOM_CARDIO_GROUPS,
  CUSTOM_EVENT_TYPES,
  draftToStandard,
  newEventDraft,
  tierUnitLabel,
  usesExercise,
  type CustomEventDraft,
  type CustomEventType,
  type CustomStandardDraft,
} from "../lib/customStandards";
import type { FitnessStandard } from "../lib/standards";

/** Label with the unit kept as typed, so "m:ss" is not title-cased. */
function LabelWithUnit({ text, unit }: { text: string; unit?: string | null }) {
  return (
    <span className="label">
      <FieldLabel>{text}</FieldLabel>
      {unit ? ` (${unit})` : null}
    </span>
  );
}

function EventEditor({
  index,
  event,
  unit,
  exercises,
  canRemove,
  onChange,
  onRemove,
  onMove,
}: {
  index: number;
  event: CustomEventDraft;
  unit: BodyWeightUnit;
  exercises: { id: string; name: string }[];
  canRemove: boolean;
  onChange: (next: CustomEventDraft) => void;
  onRemove: () => void;
  onMove: (delta: -1 | 1) => void;
}) {
  const set = (partial: Partial<CustomEventDraft>) => onChange({ ...event, ...partial });
  const id = (field: string) => `custom-${event.key}-${field}`;
  const tierUnit = tierUnitLabel(event.type, unit);
  const showReps = event.type === "LIFT_BW" || event.type === "LIFT_LOAD";
  return (
    <li className="rounded-lg border border-[var(--erv-outline-variant)] p-3 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-medium text-heading">Event {index + 1}</p>
        <div className="flex gap-1">
          <button
            type="button"
            className="btn-ghost px-2 py-0.5 text-xs"
            onClick={() => onMove(-1)}
            aria-label="Move up"
          >
            ↑
          </button>
          <button
            type="button"
            className="btn-ghost px-2 py-0.5 text-xs"
            onClick={() => onMove(1)}
            aria-label="Move down"
          >
            ↓
          </button>
          {canRemove ? (
            <button type="button" className="btn-ghost px-2 py-0.5 text-xs" onClick={onRemove}>
              Remove
            </button>
          ) : null}
        </div>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block space-y-1" htmlFor={id("type")}>
          <LabelWithUnit text="Measure" />
          <select
            id={id("type")}
            className="input w-full"
            value={event.type}
            onChange={(e) => set({ type: e.target.value as CustomEventType, base: "", strong: "", elite: "" })}
          >
            {CUSTOM_EVENT_TYPES.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </select>
        </label>
        <label className="block space-y-1" htmlFor={id("name")}>
          <LabelWithUnit text="Event name" />
          <input
            id={id("name")}
            className="input w-full"
            placeholder={
              usesExercise(event.type)
                ? "Defaults to the exercise name"
                : event.type === "CARDIO"
                  ? "Defaults to the distance, e.g. 1 mile run"
                  : "e.g. Broad Jump"
            }
            value={event.name}
            onChange={(e) => set({ name: e.target.value })}
          />
        </label>
        {usesExercise(event.type) ? (
          <label className="block space-y-1" htmlFor={id("exercise")}>
            <LabelWithUnit text="Exercise" />
            <select
              id={id("exercise")}
              className="input w-full"
              value={event.exerciseId}
              onChange={(e) => set({ exerciseId: e.target.value })}
            >
              <option value="">Pick an exercise</option>
              {exercises.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name}
                </option>
              ))}
            </select>
          </label>
        ) : null}
        {showReps ? (
          <label className="block space-y-1" htmlFor={id("reps")}>
            <LabelWithUnit text="Reps" unit="1 = estimated 1RM" />
            <input
              id={id("reps")}
              className="input w-full"
              inputMode="numeric"
              value={event.reps}
              onChange={(e) => set({ reps: e.target.value })}
            />
          </label>
        ) : null}
        {event.type === "CARDIO" ? (
          <>
            <label className="block space-y-1" htmlFor={id("group")}>
              <LabelWithUnit text="Activity" />
              <select
                id={id("group")}
                className="input w-full"
                value={event.cardioGroup}
                onChange={(e) => set({ cardioGroup: e.target.value as CustomEventDraft["cardioGroup"] })}
              >
                {CUSTOM_CARDIO_GROUPS.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="block space-y-1" htmlFor={id("distance")}>
              <LabelWithUnit text="Distance" />
              <select
                id={id("distance")}
                className="input w-full"
                value={event.distanceKey}
                onChange={(e) => set({ distanceKey: e.target.value })}
              >
                {CUSTOM_CARDIO_DISTANCES.map((d) => (
                  <option key={d.key} value={d.key}>
                    {d.label}
                  </option>
                ))}
              </select>
            </label>
            {event.cardioGroup === "RUCK" ? (
              <label className="block space-y-1" htmlFor={id("pack")}>
                <LabelWithUnit text="Minimum pack" unit={unit === "KG" ? "kg" : "lb"} />
                <input
                  id={id("pack")}
                  className="input w-full"
                  inputMode="decimal"
                  value={event.minPack}
                  onChange={(e) => set({ minPack: e.target.value })}
                />
              </label>
            ) : null}
          </>
        ) : null}
      </div>
      <fieldset className="grid gap-3 grid-cols-3">
        <legend className="sr-only">Targets</legend>
        {(["base", "strong", "elite"] as const).map((tier) => (
          <label key={tier} className="block space-y-1" htmlFor={id(tier)}>
            <LabelWithUnit text={tier.charAt(0).toUpperCase() + tier.slice(1)} unit={tierUnit} />
            <input
              id={id(tier)}
              className="input w-full"
              inputMode={tierUnit === "m:ss" ? "text" : "decimal"}
              placeholder={tierUnit === "m:ss" ? "3:15" : ""}
              value={event[tier]}
              onChange={(e) => set({ [tier]: e.target.value })}
            />
          </label>
        ))}
      </fieldset>
    </li>
  );
}

/**
 * Builds or edits a custom standard: a name, then events, each with a measure, what it reads
 * from (exercise or cardio distance, or a test result), and Base / Strong / Elite targets.
 */
export function CustomStandardEditor({
  initial,
  unit,
  exercises,
  saving,
  onSave,
  onCancel,
}: {
  initial: CustomStandardDraft;
  unit: BodyWeightUnit;
  exercises: { id: string; name: string }[];
  saving: boolean;
  onSave: (standard: FitnessStandard) => Promise<void>;
  onCancel: () => void;
}) {
  const [draft, setDraft] = useState(initial);
  const [errors, setErrors] = useState<string[]>([]);
  const sortedExercises = useMemo(() => [...exercises].sort((a, b) => a.name.localeCompare(b.name)), [exercises]);
  const names = useMemo(() => new Map(exercises.map((e) => [e.id, e.name])), [exercises]);

  const setEvent = (i: number, next: CustomEventDraft) =>
    setDraft((d) => ({ ...d, events: d.events.map((e, j) => (j === i ? next : e)) }));
  const move = (i: number, delta: -1 | 1) =>
    setDraft((d) => {
      const j = i + delta;
      if (j < 0 || j >= d.events.length) return d;
      const events = [...d.events];
      [events[i], events[j]] = [events[j], events[i]];
      return { ...d, events };
    });

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const out = draftToStandard(draft, unit, names, () => `custom-${crypto.randomUUID()}`);
    if ("errors" in out) {
      setErrors(out.errors);
      return;
    }
    setErrors([]);
    await onSave(out.standard);
  };

  return (
    <form className="space-y-4" onSubmit={submit}>
      <SectionHeader>{draft.id ? "Edit custom standard" : "New custom standard"}</SectionHeader>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block space-y-1" htmlFor="custom-standard-name">
          <LabelWithUnit text="Standard name" />
          <input
            id="custom-standard-name"
            className="input w-full"
            value={draft.name}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
        </label>
        <label className="block space-y-1" htmlFor="custom-standard-description">
          <LabelWithUnit text="Description" unit="optional" />
          <input
            id="custom-standard-description"
            className="input w-full"
            value={draft.description}
            onChange={(e) => setDraft({ ...draft, description: e.target.value })}
          />
        </label>
      </div>
      <p className="text-xs text-muted">
        Lifts and cardio fill in from your logs. Test events score from results you log on the card.
      </p>
      <ol className="space-y-3">
        {draft.events.map((ev, i) => (
          <EventEditor
            key={ev.key}
            index={i}
            event={ev}
            unit={unit}
            exercises={sortedExercises}
            canRemove={draft.events.length > 1}
            onChange={(next) => setEvent(i, next)}
            onRemove={() => setDraft((d) => ({ ...d, events: d.events.filter((_, j) => j !== i) }))}
            onMove={(delta) => move(i, delta)}
          />
        ))}
      </ol>
      <button
        type="button"
        className="btn-ghost px-3 py-1 text-sm"
        onClick={() => setDraft((d) => ({ ...d, events: [...d.events, newEventDraft()] }))}
      >
        Add Event
      </button>
      {errors.length > 0 ? (
        <ul className="text-sm text-[var(--erv-error)] list-disc pl-5" role="alert">
          {errors.map((err) => (
            <li key={err}>{err}</li>
          ))}
        </ul>
      ) : null}
      <div className="flex gap-2">
        <button type="submit" className="btn-primary px-4 py-1.5 text-sm" disabled={saving}>
          {saving ? "Saving…" : "Save Standard"}
        </button>
        <button type="button" className="btn-ghost px-4 py-1.5 text-sm" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
