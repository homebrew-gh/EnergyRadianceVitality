import {
  addDays,
  formatIsoDate,
  type CardioDayLog,
  type CardioHeartRateSummary,
  type HeartRateLoadSummary,
  type WeightDayLog,
} from "./trainingHistory";
import type { TrainingProfilePayload } from "./trainingProfile";

export type ProgressionPolicyId =
  | "autoregulated_step"
  | "double_progression"
  | "hypertrophy_volume"
  | "strength_dup"
  | "cardio_base"
  | "conditioning_density"
  | "movement_quality";

export type HeartRateEvidenceStatus = "missing" | "partial" | "usable";
export type HeartRateEvidenceConfidence = "none" | "low" | "medium" | "high";

export type HeartRateGuardrailContext = {
  status: HeartRateEvidenceStatus;
  confidence: HeartRateEvidenceConfidence;
  sessionsWithHr: number;
  sessionsWithZoneLoad: number;
  currentWeekZoneSeconds: number[];
  baselineWeeklyZoneSeconds: number[];
  currentWeekHighIntensitySeconds: number;
  baselineWeeklyHighIntensitySeconds: number;
  highIntensityDeltaRatio: number | null;
  notes: string[];
};

export type ProgressionGuardrailContext = {
  policyId: ProgressionPolicyId;
  policyLabel: string;
  heartRate: HeartRateGuardrailContext;
  rules: string[];
};

type DatedHeartRate = {
  date: string;
  heartRate: CardioHeartRateSummary;
};

const EMPTY_ZONES = [0, 0, 0, 0, 0];

function addZoneSeconds(target: number[], source: number[]): void {
  for (let i = 0; i < 5; i += 1) {
    target[i] += Math.max(0, Math.round(source[i] ?? 0));
  }
}

function normalizedZones(load: HeartRateLoadSummary | null | undefined): number[] | null {
  if (!load?.zoneSeconds?.length) return null;
  const zones = EMPTY_ZONES.slice();
  addZoneSeconds(zones, load.zoneSeconds);
  return zones;
}

function collectDatedHeartRate(weightLogs: WeightDayLog[], cardioLogs: CardioDayLog[]): DatedHeartRate[] {
  const out: DatedHeartRate[] = [];
  for (const log of weightLogs) {
    for (const workout of log.workouts) {
      if (workout.heartRate) out.push({ date: log.date, heartRate: workout.heartRate });
      const linked = workout.workoutLink?.sessionHeartRate;
      if (linked) out.push({ date: log.date, heartRate: linked });
    }
  }
  for (const log of cardioLogs) {
    for (const session of log.sessions) {
      if (session.heartRate) out.push({ date: log.date, heartRate: session.heartRate });
      const linked = session.workoutLink?.sessionHeartRate;
      if (linked) out.push({ date: log.date, heartRate: linked });
    }
  }
  return out.sort((a, b) => a.date.localeCompare(b.date));
}

export function buildHeartRateGuardrailContext(options: {
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  computedAtMs?: number;
}): HeartRateGuardrailContext {
  const dated = collectDatedHeartRate(options.weightLogs, options.cardioLogs);
  if (dated.length === 0) {
    return {
      status: "missing",
      confidence: "none",
      sessionsWithHr: 0,
      sessionsWithZoneLoad: 0,
      currentWeekZoneSeconds: EMPTY_ZONES.slice(),
      baselineWeeklyZoneSeconds: EMPTY_ZONES.slice(),
      currentWeekHighIntensitySeconds: 0,
      baselineWeeklyHighIntensitySeconds: 0,
      highIntensityDeltaRatio: null,
      notes: ["No heart-rate data available; ignore HR guardrails."],
    };
  }

  const todayIso = formatIsoDate(new Date(options.computedAtMs ?? Date.now()));
  const currentStart = addDays(todayIso, -6);
  const baselineStart = addDays(currentStart, -21);
  const currentWeek = EMPTY_ZONES.slice();
  const baselineTotal = EMPTY_ZONES.slice();
  let sessionsWithZoneLoad = 0;

  for (const row of dated) {
    const zones = normalizedZones(row.heartRate.load);
    if (!zones) continue;
    sessionsWithZoneLoad += 1;
    if (row.date >= currentStart && row.date <= todayIso) {
      addZoneSeconds(currentWeek, zones);
    } else if (row.date >= baselineStart && row.date < currentStart) {
      addZoneSeconds(baselineTotal, zones);
    }
  }

  const baselineWeekly = baselineTotal.map((seconds) => Math.round(seconds / 3));
  const currentHigh = currentWeek[3] + currentWeek[4];
  const baselineHigh = baselineWeekly[3] + baselineWeekly[4];
  const deltaRatio = baselineHigh > 0 ? currentHigh / baselineHigh : null;
  const hasUsableZoneData = sessionsWithZoneLoad >= 3;
  const status: HeartRateEvidenceStatus = hasUsableZoneData ? "usable" : "partial";
  const confidence: HeartRateEvidenceConfidence =
    sessionsWithZoneLoad >= 8 ? "high" : sessionsWithZoneLoad >= 3 ? "medium" : "low";
  const notes: string[] = [];
  if (sessionsWithZoneLoad === 0) {
    notes.push("HR summaries exist, but no zone-load summaries are available yet.");
  } else {
    notes.push(`${sessionsWithZoneLoad} session(s) include zone-load summaries.`);
  }
  if (deltaRatio != null && deltaRatio >= 1.4 && currentHigh >= 20 * 60) {
    notes.push("Current-week Z4/Z5 exposure is elevated versus the recent baseline.");
  }

  return {
    status,
    confidence,
    sessionsWithHr: dated.length,
    sessionsWithZoneLoad,
    currentWeekZoneSeconds: currentWeek,
    baselineWeeklyZoneSeconds: baselineWeekly,
    currentWeekHighIntensitySeconds: currentHigh,
    baselineWeeklyHighIntensitySeconds: baselineHigh,
    highIntensityDeltaRatio: deltaRatio,
    notes,
  };
}

export function progressionPolicyForProfile(profile: TrainingProfilePayload): {
  id: ProgressionPolicyId;
  label: string;
} {
  const presets = new Set(profile.stylePresetIds);
  if (presets.has("strength_powerlifting")) {
    return { id: "strength_dup", label: "Strength DUP / Weekly Undulation" };
  }
  if (presets.has("hypertrophy_bodybuilding")) {
    return { id: "hypertrophy_volume", label: "Hypertrophy Volume Progression" };
  }
  if (presets.has("zone2_endurance")) {
    return { id: "cardio_base", label: "Zone 2 Base Progression" };
  }
  if (presets.has("hiit_conditioning")) {
    return { id: "conditioning_density", label: "Conditioning Density Progression" };
  }
  if (presets.has("mobility_movement") || presets.has("calisthenics_minimalist")) {
    return { id: "movement_quality", label: "Movement Quality Progression" };
  }
  if (profile.primaryGoal === "hypertrophy") {
    return { id: "hypertrophy_volume", label: "Hypertrophy Volume Progression" };
  }
  if (profile.primaryGoal === "strength") {
    return { id: "double_progression", label: "Double Progression" };
  }
  return { id: "autoregulated_step", label: "Autoregulated Step Progression" };
}

export function buildProgressionGuardrailContext(options: {
  profile: TrainingProfilePayload;
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  computedAtMs?: number;
}): ProgressionGuardrailContext {
  const policy = progressionPolicyForProfile(options.profile);
  const heartRate = buildHeartRateGuardrailContext(options);
  return {
    policyId: policy.id,
    policyLabel: policy.label,
    heartRate,
    rules: [
      "Do not require heart-rate data; treat it as optional evidence only.",
      "Use HR as a soft fatigue/cardio-load signal unless non-HR progression signals also agree.",
      "Aggregate HR at whole-workout or segment level for circuits, supersets, intervals, and conditioning blocks.",
      "Never directly increase or decrease strength loads from per-exercise HR alone.",
      "Prefer holds or reduced high-intensity cardio when current-week Z4/Z5 exposure is elevated.",
    ],
  };
}
