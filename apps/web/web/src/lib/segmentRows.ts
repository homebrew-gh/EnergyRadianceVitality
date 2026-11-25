/**
 * Row tools for the workout editor's lifting tables: duplicate, move, swap and copy down.
 * Each takes a segment and returns a new one, so the editor can treat it as one undo step.
 */
import type { WeightSetLoggingStyle } from "./exerciseLogging";
import {
  applyReps,
  applyRest,
  applyRir,
  applySets,
  repsField,
  restField,
  rirField,
  setsField,
} from "./liftRowFields";
import { segmentItems, type WorkoutItem, type WorkoutSegment } from "./workoutTraining";

type WeightItem = Extract<WorkoutItem, { type: "weight" }>;

/** A copy of the row at [index], placed right after it, with its own id. */
export function duplicateItemAt(segment: WorkoutSegment, index: number, newId: string): WorkoutSegment {
  const items = segmentItems(segment);
  const item = items[index];
  if (!item) return segment;
  const copy = { ...structuredClone(item), id: newId } as WorkoutItem;
  return { ...segment, items: [...items.slice(0, index + 1), copy, ...items.slice(index + 1)] };
}

/** Moves the row at [from] so it ends up at [to]. */
export function moveItem(segment: WorkoutSegment, from: number, to: number): WorkoutSegment {
  const items = [...segmentItems(segment)];
  if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) return segment;
  const [moved] = items.splice(from, 1);
  items.splice(to, 0, moved!);
  return { ...segment, items };
}

/**
 * Takes the row at [from] in [source] and puts it in [target] at [to] (the end when [to] is
 * past it). Returns null when the target already has that exercise.
 */
export function moveItemBetween(
  source: WorkoutSegment,
  from: number,
  target: WorkoutSegment,
  to: number,
): { source: WorkoutSegment; target: WorkoutSegment } | null {
  const sourceItems = [...segmentItems(source)];
  const [moved] = sourceItems.splice(from, 1);
  if (!moved) return null;
  const targetItems = [...segmentItems(target)];
  if (
    moved.type === "weight" &&
    targetItems.some((item) => item.type === "weight" && item.exerciseId === moved.exerciseId)
  ) {
    return null;
  }
  targetItems.splice(Math.min(Math.max(to, 0), targetItems.length), 0, moved);
  return { source: { ...source, items: sourceItems }, target: { ...target, items: targetItems } };
}

/**
 * The same row with a different exercise. Sets, reps, weight, rest and notes stay; the old
 * name goes (it belonged to the old exercise), and the new one is dropped from the alternatives.
 */
export function swapExercise(item: WeightItem, exerciseId: string, title: string | null): WeightItem {
  const alternatives = (item.alternativeExerciseIds ?? []).filter((id) => id !== exerciseId);
  return {
    ...item,
    exerciseId,
    title,
    alternativeExerciseIds: alternatives.length > 0 ? alternatives : undefined,
  };
}

/**
 * Copies sets, reps, RIR and rest from the lift at [index] to every lift below it in the
 * segment. Weight is left alone, since it belongs to each exercise. A value a row cannot take
 * (a rep range on a timed hold, say) is skipped for that row, as is any column that row sets per set.
 */
export function copyRowDown(
  segment: WorkoutSegment,
  index: number,
  styleFor: (exerciseId: string) => WeightSetLoggingStyle,
): WorkoutSegment {
  const items = segmentItems(segment);
  const source = items[index];
  if (!source || source.type !== "weight") return segment;
  const from = source.prescription ?? {};
  const sourceStyle = styleFor(source.exerciseId);
  const sets = setsField(from);
  const reps = repsField(from, sourceStyle);
  const rir = rirField(from);
  const rest = restField(from);
  let changed = false;
  const next = items.map((item, i) => {
    if (i <= index || item.type !== "weight") return item;
    const style = styleFor(item.exerciseId);
    let p = item.prescription ?? {};
    if (sets.editable && setsField(p).editable) p = applySets(p, sets.text);
    if (reps.editable && repsField(p, style).editable) p = applyReps(p, reps.text, style);
    if (rir.editable && rirField(p).editable) p = applyRir(p, rir.text);
    if (rest.editable) p = applyRest(p, rest.text);
    if (p === item.prescription) return item;
    changed = true;
    return { ...item, prescription: p };
  });
  return changed ? { ...segment, items: next } : segment;
}
