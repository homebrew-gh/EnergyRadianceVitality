/**
 * Training facts ERV computes before a coach review, so the model reasons over trends and
 * targets instead of doing arithmetic on raw logs. All loads are in the athlete's unit.
 */

import type { BodyWeightEntry } from "./bodyWeight";
import { kgToLb, type BodyWeightUnit } from "./fitnessEquipment";
import {
  addDays,
  parseIsoDate,
  volumeByMuscleGroup,
  type CardioDayLog,
  type WeightDayLog,
  type WeightSet,
} from "./trainingHistory";
import type { WeightExercise } from "./weightTraining";
import { exerciseLabel } from "./weightTraining";
import {
  segmentItems,
  type Workout,
  type WorkoutWeightPrescription,
} from "./workoutTraining";

export type LiftTrend = "up" | "flat" | "down" | "new";

export type LiftSessionFact = {
  date: string;
  sets: string;
  estimatedOneRepMax: number | null;
  avgRpe?: number;
};

export type LiftFact = {
  name: string;
  muscleGroup: string;
  plannedIn: string[];
  planned: string | null;
  sessionsInPeriod: number;
  /** Newest first. */
  recentSessions: LiftSessionFact[];
  trend: LiftTrend;
  flags: string[];
  ruleSuggestion: string | null;
};

export type WeekFact = {
  weekStart: string;
  weekEnd: string;
  trainingDays: number;
  strengthSessions: number;
  cardioSessions: number;
  cardioMinutes: number;
};

export type WeeklyTrainingFact = {
  plannedDaysPerWeek: number | null;
  averageTrainingDaysPerWeek: number;
  weeks: WeekFact[];
};

export type MuscleSetsFact = {
  muscleGroup: string;
  setsPerWeek: number;
};

export type CoachFactsInput = {
  weightLogs: WeightDayLog[];
  exercises: WeightExercise[];
  workouts: Workout[];
  unit: BodyWeightUnit;
  today: string;
  liftLimit: number;
  sessionsPerLift: number;
};

const LONG_GAP_DAYS = 14;
const MAX_WEEKS = 12;

type LiftSession = { date: string; sets: WeightSet[] };

export function buildLiftFacts(input: CoachFactsInput): LiftFact[] {
  const byExercise = collectLiftSessions(input.weightLogs);
  const plans = plannedPrescriptions(input.workouts);
  const exerciseById = new Map(input.exercises.map((exercise) => [exercise.id, exercise]));

  const ranked = [...byExercise.entries()]
    .filter(([, sessions]) => sessions.some((session) => session.sets.some(isWorkingSet)))
    .sort(([, a], [, b]) => {
      if (b.length !== a.length) return b.length - a.length;
      return b[0].date.localeCompare(a[0].date);
    })
    .slice(0, input.liftLimit);

  return ranked.map(([exerciseId, sessions]) => {
    const exercise = exerciseById.get(exerciseId);
    const muscleGroup = exercise?.muscleGroup?.trim().toLowerCase() || "unknown";
    const plan = plans.get(exerciseId) ?? null;
    return liftFact({
      name: exerciseLabel(exerciseId, input.exercises),
      muscleGroup,
      sessions,
      plan,
      unit: input.unit,
      today: input.today,
      sessionsPerLift: input.sessionsPerLift,
    });
  });
}

function liftFact(options: {
  name: string;
  muscleGroup: string;
  sessions: LiftSession[];
  plan: { prescription: WorkoutWeightPrescription | null; workoutNames: string[] } | null;
  unit: BodyWeightUnit;
  today: string;
  sessionsPerLift: number;
}): LiftFact {
  const { sessions, unit } = options;
  const prescription = options.plan?.prescription ?? null;
  const e1rms = sessions.map((session) => sessionOneRepMaxKg(session.sets));
  const loaded = e1rms.some((value) => value != null);
  const recent = sessions.slice(0, options.sessionsPerLift);
  const recentSessions = recent.map((session, index) => {
    const fact: LiftSessionFact = {
      date: session.date,
      sets: setsText(session.sets, unit),
      estimatedOneRepMax: e1rms[index] != null ? roundLoad(e1rms[index] as number, unit) : null,
    };
    const rpe = averageRpe(session.sets);
    if (rpe != null) fact.avgRpe = rpe;
    return fact;
  });

  const trend = liftTrend(sessions, e1rms, loaded);
  const flags: string[] = [];

  if (sessions.length >= 2) {
    const metric = loaded ? e1rms.map((value) => value ?? 0) : sessions.map(totalReps);
    const latest = metric[0];
    const earlierBest = Math.max(...metric.slice(1));
    if (latest > earlierBest) {
      flags.push("new best in period");
    } else {
      const bestIndex = metric.indexOf(Math.max(...metric));
      if (bestIndex >= 2) flags.push(`no new best for ${bestIndex} sessions`);
    }
  }

  const minReps = prescription?.repRangeMin ?? prescription?.targetReps ?? null;
  const maxReps = prescription?.repRangeMax ?? prescription?.targetReps ?? null;
  const plannedSets = prescription?.setCount ?? null;
  const latestWorking = sessions[0].sets.filter(isWorkingSet);
  const missedLatest = minReps != null && missedTarget(latestWorking, minReps);
  const missedPrevious =
    minReps != null && sessions.length >= 2 && missedTarget(sessions[1].sets.filter(isWorkingSet), minReps);
  const hitTop =
    maxReps != null && hitTopOfRange(latestWorking, maxReps, plannedSets ?? latestWorking.length);

  if (missedLatest) flags.push(`missed rep target last session (planned ${minReps}+)`);
  else if (hitTop) flags.push("hit top of rep range on every set");

  const latestRpe = averageRpe(sessions[0].sets);
  if (latestRpe != null && latestRpe >= 9.5) flags.push(`RPE ${latestRpe} last session`);

  const daysSince = daysBetween(sessions[0].date, options.today);
  if (daysSince > LONG_GAP_DAYS) flags.push(`last done ${daysSince} days ago`);

  return {
    name: options.name,
    muscleGroup: options.muscleGroup,
    plannedIn: options.plan?.workoutNames ?? [],
    planned: prescription ? prescriptionText(prescription, unit) : null,
    sessionsInPeriod: sessions.length,
    recentSessions,
    trend,
    flags,
    ruleSuggestion: loaded
      ? ruleSuggestion({
          sets: latestWorking,
          unit,
          lowerBody: isLowerBody(options.muscleGroup),
          minReps,
          maxReps,
          hitTop,
          missedLatest,
          missedPrevious,
        })
      : null,
  };
}

function collectLiftSessions(weightLogs: WeightDayLog[]): Map<string, LiftSession[]> {
  const byExercise = new Map<string, LiftSession[]>();
  const days = [...weightLogs].sort((a, b) => b.date.localeCompare(a.date));
  for (const day of days) {
    for (const session of day.workouts) {
      const merged = new Map<string, WeightSet[]>();
      for (const entry of session.entries) {
        if (entry.sets.length === 0) continue;
        merged.set(entry.exerciseId, [...(merged.get(entry.exerciseId) ?? []), ...entry.sets]);
      }
      for (const [exerciseId, sets] of merged) {
        const list = byExercise.get(exerciseId) ?? [];
        list.push({ date: day.date, sets });
        byExercise.set(exerciseId, list);
      }
    }
  }
  return byExercise;
}

/** First saved workout that programs the exercise supplies its prescription. */
function plannedPrescriptions(
  workouts: Workout[],
): Map<string, { prescription: WorkoutWeightPrescription | null; workoutNames: string[] }> {
  const out = new Map<string, { prescription: WorkoutWeightPrescription | null; workoutNames: string[] }>();
  for (const workout of workouts) {
    for (const segment of workout.segments) {
      for (const item of segmentItems(segment)) {
        if (item.type !== "weight") continue;
        const existing = out.get(item.exerciseId);
        if (existing) {
          if (!existing.workoutNames.includes(workout.name)) existing.workoutNames.push(workout.name);
          if (!existing.prescription && item.prescription) existing.prescription = item.prescription;
          continue;
        }
        out.set(item.exerciseId, {
          prescription: item.prescription ?? null,
          workoutNames: [workout.name],
        });
      }
    }
  }
  return out;
}

function liftTrend(sessions: LiftSession[], e1rms: (number | null)[], loaded: boolean): LiftTrend {
  if (sessions.length < 2) return "new";
  const window = Math.min(sessions.length, 4);
  const newest = loaded ? e1rms[0] ?? 0 : totalReps(sessions[0]);
  const oldest = loaded ? e1rms[window - 1] ?? 0 : totalReps(sessions[window - 1]);
  if (oldest <= 0) return newest > 0 ? "up" : "flat";
  const change = (newest - oldest) / oldest;
  if (change >= 0.02) return "up";
  if (change <= -0.02) return "down";
  return "flat";
}

function ruleSuggestion(options: {
  sets: WeightSet[];
  unit: BodyWeightUnit;
  lowerBody: boolean;
  minReps: number | null;
  maxReps: number | null;
  hitTop: boolean;
  missedLatest: boolean;
  missedPrevious: boolean;
}): string | null {
  const top = Math.max(0, ...options.sets.map((set) => set.weightKg ?? 0));
  if (top <= 0) return null;
  const unitText = options.unit === "KG" ? "kg" : "lb";
  const load = roundLoad(top, options.unit);
  const increment =
    options.unit === "KG" ? (options.lowerBody ? 5 : 2.5) : options.lowerBody ? 10 : 5;
  const targetReps = options.minReps ?? options.maxReps;

  if (options.hitTop) {
    const reps = targetReps != null ? ` × ${targetReps}` : "";
    return `add ${increment} ${unitText}: ${load + increment} ${unitText}${reps}`;
  }
  if (options.missedLatest && options.missedPrevious) {
    const step = options.unit === "KG" ? 2.5 : 5;
    const reduced = Math.max(step, Math.round((load * 0.9) / step) * step);
    return `missed reps twice: drop to about ${reduced} ${unitText} and build back up`;
  }
  if (options.missedLatest) {
    return `hold ${load} ${unitText} and get ${options.minReps}+ reps on every set`;
  }
  if (options.maxReps != null) {
    return `hold ${load} ${unitText} and add reps until every set reaches ${options.maxReps}`;
  }
  return `hold ${load} ${unitText} and add a rep per set`;
}

export function buildWeeklyTraining(input: {
  weightLogs: WeightDayLog[];
  cardioLogs: CardioDayLog[];
  today: string;
  periodStart: string | null;
  plannedDaysPerWeek: number | null;
}): WeeklyTrainingFact {
  const weekCount = weeksInPeriod(input.today, input.periodStart);
  const weeks: WeekFact[] = [];
  for (let index = 0; index < weekCount; index += 1) {
    const weekEnd = addDays(input.today, -7 * index);
    const weekStart = addDays(weekEnd, -6);
    const inWeek = (date: string) => date >= weekStart && date <= weekEnd;
    const days = new Set<string>();
    let strengthSessions = 0;
    let cardioSessions = 0;
    let cardioMinutes = 0;
    for (const day of input.weightLogs) {
      if (!inWeek(day.date) || day.workouts.length === 0) continue;
      days.add(day.date);
      strengthSessions += day.workouts.length;
    }
    for (const day of input.cardioLogs) {
      if (!inWeek(day.date) || day.sessions.length === 0) continue;
      days.add(day.date);
      cardioSessions += day.sessions.length;
      cardioMinutes += day.sessions.reduce((sum, session) => sum + (session.durationMinutes ?? 0), 0);
    }
    weeks.push({
      weekStart,
      weekEnd,
      trainingDays: days.size,
      strengthSessions,
      cardioSessions,
      cardioMinutes: Math.round(cardioMinutes),
    });
  }
  const totalDays = weeks.reduce((sum, week) => sum + week.trainingDays, 0);
  return {
    plannedDaysPerWeek: input.plannedDaysPerWeek,
    averageTrainingDaysPerWeek: weeks.length > 0 ? round1(totalDays / weeks.length) : 0,
    weeks,
  };
}

export function buildWeeklySetsByMuscle(input: {
  weightLogs: WeightDayLog[];
  exercises: WeightExercise[];
  today: string;
  periodStart: string | null;
}): MuscleSetsFact[] {
  const weeks = weeksInPeriod(input.today, input.periodStart, Number.POSITIVE_INFINITY);
  return volumeByMuscleGroup(input.weightLogs, input.exercises)
    .map((row) => ({ muscleGroup: row.muscleGroup, setsPerWeek: round1(row.setCount / weeks) }))
    .filter((row) => row.setsPerWeek > 0)
    .sort((a, b) => b.setsPerWeek - a.setsPerWeek);
}

export type BodyWeightFact = {
  unit: "lb" | "kg";
  latest: { date: string; weight: number };
  entriesInPeriod: number;
  changeOverPeriod: number | null;
  /** Newest first: average weight for each recent 7-day week that has an entry. */
  weeklyAverages: { weekEnd: string; average: number; entries: number }[];
};

/** Body weight trend for the period, or null when nothing was logged. */
export function buildBodyWeightTrend(input: {
  entries: BodyWeightEntry[];
  today: string;
  periodStart: string | null;
  unit: BodyWeightUnit;
}): BodyWeightFact | null {
  const start = input.periodStart ?? "0000-01-01";
  const inPeriod = input.entries
    .filter((entry) => entry.date >= start && entry.date <= input.today)
    .sort((a, b) => a.date.localeCompare(b.date));
  if (inPeriod.length === 0) return null;
  const toUnit = (kg: number) => round1(input.unit === "KG" ? kg : kgToLb(kg));
  const latest = inPeriod[inPeriod.length - 1];
  const weekCount = weeksInPeriod(input.today, input.periodStart);
  const weeklyAverages: BodyWeightFact["weeklyAverages"] = [];
  for (let index = 0; index < weekCount; index += 1) {
    const weekEnd = addDays(input.today, -7 * index);
    const weekStart = addDays(weekEnd, -6);
    const week = inPeriod.filter((entry) => entry.date >= weekStart && entry.date <= weekEnd);
    if (week.length === 0) continue;
    const average = week.reduce((sum, entry) => sum + entry.weightKg, 0) / week.length;
    weeklyAverages.push({ weekEnd, average: toUnit(average), entries: week.length });
  }
  return {
    unit: input.unit === "KG" ? "kg" : "lb",
    latest: { date: latest.date, weight: toUnit(latest.weightKg) },
    entriesInPeriod: inPeriod.length,
    changeOverPeriod:
      inPeriod.length >= 2 ? round1(toUnit(latest.weightKg) - toUnit(inPeriod[0].weightKg)) : null,
    weeklyAverages,
  };
}

function weeksInPeriod(today: string, periodStart: string | null, cap = MAX_WEEKS): number {
  if (!periodStart) return 1;
  const days = daysBetween(periodStart, today) + 1;
  return Math.max(1, Math.min(cap, Math.ceil(days / 7)));
}

function isWorkingSet(set: WeightSet): boolean {
  return set.reps > 0 || (set.durationSeconds ?? 0) > 0;
}

function totalReps(session: LiftSession): number {
  return session.sets.reduce((sum, set) => sum + Math.max(0, set.reps), 0);
}

function missedTarget(sets: WeightSet[], minReps: number): boolean {
  const repSets = sets.filter((set) => set.reps > 0);
  return repSets.length > 0 && repSets.some((set) => set.reps < minReps);
}

function hitTopOfRange(sets: WeightSet[], maxReps: number, plannedSets: number): boolean {
  const repSets = sets.filter((set) => set.reps > 0);
  if (repSets.length === 0 || repSets.length < plannedSets) return false;
  const top = Math.max(...repSets.map((set) => set.weightKg ?? 0));
  const atTop = repSets.filter((set) => (set.weightKg ?? 0) >= top - 0.01);
  return atTop.length >= Math.min(plannedSets, repSets.length) && atTop.every((set) => set.reps >= maxReps);
}

function sessionOneRepMaxKg(sets: WeightSet[]): number | null {
  let best: number | null = null;
  for (const set of sets) {
    const kg = set.weightKg ?? 0;
    if (kg <= 0 || set.reps <= 0 || set.reps > 12) continue;
    const estimate = set.reps === 1 ? kg : kg * (1 + set.reps / 30);
    if (best == null || estimate > best) best = estimate;
  }
  return best;
}

function averageRpe(sets: WeightSet[]): number | null {
  const values = sets
    .map((set) => set.rpe)
    .filter((rpe): rpe is number => typeof rpe === "number" && rpe > 0);
  if (values.length === 0) return null;
  return round1(values.reduce((sum, rpe) => sum + rpe, 0) / values.length);
}

function setsText(sets: WeightSet[], unit: BodyWeightUnit): string {
  const parts = sets.filter(isWorkingSet).map((set) => {
    if (set.reps <= 0) return `${set.durationSeconds}s hold`;
    const kg = set.weightKg ?? 0;
    return kg > 0 ? `${roundLoad(kg, unit)}×${set.reps}` : `${set.reps} reps`;
  });
  const loaded = sets.some((set) => (set.weightKg ?? 0) > 0);
  return loaded ? `${parts.join(", ")} ${unit === "KG" ? "kg" : "lb"}` : parts.join(", ");
}

function prescriptionText(prescription: WorkoutWeightPrescription, unit: BodyWeightUnit): string {
  const sets = prescription.setCount ?? prescription.sets?.length ?? null;
  let reps = "";
  if (prescription.mode === "time_based" && prescription.durationSeconds) {
    reps = `${prescription.durationSeconds}s`;
  } else if (prescription.mode === "max_reps") {
    reps = "max reps";
  } else if (prescription.repRangeMin && prescription.repRangeMax) {
    reps =
      prescription.repRangeMin === prescription.repRangeMax
        ? String(prescription.repRangeMin)
        : `${prescription.repRangeMin}–${prescription.repRangeMax}`;
  } else if (prescription.targetReps) {
    reps = String(prescription.targetReps);
  }
  let text = sets && reps ? `${sets} × ${reps}` : sets ? `${sets} sets` : reps;
  if (prescription.targetWeightKg && prescription.targetWeightKg > 0) {
    text += ` @ ${roundLoad(prescription.targetWeightKg, unit)} ${unit === "KG" ? "kg" : "lb"}`;
  }
  if (prescription.targetRir != null) text += `, ${prescription.targetRir} RIR`;
  if (prescription.restBetweenSetsSeconds) {
    text += `, rest ${restText(prescription.restBetweenSetsSeconds)}`;
  }
  return text.trim() || "programmed";
}

function restText(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

function isLowerBody(muscleGroup: string): boolean {
  return /leg|quad|hamstring|glute|calf|calves|hip/.test(muscleGroup);
}

function roundLoad(kg: number, unit: BodyWeightUnit): number {
  const value = unit === "KG" ? kg : kgToLb(kg);
  return Math.round(value * 2) / 2;
}

function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

function daysBetween(fromIso: string, toIso: string): number {
  const ms = parseIsoDate(toIso).getTime() - parseIsoDate(fromIso).getTime();
  return Math.max(0, Math.round(ms / 86_400_000));
}
