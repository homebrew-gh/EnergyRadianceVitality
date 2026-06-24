import { normalizeEnabledPackIds } from "./weightExercisePacks";

/** Matches Android `erv/equipment` kind-30078 payload (FitnessEquipmentSync). */

export const FITNESS_EQUIPMENT_D_TAG = "erv/equipment";

export type BodyWeightUnit = "KG" | "LB";

export type WorkoutModality =
  | "CARDIO"
  | "WEIGHT_TRAINING"
  | "STRETCHING"
  | "HIIT";

export type EquipmentCatalogKind =
  | "MANUAL"
  | "BARBELL"
  | "DUMBBELLS"
  | "KETTLEBELLS"
  | "PLATES"
  | "BANDS"
  | "BENCH"
  | "SQUAT_RACK"
  | "PULL_UP_DIP"
  | "CARDIO_MACHINES"
  | "CABLE_STATION"
  | "JUMP_ROPE"
  | "MEDICINE_BALL"
  | "SUSPENSION_TRAINER"
  | "PLYO_BOX"
  | "BATTLE_ROPES"
  | "MOBILITY_TOOLS"
  | "PARALLETTE_RINGS";

export type StandardBarbellType =
  | "OLYMPIC_MENS"
  | "OLYMPIC_WOMENS"
  | "TRAINING_10KG"
  | "EZ_CURL"
  | "TRAP_HEX_20"
  | "TRAP_HEX_25"
  | "OPEN_TRAP_BAR"
  | "STANDARD_1INCH"
  | "CUSTOM";

export type DumbbellOwnershipMode = "FIXED_PAIRS" | "SELECTORIZED";

export type BandResistanceTier =
  | "VERY_LIGHT"
  | "LIGHT"
  | "MEDIUM"
  | "HEAVY"
  | "EXTRA_HEAVY"
  | "ULTRA";

export type BenchType =
  | "FLAT_ONLY"
  | "ADJUSTABLE_INCLINE"
  | "FID_INCLINE_DECLINE"
  | "UTILITY_COMPACT";

export type SquatRackType =
  | "SQUAT_STANDS"
  | "HALF_RACK"
  | "FULL_POWER_CAGE"
  | "WALL_MOUNTED"
  | "COMPACT_HALF_RACK";

export type PullUpStationOption =
  | "DOORWAY_BAR"
  | "WALL_CEILING_BAR"
  | "RACK_MOUNTED"
  | "FREE_STANDING_TOWER"
  | "DIP_STATION_ATTACHMENT";

export type CardioMachineKind =
  | "TREADMILL"
  | "MANUAL_TREADMILL"
  | "STATIONARY_BIKE"
  | "SPIN_BIKE"
  | "ROWER"
  | "ELLIPTICAL"
  | "SKIERG"
  | "FAN_BIKE"
  | "STAIR_CLIMBER";

export type CableStationType =
  | "SINGLE_STACK"
  | "DUAL_ADJUSTABLE"
  | "FUNCTIONAL_TRAINER"
  | "LAT_TOWER_ONLY"
  | "COMPACT_HOME_GYM";

export type JumpRopeStyle = "SPEED" | "WEIGHTED" | "BEADED" | "BASIC";

export type SuspensionAnchorKind = "DOOR" | "CEILING" | "RACK_BEAM";

export type PlyoBoxKind = "SOFT_BOX" | "WOOD_FIXED" | "ADJUSTABLE_MULTI_HEIGHT";

export type BattleRopeHeft = "LIGHT" | "MEDIUM" | "HEAVY";

export type BarbellOwnership = {
  barType: StandardBarbellType;
  customWeightKg?: number | null;
  customName?: string | null;
};

export type DumbbellOwnership = {
  mode: DumbbellOwnershipMode;
  pairWeightsKg?: number[];
  selectorizedMinKg?: number | null;
  selectorizedMaxKg?: number | null;
  selectorizedIncrementKg?: number | null;
};

export type KettlebellOwnership = { weightsKg?: number[] };

export type PlatePairEntry = { weightKg: number; pairCount: number };

export type PlateOwnership = {
  plateWeightsKg?: number[];
  pairs?: PlatePairEntry[];
};

export type BandOwnership = {
  tiers?: BandResistanceTier[];
  hasMiniLoopSet?: boolean;
  hasLongLoopBand?: boolean;
  hasPullUpAssist?: boolean;
  hasTubeHandles?: boolean;
};

export type BenchOwnership = { benchType: BenchType };

export type SquatRackOwnership = { rackType: SquatRackType };

export type PullUpOwnership = { options?: PullUpStationOption[] };

export type CardioMachinesOwnership = { machines?: CardioMachineKind[] };

export type CableStationOwnership = { stationType: CableStationType };

export type JumpRopeOwnership = { styles?: JumpRopeStyle[] };

export type MedicineBallOwnership = { ballWeightsKg?: number[] };

export type SuspensionTrainerOwnership = { anchors?: SuspensionAnchorKind[] };

export type PlyoBoxOwnership = { kinds?: PlyoBoxKind[] };

export type BattleRopeOwnership = { heft?: BattleRopeHeft[] };

export type MobilityToolsOwnership = {
  foamRoller?: boolean;
  lacrosseBall?: boolean;
  peanutBall?: boolean;
  massageGun?: boolean;
};

export type ParallettesRingsOwnership = {
  parallettes?: boolean;
  gymnasticRings?: boolean;
};

export type OwnedEquipmentItem = {
  id: string;
  name: string;
  modalities?: WorkoutModality[];
  catalogKind?: EquipmentCatalogKind;
  barbell?: BarbellOwnership | null;
  dumbbells?: DumbbellOwnership | null;
  kettlebells?: KettlebellOwnership | null;
  plates?: PlateOwnership | null;
  bands?: BandOwnership | null;
  bench?: BenchOwnership | null;
  squatRack?: SquatRackOwnership | null;
  pullUp?: PullUpOwnership | null;
  cardioMachines?: CardioMachinesOwnership | null;
  cableStation?: CableStationOwnership | null;
  jumpRope?: JumpRopeOwnership | null;
  medicineBalls?: MedicineBallOwnership | null;
  suspensionTrainer?: SuspensionTrainerOwnership | null;
  plyoBox?: PlyoBoxOwnership | null;
  battleRopes?: BattleRopeOwnership | null;
  mobilityTools?: MobilityToolsOwnership | null;
  parallettesRings?: ParallettesRingsOwnership | null;
};

export type FitnessEquipmentPayload = {
  gymMembership: boolean;
  equipment: OwnedEquipmentItem[];
  /** Built-in specialty pack ids the user has enabled (e.g. `iron-neck`). */
  enabledWeightExercisePackIds: string[];
};

const LB_PER_KG = 2.2046226218;

export function kgToLb(kg: number): number {
  return kg * LB_PER_KG;
}

export function lbToKg(lb: number): number {
  return lb / LB_PER_KG;
}

export function formatWeightValue(kg: number, unit: BodyWeightUnit): string {
  if (unit === "KG") {
    const v = kg % 1 === 0 ? String(kg) : kg.toFixed(1);
    return `${v} kg`;
  }
  const lb = kgToLb(kg);
  const rounded = Math.round(lb);
  const v = Math.abs(lb - rounded) < 0.05 ? String(rounded) : lb.toFixed(1);
  return `${v} lb`;
}

export function formatWeightRange(minKg: number, maxKg: number, unit: BodyWeightUnit): string {
  return `${formatWeightValue(minKg, unit)} – ${formatWeightValue(maxKg, unit)}`;
}

export function parsePositiveWeightToKg(raw: string, unit: BodyWeightUnit): number | null {
  const v = Number.parseFloat(raw.trim().replace(",", "."));
  if (!Number.isFinite(v) || v <= 0) return null;
  return unit === "KG" ? v : lbToKg(v);
}

export function workoutModalityLabel(m: WorkoutModality): string {
  switch (m) {
    case "CARDIO":
      return "Cardio";
    case "WEIGHT_TRAINING":
      return "Weight training";
    case "STRETCHING":
      return "Stretching";
    case "HIIT":
      return "HIIT";
  }
}

export const WORKOUT_MODALITIES: WorkoutModality[] = [
  "CARDIO",
  "WEIGHT_TRAINING",
  "STRETCHING",
  "HIIT",
];

export function defaultModalities(kind: EquipmentCatalogKind): WorkoutModality[] {
  switch (kind) {
    case "BANDS":
      return ["STRETCHING", "WEIGHT_TRAINING"];
    case "CARDIO_MACHINES":
    case "JUMP_ROPE":
      return ["CARDIO", "HIIT"];
    case "BATTLE_ROPES":
    case "PLYO_BOX":
      return ["HIIT", "WEIGHT_TRAINING"];
    case "MOBILITY_TOOLS":
      return ["STRETCHING", "WEIGHT_TRAINING"];
    case "PARALLETTE_RINGS":
      return ["WEIGHT_TRAINING", "HIIT"];
    default:
      return ["WEIGHT_TRAINING"];
  }
}

function typicalBarbellWeightKg(type: StandardBarbellType): number | null {
  switch (type) {
    case "OLYMPIC_MENS":
      return 20;
    case "OLYMPIC_WOMENS":
      return 15;
    case "TRAINING_10KG":
      return 10;
    case "EZ_CURL":
      return 8;
    case "TRAP_HEX_20":
      return 20;
    case "TRAP_HEX_25":
      return 25;
    case "OPEN_TRAP_BAR":
      return 25;
    case "STANDARD_1INCH":
      return 7;
    case "CUSTOM":
      return null;
  }
}

export function standardBarbellLabel(type: StandardBarbellType): string {
  switch (type) {
    case "OLYMPIC_MENS":
      return "Olympic bar (20 kg / 45 lb)";
    case "OLYMPIC_WOMENS":
      return "Women's Olympic bar (15 kg / 33 lb)";
    case "TRAINING_10KG":
      return "Training bar (10 kg / 25 lb)";
    case "EZ_CURL":
      return "EZ curl bar (~8 kg / 18 lb)";
    case "TRAP_HEX_20":
      return "Trap / Hex Bar (~20 kg / 45 lb)";
    case "TRAP_HEX_25":
      return "Trap / Hex Bar (~25 kg / 55 lb)";
    case "OPEN_TRAP_BAR":
      return "Open Trap Bar (~25 kg / 55 lb)";
    case "STANDARD_1INCH":
      return 'Standard 1" Bar (~7 kg / 15 lb)';
    case "CUSTOM":
      return "Custom Bar (Name and Weight)";
  }
}

export const STANDARD_BARBELL_TYPES: StandardBarbellType[] = [
  "OLYMPIC_MENS",
  "OLYMPIC_WOMENS",
  "TRAINING_10KG",
  "EZ_CURL",
  "TRAP_HEX_20",
  "TRAP_HEX_25",
  "OPEN_TRAP_BAR",
  "STANDARD_1INCH",
  "CUSTOM",
];

export function effectiveBarbellWeightKg(b: BarbellOwnership): number {
  if (b.barType === "CUSTOM") {
    return b.customWeightKg && b.customWeightKg > 0 ? b.customWeightKg : 20;
  }
  return typicalBarbellWeightKg(b.barType) ?? 20;
}

export function bandTierLabel(t: BandResistanceTier): string {
  switch (t) {
    case "VERY_LIGHT":
      return "Very light";
    case "LIGHT":
      return "Light";
    case "MEDIUM":
      return "Medium";
    case "HEAVY":
      return "Heavy";
    case "EXTRA_HEAVY":
      return "Extra heavy";
    case "ULTRA":
      return "Ultra / monster";
  }
}

export function benchTypeLabel(t: BenchType): string {
  switch (t) {
    case "FLAT_ONLY":
      return "Flat bench";
    case "ADJUSTABLE_INCLINE":
      return "Adjustable incline bench";
    case "FID_INCLINE_DECLINE":
      return "FID (flat / incline / decline)";
    case "UTILITY_COMPACT":
      return "Compact utility bench";
  }
}

export function squatRackTypeLabel(t: SquatRackType): string {
  switch (t) {
    case "SQUAT_STANDS":
      return "Squat stands";
    case "HALF_RACK":
      return "Half rack";
    case "FULL_POWER_CAGE":
      return "Full power cage";
    case "WALL_MOUNTED":
      return "Wall-mounted rack";
    case "COMPACT_HALF_RACK":
      return "Compact half rack";
  }
}

export function pullUpOptionLabel(o: PullUpStationOption): string {
  switch (o) {
    case "DOORWAY_BAR":
      return "Doorway pull-up bar";
    case "WALL_CEILING_BAR":
      return "Wall / ceiling bar";
    case "RACK_MOUNTED":
      return "Rack-mounted bar";
    case "FREE_STANDING_TOWER":
      return "Freestanding tower";
    case "DIP_STATION_ATTACHMENT":
      return "Dip station / attachment";
  }
}

export function cardioMachineLabel(m: CardioMachineKind): string {
  switch (m) {
    case "TREADMILL":
      return "Treadmill";
    case "MANUAL_TREADMILL":
      return "Manual Treadmill";
    case "STATIONARY_BIKE":
      return "Stationary Bike";
    case "SPIN_BIKE":
      return "Spin Bike";
    case "ROWER":
      return "Rower";
    case "ELLIPTICAL":
      return "Elliptical";
    case "SKIERG":
      return "SkiErg";
    case "FAN_BIKE":
      return "Fan Bike (Assault-Style)";
    case "STAIR_CLIMBER":
      return "Stair Climber";
  }
}

export function cableStationLabel(t: CableStationType): string {
  switch (t) {
    case "SINGLE_STACK":
      return "Single weight stack";
    case "DUAL_ADJUSTABLE":
      return "Dual adjustable pulleys";
    case "FUNCTIONAL_TRAINER":
      return "Functional trainer";
    case "LAT_TOWER_ONLY":
      return "Lat tower / single stack tower";
    case "COMPACT_HOME_GYM":
      return "Compact home gym (cable + bench)";
  }
}

export function jumpRopeStyleLabel(s: JumpRopeStyle): string {
  switch (s) {
    case "SPEED":
      return "Speed rope";
    case "WEIGHTED":
      return "Weighted rope";
    case "BEADED":
      return "Beaded rope";
    case "BASIC":
      return "Basic / PVC";
  }
}

export function suspensionAnchorLabel(a: SuspensionAnchorKind): string {
  switch (a) {
    case "DOOR":
      return "Door anchor";
    case "CEILING":
      return "Ceiling / joist anchor";
    case "RACK_BEAM":
      return "Rack / beam anchor";
  }
}

export function plyoBoxKindLabel(k: PlyoBoxKind): string {
  switch (k) {
    case "SOFT_BOX":
      return "Soft plyo box";
    case "WOOD_FIXED":
      return "Wood box (fixed height)";
    case "ADJUSTABLE_MULTI_HEIGHT":
      return "Adjustable / multi-height box";
  }
}

export function battleRopeHeftLabel(h: BattleRopeHeft): string {
  switch (h) {
    case "LIGHT":
      return "Light";
    case "MEDIUM":
      return "Medium";
    case "HEAVY":
      return "Heavy";
  }
}

function resolvedPlatePairs(p: PlateOwnership): PlatePairEntry[] {
  if (p.pairs && p.pairs.length > 0) {
    return [...p.pairs]
      .filter((e) => e.pairCount > 0)
      .sort((a, b) => b.weightKg - a.weightKg);
  }
  if (p.plateWeightsKg && p.plateWeightsKg.length > 0) {
    return [...p.plateWeightsKg]
      .map((w) => ({ weightKg: w, pairCount: 1 }))
      .sort((a, b) => b.weightKg - a.weightKg);
  }
  return [];
}

export function displayEquipmentTitle(item: OwnedEquipmentItem, unit: BodyWeightUnit): string {
  const kind = item.catalogKind ?? "MANUAL";
  switch (kind) {
    case "MANUAL":
      return item.name;
    case "BARBELL": {
      const b = item.barbell;
      if (!b) return item.name;
      if (b.barType === "CUSTOM") {
        const w = formatWeightValue(effectiveBarbellWeightKg(b), unit);
        const n = b.customName?.trim() ?? "";
        return n ? `${n} (${w})` : `Barbell (${w})`;
      }
      return `Barbell · ${standardBarbellLabel(b.barType)}`;
    }
    case "DUMBBELLS":
      return "Dumbbells";
    case "KETTLEBELLS":
      return "Kettlebells";
    case "PLATES":
      return "Weight Plates";
    case "BANDS":
      return "Resistance Bands";
    case "BENCH":
      return "Bench";
    case "SQUAT_RACK":
      return "Squat Rack / Cage";
    case "PULL_UP_DIP":
      return "Pull-Up & Dip";
    case "CARDIO_MACHINES":
      return "Cardio Machines";
    case "CABLE_STATION":
      return "Cable Station";
    case "JUMP_ROPE":
      return "Jump Rope";
    case "MEDICINE_BALL":
      return "Medicine / Slam Balls";
    case "SUSPENSION_TRAINER":
      return "Suspension Trainer";
    case "PLYO_BOX":
      return "Plyo Box";
    case "BATTLE_ROPES":
      return "Battle Ropes";
    case "MOBILITY_TOOLS":
      return "Mobility Tools";
    case "PARALLETTE_RINGS":
      return "Parallettes & Rings";
  }
}

export function equipmentSummaryLine(item: OwnedEquipmentItem, unit: BodyWeightUnit): string | null {
  const kind = item.catalogKind ?? "MANUAL";
  switch (kind) {
    case "MANUAL":
      return null;
    case "BARBELL": {
      const b = item.barbell;
      if (!b) return null;
      const w = formatWeightValue(effectiveBarbellWeightKg(b), unit);
      const n = b.customName?.trim() ?? "";
      if (b.barType === "CUSTOM" && n) return `${n} · Bar weight: ${w}`;
      return `Bar weight: ${w}`;
    }
    case "DUMBBELLS": {
      const d = item.dumbbells;
      if (!d) return null;
      if (d.mode === "FIXED_PAIRS") {
        const pairs = d.pairWeightsKg ?? [];
        if (pairs.length === 0) return "No pairs selected";
        const sorted = [...pairs].sort((a, b) => a - b);
        const parts = sorted.slice(0, 8).map((kg) => formatWeightValue(kg, unit));
        const more = sorted.length > 8 ? `… +${sorted.length - 8}` : "";
        return `Pairs: ${parts.join(", ")} ${more}`.trim();
      }
      const min = d.selectorizedMinKg;
      const max = d.selectorizedMaxKg;
      const inc = d.selectorizedIncrementKg;
      if (min == null || max == null || inc == null) return null;
      return `Selectorized ${formatWeightRange(min, max, unit)}, step ${formatWeightValue(inc, unit)}`;
    }
    case "KETTLEBELLS": {
      const k = item.kettlebells;
      if (!k?.weightsKg?.length) return "No kettlebells selected";
      return [...k.weightsKg]
        .sort((a, b) => a - b)
        .map((kg) => formatWeightValue(kg, "KG"))
        .join(", ");
    }
    case "PLATES": {
      const p = item.plates;
      if (!p) return null;
      const entries = resolvedPlatePairs(p);
      if (entries.length === 0) return "No plates selected";
      return entries
        .map((e) => `${e.pairCount}×${formatWeightValue(e.weightKg, unit)}`)
        .join(", ");
    }
    case "BANDS": {
      const b = item.bands;
      if (!b) return null;
      const parts: string[] = [];
      (b.tiers ?? [])
        .slice()
        .sort()
        .forEach((t) => parts.push(bandTierLabel(t)));
      if (b.hasMiniLoopSet) parts.push("Mini loop set");
      if (b.hasLongLoopBand) parts.push("Long loop band");
      if (b.hasPullUpAssist) parts.push("Pull-up assist");
      if (b.hasTubeHandles) parts.push("Tube bands with handles");
      return parts.length ? parts.join(" · ") : null;
    }
    case "BENCH":
      return item.bench ? benchTypeLabel(item.bench.benchType) : null;
    case "SQUAT_RACK":
      return item.squatRack ? squatRackTypeLabel(item.squatRack.rackType) : null;
    case "PULL_UP_DIP": {
      const opts = item.pullUp?.options ?? [];
      if (!opts.length) return "Nothing selected";
      return opts.map(pullUpOptionLabel).join(" · ");
    }
    case "CARDIO_MACHINES": {
      const machines = item.cardioMachines?.machines ?? [];
      if (!machines.length) return "Nothing selected";
      return machines.map(cardioMachineLabel).join(" · ");
    }
    case "CABLE_STATION":
      return item.cableStation ? cableStationLabel(item.cableStation.stationType) : null;
    case "JUMP_ROPE": {
      const styles = item.jumpRope?.styles ?? [];
      if (!styles.length) return "Nothing selected";
      return styles.map(jumpRopeStyleLabel).join(" · ");
    }
    case "MEDICINE_BALL": {
      const balls = item.medicineBalls?.ballWeightsKg ?? [];
      if (!balls.length) return "No balls selected";
      return [...balls]
        .sort((a, b) => a - b)
        .map((kg) => formatWeightValue(kg, unit))
        .join(", ");
    }
    case "SUSPENSION_TRAINER": {
      const anchors = item.suspensionTrainer?.anchors ?? [];
      if (!anchors.length) return "No anchor selected";
      return anchors.map(suspensionAnchorLabel).join(" · ");
    }
    case "PLYO_BOX": {
      const kinds = item.plyoBox?.kinds ?? [];
      if (!kinds.length) return "Nothing selected";
      return kinds.map(plyoBoxKindLabel).join(" · ");
    }
    case "BATTLE_ROPES": {
      const heft = item.battleRopes?.heft ?? [];
      if (!heft.length) return "Nothing selected";
      return heft.map(battleRopeHeftLabel).join(" · ");
    }
    case "MOBILITY_TOOLS": {
      const m = item.mobilityTools;
      if (!m) return null;
      const parts: string[] = [];
      if (m.foamRoller) parts.push("Foam roller");
      if (m.lacrosseBall) parts.push("Lacrosse ball");
      if (m.peanutBall) parts.push("Peanut / double ball");
      if (m.massageGun) parts.push("Massage gun");
      return parts.length ? parts.join(" · ") : null;
    }
    case "PARALLETTE_RINGS": {
      const p = item.parallettesRings;
      if (!p) return null;
      const parts: string[] = [];
      if (p.parallettes) parts.push("Parallettes");
      if (p.gymnasticRings) parts.push("Gymnastic rings");
      return parts.length ? parts.join(" · ") : null;
    }
  }
}

export function buildCatalogItem(
  existingId: string | null,
  catalogKind: EquipmentCatalogKind,
  unit: BodyWeightUnit,
  modalities: WorkoutModality[],
  build: (base: OwnedEquipmentItem) => OwnedEquipmentItem,
): OwnedEquipmentItem {
  const id = existingId ?? crypto.randomUUID();
  const base: OwnedEquipmentItem = {
    id,
    name: "",
    modalities,
    catalogKind,
  };
  const built = build(base);
  return { ...built, name: displayEquipmentTitle(built, unit) };
}

export const FITNESS_EQUIPMENT_PRESETS = {
  dumbbellPairsLb(): number[] {
    return Array.from({ length: 20 }, (_, i) => lbToKg((i + 1) * 5));
  },
  dumbbellPairsLbHeavy(): number[] {
    return Array.from({ length: 20 }, (_, i) => lbToKg((i + 21) * 5));
  },
  dumbbellPairsKg(): number[] {
    const out: number[] = [];
    for (let v = 2.5; v <= 45.01; v += 2.5) out.push(v);
    return out;
  },
  dumbbellPairsKgHeavy(): number[] {
    const out: number[] = [];
    for (let v = 47.5; v <= 90.01; v += 2.5) out.push(v);
    return out;
  },
  platesLb(): number[] {
    return [45, 35, 25, 15, 10, 5, 2.5, 1.25].map(lbToKg);
  },
  platesKg(): number[] {
    return [25, 20, 15, 10, 5, 2.5, 1.25, 0.5];
  },
  kettlebellsKg(): number[] {
    return [8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40, 44, 48].map(Number);
  },
  medicineBallsLb(): number[] {
    return [6, 8, 10, 12, 14, 16, 20, 25, 30].map(lbToKg);
  },
  medicineBallsKg(): number[] {
    return [2, 3, 4, 5, 6, 8, 10, 12, 15].map(Number);
  },
};

export function parseFitnessEquipmentPayload(raw: string): FitnessEquipmentPayload {
  try {
    const parsed = JSON.parse(raw) as Partial<FitnessEquipmentPayload>;
    return {
      gymMembership: parsed.gymMembership === true,
      equipment: Array.isArray(parsed.equipment)
        ? parsed.equipment.filter(
            (item): item is OwnedEquipmentItem =>
              item != null && typeof item === "object" && typeof item.id === "string",
          )
        : [],
      enabledWeightExercisePackIds: normalizeEnabledPackIds(
        Array.isArray(parsed.enabledWeightExercisePackIds)
          ? parsed.enabledWeightExercisePackIds.filter(
              (id): id is string => typeof id === "string",
            )
          : [],
      ),
    };
  } catch {
    return { gymMembership: false, equipment: [], enabledWeightExercisePackIds: [] };
  }
}

export function fitnessEquipmentPayload(payload: FitnessEquipmentPayload): string {
  return JSON.stringify({
    gymMembership: payload.gymMembership,
    equipment: payload.equipment,
    enabledWeightExercisePackIds: normalizeEnabledPackIds(payload.enabledWeightExercisePackIds),
  });
}

export function equipmentDraftFingerprint(payload: FitnessEquipmentPayload): string {
  return JSON.stringify(payload);
}

export const QUICK_ADD_CATEGORIES: { kind: EquipmentCatalogKind; label: string }[] = [
  { kind: "BARBELL", label: "Barbell" },
  { kind: "DUMBBELLS", label: "Dumbbells" },
  { kind: "KETTLEBELLS", label: "Kettlebells" },
  { kind: "PLATES", label: "Plates" },
  { kind: "BANDS", label: "Bands" },
  { kind: "BENCH", label: "Bench" },
  { kind: "SQUAT_RACK", label: "Rack / Cage" },
  { kind: "PULL_UP_DIP", label: "Pull-Up / Dip" },
  { kind: "CARDIO_MACHINES", label: "Cardio" },
  { kind: "CABLE_STATION", label: "Cable" },
  { kind: "JUMP_ROPE", label: "Jump Rope" },
  { kind: "MEDICINE_BALL", label: "Med Ball" },
  { kind: "SUSPENSION_TRAINER", label: "Suspension" },
  { kind: "PLYO_BOX", label: "Plyo Box" },
  { kind: "BATTLE_ROPES", label: "Battle Ropes" },
  { kind: "MOBILITY_TOOLS", label: "Mobility" },
  { kind: "PARALLETTE_RINGS", label: "Parallettes / Rings" },
];

export const BENCH_TYPES: BenchType[] = [
  "FLAT_ONLY",
  "ADJUSTABLE_INCLINE",
  "FID_INCLINE_DECLINE",
  "UTILITY_COMPACT",
];

export const SQUAT_RACK_TYPES: SquatRackType[] = [
  "SQUAT_STANDS",
  "HALF_RACK",
  "FULL_POWER_CAGE",
  "WALL_MOUNTED",
  "COMPACT_HALF_RACK",
];

export const PULL_UP_OPTIONS: PullUpStationOption[] = [
  "DOORWAY_BAR",
  "WALL_CEILING_BAR",
  "RACK_MOUNTED",
  "FREE_STANDING_TOWER",
  "DIP_STATION_ATTACHMENT",
];

export const CARDIO_MACHINES: CardioMachineKind[] = [
  "TREADMILL",
  "MANUAL_TREADMILL",
  "STATIONARY_BIKE",
  "SPIN_BIKE",
  "ROWER",
  "ELLIPTICAL",
  "SKIERG",
  "FAN_BIKE",
  "STAIR_CLIMBER",
];

export const CABLE_STATION_TYPES: CableStationType[] = [
  "SINGLE_STACK",
  "DUAL_ADJUSTABLE",
  "FUNCTIONAL_TRAINER",
  "LAT_TOWER_ONLY",
  "COMPACT_HOME_GYM",
];

export const JUMP_ROPE_STYLES: JumpRopeStyle[] = ["SPEED", "WEIGHTED", "BEADED", "BASIC"];

export const BAND_TIERS: BandResistanceTier[] = [
  "VERY_LIGHT",
  "LIGHT",
  "MEDIUM",
  "HEAVY",
  "EXTRA_HEAVY",
  "ULTRA",
];

export const SUSPENSION_ANCHORS: SuspensionAnchorKind[] = ["DOOR", "CEILING", "RACK_BEAM"];

export const PLYO_BOX_KINDS: PlyoBoxKind[] = [
  "SOFT_BOX",
  "WOOD_FIXED",
  "ADJUSTABLE_MULTI_HEIGHT",
];

export const BATTLE_ROPE_HEFT: BattleRopeHeft[] = ["LIGHT", "MEDIUM", "HEAVY"];
