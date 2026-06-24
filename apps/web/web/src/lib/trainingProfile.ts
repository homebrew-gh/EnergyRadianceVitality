/** Matches Android `erv/training-profile` kind-30078 payload (TrainingProfileSync). */

export const TRAINING_PROFILE_D_TAG = "erv/training-profile";

export type TrainingPrimaryGoal =
  | "general_fitness"
  | "strength"
  | "hypertrophy"
  | "endurance"
  | "longevity"
  | "sport";

export type TrainingExperienceLevel = "beginner" | "intermediate" | "advanced";

export type TrainingSplitPreference =
  | "full_body"
  | "upper_lower"
  | "push_pull_legs"
  | "custom"
  | "none";

export type TrainingProgressionStyle = "conservative" | "moderate" | "aggressive";

export type TrainingCardioBias = "none" | "zone2_base" | "intervals" | "mixed";

export type HeartRateZoneMethod = "percent_max_hr" | "karvonen_hrr";

export type TrainingProfilePayload = {
  profileVersion: number;
  primaryGoal?: TrainingPrimaryGoal | null;
  experienceLevel?: TrainingExperienceLevel | null;
  typicalSessionMinutes?: number | null;
  typicalTrainingDaysPerWeek?: number | null;
  preferredSplit?: TrainingSplitPreference | null;
  stylePresetIds: string[];
  influenceLabels: string[];
  styleNotes?: string | null;
  avoidMovementPatterns: string[];
  customAvoidNotes?: string | null;
  progressionStyle?: TrainingProgressionStyle | null;
  cardioBias?: TrainingCardioBias | null;
  ageYears?: number | null;
  heartRateMaxBpm?: number | null;
  heartRateRestingBpm?: number | null;
  heartRateZoneMethod?: HeartRateZoneMethod | null;
  lastModifiedEpochSeconds: number;
};

export type TrainingStylePreset = {
  id: string;
  label: string;
  description: string;
};

export type AvoidMovementPattern = {
  id: string;
  label: string;
};

export const TRAINING_STYLE_PRESETS: TrainingStylePreset[] = [
  {
    id: "longevity_blueprint",
    label: "Longevity / Blueprint-adjacent",
    description: "Conservative loading, full-body bias, recovery-aware sessions.",
  },
  {
    id: "kot_durable",
    label: "KOT / joint-durable",
    description: "Knee and ankle prep, controlled ROM, split squats and tibialis bias.",
  },
  {
    id: "powerlifting",
    label: "Powerlifting",
    description: "Squat, bench, and deadlift focus with percentage-style progression.",
  },
  {
    id: "hypertrophy",
    label: "Hypertrophy",
    description: "Higher volume, 8–15 rep ranges, accessory density.",
  },
  {
    id: "zone2_minimal",
    label: "Zone 2 + minimal strength",
    description: "Cardio base with short strength maintenance blocks.",
  },
  {
    id: "general_athletic",
    label: "General athletic",
    description: "Balanced mixed modalities across the week.",
  },
];

export const AVOID_MOVEMENT_PATTERNS: AvoidMovementPattern[] = [
  { id: "heavy_overhead_press", label: "Heavy overhead press" },
  { id: "deep_knee_flexion", label: "Deep knee flexion under load" },
  { id: "spinal_axial_load", label: "Heavy spinal axial load" },
  { id: "jumping_plyometrics", label: "Jumping / plyometrics" },
  { id: "hanging_from_bar", label: "Hanging from bar" },
  { id: "high_impact_cardio", label: "High-impact cardio" },
];

export const PRIMARY_GOAL_OPTIONS: { value: TrainingPrimaryGoal; label: string }[] = [
  { value: "general_fitness", label: "General fitness" },
  { value: "strength", label: "Strength" },
  { value: "hypertrophy", label: "Hypertrophy" },
  { value: "endurance", label: "Endurance" },
  { value: "longevity", label: "Longevity" },
  { value: "sport", label: "Sport-specific" },
];

export const EXPERIENCE_LEVEL_OPTIONS: { value: TrainingExperienceLevel; label: string }[] = [
  { value: "beginner", label: "Beginner" },
  { value: "intermediate", label: "Intermediate" },
  { value: "advanced", label: "Advanced" },
];

export const SPLIT_PREFERENCE_OPTIONS: { value: TrainingSplitPreference; label: string }[] = [
  { value: "full_body", label: "Full body" },
  { value: "upper_lower", label: "Upper / lower" },
  { value: "push_pull_legs", label: "Push / pull / legs" },
  { value: "custom", label: "Custom" },
  { value: "none", label: "No preference" },
];

export const PROGRESSION_STYLE_OPTIONS: { value: TrainingProgressionStyle; label: string }[] = [
  { value: "conservative", label: "Conservative" },
  { value: "moderate", label: "Moderate" },
  { value: "aggressive", label: "Aggressive" },
];

export const CARDIO_BIAS_OPTIONS: { value: TrainingCardioBias; label: string }[] = [
  { value: "none", label: "None" },
  { value: "zone2_base", label: "Zone 2 base" },
  { value: "intervals", label: "Intervals" },
  { value: "mixed", label: "Mixed" },
];

export const HEART_RATE_ZONE_METHOD_OPTIONS: { value: HeartRateZoneMethod; label: string }[] = [
  { value: "percent_max_hr", label: "% max HR" },
  { value: "karvonen_hrr", label: "Karvonen (HRR)" },
];

export const SESSION_LENGTH_OPTIONS = [30, 45, 60, 75, 90];

export const TRAINING_DAYS_PER_WEEK_OPTIONS = [1, 2, 3, 4, 5, 6, 7] as const;

export function emptyTrainingProfile(): TrainingProfilePayload {
  return {
    profileVersion: 1,
    stylePresetIds: [],
    influenceLabels: [],
    avoidMovementPatterns: [],
    lastModifiedEpochSeconds: 0,
  };
}

function normalizeStringList(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((v): v is string => typeof v === "string")
    .map((s) => s.trim())
    .filter(Boolean);
}

function optionalEnum<T extends string>(raw: unknown, allowed: readonly T[]): T | null {
  if (typeof raw !== "string") return null;
  return allowed.includes(raw as T) ? (raw as T) : null;
}

function optionalInt(raw: unknown): number | null {
  if (typeof raw !== "number" || !Number.isFinite(raw)) return null;
  const n = Math.round(raw);
  return n > 0 ? n : null;
}

function optionalIntInRange(raw: unknown, min: number, max: number): number | null {
  const n = optionalInt(raw);
  if (n == null || n < min || n > max) return null;
  return n;
}

export function parseTrainingProfilePayload(raw: string): TrainingProfilePayload {
  try {
    const data = JSON.parse(raw) as Record<string, unknown>;
    return {
      profileVersion: typeof data.profileVersion === "number" ? data.profileVersion : 1,
      primaryGoal: optionalEnum(data.primaryGoal, [
        "general_fitness",
        "strength",
        "hypertrophy",
        "endurance",
        "longevity",
        "sport",
      ] as const),
      experienceLevel: optionalEnum(data.experienceLevel, [
        "beginner",
        "intermediate",
        "advanced",
      ] as const),
      typicalSessionMinutes: optionalInt(data.typicalSessionMinutes),
      typicalTrainingDaysPerWeek: optionalIntInRange(data.typicalTrainingDaysPerWeek, 1, 7),
      preferredSplit: optionalEnum(data.preferredSplit, [
        "full_body",
        "upper_lower",
        "push_pull_legs",
        "custom",
        "none",
      ] as const),
      stylePresetIds: normalizeStringList(data.stylePresetIds),
      influenceLabels: normalizeStringList(data.influenceLabels),
      styleNotes: typeof data.styleNotes === "string" ? data.styleNotes : null,
      avoidMovementPatterns: normalizeStringList(data.avoidMovementPatterns),
      customAvoidNotes:
        typeof data.customAvoidNotes === "string" ? data.customAvoidNotes : null,
      progressionStyle: optionalEnum(data.progressionStyle, [
        "conservative",
        "moderate",
        "aggressive",
      ] as const),
      cardioBias: optionalEnum(data.cardioBias, [
        "none",
        "zone2_base",
        "intervals",
        "mixed",
      ] as const),
      ageYears: optionalInt(data.ageYears),
      heartRateMaxBpm: optionalInt(data.heartRateMaxBpm),
      heartRateRestingBpm: optionalInt(data.heartRateRestingBpm),
      heartRateZoneMethod: optionalEnum(data.heartRateZoneMethod, [
        "percent_max_hr",
        "karvonen_hrr",
      ] as const),
      lastModifiedEpochSeconds:
        typeof data.lastModifiedEpochSeconds === "number"
          ? Math.max(0, Math.floor(data.lastModifiedEpochSeconds))
          : 0,
    };
  } catch {
    return emptyTrainingProfile();
  }
}

export function isTrainingProfileBlank(profile: TrainingProfilePayload): boolean {
  if (profile.primaryGoal) return false;
  if (profile.experienceLevel) return false;
  if (profile.typicalSessionMinutes) return false;
  if (profile.typicalTrainingDaysPerWeek) return false;
  if (profile.preferredSplit) return false;
  if (profile.stylePresetIds.length > 0) return false;
  if (profile.influenceLabels.length > 0) return false;
  if (profile.styleNotes?.trim()) return false;
  if (profile.avoidMovementPatterns.length > 0) return false;
  if (profile.customAvoidNotes?.trim()) return false;
  if (profile.progressionStyle) return false;
  if (profile.cardioBias) return false;
  if (profile.ageYears) return false;
  if (profile.heartRateMaxBpm) return false;
  if (profile.heartRateRestingBpm) return false;
  if (profile.heartRateZoneMethod) return false;
  return true;
}

export function trainingProfilePayload(profile: TrainingProfilePayload): string {
  const body: Record<string, unknown> = {
    profileVersion: profile.profileVersion,
    stylePresetIds: [...profile.stylePresetIds].sort(),
    influenceLabels: profile.influenceLabels,
    avoidMovementPatterns: [...profile.avoidMovementPatterns].sort(),
    lastModifiedEpochSeconds: profile.lastModifiedEpochSeconds,
  };
  if (profile.primaryGoal) body.primaryGoal = profile.primaryGoal;
  if (profile.experienceLevel) body.experienceLevel = profile.experienceLevel;
  if (profile.typicalSessionMinutes) body.typicalSessionMinutes = profile.typicalSessionMinutes;
  if (profile.typicalTrainingDaysPerWeek) {
    body.typicalTrainingDaysPerWeek = profile.typicalTrainingDaysPerWeek;
  }
  if (profile.preferredSplit) body.preferredSplit = profile.preferredSplit;
  if (profile.styleNotes?.trim()) body.styleNotes = profile.styleNotes.trim();
  if (profile.customAvoidNotes?.trim()) body.customAvoidNotes = profile.customAvoidNotes.trim();
  if (profile.progressionStyle) body.progressionStyle = profile.progressionStyle;
  if (profile.cardioBias) body.cardioBias = profile.cardioBias;
  if (profile.ageYears) body.ageYears = profile.ageYears;
  if (profile.heartRateMaxBpm) body.heartRateMaxBpm = profile.heartRateMaxBpm;
  if (profile.heartRateRestingBpm) body.heartRateRestingBpm = profile.heartRateRestingBpm;
  if (profile.heartRateZoneMethod) body.heartRateZoneMethod = profile.heartRateZoneMethod;
  return JSON.stringify(body);
}

export function trainingProfileDraftFingerprint(profile: TrainingProfilePayload): string {
  return trainingProfilePayload({
    ...profile,
    lastModifiedEpochSeconds: 0,
  });
}

export function stylePresetLabel(id: string): string {
  return TRAINING_STYLE_PRESETS.find((p) => p.id === id)?.label ?? id.replace(/_/g, " ");
}

export function avoidPatternLabel(id: string): string {
  return AVOID_MOVEMENT_PATTERNS.find((p) => p.id === id)?.label ?? id.replace(/_/g, " ");
}
