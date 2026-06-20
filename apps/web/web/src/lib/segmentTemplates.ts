/** Pre-built segment shells for the workout composer (+ menu). */

import type { WorkoutItem, WorkoutSegment, WorkoutSegmentKind } from "./workoutTraining";
import { defaultRestPolicy, defaultSegmentTitle } from "./workoutTraining";

export type WorkoutSegmentTemplate = {
  id: string;
  label: string;
  description: string;
  segment: Omit<WorkoutSegment, "id">;
};

function cloneItem(item: WorkoutItem): WorkoutItem {
  const id = crypto.randomUUID();
  switch (item.type) {
    case "weight":
      return {
        ...item,
        id,
        prescription: item.prescription ? { ...item.prescription } : undefined,
      };
    case "cardio":
      return {
        ...item,
        id,
        cardio: {
          ...item.cardio,
          legs: item.cardio.legs?.map((leg) => ({ ...leg })),
        },
      };
    case "mobility":
      return { ...item, id, mobility: { ...item.mobility } };
    case "note":
    case "rest":
      return { ...item, id };
  }
}

export function instantiateSegmentTemplate(template: WorkoutSegmentTemplate): WorkoutSegment {
  return {
    ...template.segment,
    id: crypto.randomUUID(),
    items: (template.segment.items ?? []).map(cloneItem),
  };
}

function shell(kind: WorkoutSegmentKind, title: string, items: WorkoutItem[] = []): Omit<WorkoutSegment, "id"> {
  const base: Omit<WorkoutSegment, "id"> = { kind, title, items };
  if (kind === "circuit" || kind === "superset") {
    return { ...base, rounds: 3, restPolicy: defaultRestPolicy() };
  }
  return base;
}

export const WORKOUT_SEGMENT_TEMPLATES: WorkoutSegmentTemplate[] = [
  {
    id: "warmup_flow",
    label: "+ Warm-up flow",
    description: "Easy cardio + coach note",
    segment: shell("composite", "Warm-up", [
      {
        type: "cardio",
        id: crypto.randomUUID(),
        cardio: {
          activity: "STATIONARY_BIKE",
          mode: "steady",
          targetMinutes: 10,
          hrTargetBpm: 115,
        },
      },
      {
        type: "note",
        id: crypto.randomUUID(),
        text: "Easy pace; optional HR cue if monitor connected.",
      },
    ]),
  },
  {
    id: "main_lift",
    label: "+ Main lift",
    description: "Straight sets with rep range + RIR",
    segment: shell("straight_sets", "Main lift", [
      {
        type: "weight",
        id: crypto.randomUUID(),
        exerciseId: "erv-weight-exercise-squat-v1",
        prescription: {
          mode: "straight",
          setCount: 3,
          repRangeMin: 8,
          repRangeMax: 12,
          targetRir: 2,
          restBetweenSetsSeconds: 120,
        },
      },
    ]),
  },
  {
    id: "superset_pair",
    label: "+ Superset pair",
    description: "Push + pull, 3 rounds",
    segment: {
      kind: "superset",
      title: "Push + pull",
      rounds: 3,
      restPolicy: { restBetweenItemsSeconds: 60, restAfterRoundSeconds: 60 },
      items: [
        {
          type: "weight",
          id: crypto.randomUUID(),
          exerciseId: "erv-weight-exercise-bench-v1",
          prescription: {
            mode: "straight",
            setCount: 1,
            repRangeMin: 8,
            repRangeMax: 12,
            targetRir: 2,
          },
        },
        {
          type: "weight",
          id: crypto.randomUUID(),
          exerciseId: "erv-weight-exercise-bw-pullup-v1",
          prescription: { mode: "max_reps", setCount: 1 },
        },
      ],
    },
  },
  {
    id: "core_circuit",
    label: "+ Core circuit",
    description: "Mixed reps + timed hold",
    segment: {
      kind: "circuit",
      title: "Core circuit",
      rounds: 2,
      restPolicy: { restBetweenItemsSeconds: 10, restAfterRoundSeconds: 60 },
      items: [
        {
          type: "weight",
          id: crypto.randomUUID(),
          exerciseId: "erv-weight-exercise-bw-hanging-knee-raise-v1",
          prescription: { mode: "straight", setCount: 1, targetReps: 12 },
        },
        {
          type: "weight",
          id: crypto.randomUUID(),
          exerciseId: "erv-weight-exercise-bw-plank-v1",
          prescription: { mode: "time_based", durationSeconds: 45 },
        },
      ],
    },
  },
  {
    id: "zone2_cardio",
    label: "+ Zone 2 cardio",
    description: "Steady state with HR range",
    segment: shell("cardio", "Zone 2", [
      {
        type: "cardio",
        id: crypto.randomUUID(),
        cardio: {
          activity: "WALK",
          mode: "steady",
          targetMinutes: 45,
          hrTargetMinBpm: 110,
          hrTargetMaxBpm: 130,
          hrZoneLabel: "Zone 2",
          logFields: ["INCLINE", "SPEED"],
        },
      },
    ]),
  },
  {
    id: "sprint_intervals",
    label: "+ Sprint intervals",
    description: "Flat work/rest rounds",
    segment: shell("interval", "Sprint intervals", [
      {
        type: "cardio",
        id: crypto.randomUUID(),
        cardio: {
          activity: "STATIONARY_BIKE",
          mode: "sprint_intervals",
          rounds: 10,
          workSeconds: 60,
          restSeconds: 60,
        },
      },
    ]),
  },
  {
    id: "hiit_block",
    label: "+ HIIT block",
    description: "Nested outer rounds + work leg",
    segment: shell("interval", "HIIT block", [
      {
        type: "cardio",
        id: crypto.randomUUID(),
        cardio: {
          activity: "STATIONARY_BIKE",
          mode: "interval_template",
          outerRounds: 3,
          hrTargetBpm: 168,
          legs: [{ workSeconds: 240, restSeconds: 240, label: "Work leg" }],
        },
      },
    ]),
  },
  {
    id: "mobility_cooldown",
    label: "+ Mobility cooldown",
    description: "Per-side hold stretch",
    segment: shell("mobility", "Cooldown", [
      {
        type: "mobility",
        id: crypto.randomUUID(),
        mobility: {
          catalogId: "builtin_hip_flexor_lunge",
          holdSecondsPerSide: 120,
        },
      },
    ]),
  },
];

export function templatesForKind(kind: WorkoutSegmentKind): WorkoutSegmentTemplate[] {
  return WORKOUT_SEGMENT_TEMPLATES.filter((t) => t.segment.kind === kind);
}

export function emptySegmentForKind(kind: WorkoutSegmentKind): WorkoutSegment {
  const base: WorkoutSegment = {
    id: crypto.randomUUID(),
    kind,
    title: defaultSegmentTitle(kind),
    items: [],
  };
  if (kind === "circuit" || kind === "superset") {
    return { ...base, rounds: 3, restPolicy: defaultRestPolicy() };
  }
  return base;
}
