package com.ninthlevel.phoenix.data.local.seed

import com.ninthlevel.phoenix.domain.model.CableConfiguration
import com.ninthlevel.phoenix.domain.model.Equipment
import com.ninthlevel.phoenix.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultExercisesTest {

    private val slugRegex = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    @Test
    fun idsAreUniqueKebabSlugsWithoutUnderscores() {
        val ids = DefaultExercises.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue("id is not a kebab slug: $id", slugRegex.matches(id))
            assertFalse("id contains underscore: $id", id.contains('_'))
        }
    }

    @Test
    fun namesAreUnique() {
        val names = DefaultExercises.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun muscleAndEquipmentTokensAreInVocabulary() {
        DefaultExercises.all.forEach { exercise ->
            exercise.muscleGroups.split(",").map { it.trim() }.forEach { token ->
                assertTrue(
                    "${exercise.id} has unknown muscle group '$token'",
                    token in MuscleGroup.all
                )
            }
            exercise.equipment.split(",").map { it.trim() }.forEach { token ->
                assertTrue(
                    "${exercise.id} has unknown equipment '$token'",
                    token in Equipment.all
                )
            }
        }
    }

    @Test
    fun cableConfigIsAKnownEnumName() {
        val allowed = CableConfiguration.entries.map { it.name }.toSet()
        DefaultExercises.all.forEach { exercise ->
            assertTrue(
                "${exercise.id} has invalid cable config ${exercise.defaultCableConfig}",
                exercise.defaultCableConfig in allowed
            )
        }
    }

    @Test
    fun bodyweightRowsUseExactBodyweightToken() {
        DefaultExercises.all
            .filter { it.equipment.split(",").map { token -> token.trim() }.contains(Equipment.Bodyweight) }
            .forEach { exercise ->
                assertEquals(
                    "${exercise.id} must use equipment exactly '${Equipment.Bodyweight}'",
                    Equipment.Bodyweight,
                    exercise.equipment
                )
            }
    }

    @Test
    fun seedContainsNoMediaOrNetworkStrings() {
        DefaultExercises.all.forEach { exercise ->
            val blob = listOf(
                exercise.id,
                exercise.name,
                exercise.muscleGroups,
                exercise.equipment,
                exercise.defaultCableConfig,
                exercise.aliases
            ).joinToString(" ")
            assertFalse("${exercise.id} contains http", blob.contains("http", ignoreCase = true))
            assertFalse("${exercise.id} contains mux", blob.contains("mux", ignoreCase = true))
        }
    }
}
