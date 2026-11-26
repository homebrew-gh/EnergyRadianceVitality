/**
 * Built-in fitness standards. Targets are in each measure's canonical unit (see `standards.ts`):
 * ratios of bodyweight, kg, reps, seconds, meters, or cm beyond your height.
 *
 * Keep ids stable: followed standards and test results are stored by these ids.
 */
import type { FitnessStandard, StandardTierValues, StandardVariant } from "./standards";

const LB = 0.45359237;
const MILE = 1609.344;
const FOOT_M = 0.3048;
const INCH_CM = 2.54;

const BENCH = ["erv-weight-exercise-bench-v1"];
const SQUAT = ["erv-weight-exercise-squat-v1"];
const DEADLIFT = ["erv-weight-exercise-deadlift-v1"];
const PULL_UP = ["erv-weight-exercise-bw-pullup-v1"];
const PLANK = ["erv-weight-exercise-bw-plank-v1"];
const PULL_UP_NAME = "^(weighted )?pull[- ]?ups?$";
const TRAP_BAR_NAME = "(trap|hex)[ -]?bar";
const HAND_RELEASE_NAME = "hand[- ]?release";

const t = (base: number, strong: number, elite: number): StandardTierValues => ({
  BASE: base,
  STRONG: strong,
  ELITE: elite,
});
const mmss = (m: number, s = 0) => m * 60 + s;
const female = (tiers: StandardTierValues): StandardVariant => ({ sex: "female", tiers });

export const ERV_GENERAL: FitnessStandard = {
  id: "erv-general-v1",
  name: "ERV General",
  description:
    "Strength, pulling, core, speed and load carrying. Lifts use your estimated 1RM against bodyweight. Separate targets for women where it changes the picture.",
  events: [
    {
      id: "squat",
      name: "Squat",
      detail: "Estimated 1RM, times bodyweight",
      measure: { kind: "LIFT_MAX_BW", reps: 1 },
      exerciseIds: SQUAT,
      lowerIsBetter: false,
      tiers: t(1.25, 1.5, 2),
      variants: [female(t(0.75, 1, 1.5))],
    },
    {
      id: "bench",
      name: "Bench Press",
      detail: "Estimated 1RM, times bodyweight",
      measure: { kind: "LIFT_MAX_BW", reps: 1 },
      exerciseIds: BENCH,
      lowerIsBetter: false,
      tiers: t(1, 1.25, 1.5),
      variants: [female(t(0.5, 0.75, 1))],
    },
    {
      id: "deadlift",
      name: "Deadlift",
      detail: "Estimated 1RM, times bodyweight",
      measure: { kind: "LIFT_MAX_BW", reps: 1 },
      exerciseIds: DEADLIFT,
      lowerIsBetter: false,
      tiers: t(1.5, 2, 2.5),
      variants: [female(t(1, 1.25, 1.75))],
    },
    {
      id: "pull-ups",
      name: "Pull-Ups",
      detail: "Most reps in one set",
      measure: { kind: "MAX_REPS" },
      exerciseIds: PULL_UP,
      exerciseNamePattern: PULL_UP_NAME,
      lowerIsBetter: false,
      tiers: t(8, 12, 18),
      variants: [female(t(3, 6, 10))],
    },
    {
      id: "plank",
      name: "Plank",
      detail: "Longest hold",
      measure: { kind: "LONGEST_HOLD" },
      exerciseIds: PLANK,
      lowerIsBetter: false,
      tiers: t(mmss(2), mmss(3), mmss(4)),
    },
    {
      id: "mile",
      name: "Mile Run",
      detail: "Fastest mile",
      measure: { kind: "FASTEST", meters: MILE, cardioGroups: ["RUN"] },
      lowerIsBetter: true,
      tiers: t(mmss(9), mmss(7, 30), mmss(6, 30)),
      variants: [female(t(mmss(10, 30), mmss(9), mmss(7, 30)))],
    },
    {
      id: "ruck-12mi",
      name: "12-Mile Ruck",
      detail: "Fastest 12 miles with at least 35 lb",
      measure: { kind: "FASTEST", meters: 12 * MILE, cardioGroups: ["RUCK"], minPackKg: 35 * LB },
      lowerIsBetter: true,
      tiers: t(mmss(210), mmss(180), mmss(160)),
      variants: [female(t(mmss(225), mmss(195), mmss(175)))],
    },
  ],
};

export const SEVEN_EVENT: FitnessStandard = {
  id: "seven-event-v1",
  name: "Seven-Event Performance",
  description:
    "Seven events across power, pressing, pulling, hinge strength, core, grip and speed, mostly scaled to bodyweight. One set of targets for everyone.",
  events: [
    {
      id: "broad-jump",
      name: "Broad Jump",
      detail: "Standing broad jump against your height",
      measure: { kind: "MANUAL", unit: "CM_OVER_HEIGHT" },
      lowerIsBetter: false,
      tiers: t(0, 12 * INCH_CM, 24 * INCH_CM),
    },
    {
      id: "bench-bw-reps",
      name: "Bench Press",
      detail: "Bodyweight on the bar, most reps",
      measure: { kind: "REPS_AT_BW" },
      exerciseIds: BENCH,
      lowerIsBetter: false,
      tiers: t(10, 15, 20),
    },
    {
      id: "pull-ups",
      name: "Pull-Ups",
      detail: "Most reps in one set",
      measure: { kind: "MAX_REPS" },
      exerciseIds: PULL_UP,
      exerciseNamePattern: PULL_UP_NAME,
      lowerIsBetter: false,
      tiers: t(10, 15, 20),
    },
    {
      id: "trap-bar-5",
      name: "Trap Bar Deadlift",
      detail: "Heaviest 5 reps, times bodyweight",
      measure: { kind: "LIFT_MAX_BW", reps: 5 },
      exerciseNamePattern: TRAP_BAR_NAME,
      fallbackExerciseIds: DEADLIFT,
      lowerIsBetter: false,
      tiers: t(1.5, 1.75, 2),
    },
    {
      id: "plank",
      name: "Plank",
      detail: "Longest hold",
      measure: { kind: "LONGEST_HOLD" },
      exerciseIds: PLANK,
      lowerIsBetter: false,
      tiers: t(mmss(2), mmss(2, 30), mmss(3)),
    },
    {
      id: "farmers-carry",
      name: "Farmer's Carry",
      detail: "Bodyweight in total, distance carried",
      measure: { kind: "MANUAL", unit: "METERS" },
      lowerIsBetter: false,
      tiers: t(175 * FOOT_M, 225 * FOOT_M, 250 * FOOT_M),
    },
    {
      id: "run-800m",
      name: "800 m Run",
      detail: "Fastest 800 m",
      measure: { kind: "FASTEST", meters: 800, cardioGroups: ["RUN"] },
      lowerIsBetter: true,
      tiers: t(mmss(3, 15), mmss(3), mmss(2, 45)),
    },
  ],
};

/** Age bands of the Army tables, in order. */
const AFT_AGES: [number, number][] = [
  [17, 21],
  [22, 26],
  [27, 31],
  [32, 36],
  [37, 41],
  [42, 46],
  [47, 51],
  [52, 56],
  [57, 61],
  [62, 200],
];

/** One row per age band: [60 points, 80 points, 100 points]. */
function aftVariants(rows: [number, number, number][]): StandardVariant[] {
  return rows.map(([base, strong, elite], i) => ({
    minAge: AFT_AGES[i][0],
    maxAge: AFT_AGES[i][1],
    tiers: t(base, strong, elite),
  }));
}

const lb = (n: number) => n * LB;

/*
 * Army Fitness Test, combat (sex-neutral) scale, from the scoring scales effective 1 June 2025
 * (https://www.army.mil/e2/downloads/rv7/aft/AFT_Scoring_Scales_250601.pdf). Base = 60 points,
 * Strong = 80, Elite = 100. Where the table has no exact 80-point entry the value is taken halfway
 * between 60 and 100 (deadlift, rounded to 10 lb) or from the neighbouring age (2-mile, 22-26).
 */
const AFT_DEADLIFT = aftVariants([
  [lb(150), lb(250), lb(340)],
  [lb(150), lb(250), lb(350)],
  [lb(150), lb(250), lb(350)],
  [lb(140), lb(250), lb(350)],
  [lb(140), lb(250), lb(350)],
  [lb(140), lb(250), lb(350)],
  [lb(140), lb(240), lb(340)],
  [lb(140), lb(230), lb(330)],
  [lb(140), lb(200), lb(250)],
  [lb(140), lb(190), lb(230)],
]);
const AFT_PUSH_UPS = aftVariants([
  [15, 37, 58],
  [14, 37, 61],
  [14, 37, 62],
  [13, 36, 60],
  [12, 35, 59],
  [11, 34, 57],
  [11, 32, 55],
  [10, 30, 51],
  [10, 18, 46],
  [10, 17, 43],
]);
const AFT_SDC = aftVariants([
  [mmss(2, 28), mmss(1, 53), mmss(1, 29)],
  [mmss(2, 31), mmss(1, 53), mmss(1, 30)],
  [mmss(2, 32), mmss(1, 55), mmss(1, 30)],
  [mmss(2, 36), mmss(1, 58), mmss(1, 33)],
  [mmss(2, 41), mmss(2, 2), mmss(1, 36)],
  [mmss(2, 45), mmss(2, 7), mmss(1, 40)],
  [mmss(2, 53), mmss(2, 14), mmss(1, 45)],
  [mmss(3, 0), mmss(2, 23), mmss(1, 52)],
  [mmss(3, 12), mmss(2, 29), mmss(1, 58)],
  [mmss(3, 16), mmss(2, 32), mmss(2, 9)],
]);
const AFT_PLANK = aftVariants([
  [mmss(1, 30), mmss(2, 35), mmss(3, 40)],
  [mmss(1, 25), mmss(2, 30), mmss(3, 35)],
  [mmss(1, 20), mmss(2, 25), mmss(3, 30)],
  [mmss(1, 15), mmss(2, 20), mmss(3, 25)],
  [mmss(1, 10), mmss(2, 15), mmss(3, 20)],
  [mmss(1, 10), mmss(2, 15), mmss(3, 20)],
  [mmss(1, 10), mmss(2, 15), mmss(3, 20)],
  [mmss(1, 10), mmss(2, 15), mmss(3, 20)],
  [mmss(1, 10), mmss(2, 15), mmss(3, 20)],
  [mmss(1, 10), mmss(2, 15), mmss(3, 20)],
]);
const AFT_TWO_MILE = aftVariants([
  [mmss(19, 57), mmss(17, 13), mmss(13, 22)],
  [mmss(19, 45), mmss(17, 17), mmss(13, 25)],
  [mmss(19, 45), mmss(17, 21), mmss(13, 25)],
  [mmss(20, 44), mmss(17, 16), mmss(13, 42)],
  [mmss(20, 44), mmss(17, 33), mmss(13, 42)],
  [mmss(22, 4), mmss(17, 47), mmss(14, 5)],
  [mmss(22, 4), mmss(18, 12), mmss(14, 30)],
  [mmss(22, 50), mmss(19, 0), mmss(15, 9)],
  [mmss(23, 36), mmss(19, 45), mmss(15, 28)],
  [mmss(23, 36), mmss(19, 45), mmss(15, 28)],
]);

export const ARMY_AFT: FitnessStandard = {
  id: "army-aft-2025",
  name: "Army Fitness Test",
  description:
    "The US Army's five-event test (2025), combat scale, which is the same for men and women and adjusted for age. Base is 60 points per event, Strong 80, Elite 100.",
  source: { label: "army.mil/aft", url: "https://www.army.mil/aft/" },
  events: [
    {
      id: "deadlift-3rm",
      name: "Hex Bar Deadlift",
      detail: "Heaviest 3 reps",
      measure: { kind: "LIFT_MAX_LOAD", reps: 3 },
      exerciseNamePattern: TRAP_BAR_NAME,
      fallbackExerciseIds: DEADLIFT,
      lowerIsBetter: false,
      tiers: AFT_DEADLIFT[0].tiers,
      variants: AFT_DEADLIFT,
    },
    {
      id: "hand-release-push-ups",
      name: "Hand-Release Push-Ups",
      detail: "Most reps in 2 minutes",
      measure: { kind: "MAX_REPS" },
      exerciseNamePattern: HAND_RELEASE_NAME,
      lowerIsBetter: false,
      tiers: AFT_PUSH_UPS[0].tiers,
      variants: AFT_PUSH_UPS,
    },
    {
      id: "sprint-drag-carry",
      name: "Sprint-Drag-Carry",
      detail: "5 x 50 m shuttles: sprint, sled drag, lateral, carry, sprint",
      measure: { kind: "MANUAL", unit: "SECONDS" },
      lowerIsBetter: true,
      tiers: AFT_SDC[0].tiers,
      variants: AFT_SDC,
    },
    {
      id: "plank",
      name: "Plank",
      detail: "Longest hold",
      measure: { kind: "LONGEST_HOLD" },
      exerciseIds: PLANK,
      lowerIsBetter: false,
      tiers: AFT_PLANK[0].tiers,
      variants: AFT_PLANK,
    },
    {
      id: "two-mile",
      name: "2-Mile Run",
      detail: "Fastest 2 miles",
      measure: { kind: "FASTEST", meters: 2 * MILE, cardioGroups: ["RUN"] },
      lowerIsBetter: true,
      tiers: AFT_TWO_MILE[0].tiers,
      variants: AFT_TWO_MILE,
    },
  ],
};

export const RUCK_12_MILE: FitnessStandard = {
  id: "ruck-12mi-v1",
  name: "12-Mile Ruck",
  description:
    "12 miles with at least 35 lb. Base is the Army's 3-hour standard for the Expert Infantry Badge; Strong and Elite are faster marks.",
  events: [
    {
      id: "ruck-12mi",
      name: "12-Mile Ruck",
      detail: "Fastest 12 miles with at least 35 lb",
      measure: { kind: "FASTEST", meters: 12 * MILE, cardioGroups: ["RUCK"], minPackKg: 35 * LB },
      lowerIsBetter: true,
      tiers: t(mmss(180), mmss(165), mmss(150)),
    },
  ],
};

export const BUILT_IN_STANDARDS: FitnessStandard[] = [ERV_GENERAL, SEVEN_EVENT, ARMY_AFT, RUCK_12_MILE];

export function builtInStandard(id: string): FitnessStandard | null {
  return BUILT_IN_STANDARDS.find((s) => s.id === id) ?? null;
}
