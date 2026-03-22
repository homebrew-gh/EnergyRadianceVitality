package com.erv.app.data

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.round

private const val LB_PER_KG = 2.2046226218

fun kgToLb(kg: Double): Double = kg * LB_PER_KG
fun lbToKg(lb: Double): Double = lb / LB_PER_KG

fun formatWeightValue(kg: Double, unit: BodyWeightUnit): String {
    return when (unit) {
        BodyWeightUnit.KG -> {
            val v = if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format("%.1f", kg)
            "$v kg"
        }
        BodyWeightUnit.LB -> {
            val lb = kgToLb(kg)
            val v = if (abs(lb - round(lb)) < 0.05) round(lb).toInt().toString() else String.format("%.1f", lb)
            "$v lb"
        }
    }
}

fun formatWeightRange(minKg: Double, maxKg: Double, unit: BodyWeightUnit): String =
    "${formatWeightValue(minKg, unit)} – ${formatWeightValue(maxKg, unit)}"

/** How this row was created: manual text vs structured catalog. */
@Serializable
enum class EquipmentCatalogKind {
    MANUAL,
    BARBELL,
    DUMBBELLS,
    KETTLEBELLS,
    PLATES,
    BANDS,
    BENCH,
    SQUAT_RACK,
    PULL_UP_DIP,
    CARDIO_MACHINES,
    CABLE_STATION,
    JUMP_ROPE,
    MEDICINE_BALL,
    SUSPENSION_TRAINER,
    PLYO_BOX,
    BATTLE_ROPES,
    MOBILITY_TOOLS,
    PARALLETTE_RINGS,
}

@Serializable
enum class StandardBarbellType {
    OLYMPIC_MENS,
    OLYMPIC_WOMENS,
    TRAINING_10KG,
    EZ_CURL,
    TRAP_HEX_20,
    TRAP_HEX_25,
    /** Open / hybrid trap bar (e.g. REP, Eleiko) — typical unloaded weight varies by model. */
    OPEN_TRAP_BAR,
    STANDARD_1INCH,
    CUSTOM,
}

fun StandardBarbellType.typicalWeightKg(): Double? =
    when (this) {
        StandardBarbellType.OLYMPIC_MENS -> 20.0
        StandardBarbellType.OLYMPIC_WOMENS -> 15.0
        StandardBarbellType.TRAINING_10KG -> 10.0
        StandardBarbellType.EZ_CURL -> 8.0
        StandardBarbellType.TRAP_HEX_20 -> 20.0
        StandardBarbellType.TRAP_HEX_25 -> 25.0
        StandardBarbellType.OPEN_TRAP_BAR -> 25.0
        StandardBarbellType.STANDARD_1INCH -> 7.0
        StandardBarbellType.CUSTOM -> null
    }

fun StandardBarbellType.label(): String =
    when (this) {
        StandardBarbellType.OLYMPIC_MENS -> "Olympic bar (20 kg / 45 lb)"
        StandardBarbellType.OLYMPIC_WOMENS -> "Women's Olympic bar (15 kg / 33 lb)"
        StandardBarbellType.TRAINING_10KG -> "Training bar (10 kg / 25 lb)"
        StandardBarbellType.EZ_CURL -> "EZ curl bar (~8 kg / 18 lb)"
        StandardBarbellType.TRAP_HEX_20 -> "Trap / Hex Bar (~20 kg / 45 lb)"
        StandardBarbellType.TRAP_HEX_25 -> "Trap / Hex Bar (~25 kg / 55 lb)"
        StandardBarbellType.OPEN_TRAP_BAR -> "Open Trap Bar (~25 kg / 55 lb)"
        StandardBarbellType.STANDARD_1INCH -> "Standard 1\" Bar (~7 kg / 15 lb)"
        StandardBarbellType.CUSTOM -> "Custom Bar (Name and Weight)"
    }

@Serializable
data class BarbellOwnership(
    val barType: StandardBarbellType,
    val customWeightKg: Double? = null,
    /** Only used when [barType] is [StandardBarbellType.CUSTOM]; shown in the inventory title. */
    val customName: String? = null,
)

fun BarbellOwnership.effectiveWeightKg(): Double =
    when (barType) {
        StandardBarbellType.CUSTOM -> customWeightKg?.takeIf { it > 0 } ?: 20.0
        else -> barType.typicalWeightKg() ?: 20.0
    }

@Serializable
enum class DumbbellOwnershipMode {
    FIXED_PAIRS,
    SELECTORIZED,
}

@Serializable
data class DumbbellOwnership(
    val mode: DumbbellOwnershipMode,
    val pairWeightsKg: List<Double> = emptyList(),
    val selectorizedMinKg: Double? = null,
    val selectorizedMaxKg: Double? = null,
    val selectorizedIncrementKg: Double? = null,
)

@Serializable
data class KettlebellOwnership(
    val weightsKg: List<Double> = emptyList(),
)

@Serializable
data class PlatePairEntry(
    val weightKg: Double,
    /** How many pairs of this weight (a pair = two plates, one per side). */
    val pairCount: Int,
)

@Serializable
data class PlateOwnership(
    /** Legacy: distinct weights only, each implied 1 pair. Prefer [pairs]. */
    val plateWeightsKg: List<Double> = emptyList(),
    val pairs: List<PlatePairEntry> = emptyList(),
) {
    /** Weights from [pairs] if present, otherwise one pair per legacy weight. */
    fun resolvedPlatePairs(): List<PlatePairEntry> =
        when {
            pairs.isNotEmpty() -> pairs.filter { it.pairCount > 0 }.sortedByDescending { it.weightKg }
            plateWeightsKg.isNotEmpty() ->
                plateWeightsKg.map { PlatePairEntry(it, 1) }.sortedByDescending { it.weightKg }
            else -> emptyList()
        }
}

@Serializable
enum class BandResistanceTier {
    VERY_LIGHT,
    LIGHT,
    MEDIUM,
    HEAVY,
    EXTRA_HEAVY,
    ULTRA,
}

fun BandResistanceTier.label(): String =
    when (this) {
        BandResistanceTier.VERY_LIGHT -> "Very light"
        BandResistanceTier.LIGHT -> "Light"
        BandResistanceTier.MEDIUM -> "Medium"
        BandResistanceTier.HEAVY -> "Heavy"
        BandResistanceTier.EXTRA_HEAVY -> "Extra heavy"
        BandResistanceTier.ULTRA -> "Ultra / monster"
    }

@Serializable
data class BandOwnership(
    val tiers: Set<BandResistanceTier> = emptySet(),
    val hasMiniLoopSet: Boolean = false,
    val hasLongLoopBand: Boolean = false,
    val hasPullUpAssist: Boolean = false,
    val hasTubeHandles: Boolean = false,
)

@Serializable
enum class BenchType {
    FLAT_ONLY,
    ADJUSTABLE_INCLINE,
    FID_INCLINE_DECLINE,
    UTILITY_COMPACT,
}

fun BenchType.label(): String =
    when (this) {
        BenchType.FLAT_ONLY -> "Flat bench"
        BenchType.ADJUSTABLE_INCLINE -> "Adjustable incline bench"
        BenchType.FID_INCLINE_DECLINE -> "FID (flat / incline / decline)"
        BenchType.UTILITY_COMPACT -> "Compact utility bench"
    }

@Serializable
data class BenchOwnership(val benchType: BenchType)

@Serializable
enum class SquatRackType {
    SQUAT_STANDS,
    HALF_RACK,
    FULL_POWER_CAGE,
    WALL_MOUNTED,
    COMPACT_HALF_RACK,
}

fun SquatRackType.label(): String =
    when (this) {
        SquatRackType.SQUAT_STANDS -> "Squat stands"
        SquatRackType.HALF_RACK -> "Half rack"
        SquatRackType.FULL_POWER_CAGE -> "Full power cage"
        SquatRackType.WALL_MOUNTED -> "Wall-mounted rack"
        SquatRackType.COMPACT_HALF_RACK -> "Compact half rack"
    }

@Serializable
data class SquatRackOwnership(val rackType: SquatRackType)

@Serializable
enum class PullUpStationOption {
    DOORWAY_BAR,
    WALL_CEILING_BAR,
    RACK_MOUNTED,
    FREE_STANDING_TOWER,
    DIP_STATION_ATTACHMENT,
}

fun PullUpStationOption.label(): String =
    when (this) {
        PullUpStationOption.DOORWAY_BAR -> "Doorway pull-up bar"
        PullUpStationOption.WALL_CEILING_BAR -> "Wall / ceiling bar"
        PullUpStationOption.RACK_MOUNTED -> "Rack-mounted bar"
        PullUpStationOption.FREE_STANDING_TOWER -> "Freestanding tower"
        PullUpStationOption.DIP_STATION_ATTACHMENT -> "Dip station / attachment"
    }

@Serializable
data class PullUpOwnership(val options: Set<PullUpStationOption> = emptySet())

@Serializable
enum class CardioMachineKind {
    TREADMILL,
    MANUAL_TREADMILL,
    STATIONARY_BIKE,
    SPIN_BIKE,
    ROWER,
    ELLIPTICAL,
    SKIERG,
    FAN_BIKE,
    STAIR_CLIMBER,
}

fun CardioMachineKind.label(): String =
    when (this) {
        CardioMachineKind.TREADMILL -> "Treadmill"
        CardioMachineKind.MANUAL_TREADMILL -> "Manual Treadmill"
        CardioMachineKind.STATIONARY_BIKE -> "Stationary Bike"
        CardioMachineKind.SPIN_BIKE -> "Spin Bike"
        CardioMachineKind.ROWER -> "Rower"
        CardioMachineKind.ELLIPTICAL -> "Elliptical"
        CardioMachineKind.SKIERG -> "SkiErg"
        CardioMachineKind.FAN_BIKE -> "Fan Bike (Assault-Style)"
        CardioMachineKind.STAIR_CLIMBER -> "Stair Climber"
    }

@Serializable
data class CardioMachinesOwnership(val machines: Set<CardioMachineKind> = emptySet())

@Serializable
enum class CableStationType {
    SINGLE_STACK,
    DUAL_ADJUSTABLE,
    FUNCTIONAL_TRAINER,
    LAT_TOWER_ONLY,
    COMPACT_HOME_GYM,
}

fun CableStationType.label(): String =
    when (this) {
        CableStationType.SINGLE_STACK -> "Single weight stack"
        CableStationType.DUAL_ADJUSTABLE -> "Dual adjustable pulleys"
        CableStationType.FUNCTIONAL_TRAINER -> "Functional trainer"
        CableStationType.LAT_TOWER_ONLY -> "Lat tower / single stack tower"
        CableStationType.COMPACT_HOME_GYM -> "Compact home gym (cable + bench)"
    }

@Serializable
data class CableStationOwnership(val stationType: CableStationType)

@Serializable
enum class JumpRopeStyle {
    SPEED,
    WEIGHTED,
    BEADED,
    BASIC,
}

fun JumpRopeStyle.label(): String =
    when (this) {
        JumpRopeStyle.SPEED -> "Speed rope"
        JumpRopeStyle.WEIGHTED -> "Weighted rope"
        JumpRopeStyle.BEADED -> "Beaded rope"
        JumpRopeStyle.BASIC -> "Basic / PVC"
    }

@Serializable
data class JumpRopeOwnership(val styles: Set<JumpRopeStyle> = emptySet())

@Serializable
data class MedicineBallOwnership(val ballWeightsKg: List<Double> = emptyList())

@Serializable
enum class SuspensionAnchorKind {
    DOOR,
    CEILING,
    RACK_BEAM,
}

fun SuspensionAnchorKind.label(): String =
    when (this) {
        SuspensionAnchorKind.DOOR -> "Door anchor"
        SuspensionAnchorKind.CEILING -> "Ceiling / joist anchor"
        SuspensionAnchorKind.RACK_BEAM -> "Rack / beam anchor"
    }

@Serializable
data class SuspensionTrainerOwnership(val anchors: Set<SuspensionAnchorKind> = emptySet())

@Serializable
enum class PlyoBoxKind {
    SOFT_BOX,
    WOOD_FIXED,
    ADJUSTABLE_MULTI_HEIGHT,
}

fun PlyoBoxKind.label(): String =
    when (this) {
        PlyoBoxKind.SOFT_BOX -> "Soft plyo box"
        PlyoBoxKind.WOOD_FIXED -> "Wood box (fixed height)"
        PlyoBoxKind.ADJUSTABLE_MULTI_HEIGHT -> "Adjustable / multi-height box"
    }

@Serializable
data class PlyoBoxOwnership(val kinds: Set<PlyoBoxKind> = emptySet())

@Serializable
enum class BattleRopeHeft {
    LIGHT,
    MEDIUM,
    HEAVY,
}

fun BattleRopeHeft.label(): String =
    when (this) {
        BattleRopeHeft.LIGHT -> "Light"
        BattleRopeHeft.MEDIUM -> "Medium"
        BattleRopeHeft.HEAVY -> "Heavy"
    }

@Serializable
data class BattleRopeOwnership(val heft: Set<BattleRopeHeft> = emptySet())

@Serializable
data class MobilityToolsOwnership(
    val foamRoller: Boolean = false,
    val lacrosseBall: Boolean = false,
    val peanutBall: Boolean = false,
    val massageGun: Boolean = false,
)

@Serializable
data class ParallettesRingsOwnership(
    val parallettes: Boolean = false,
    val gymnasticRings: Boolean = false,
)

/** Preset pair weights (per dumbbell) for multi-select UI; stored as kg. */
object FitnessEquipmentPresets {
    /** Standard home-gym pairs: 5–100 lb in 5 lb steps. */
    fun dumbbellPairsLb(): List<Double> =
        (5..100 step 5).map { lbToKg(it.toDouble()) }

    /** Strongman / heavy pairs: 105–200 lb in 5 lb steps. */
    fun dumbbellPairsLbHeavy(): List<Double> =
        (105..200 step 5).map { lbToKg(it.toDouble()) }

    /** Standard pairs: 2.5–45 kg in 2.5 kg steps (~up to ~100 lb spacing). */
    fun dumbbellPairsKg(): List<Double> {
        val out = ArrayList<Double>()
        var v = 2.5
        while (v <= 45.0 + 0.01) {
            out.add(v)
            v += 2.5
        }
        return out
    }

    /** Heavy pairs: 47.5–90 kg in 2.5 kg steps (~105–200 lb range). */
    fun dumbbellPairsKgHeavy(): List<Double> {
        val out = ArrayList<Double>()
        var v = 47.5
        while (v <= 90.0 + 0.01) {
            out.add(v)
            v += 2.5
        }
        return out
    }

    fun platesLb(): List<Double> =
        listOf(45, 35, 25, 15, 10, 5, 2.5, 1.25).map { lbToKg(it.toDouble()) }

    fun platesKg(): List<Double> =
        listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)

    fun kettlebellsKg(): List<Double> =
        listOf(8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40, 44, 48).map { it.toDouble() }

    /** Medicine / slam ball presets (kg storage). */
    fun medicineBallsLb(): List<Double> =
        listOf(6, 8, 10, 12, 14, 16, 20, 25, 30).map { lbToKg(it.toDouble()) }

    fun medicineBallsKg(): List<Double> =
        listOf(2, 3, 4, 5, 6, 8, 10, 12, 15).map { it.toDouble() }
}

@Serializable
data class OwnedEquipmentItem(
    val id: String,
    val name: String,
    val modalities: Set<WorkoutModality> = emptySet(),
    val catalogKind: EquipmentCatalogKind = EquipmentCatalogKind.MANUAL,
    val barbell: BarbellOwnership? = null,
    val dumbbells: DumbbellOwnership? = null,
    val kettlebells: KettlebellOwnership? = null,
    val plates: PlateOwnership? = null,
    val bands: BandOwnership? = null,
    val bench: BenchOwnership? = null,
    val squatRack: SquatRackOwnership? = null,
    val pullUp: PullUpOwnership? = null,
    val cardioMachines: CardioMachinesOwnership? = null,
    val cableStation: CableStationOwnership? = null,
    val jumpRope: JumpRopeOwnership? = null,
    val medicineBalls: MedicineBallOwnership? = null,
    val suspensionTrainer: SuspensionTrainerOwnership? = null,
    val plyoBox: PlyoBoxOwnership? = null,
    val battleRopes: BattleRopeOwnership? = null,
    val mobilityTools: MobilityToolsOwnership? = null,
    val parallettesRings: ParallettesRingsOwnership? = null,
)

fun OwnedEquipmentItem.displayTitle(unit: BodyWeightUnit): String {
    return when (catalogKind) {
        EquipmentCatalogKind.MANUAL -> name
        EquipmentCatalogKind.BARBELL -> {
            val b = barbell ?: return name
            if (b.barType == StandardBarbellType.CUSTOM) {
                val w = formatWeightValue(b.effectiveWeightKg(), unit)
                val n = b.customName?.trim().orEmpty()
                if (n.isNotEmpty()) "$n ($w)" else "Barbell ($w)"
            } else {
                "Barbell · ${b.barType.label()}"
            }
        }
        EquipmentCatalogKind.DUMBBELLS -> "Dumbbells"
        EquipmentCatalogKind.KETTLEBELLS -> "Kettlebells"
        EquipmentCatalogKind.PLATES -> "Weight Plates"
        EquipmentCatalogKind.BANDS -> "Resistance Bands"
        EquipmentCatalogKind.BENCH -> "Bench"
        EquipmentCatalogKind.SQUAT_RACK -> "Squat Rack / Cage"
        EquipmentCatalogKind.PULL_UP_DIP -> "Pull-Up & Dip"
        EquipmentCatalogKind.CARDIO_MACHINES -> "Cardio Machines"
        EquipmentCatalogKind.CABLE_STATION -> "Cable Station"
        EquipmentCatalogKind.JUMP_ROPE -> "Jump Rope"
        EquipmentCatalogKind.MEDICINE_BALL -> "Medicine / Slam Balls"
        EquipmentCatalogKind.SUSPENSION_TRAINER -> "Suspension Trainer"
        EquipmentCatalogKind.PLYO_BOX -> "Plyo Box"
        EquipmentCatalogKind.BATTLE_ROPES -> "Battle Ropes"
        EquipmentCatalogKind.MOBILITY_TOOLS -> "Mobility Tools"
        EquipmentCatalogKind.PARALLETTE_RINGS -> "Parallettes & Rings"
    }
}

fun OwnedEquipmentItem.summaryLine(unit: BodyWeightUnit): String? {
    return when (catalogKind) {
        EquipmentCatalogKind.MANUAL -> null
        EquipmentCatalogKind.BARBELL -> {
            val b = barbell ?: return null
            val w = formatWeightValue(b.effectiveWeightKg(), unit)
            val n = b.customName?.trim().orEmpty()
            if (b.barType == StandardBarbellType.CUSTOM && n.isNotEmpty()) "$n · Bar weight: $w"
            else "Bar weight: $w"
        }
        EquipmentCatalogKind.DUMBBELLS -> {
            val d = dumbbells ?: return null
            when (d.mode) {
                DumbbellOwnershipMode.FIXED_PAIRS -> {
                    if (d.pairWeightsKg.isEmpty()) "No pairs selected"
                    else {
                        val sorted = d.pairWeightsKg.sorted()
                        val parts = sorted.take(8).map { formatWeightValue(it, unit) }
                        val more = if (sorted.size > 8) "… +${sorted.size - 8}" else ""
                        "Pairs: ${parts.joinToString(", ")} $more".trim()
                    }
                }
                DumbbellOwnershipMode.SELECTORIZED -> {
                    val min = d.selectorizedMinKg ?: return null
                    val max = d.selectorizedMaxKg ?: return null
                    val inc = d.selectorizedIncrementKg ?: return null
                    "Selectorized ${formatWeightRange(min, max, unit)}, step ${formatWeightValue(inc, unit)}"
                }
            }
        }
        EquipmentCatalogKind.KETTLEBELLS -> {
            val k = kettlebells ?: return null
            if (k.weightsKg.isEmpty()) "No kettlebells selected"
            else {
                val kgUnit = BodyWeightUnit.KG
                k.weightsKg.sorted().joinToString(", ") { formatWeightValue(it, kgUnit) }
            }
        }
        EquipmentCatalogKind.PLATES -> {
            val p = plates ?: return null
            val entries = p.resolvedPlatePairs()
            if (entries.isEmpty()) "No plates selected"
            else {
                entries.joinToString(", ") { e ->
                    "${e.pairCount}×${formatWeightValue(e.weightKg, unit)}"
                }
            }
        }
        EquipmentCatalogKind.BANDS -> {
            val b = bands ?: return null
            val parts = buildList {
                b.tiers.sortedBy { it.ordinal }.forEach { add(it.label()) }
                if (b.hasMiniLoopSet) add("Mini loop set")
                if (b.hasLongLoopBand) add("Long loop band")
                if (b.hasPullUpAssist) add("Pull-up assist")
                if (b.hasTubeHandles) add("Tube bands with handles")
            }
            if (parts.isEmpty()) null else parts.joinToString(" · ")
        }
        EquipmentCatalogKind.BENCH -> {
            val b = bench ?: return null
            b.benchType.label()
        }
        EquipmentCatalogKind.SQUAT_RACK -> {
            val r = squatRack ?: return null
            r.rackType.label()
        }
        EquipmentCatalogKind.PULL_UP_DIP -> {
            val p = pullUp ?: return null
            if (p.options.isEmpty()) "Nothing selected"
            else p.options.sortedBy { it.ordinal }.joinToString(" · ") { it.label() }
        }
        EquipmentCatalogKind.CARDIO_MACHINES -> {
            val c = cardioMachines ?: return null
            if (c.machines.isEmpty()) "Nothing selected"
            else c.machines.sortedBy { it.ordinal }.joinToString(" · ") { it.label() }
        }
        EquipmentCatalogKind.CABLE_STATION -> {
            val c = cableStation ?: return null
            c.stationType.label()
        }
        EquipmentCatalogKind.JUMP_ROPE -> {
            val j = jumpRope ?: return null
            if (j.styles.isEmpty()) "Nothing selected"
            else j.styles.sortedBy { it.ordinal }.joinToString(" · ") { it.label() }
        }
        EquipmentCatalogKind.MEDICINE_BALL -> {
            val m = medicineBalls ?: return null
            if (m.ballWeightsKg.isEmpty()) "No balls selected"
            else m.ballWeightsKg.sorted().joinToString(", ") { formatWeightValue(it, unit) }
        }
        EquipmentCatalogKind.SUSPENSION_TRAINER -> {
            val s = suspensionTrainer ?: return null
            if (s.anchors.isEmpty()) "No anchor selected"
            else s.anchors.sortedBy { it.ordinal }.joinToString(" · ") { it.label() }
        }
        EquipmentCatalogKind.PLYO_BOX -> {
            val p = plyoBox ?: return null
            if (p.kinds.isEmpty()) "Nothing selected"
            else p.kinds.sortedBy { it.ordinal }.joinToString(" · ") { it.label() }
        }
        EquipmentCatalogKind.BATTLE_ROPES -> {
            val b = battleRopes ?: return null
            if (b.heft.isEmpty()) "Nothing selected"
            else b.heft.sortedBy { it.ordinal }.joinToString(" · ") { it.label() }
        }
        EquipmentCatalogKind.MOBILITY_TOOLS -> {
            val m = mobilityTools ?: return null
            val s = buildList {
                if (m.foamRoller) add("Foam roller")
                if (m.lacrosseBall) add("Lacrosse ball")
                if (m.peanutBall) add("Peanut / double ball")
                if (m.massageGun) add("Massage gun")
            }.joinToString(" · ")
            if (s.isBlank()) null else s
        }
        EquipmentCatalogKind.PARALLETTE_RINGS -> {
            val p = parallettesRings ?: return null
            val s = buildList {
                if (p.parallettes) add("Parallettes")
                if (p.gymnasticRings) add("Gymnastic rings")
            }.joinToString(" · ")
            if (s.isBlank()) null else s
        }
    }
}
