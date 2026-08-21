package com.ninthlevel.phoenix.data.local.seed

import com.ninthlevel.phoenix.data.local.ExerciseEntity
import com.ninthlevel.phoenix.domain.model.CableConfiguration
import com.ninthlevel.phoenix.domain.model.Equipment
import com.ninthlevel.phoenix.domain.model.MuscleGroup

/**
 * Self-authored generic exercise list. Names, muscle groups, and equipment only.
 */
object DefaultExercises {

    val all: List<ExerciseEntity> = listOf(
        ex("cable-chest-press", "Chest Press", MuscleGroup.Chest, Equipment.Handles, DOUBLE),
        ex("cable-incline-press", "Incline Press", MuscleGroup.Chest, Equipment.Handles, DOUBLE),
        ex("cable-decline-press", "Decline Press", MuscleGroup.Chest, Equipment.Handles, DOUBLE),
        ex("cable-chest-fly", "Fly", MuscleGroup.Chest, Equipment.Handles, EITHER),
        ex("cable-single-arm-press", "Single-Arm Press", MuscleGroup.Chest, Equipment.Handles, SINGLE),
        ex("cable-single-arm-fly", "Single-Arm Fly", MuscleGroup.Chest, Equipment.Handles, SINGLE),
        ex("cable-bench-press", "Bench Press", MuscleGroup.Chest, "${Equipment.LongBar},${Equipment.Bench}", DOUBLE),
        ex("cable-squeeze-press", "Squeeze Press", MuscleGroup.Chest, Equipment.Handles, DOUBLE),
        ex("cable-pullover", "Pullover", MuscleGroup.Chest, Equipment.Handles, DOUBLE, "straight-arm pullover"),
        ex("push-up", "Push-Up", MuscleGroup.Chest, Equipment.Bodyweight, DOUBLE),
        ex("cable-seated-row", "Seated Row", MuscleGroup.Back, Equipment.Handles, DOUBLE),
        ex("cable-single-arm-row", "Single-Arm Row", MuscleGroup.Back, Equipment.Handles, SINGLE),
        ex("cable-bent-over-row", "Bent-Over Row", MuscleGroup.Back, Equipment.LongBar, DOUBLE),
        ex("cable-close-grip-row", "Close-Grip Row", MuscleGroup.Back, Equipment.ShortBar, DOUBLE),
        ex("cable-wide-grip-row", "Wide-Grip Row", MuscleGroup.Back, Equipment.LongBar, DOUBLE),
        ex("cable-high-row", "High Row", MuscleGroup.Back, Equipment.Handles, DOUBLE),
        ex("cable-low-row", "Low Row", MuscleGroup.Back, Equipment.Handles, DOUBLE),
        ex("cable-lat-pulldown", "Lat Pulldown", MuscleGroup.Back, Equipment.LongBar, DOUBLE),
        ex("cable-straight-arm-pulldown", "Straight-Arm Pulldown", MuscleGroup.Back, Equipment.Handles, DOUBLE),
        ex("cable-face-pull", "Face Pull", MuscleGroup.Back, Equipment.Rope, DOUBLE, "rear delt pull"),
        ex("cable-reverse-fly", "Reverse Fly", MuscleGroup.Back, Equipment.Handles, EITHER),
        ex("cable-shrug", "Shrug", MuscleGroup.Back, Equipment.LongBar, DOUBLE),
        ex("cable-shoulder-press", "Shoulder Press", MuscleGroup.Shoulders, Equipment.Handles, DOUBLE, "overhead press"),
        ex("cable-single-arm-shoulder-press", "Single-Arm Shoulder Press", MuscleGroup.Shoulders, Equipment.Handles, SINGLE),
        ex("cable-lateral-raise", "Lateral Raise", MuscleGroup.Shoulders, Equipment.Handles, EITHER, "side raise"),
        ex("cable-front-raise", "Front Raise", MuscleGroup.Shoulders, Equipment.Handles, EITHER),
        ex("cable-rear-delt-fly", "Rear Delt Fly", MuscleGroup.Shoulders, Equipment.Handles, EITHER),
        ex("cable-upright-row", "Upright Row", MuscleGroup.Shoulders, Equipment.ShortBar, DOUBLE),
        ex("cable-y-raise", "Y Raise", MuscleGroup.Shoulders, Equipment.Handles, DOUBLE),
        ex("cable-external-rotation", "External Rotation", MuscleGroup.Shoulders, Equipment.Handles, SINGLE),
        ex("cable-internal-rotation", "Internal Rotation", MuscleGroup.Shoulders, Equipment.Handles, SINGLE),
        ex("cable-bicep-curl", "Bicep Curl", MuscleGroup.Arms, Equipment.Handles, EITHER),
        ex("cable-hammer-curl", "Hammer Curl", MuscleGroup.Arms, Equipment.Handles, EITHER),
        ex("cable-concentration-curl", "Concentration Curl", MuscleGroup.Arms, Equipment.Handles, SINGLE),
        ex("cable-preacher-curl", "Preacher Curl", MuscleGroup.Arms, Equipment.ShortBar, DOUBLE),
        ex("cable-bar-curl", "Bar Curl", MuscleGroup.Arms, Equipment.LongBar, DOUBLE),
        ex("cable-spider-curl", "Spider Curl", MuscleGroup.Arms, Equipment.Handles, DOUBLE),
        ex("cable-overhead-curl", "Overhead Curl", MuscleGroup.Arms, Equipment.Handles, DOUBLE),
        ex("cable-tricep-pushdown", "Tricep Pushdown", MuscleGroup.Arms, Equipment.Rope, DOUBLE),
        ex("cable-overhead-extension", "Overhead Extension", MuscleGroup.Arms, Equipment.Handles, EITHER, "tricep extension"),
        ex("cable-tricep-kickback", "Tricep Kickback", MuscleGroup.Arms, Equipment.Handles, SINGLE),
        ex("cable-skull-crusher", "Skull Crusher", MuscleGroup.Arms, Equipment.ShortBar, DOUBLE, "lying tricep extension"),
        ex("dip", "Dip", MuscleGroup.Arms, Equipment.Bodyweight, DOUBLE),
        ex("cable-squat", "Squat", MuscleGroup.Legs, Equipment.LongBar, DOUBLE),
        ex("cable-front-squat", "Front Squat", MuscleGroup.Legs, Equipment.LongBar, DOUBLE),
        ex("cable-goblet-squat", "Goblet Squat", MuscleGroup.Legs, Equipment.Handles, DOUBLE),
        ex("cable-belt-squat", "Belt Squat", MuscleGroup.Legs, Equipment.Belt, DOUBLE),
        ex("cable-romanian-deadlift", "Romanian Deadlift", MuscleGroup.Legs, Equipment.LongBar, DOUBLE, "RDL"),
        ex("cable-deadlift", "Deadlift", MuscleGroup.Legs, Equipment.LongBar, DOUBLE),
        ex("cable-sumo-deadlift", "Sumo Deadlift", MuscleGroup.Legs, Equipment.LongBar, DOUBLE),
        ex("cable-good-morning", "Good Morning", MuscleGroup.Legs, Equipment.LongBar, DOUBLE),
        ex("cable-lunge", "Lunge", MuscleGroup.Legs, Equipment.Handles, EITHER),
        ex("cable-reverse-lunge", "Reverse Lunge", MuscleGroup.Legs, Equipment.Handles, EITHER),
        ex("cable-walking-lunge", "Walking Lunge", MuscleGroup.Legs, Equipment.Handles, EITHER),
        ex("cable-split-squat", "Split Squat", MuscleGroup.Legs, Equipment.Handles, EITHER),
        ex("cable-bulgarian-split-squat", "Bulgarian Split Squat", MuscleGroup.Legs, Equipment.Handles, EITHER),
        ex("cable-step-up", "Step-Up", MuscleGroup.Legs, Equipment.Handles, EITHER),
        ex("cable-kickstand-rdl", "Kickstand RDL", MuscleGroup.Legs, Equipment.Handles, SINGLE),
        ex("cable-hip-thrust", "Hip Thrust", MuscleGroup.Legs, "${Equipment.LongBar},${Equipment.Bench}", DOUBLE),
        ex("cable-glute-bridge", "Glute Bridge", MuscleGroup.Legs, Equipment.LongBar, DOUBLE),
        ex("cable-glute-kickback", "Glute Kickback", MuscleGroup.Legs, Equipment.AnkleStrap, SINGLE),
        ex("cable-hip-abduction", "Hip Abduction", MuscleGroup.Legs, Equipment.AnkleStrap, SINGLE),
        ex("cable-hip-adduction", "Hip Adduction", MuscleGroup.Legs, Equipment.AnkleStrap, SINGLE),
        ex("cable-leg-curl", "Leg Curl", MuscleGroup.Legs, Equipment.AnkleStrap, SINGLE),
        ex("cable-leg-extension", "Leg Extension", MuscleGroup.Legs, Equipment.AnkleStrap, SINGLE),
        ex("cable-calf-raise", "Calf Raise", MuscleGroup.Legs, Equipment.LongBar, DOUBLE),
        ex("cable-standing-calf-raise", "Standing Calf Raise", MuscleGroup.Legs, Equipment.Handles, DOUBLE),
        ex("bodyweight-squat", "Bodyweight Squat", MuscleGroup.Legs, Equipment.Bodyweight, DOUBLE),
        ex("bodyweight-lunge", "Bodyweight Lunge", MuscleGroup.Legs, Equipment.Bodyweight, EITHER),
        ex("bodyweight-glute-bridge", "Bodyweight Glute Bridge", MuscleGroup.Legs, Equipment.Bodyweight, DOUBLE),
        ex("cable-pallof-press", "Pallof Press", MuscleGroup.Core, Equipment.Handles, SINGLE),
        ex("cable-anti-rotation-hold", "Anti-Rotation Hold", MuscleGroup.Core, Equipment.Handles, SINGLE),
        ex("cable-woodchop", "Woodchop", MuscleGroup.Core, Equipment.Handles, SINGLE),
        ex("cable-reverse-woodchop", "Reverse Woodchop", MuscleGroup.Core, Equipment.Handles, SINGLE),
        ex("cable-crunch", "Crunch", MuscleGroup.Core, Equipment.Rope, DOUBLE),
        ex("cable-standing-crunch", "Standing Crunch", MuscleGroup.Core, Equipment.Rope, DOUBLE),
        ex("cable-kneeling-crunch", "Kneeling Crunch", MuscleGroup.Core, Equipment.Rope, DOUBLE),
        ex("cable-russian-twist", "Russian Twist", MuscleGroup.Core, Equipment.Handles, DOUBLE),
        ex("cable-side-bend", "Side Bend", MuscleGroup.Core, Equipment.Handles, SINGLE),
        ex("plank", "Plank", MuscleGroup.Core, Equipment.Bodyweight, DOUBLE),
        ex("side-plank", "Side Plank", MuscleGroup.Core, Equipment.Bodyweight, SINGLE),
        ex("dead-bug", "Dead Bug", MuscleGroup.Core, Equipment.Bodyweight, DOUBLE),
        ex("hollow-hold", "Hollow Hold", MuscleGroup.Core, Equipment.Bodyweight, DOUBLE),
        ex("bird-dog", "Bird Dog", MuscleGroup.Core, Equipment.Bodyweight, SINGLE),
        ex("bicycle-crunch", "Bicycle Crunch", MuscleGroup.Core, Equipment.Bodyweight, DOUBLE),
        ex("mountain-climber", "Mountain Climber", MuscleGroup.Core, Equipment.Bodyweight, DOUBLE)
    )

    private const val SINGLE = "SINGLE"
    private const val DOUBLE = "DOUBLE"
    private const val EITHER = "EITHER"

    private fun ex(
        id: String,
        name: String,
        muscles: String,
        equipment: String,
        cables: String,
        aliases: String = ""
    ): ExerciseEntity {
        check(
            cables == CableConfiguration.SINGLE.name ||
                cables == CableConfiguration.DOUBLE.name ||
                cables == CableConfiguration.EITHER.name
        ) {
            "Invalid cable config for $id: $cables"
        }
        return ExerciseEntity(
            id = id,
            name = name,
            muscleGroups = muscles,
            equipment = equipment,
            defaultCableConfig = cables,
            aliases = aliases
        )
    }
}
