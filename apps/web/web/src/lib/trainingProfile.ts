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

export type TrainingProgressionStyle = "conservative" | "moderate" | "aggressive";

export type TrainingCardioBias = "none" | "zone2_base" | "intervals" | "mixed";

export type HeartRateZoneMethod = "percent_max_hr" | "karvonen_hrr";

export type TrainingProfilePayload = {
  profileVersion: number;
  primaryGoal?: TrainingPrimaryGoal | null;
  experienceLevel?: TrainingExperienceLevel | null;
  typicalSessionMinutes?: number | null;
  typicalTrainingDaysPerWeek?: number | null;
  stylePresetIds: string[];
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
  influenceExamples: string[];
};

export type AvoidMovementPattern = {
  id: string;
  label: string;
};

export const MAX_TRAINING_STYLE_PRESETS = 2;

export const TRAINING_STYLE_PRESETS: TrainingStylePreset[] = [
  {
    id: "longevity_recovery",
    label: "Longevity & Recovery",
    description: "Conservative progression, recovery, mobility, zone 2, and full-body balance.",
    influenceExamples: ["Bryan Johnson", "Rhonda Patrick", "Peter Attia", "Andrew Huberman"],
  },
  {
    id: "joint_durability",
    label: "Joint Durability / ATG",
    description: "Knee and ankle capacity, controlled ROM, tendon resilience, and progressive range.",
    influenceExamples: ["Ben Patrick", "Knees Over Toes", "ATG", "Kelly Starrett"],
  },
  {
    id: "hypertrophy_bodybuilding",
    label: "Hypertrophy / Bodybuilding",
    description: "Volume, 8-15 rep work, accessories, and proximity-to-failure progressions.",
    influenceExamples: ["Renaissance Periodization", "Mike Israetel", "Jeff Nippard", "Menno Henselmans"],
  },
  {
    id: "strength_powerlifting",
    label: "Strength / Powerlifting",
    description: "Squat, bench, deadlift, heavier loading, and planned strength progression.",
    influenceExamples: ["Starting Strength", "Jim Wendler", "Westside"],
  },
  {
    id: "zone2_endurance",
    label: "Zone 2 / Aerobic Base",
    description: "Aerobic base building, low-intensity cardio, and strength maintenance.",
    influenceExamples: ["Phil Maffetone", "Inigo San Millan", "Peter Attia"],
  },
  {
    id: "hiit_conditioning",
    label: "HIIT / Conditioning",
    description: "Intervals, circuits, work capacity, and careful fatigue control.",
    influenceExamples: ["Tabata", "Norwegian 4x4", "CrossFit-style conditioning"],
  },
  {
    id: "general_athletic",
    label: "General Athletic Performance",
    description: "Strength, mobility, cardio, power, and coordination for balanced performance.",
    influenceExamples: ["Field-sport S&C", "EXOS-style training"],
  },
  {
    id: "mobility_movement",
    label: "Mobility & Movement Quality",
    description: "Range of motion, control, prehab, and lower-intensity movement practice.",
    influenceExamples: ["FRC", "GMB-style movement", "Yoga", "Pilates"],
  },
  {
    id: "calisthenics_minimalist",
    label: "Calisthenics / Minimal Equipment",
    description: "Bodyweight progressions, low-equipment strength, and skill practice.",
    influenceExamples: ["Bodyweight strength", "Pavel-style simple strength", "Rings and bar basics"],
  },
];

const STYLE_PRESET_ID_ALIASES: Record<string, string> = {
  longevity_blueprint: "longevity_recovery",
  kot_durable: "joint_durability",
  hypertrophy: "hypertrophy_bodybuilding",
  powerlifting: "strength_powerlifting",
  zone2_minimal: "zone2_endurance",
  general_athletic: "general_athletic",
};

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
    profileVersion: 2,
    stylePresetIds: [],
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

function normalizeStylePresetIds(raw: unknown): string[] {
  const allowed = new Set(TRAINING_STYLE_PRESETS.map((preset) => preset.id));
  const normalized: string[] = [];
  for (const id of normalizeStringList(raw)) {
    const mapped = STYLE_PRESET_ID_ALIASES[id] ?? id;
    if (!allowed.has(mapped) || normalized.includes(mapped)) continue;
    normalized.push(mapped);
    if (normalized.length >= MAX_TRAINING_STYLE_PRESETS) break;
  }
  return normalized;
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
      profileVersion: typeof data.profileVersion === "number" ? data.profileVersion : 2,
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
      stylePresetIds: normalizeStylePresetIds(data.stylePresetIds),
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
  if (profile.stylePresetIds.length > 0) return false;
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
    profileVersion: 2,
    stylePresetIds: normalizeStylePresetIds(profile.stylePresetIds).sort(),
    avoidMovementPatterns: [...profile.avoidMovementPatterns].sort(),
    lastModifiedEpochSeconds: profile.lastModifiedEpochSeconds,
  };
  if (profile.primaryGoal) body.primaryGoal = profile.primaryGoal;
  if (profile.experienceLevel) body.experienceLevel = profile.experienceLevel;
  if (profile.typicalSessionMinutes) body.typicalSessionMinutes = profile.typicalSessionMinutes;
  if (profile.typicalTrainingDaysPerWeek) {
    body.typicalTrainingDaysPerWeek = profile.typicalTrainingDaysPerWeek;
  }
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
