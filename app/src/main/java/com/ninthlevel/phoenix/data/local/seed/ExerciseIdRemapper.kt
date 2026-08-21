package com.ninthlevel.phoenix.data.local.seed

import com.ninthlevel.phoenix.data.local.ExerciseEntity
import com.ninthlevel.phoenix.data.local.PRType

/**
 * Maps v26 catalogue exercise ids onto the v27 slim seed by exact normalized name/alias.
 */
object ExerciseIdRemapper {

    data class LegacyExercise(
        val id: String,
        val name: String,
        val aliases: String = "",
        val isFavorite: Boolean = false,
        val timesPerformed: Int = 0,
        val lastPerformed: Long? = null
    )

    data class PersonalRecordSnapshot(
        val id: Long,
        val exerciseId: String,
        val weightPerCableKg: Float,
        val reps: Int,
        val timestamp: Long,
        val workoutMode: String,
        val prType: String,
        val volume: Float
    )

    fun normalizeName(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
    }

    fun catalogKeys(name: String, aliases: String): Set<String> {
        val keys = linkedSetOf<String>()
        fun add(value: String) {
            val normalized = normalizeName(value)
            if (normalized.isEmpty()) return
            keys.add(normalized)
            if (normalized.startsWith("cable ")) {
                val stripped = normalized.removePrefix("cable ").trim()
                if (stripped.isNotEmpty()) keys.add(stripped)
            }
        }
        add(name)
        aliases.split(',').forEach { add(it) }
        return keys
    }

    fun buildOldToNewIdMap(
        legacy: List<LegacyExercise>,
        seed: List<ExerciseEntity> = DefaultExercises.all
    ): Map<String, String> {
        val newIdsByKey = mutableMapOf<String, MutableSet<String>>()
        seed.forEach { exercise ->
            catalogKeys(exercise.name, exercise.aliases).forEach { key ->
                newIdsByKey.getOrPut(key) { mutableSetOf() }.add(exercise.id)
            }
        }

        val oldToNew = linkedMapOf<String, String>()
        legacy.forEach { row ->
            val matchedNewIds = linkedSetOf<String>()
            catalogKeys(row.name, row.aliases).forEach { key ->
                val ids = newIdsByKey[key] ?: return@forEach
                if (ids.size == 1) {
                    matchedNewIds.add(ids.first())
                }
            }
            if (matchedNewIds.size == 1) {
                oldToNew[row.id] = matchedNewIds.first()
            }
        }
        return oldToNew
    }

    fun mergeLegacyMetadata(
        legacy: List<LegacyExercise>,
        oldToNew: Map<String, String>
    ): Map<String, LegacyExercise> {
        val grouped = linkedMapOf<String, MutableList<LegacyExercise>>()
        legacy.forEach { row ->
            val newId = oldToNew[row.id] ?: return@forEach
            grouped.getOrPut(newId) { mutableListOf() }.add(row)
        }
        return grouped.mapValues { (newId, rows) ->
            LegacyExercise(
                id = newId,
                name = rows.first().name,
                aliases = "",
                isFavorite = rows.any { it.isFavorite },
                timesPerformed = rows.sumOf { it.timesPerformed },
                lastPerformed = rows.maxOfOrNull { it.lastPerformed ?: Long.MIN_VALUE }
                    ?.takeIf { it != Long.MIN_VALUE }
            )
        }
    }

    fun mergePersonalRecords(
        rows: List<PersonalRecordSnapshot>,
        oldToNew: Map<String, String>
    ): List<PersonalRecordSnapshot> {
        val remapped = rows.map { row ->
            row.copy(exerciseId = oldToNew[row.exerciseId] ?: row.exerciseId)
        }
        return remapped
            .groupBy { Triple(it.exerciseId, it.workoutMode, it.prType) }
            .values
            .map { group -> pickWinningPr(group) }
    }

    internal fun pickWinningPr(rows: List<PersonalRecordSnapshot>): PersonalRecordSnapshot {
        return when (rows.first().prType) {
            PRType.MAX_WEIGHT.name ->
                rows.maxWith(
                    compareBy<PersonalRecordSnapshot> { it.weightPerCableKg }
                        .thenBy { it.reps }
                        .thenBy { it.timestamp }
                )
            PRType.MAX_VOLUME.name ->
                rows.maxWith(
                    compareBy<PersonalRecordSnapshot> { it.volume }
                        .thenBy { it.timestamp }
                )
            else -> rows.maxBy { it.timestamp }
        }
    }
}
