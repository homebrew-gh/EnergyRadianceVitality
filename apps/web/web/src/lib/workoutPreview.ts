import type {
  CardioCatalogActivity,
  StretchCatalogEntry,
  WeightCatalogExercise,
} from "./catalog";
import type { CardioRoutine } from "./cardioTraining";
import {
  cardioItemSummary,
  mobilityItemSummary,
  prescriptionSetCount,
  prescriptionSummary,
  segmentItems,
  segmentKindLabel,
  type WorkoutCardioPrescription,
  type WorkoutItem,
  type WorkoutSegment,
  type WorkoutWeightPrescription,
} from "./workoutTraining";
import { exerciseLabel } from "./weightTraining";
import { stretchLabel } from "./stretchTraining";

export type WorkoutPreviewItem = {
  key: string;
  kind: string;
  title: string;
  detail: string;
  estimatedSeconds: number;
};

export type WorkoutPreviewSegment = {
  key: string;
  title: string;
  kindLabel: string;
  itemCount: number;
  rounds: number | null;
  estimatedSeconds: number;
  items: WorkoutPreviewItem[];
};

export type WorkoutPreview = {
  estimatedSeconds: number;
  segmentCount: number;
  itemCount: number;
  strengthItemCount: number;
  cardioItemCount: number;
  mobilityItemCount: number;
  restSeconds: number;
  segments: WorkoutPreviewSegment[];
};

type PreviewCatalogs = {
  exercises: WeightCatalogExercise[];
  stretchCatalog: StretchCatalogEntry[];
  cardioCatalog: CardioCatalogActivity[];
  cardioRoutines: CardioRoutine[];
};

const DEFAULT_WEIGHT_SET_SECONDS = 45;
const DEFAULT_MAX_REPS_SECONDS = 60;
const DEFAULT_NOTE_SECONDS = 10;

export function formatDuration(seconds: number): string {
  if (seconds <= 0) return "0 min";
  const minutes = Math.max(1, Math.round(seconds / 60));
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return remainder > 0 ? `${hours} hr ${remainder} min` : `${hours} hr`;
}

function positiveSeconds(value: number | null | undefined): number {
  return typeof value === "number" && value > 0 ? value : 0;
}

function weightWorkSeconds(prescription: WorkoutWeightPrescription | undefined): number {
  const sets = Math.max(1, prescriptionSetCount(prescription));
  if (prescription?.mode === "max_reps") return sets * DEFAULT_MAX_REPS_SECONDS;
  if (prescription?.mode === "time_based") {
    const duration = positiveSeconds(prescription.durationSeconds) || DEFAULT_WEIGHT_SET_SECONDS;
    return sets * duration;
  }
  const perSetDurations = prescription?.sets
    ?.map((set) => positiveSeconds(set.durationSeconds ?? set.targetDurationSeconds))
    .filter((duration) => duration > 0);
  if (perSetDurations != null && perSetDurations.length > 0) {
    return perSetDurations.reduce((sum, duration) => sum + duration, 0);
  }
  return sets * DEFAULT_WEIGHT_SET_SECONDS;
}

function estimateWeightItemSeconds(
  prescription: WorkoutWeightPrescription | undefined,
  includeAfterExerciseRest: boolean,
): number {
  const sets = Math.max(1, prescriptionSetCount(prescription));
  const restBetweenSets = positiveSeconds(prescription?.restBetweenSetsSeconds) * Math.max(0, sets - 1);
  const restAfterExercise = includeAfterExerciseRest
    ? positiveSeconds(prescription?.restAfterExerciseSeconds)
    : 0;
  return weightWorkSeconds(prescription) + restBetweenSets + restAfterExercise;
}

function estimateCardioSeconds(
  cardio: WorkoutCardioPrescription,
  cardioRoutines: CardioRoutine[],
): number {
  if (cardio.cardioRoutineId) {
    const routine = cardioRoutines.find((entry) => entry.id === cardio.cardioRoutineId);
    if (routine?.steps?.length) {
      return routine.steps.reduce(
        (sum, step) => sum + Math.max(0, step.targetDurationMinutes ?? 0) * 60,
        0,
      );
    }
    if (routine?.targetDurationMinutes != null) return Math.max(0, routine.targetDurationMinutes) * 60;
  }

  const mode = cardio.mode ?? "steady";
  if (mode === "sprint_intervals") {
    const rounds = Math.max(1, cardio.rounds ?? 1);
    return rounds * (positiveSeconds(cardio.workSeconds) + positiveSeconds(cardio.restSeconds));
  }
  if (mode === "interval_template") {
    const outerRounds = Math.max(1, cardio.outerRounds ?? 1);
    const legSeconds = (cardio.legs ?? []).reduce(
      (sum, leg) => sum + positiveSeconds(leg.workSeconds) + positiveSeconds(leg.restSeconds),
      0,
    );
    return outerRounds * legSeconds;
  }
  return Math.max(0, cardio.targetMinutes ?? 0) * 60;
}

function estimateMobilitySeconds(item: Extract<WorkoutItem, { type: "mobility" }>): number {
  if (item.mobility.holdSecondsPerSide != null && item.mobility.holdSecondsPerSide > 0) {
    return item.mobility.holdSecondsPerSide * 2;
  }
  return positiveSeconds(item.mobility.holdSeconds);
}

function itemKind(item: WorkoutItem): string {
  switch (item.type) {
    case "weight":
      return "Lift";
    case "cardio":
      return "Cardio";
    case "mobility":
      return "Mobility";
    case "note":
      return "Note";
    case "rest":
      return "Rest";
  }
}

function itemTitle(item: WorkoutItem, catalogs: PreviewCatalogs): string {
  switch (item.type) {
    case "weight":
      return item.title?.trim() || exerciseLabel(item.exerciseId, catalogs.exercises);
    case "cardio":
      return (
        item.title?.trim() ||
        catalogs.cardioCatalog.find((activity) => activity.id === item.cardio.activity)?.displayName ||
        item.cardio.activity
      );
    case "mobility":
      return item.title?.trim() || stretchLabel(item.mobility.catalogId, catalogs.stretchCatalog);
    case "note":
      return item.text.trim() || "Coach note";
    case "rest":
      return "Rest";
  }
}

function itemDetail(item: WorkoutItem, catalogs: PreviewCatalogs): string {
  switch (item.type) {
    case "weight":
      return prescriptionSummary(item.prescription);
    case "cardio": {
      const activityLabel =
        catalogs.cardioCatalog.find((activity) => activity.id === item.cardio.activity)?.displayName ??
        item.cardio.activity;
      return cardioItemSummary(item, activityLabel);
    }
    case "mobility":
      return mobilityItemSummary(
        item,
        stretchLabel(item.mobility.catalogId, catalogs.stretchCatalog),
      );
    case "note":
      return "Read during the live workout.";
    case "rest":
      return `${formatDuration(item.durationSeconds)} recovery`;
  }
}

function estimateItemSeconds(
  item: WorkoutItem,
  catalogs: PreviewCatalogs,
  includeAfterExerciseRest: boolean,
): number {
  switch (item.type) {
    case "weight":
      return estimateWeightItemSeconds(item.prescription, includeAfterExerciseRest);
    case "cardio":
      return estimateCardioSeconds(item.cardio, catalogs.cardioRoutines);
    case "mobility":
      return estimateMobilitySeconds(item);
    case "note":
      return DEFAULT_NOTE_SECONDS;
    case "rest":
      return Math.max(0, item.durationSeconds);
  }
}

export function buildWorkoutPreview(
  segments: WorkoutSegment[],
  catalogs: PreviewCatalogs,
): WorkoutPreview {
  let estimatedSeconds = 0;
  let itemCount = 0;
  let strengthItemCount = 0;
  let cardioItemCount = 0;
  let mobilityItemCount = 0;
  let restSeconds = 0;

  const previewSegments = segments.map((segment, segmentIndex) => {
    const items = segmentItems(segment);
    const isRoundRobin = segment.kind === "circuit" || segment.kind === "superset";
    const rounds = isRoundRobin ? Math.max(1, segment.rounds ?? 1) : null;
    const previewItems = items.map((item, itemIndex) => {
      const seconds = estimateItemSeconds(item, catalogs, !isRoundRobin);
      if (item.type === "weight") strengthItemCount += 1;
      if (item.type === "cardio") cardioItemCount += 1;
      if (item.type === "mobility") mobilityItemCount += 1;
      if (item.type === "rest") restSeconds += seconds;
      return {
        key: `${segment.id ?? segmentIndex}-${item.type}-${itemIndex}`,
        kind: itemKind(item),
        title: itemTitle(item, catalogs),
        detail: itemDetail(item, catalogs),
        estimatedSeconds: seconds,
      };
    });

    const itemSeconds = previewItems.reduce((sum, item) => sum + item.estimatedSeconds, 0);
    const segmentRestPolicy = segment.restPolicy ?? {};
    let segmentSeconds = itemSeconds;
    if (isRoundRobin) {
      const betweenItems = positiveSeconds(segmentRestPolicy.restBetweenItemsSeconds) * Math.max(0, items.length - 1);
      const afterRound = positiveSeconds(segmentRestPolicy.restAfterRoundSeconds);
      restSeconds += rounds! * betweenItems + rounds! * afterRound;
      segmentSeconds = rounds! * (itemSeconds + betweenItems + afterRound);
    }
    const afterSegment = positiveSeconds(segment.restAfterSeconds);
    restSeconds += afterSegment;
    segmentSeconds += afterSegment;
    estimatedSeconds += segmentSeconds;
    itemCount += items.length;

    return {
      key: segment.id ?? String(segmentIndex),
      title: segment.title?.trim() || segmentKindLabel(segment.kind),
      kindLabel: segmentKindLabel(segment.kind),
      itemCount: items.length,
      rounds,
      estimatedSeconds: segmentSeconds,
      items: previewItems,
    };
  });

  return {
    estimatedSeconds,
    segmentCount: segments.length,
    itemCount,
    strengthItemCount,
    cardioItemCount,
    mobilityItemCount,
    restSeconds,
    segments: previewSegments,
  };
}
