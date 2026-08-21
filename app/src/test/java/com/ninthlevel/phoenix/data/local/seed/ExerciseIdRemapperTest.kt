package com.ninthlevel.phoenix.data.local.seed

import com.ninthlevel.phoenix.data.local.PRType
import com.ninthlevel.phoenix.data.local.seed.ExerciseIdRemapper.LegacyExercise
import com.ninthlevel.phoenix.data.local.seed.ExerciseIdRemapper.PersonalRecordSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseIdRemapperTest {

    @Test
    fun normalizeTreatsHyphenAndCaseAsEquivalent() {
        assertEquals("push up", ExerciseIdRemapper.normalizeName("Push-Up"))
        assertEquals("push up", ExerciseIdRemapper.normalizeName("  push up  "))
    }

    @Test
    fun cablePrefixIsAnAlternateKeyNotAFuzzySubstring() {
        val keys = ExerciseIdRemapper.catalogKeys("Cable Fly", "")
        assertTrue(keys.contains("cable fly"))
        assertTrue(keys.contains("fly"))
        assertFalse(keys.contains("sa rear delt fly bench supported"))
    }

    @Test
    fun mapsCatalogueIdsOntoSeedByExactNameOrAlias() {
        val legacy = listOf(
            LegacyExercise(id = "old-bench", name = "Bench Press"),
            LegacyExercise(id = "old-fly", name = "Cable Fly"),
            LegacyExercise(id = "old-rdl", name = "RDL"),
            LegacyExercise(id = "old-push", name = "Push Up"),
            LegacyExercise(id = "unrelated", name = "100s")
        )
        val map = ExerciseIdRemapper.buildOldToNewIdMap(legacy)
        assertEquals("cable-bench-press", map["old-bench"])
        assertEquals("cable-chest-fly", map["old-fly"])
        assertEquals("cable-romanian-deadlift", map["old-rdl"])
        assertEquals("push-up", map["old-push"])
        assertFalse(map.containsKey("unrelated"))
    }

    @Test
    fun doesNotMapDistinctExercisesViaSubstring() {
        val legacy = listOf(
            LegacyExercise(id = "belt", name = "Belt Squat"),
            LegacyExercise(id = "wide", name = "Wide-Grip Row"),
            LegacyExercise(id = "sa-fly", name = "SA Rear Delt Fly Bench Supported"),
            LegacyExercise(id = "kneeling", name = "Kneeling Row")
        )
        val map = ExerciseIdRemapper.buildOldToNewIdMap(legacy)
        assertEquals("cable-belt-squat", map["belt"])
        assertEquals("cable-wide-grip-row", map["wide"])
        assertFalse(map.containsKey("sa-fly"))
        assertFalse(map.containsKey("kneeling"))
    }

    @Test
    fun skipsAmbiguousLegacyRowsThatMatchTwoSeedExercises() {
        val legacy = listOf(
            LegacyExercise(id = "both", name = "Squat", aliases = "Bodyweight Squat")
        )
        val map = ExerciseIdRemapper.buildOldToNewIdMap(legacy)
        assertFalse(map.containsKey("both"))
    }

    @Test
    fun mergeMetadataORsFavoritesAndSumsTimesPerformed() {
        val legacy = listOf(
            LegacyExercise(
                id = "a",
                name = "Bench Press",
                isFavorite = true,
                timesPerformed = 3,
                lastPerformed = 10L
            ),
            LegacyExercise(
                id = "b",
                name = "Bench Press ",
                isFavorite = false,
                timesPerformed = 4,
                lastPerformed = 40L
            )
        )
        val map = ExerciseIdRemapper.buildOldToNewIdMap(legacy)
        val meta = ExerciseIdRemapper.mergeLegacyMetadata(legacy, map)
        val bench = meta.getValue("cable-bench-press")
        assertTrue(bench.isFavorite)
        assertEquals(7, bench.timesPerformed)
        assertEquals(40L, bench.lastPerformed)
    }

    @Test
    fun mergePersonalRecordsKeepsBestWeightAndVolumePerMode() {
        val oldToNew = mapOf("old-a" to "cable-bench-press", "old-b" to "cable-bench-press")
        val rows = listOf(
            pr(1, "old-a", weight = 40f, reps = 5, volume = 200f, type = PRType.MAX_WEIGHT, time = 1),
            pr(2, "old-b", weight = 50f, reps = 3, volume = 150f, type = PRType.MAX_WEIGHT, time = 2),
            pr(3, "old-a", weight = 30f, reps = 10, volume = 300f, type = PRType.MAX_VOLUME, time = 1),
            pr(4, "old-b", weight = 40f, reps = 6, volume = 240f, type = PRType.MAX_VOLUME, time = 2)
        )
        val merged = ExerciseIdRemapper.mergePersonalRecords(rows, oldToNew)
        assertEquals(2, merged.size)
        val weight = merged.single { it.prType == PRType.MAX_WEIGHT.name }
        val volume = merged.single { it.prType == PRType.MAX_VOLUME.name }
        assertEquals("cable-bench-press", weight.exerciseId)
        assertEquals(50f, weight.weightPerCableKg)
        assertEquals(2L, weight.id)
        assertEquals(300f, volume.volume)
        assertEquals(3L, volume.id)
    }

    @Test
    fun seedCatalogKeysDoNotCollideAcrossExercises() {
        val owners = mutableMapOf<String, MutableSet<String>>()
        DefaultExercises.all.forEach { exercise ->
            ExerciseIdRemapper.catalogKeys(exercise.name, exercise.aliases).forEach { key ->
                owners.getOrPut(key) { mutableSetOf() }.add(exercise.id)
            }
        }
        val collisions = owners.filter { it.value.size > 1 }
        assertTrue("seed keys collide: $collisions", collisions.isEmpty())
    }

    private fun pr(
        id: Long,
        exerciseId: String,
        weight: Float,
        reps: Int,
        volume: Float,
        type: PRType,
        time: Long
    ) = PersonalRecordSnapshot(
        id = id,
        exerciseId = exerciseId,
        weightPerCableKg = weight,
        reps = reps,
        timestamp = time,
        workoutMode = "OldSchool",
        prType = type.name,
        volume = volume
    )
}
