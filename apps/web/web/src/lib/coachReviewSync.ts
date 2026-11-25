/**
 * Latest coach review for the phone: kind 30078, NIP-44 encrypt-to-self, d-tag `erv/coach/review`
 * (replaceable, so the relay keeps only the newest). Web publishes; Android reads it read-only.
 */

import type { BodyWeightUnit } from "./fitnessEquipment";
import type { CoachLiftTrend, CoachReviewJson } from "./coachReview";
import type { WeightExercise } from "./weightTraining";
import { exerciseLabel } from "./weightTraining";

export const COACH_REVIEW_D_TAG = "erv/coach/review";
export const COACH_REVIEW_SYNC_VERSION = 1;

const SEND_TO_PHONE_KEY = "erv-coach-review-send-to-phone";

export type CoachLiftTarget = {
  /** Catalog or custom exercise id, so Android can match the lift in a live workout. */
  exerciseId: string;
  name: string;
  trend: CoachLiftTrend;
  next: string;
  why: string;
};

export type CoachReviewSyncPayload = {
  ervCoachReviewVersion: number;
  generatedAtEpochSeconds: number;
  periodLabel: string;
  periodStart: string | null;
  periodEnd: string;
  weightUnit: "lb" | "kg";
  model: string;
  promptVersion: string;
  review: CoachReviewJson;
  liftTargets: CoachLiftTarget[];
};

export function buildCoachReviewSyncPayload(options: {
  review: CoachReviewJson;
  exercises: WeightExercise[];
  generatedAt: Date;
  periodLabel: string;
  periodStart: string | null;
  periodEnd: string;
  unit: BodyWeightUnit;
  model: string;
  promptVersion: string;
}): CoachReviewSyncPayload {
  return {
    ervCoachReviewVersion: COACH_REVIEW_SYNC_VERSION,
    generatedAtEpochSeconds: Math.floor(options.generatedAt.getTime() / 1000),
    periodLabel: options.periodLabel,
    periodStart: options.periodStart,
    periodEnd: options.periodEnd,
    weightUnit: options.unit === "KG" ? "kg" : "lb",
    model: options.model,
    promptVersion: options.promptVersion,
    review: options.review,
    liftTargets: liftTargets(options.review, options.exercises),
  };
}

/** Match review lift names back to exercise ids. Unmatched lifts stay in the review only. */
export function liftTargets(review: CoachReviewJson, exercises: WeightExercise[]): CoachLiftTarget[] {
  const byName = new Map<string, string>();
  for (const exercise of exercises) {
    const label = exerciseLabel(exercise.id, exercises).trim().toLowerCase();
    if (label && !byName.has(label)) byName.set(label, exercise.id);
  }
  const out: CoachLiftTarget[] = [];
  for (const lift of review.lifts) {
    const exerciseId = byName.get(lift.name.trim().toLowerCase());
    if (!exerciseId) continue;
    out.push({ exerciseId, name: lift.name, trend: lift.trend, next: lift.next, why: lift.why });
  }
  return out;
}

export function readSendToPhone(): boolean {
  try {
    return window.localStorage.getItem(SEND_TO_PHONE_KEY) !== "0";
  } catch {
    return true;
  }
}

export function writeSendToPhone(value: boolean): void {
  try {
    window.localStorage.setItem(SEND_TO_PHONE_KEY, value ? "1" : "0");
  } catch {
    // The toggle resets to on next visit; nothing else depends on it.
  }
}
