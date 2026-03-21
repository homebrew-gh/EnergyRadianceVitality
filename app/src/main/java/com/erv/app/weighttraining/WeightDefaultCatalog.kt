package com.erv.app.weighttraining

/**
 * Built-in exercise library beyond the four compounds in [defaultCompoundExercises].
 * Stable `id` values so sync and merge-by-id stay consistent across app versions.
 */
internal fun builtinCatalogBeyondCompounds(): List<WeightExercise> = listOf(
    // —— Barbell ——
    WeightExercise(id = "erv-weight-exercise-bb-incline-bench-v1", name = "Incline Bench Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-decline-bench-v1", name = "Decline Bench Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-close-grip-bench-v1", name = "Close-Grip Bench Press", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-floor-press-v1", name = "Floor Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-bent-over-row-v1", name = "Bent-Over Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-pendlay-row-v1", name = "Pendlay Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-tbar-row-v1", name = "T-Bar Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-upright-row-v1", name = "Upright Row", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-shrug-v1", name = "Barbell Shrug", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-rdl-v1", name = "Romanian Deadlift", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-good-morning-v1", name = "Good Morning", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-sumo-deadlift-v1", name = "Sumo Deadlift", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-rack-pull-v1", name = "Rack Pull", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-front-squat-v1", name = "Front Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-box-squat-v1", name = "Box Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-pause-squat-v1", name = "Pause Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-zercher-squat-v1", name = "Zercher Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-hip-thrust-v1", name = "Barbell Hip Thrust", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-bulgarian-split-v1", name = "Bulgarian Split Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-lunge-walk-v1", name = "Barbell Walking Lunge", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-push-press-v1", name = "Push Press", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-jm-press-v1", name = "JM Press", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-skull-crusher-v1", name = "Skull Crusher", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-barbell-curl-v1", name = "Barbell Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-drag-curl-v1", name = "Drag Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-preacher-curl-v1", name = "Preacher Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-ezbar-curl-v1", name = "EZ-Bar Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-landmine-press-v1", name = "Landmine Press", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-landmine-row-v1", name = "Landmine Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
    WeightExercise(id = "erv-weight-exercise-bb-rollout-v1", name = "Barbell Rollout", muscleGroup = "core", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),

    // —— Dumbbell ——
    WeightExercise(id = "erv-weight-exercise-db-bench-v1", name = "Dumbbell Bench Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-incline-bench-v1", name = "Incline Dumbbell Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-decline-bench-v1", name = "Decline Dumbbell Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-fly-v1", name = "Dumbbell Fly", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-pullover-v1", name = "Dumbbell Pullover", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-row-v1", name = "One-Arm Dumbbell Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-chest-supported-row-v1", name = "Chest-Supported Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-shoulder-press-v1", name = "Dumbbell Shoulder Press", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-arnold-press-v1", name = "Arnold Press", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-lateral-raise-v1", name = "Lateral Raise", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-front-raise-v1", name = "Front Raise", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-rear-fly-v1", name = "Rear Delt Fly", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-shrug-v1", name = "Dumbbell Shrug", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-curl-v1", name = "Dumbbell Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-hammer-curl-v1", name = "Hammer Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-incline-curl-v1", name = "Incline Dumbbell Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-concentration-curl-v1", name = "Concentration Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-zottman-curl-v1", name = "Zottman Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-tricep-kickback-v1", name = "Tricep Kickback", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-overhead-ext-v1", name = "Overhead Tricep Extension", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-tate-press-v1", name = "Tate Press", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-goblet-squat-v1", name = "Goblet Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-rdl-v1", name = "Dumbbell Romanian Deadlift", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-lunge-v1", name = "Dumbbell Lunge", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-reverse-lunge-v1", name = "Dumbbell Reverse Lunge", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-step-up-v1", name = "Dumbbell Step-Up", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-bulgarian-split-v1", name = "Dumbbell Bulgarian Split Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-sumo-squat-v1", name = "Dumbbell Sumo Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-calf-raise-v1", name = "Dumbbell Calf Raise", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-farmers-carry-v1", name = "Farmer's Carry", muscleGroup = "core", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-renegade-row-v1", name = "Renegade Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
    WeightExercise(id = "erv-weight-exercise-db-swing-v1", name = "Dumbbell Swing", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),

    // —— Bodyweight ——
    WeightExercise(id = "erv-weight-exercise-bw-pullup-v1", name = "Pull-Up", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.OTHER),
    WeightExercise(id = "erv-weight-exercise-bw-chinup-v1", name = "Chin-Up", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.OTHER),
    WeightExercise(id = "erv-weight-exercise-bw-dip-v1", name = "Dip", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.OTHER),
    WeightExercise(id = "erv-weight-exercise-bw-hanging-leg-raise-v1", name = "Hanging Leg Raise", muscleGroup = "core", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.OTHER),
    WeightExercise(id = "erv-weight-exercise-bw-ab-wheel-rollout-v1", name = "Ab Wheel Rollout", muscleGroup = "core", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.OTHER),

    // —— Machine / selectorized ——
    WeightExercise(id = "erv-weight-exercise-m-leg-press-v1", name = "Leg Press", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-hack-squat-v1", name = "Hack Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-leg-extension-v1", name = "Leg Extension", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-lying-leg-curl-v1", name = "Lying Leg Curl", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-seated-leg-curl-v1", name = "Seated Leg Curl", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-seated-calf-v1", name = "Seated Calf Raise", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-standing-calf-v1", name = "Standing Calf Raise", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-adductor-v1", name = "Hip Adduction", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-abductor-v1", name = "Hip Abduction", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-glute-kickback-v1", name = "Glute Kickback", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-chest-press-v1", name = "Machine Chest Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-incline-chest-press-v1", name = "Incline Machine Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-pec-deck-v1", name = "Pec Deck", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-lat-pulldown-v1", name = "Lat Pulldown", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-close-grip-pulldown-v1", name = "Close-Grip Pulldown", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-seated-row-v1", name = "Seated Cable Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-low-row-v1", name = "Low Cable Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-assisted-pullup-v1", name = "Assisted Pull-Up", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-assisted-dip-v1", name = "Assisted Dip", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-shoulder-press-v1", name = "Machine Shoulder Press", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-lateral-raise-machine-v1", name = "Lateral Raise Machine", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-reverse-fly-machine-v1", name = "Reverse Fly Machine", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-face-pull-v1", name = "Face Pull", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-cable-crossover-v1", name = "Cable Crossover", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-tricep-pushdown-v1", name = "Tricep Pushdown", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-rope-pushdown-v1", name = "Rope Tricep Pushdown", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-overhead-cable-ext-v1", name = "Overhead Cable Extension", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-cable-curl-v1", name = "Cable Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-pallof-press-v1", name = "Pallof Press", muscleGroup = "core", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-cable-crunch-v1", name = "Cable Crunch", muscleGroup = "core", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-back-extension-v1", name = "Back Extension", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-ab-crunch-machine-v1", name = "Ab Crunch Machine", muscleGroup = "core", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-smith-squat-v1", name = "Smith Machine Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-smith-bench-v1", name = "Smith Machine Bench Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),
    WeightExercise(id = "erv-weight-exercise-m-smith-lunge-v1", name = "Smith Machine Lunge", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE),

    // —— Kettlebell ——
    WeightExercise(id = "erv-weight-exercise-kb-swing-v1", name = "Kettlebell Swing", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-one-arm-swing-v1", name = "One-Arm Kettlebell Swing", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-goblet-squat-v1", name = "Kettlebell Goblet Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-front-squat-v1", name = "Kettlebell Front Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-rdl-v1", name = "Kettlebell Romanian Deadlift", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-deadlift-v1", name = "Kettlebell Deadlift", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-clean-v1", name = "Kettlebell Clean", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-high-pull-v1", name = "Kettlebell High Pull", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-snatch-v1", name = "Kettlebell Snatch", muscleGroup = "legs", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-press-v1", name = "Kettlebell Press", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-push-press-v1", name = "Kettlebell Push Press", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-floor-press-v1", name = "Kettlebell Floor Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-row-v1", name = "Kettlebell Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-upright-row-v1", name = "Kettlebell Upright Row", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-windmill-v1", name = "Kettlebell Windmill", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-turkish-getup-v1", name = "Turkish Get-Up", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-halo-v1", name = "Kettlebell Halo", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-around-world-v1", name = "Around the World", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-carry-v1", name = "Kettlebell Carry", muscleGroup = "core", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-lunge-v1", name = "Kettlebell Lunge", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.KETTLEBELL),
    WeightExercise(id = "erv-weight-exercise-kb-overhead-squat-v1", name = "Kettlebell Overhead Squat", muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.KETTLEBELL),
)

/** Full built-in catalog: four compounds + [builtinCatalogBeyondCompounds]. */
fun defaultCatalogExercises(): List<WeightExercise> =
    defaultCompoundExercises() + builtinCatalogBeyondCompounds()

private object CatalogBicepsTricepsIds {
    val biceps: Set<String> by lazy {
        defaultCatalogExercises().filter { it.muscleGroup.equals("biceps", ignoreCase = true) }
            .map { it.id }.toSet()
    }
    val triceps: Set<String> by lazy {
        defaultCatalogExercises().filter { it.muscleGroup.equals("triceps", ignoreCase = true) }
            .map { it.id }.toSet()
    }
}

/** Maps persisted built-in exercises that still use legacy `arms` to biceps/triceps by stable id. */
internal fun WeightExercise.withMigratedArmsMuscleGroup(): WeightExercise {
    if (!muscleGroup.trim().equals("arms", ignoreCase = true)) return this
    return when {
        id in CatalogBicepsTricepsIds.triceps -> copy(muscleGroup = "triceps")
        id in CatalogBicepsTricepsIds.biceps -> copy(muscleGroup = "biceps")
        else -> this
    }
}
